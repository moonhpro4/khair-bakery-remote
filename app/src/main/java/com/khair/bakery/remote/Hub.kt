package com.khair.bakery.remote

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An error whose text (Arabic) can be shown to the user as is. */
class RemoteError(message: String) : Exception(message)

/**
 * Keeps one polling loop per paired computer. Replies to our requests are matched by id; shift events become
 * notifications. The computer is the only place the data lives - the phone just asks for it every time.
 */
object Hub {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lock = Any()
    private var started = false

    private val shopsFlow = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = shopsFlow

    private val onlineFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val online: StateFlow<Map<String, Boolean>> = onlineFlow

    fun init(ctx: Context) {
        synchronized(lock) {
            if (started) return
            started = true
            app = ctx.applicationContext
            Store.init(app)
            shopsFlow.value = Store.load()
        }
        Notifier.ensureChannels(app)
        for (s in shopsFlow.value) startPolling(s.id)
    }

    fun shop(id: String): Shop? = shopsFlow.value.firstOrNull { it.id == id }

    private fun setOnline(id: String, value: Boolean) {
        if (onlineFlow.value[id] != value) onlineFlow.value = onlineFlow.value + (id to value)
    }

    private fun updateCursor(id: String, cursor: String) {
        synchronized(lock) {
            val list = shopsFlow.value.map { if (it.id == id) it.copy(cursor = cursor) else it }
            shopsFlow.value = list
            Store.save(list)
        }
    }

    fun setNotify(id: String, value: Boolean) {
        synchronized(lock) {
            val list = shopsFlow.value.map { if (it.id == id) it.copy(notify = value) else it }
            shopsFlow.value = list
            Store.save(list)
        }
    }

    fun addShop(shop: Shop) {
        synchronized(lock) {
            val list = shopsFlow.value.filter { it.mcode != shop.mcode } + shop
            shopsFlow.value = list
            Store.save(list)
        }
        startPolling(shop.id)
    }

    fun removeShop(id: String) {
        val shop = shop(id)
        if (shop != null) {
            // best effort: tell the computer to forget this phone
            scope.launch { try { request(shop, "unpair") } catch (e: Exception) { } }
        }
        scope.launch {
            delay(1500)
            jobs.remove(id)?.cancel()
            synchronized(lock) {
                val list = shopsFlow.value.filter { it.id != id }
                shopsFlow.value = list
                Store.save(list)
            }
        }
    }

    private fun startPolling(id: String) {
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            while (isActive) {
                val shop = shop(id) ?: return@launch
                try {
                    val data = Relay.fetch(shop.mcode, shop.cursor)
                    setOnline(id, true)
                    val msgs = data.optJSONArray("messages") ?: JSONArray()
                    var cursor = shop.cursor
                    for (i in 0 until msgs.length()) {
                        val m = msgs.getJSONObject(i)
                        handle(shop, m)
                        cursor = m.opt("seq")?.toString() ?: cursor
                    }
                    if (cursor != shop.cursor) updateCursor(id, cursor)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RevokedException) {
                    setOnline(id, false)
                    delay(30000)
                } catch (e: Exception) {
                    setOnline(id, false)
                }
                delay(if (pending.isNotEmpty()) 400L else 2000L)
            }
        }
    }

    private fun handle(shop: Shop, m: JSONObject) {
        val p = m.optJSONObject("payload") ?: return
        if (p.optString("from") != "desktop") return
        when (m.optString("type")) {
            "reply" -> if (p.optString("device_id") == shop.deviceId) {
                pending.remove(p.optString("re"))?.complete(p)
            }
            "shift.event" -> if (shop.notify) {
                Notifier.shift(app, shop, p.optString("title"), p.optString("text"))
            }
        }
    }

    /** Sends a command to the computer and waits for its answer. Returns the answer's data. */
    suspend fun request(shop: Shop, type: String, args: JSONObject = JSONObject()): JSONObject {
        val rid = UUID.randomUUID().toString()
        val waiter = CompletableDeferred<JSONObject>()
        pending[rid] = waiter
        try {
            args.put("from", "mobile").put("device_id", shop.deviceId).put("token", shop.token).put("rid", rid)
            Relay.send(shop.mcode, type, args)
            val reply = withTimeoutOrNull(25000L) { waiter.await() }
                ?: throw RemoteError("لا يوجد رد من الكمبيوتر. تأكد أن برنامج الكاشير مفتوح ومتصل بالإنترنت.")
            if (!reply.optBoolean("ok")) throw RemoteError(reply.optString("error", "حدث خطأ"))
            return reply.optJSONObject("data") ?: JSONObject()
        } catch (e: RemoteError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RemoteError("تعذر الاتصال بالإنترنت")
        } finally {
            pending.remove(rid)
        }
    }

    /** Pairs this phone with a computer using its connection code and the PIN shown on the computer. */
    suspend fun pair(mcode: String, pin: String, deviceName: String): Shop {
        val deviceId = UUID.randomUUID().toString()
        val rid = UUID.randomUUID().toString()
        val payload = JSONObject().put("from", "mobile").put("device_id", deviceId).put("device_name", deviceName)
            .put("pin", pin).put("rid", rid)
        try {
            Relay.send(mcode, "pair.request", payload)
        } catch (e: RevokedException) {
            throw RemoteError("رمز الاتصال غير صحيح")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RemoteError("تعذر الاتصال بالإنترنت")
        }
        val deadline = System.currentTimeMillis() + 40000L
        var cursor = "0"
        while (System.currentTimeMillis() < deadline) {
            try {
                val data = Relay.fetch(mcode, cursor)
                val msgs = data.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until msgs.length()) {
                    val m = msgs.getJSONObject(i)
                    cursor = m.opt("seq")?.toString() ?: cursor
                    val p = m.optJSONObject("payload") ?: continue
                    if (m.optString("type") == "reply" && p.optString("from") == "desktop" && p.optString("re") == rid) {
                        if (!p.optBoolean("ok")) throw RemoteError(p.optString("error", "تعذر الربط"))
                        val d = p.getJSONObject("data")
                        return Shop(
                            id = UUID.randomUUID().toString(), name = d.optString("shop_name", "مخبز الخير"),
                            mcode = mcode, deviceId = deviceId, token = d.getString("token"), cursor = cursor
                        )
                    }
                }
            } catch (e: RemoteError) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: RevokedException) {
                throw RemoteError("رمز الاتصال غير صحيح")
            } catch (e: Exception) {
                // network hiccup: try again until the deadline
            }
            delay(700)
        }
        throw RemoteError("لا يوجد رد من الكمبيوتر. تأكد أن البرنامج مفتوح وأن الرمز لم تنتهِ صلاحيته.")
    }
}

package com.khair.bakery.remote

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One paired computer (= one shop). */
data class Shop(
    val id: String,
    val name: String,
    val mcode: String,
    val deviceId: String,
    val token: String,
    val cursor: String = "0",
    val notify: Boolean = true
)

object Store {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("khair", Context.MODE_PRIVATE)
    }

    fun load(): List<Shop> {
        val arr = JSONArray(prefs.getString("shops", "[]"))
        val out = ArrayList<Shop>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Shop(
                    id = o.getString("id"), name = o.getString("name"), mcode = o.getString("mcode"),
                    deviceId = o.getString("deviceId"), token = o.getString("token"),
                    cursor = o.optString("cursor", "0"), notify = o.optBoolean("notify", true)
                )
            )
        }
        return out
    }

    fun save(list: List<Shop>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("mcode", s.mcode).put("deviceId", s.deviceId)
                    .put("token", s.token).put("cursor", s.cursor).put("notify", s.notify)
            )
        }
        prefs.edit().putString("shops", arr.toString()).apply()
    }
}

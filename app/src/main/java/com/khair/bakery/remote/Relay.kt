package com.khair.bakery.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RevokedException : Exception("revoked")

/** Talks to the message relay over HTTPS. The phone's own code (m_...) is the bearer token. */
object Relay {
    const val API = "https://lockable-bridge-api.lovable.app/api/public/anything"

    private suspend fun call(code: String, query: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val c = URL(API + query).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.setRequestProperty("Authorization", "Bearer $code")
                c.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                if (c.responseCode == 401) throw RevokedException()
                if (c.responseCode !in 200..299) throw IOException("HTTP " + c.responseCode)
                val text = c.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally {
                c.disconnect()
            }
        }

    suspend fun fetch(code: String, after: String): JSONObject = call(code, "?after=$after", null)

    suspend fun send(code: String, type: String, payload: JSONObject) {
        val body = JSONObject().put("type", type).put("payload", payload).put("request_id", UUID.randomUUID().toString())
        call(code, "", body)
    }
}

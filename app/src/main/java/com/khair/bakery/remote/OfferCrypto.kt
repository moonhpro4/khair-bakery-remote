package com.khair.bakery.remote

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class Offer(val mcode: String, val name: String)

/**
 * Opens the pairing offer a computer leaves on the directory channel. Same scheme as remote_link.py:
 * key = PBKDF2-HMAC-SHA256(pin, salt, 100000) -> 64 bytes (enc 32 + mac 32);
 * body = plaintext XOR HMAC-SHA256(enc, nonce || counter_u32_be); tag = HMAC-SHA256(mac, nonce || body).
 */
object OfferCrypto {
    private const val ITERATIONS = 100_000

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data)
    }

    fun open(pin: String, salt: ByteArray, nonce: ByteArray, ct: ByteArray, tag: ByteArray): Offer? {
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 512)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            val enc = key.copyOfRange(0, 32)
            val mac = key.copyOfRange(32, 64)
            if (!MessageDigest.isEqual(hmac(mac, nonce + ct), tag)) return null
            val plain = ByteArray(ct.size)
            var pos = 0
            var counter = 0
            while (pos < ct.size) {
                val block = hmac(
                    enc,
                    nonce + byteArrayOf(
                        (counter ushr 24).toByte(), (counter ushr 16).toByte(),
                        (counter ushr 8).toByte(), counter.toByte(),
                    ),
                )
                var j = 0
                while (j < block.size && pos < ct.size) {
                    plain[pos] = (ct[pos].toInt() xor block[j].toInt()).toByte()
                    pos++
                    j++
                }
                counter++
            }
            val o = JSONObject(String(plain, Charsets.UTF_8))
            Offer(o.getString("mcode"), o.optString("name", "مخبز الخير"))
        } catch (e: Exception) {
            null
        }
    }
}

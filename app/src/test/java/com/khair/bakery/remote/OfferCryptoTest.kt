package com.khair.bakery.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/** The vector was produced by remote_link.make_offer() on the computer side. */
class OfferCryptoTest {
    private fun d(s: String): ByteArray = Base64.getDecoder().decode(s)

    private val salt = "AAECAwQFBgcICQoLDA0ODw=="
    private val nonce = "EBESExQVFhcYGRobHB0eHw=="
    private val ct = "9YA984lXLXL4Ip+QkdZVBLdEx9EK7GMn3VcG60eGdHX8DfIbAXMcHc19WouPujHhHAtNDXzK1xW/hyal"
    private val tag = "1wW4qNqqUDcWWMXrsckIHVYl6ysVQJR/WiGoNuWVU/U="

    @Test
    fun opensTheOfferMadeByThePythonSide() {
        val o = OfferCrypto.open("12345678", d(salt), d(nonce), d(ct), d(tag))
        assertEquals("m_testcode123", o?.mcode)
        assertEquals("مخبز الخير ١", o?.name)
    }

    @Test
    fun aWrongPinOpensNothing() {
        assertNull(OfferCrypto.open("12345679", d(salt), d(nonce), d(ct), d(tag)))
    }
}

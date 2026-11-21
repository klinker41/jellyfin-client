package com.makd.afinity.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlRedactionTest {

    @Test
    fun redactSecretsMasksSensitiveQueryParametersCaseInsensitively() {
        val raw =
            "https://jf.example.com/Items/1/Images/Primary?maxWidth=400&api_key=abc123&TOKEN=xyz&accessToken=tok99&apikey=key2"
        assertEquals(
            "https://jf.example.com/Items/1/Images/Primary?maxWidth=400&api_key=[REDACTED]&TOKEN=[REDACTED]&accessToken=[REDACTED]&apikey=[REDACTED]",
            redactSecrets(raw),
        )
    }

    @Test
    fun redactSecretsMasksAuthKeyPathSegmentsAndPreservesSafeText() {
        val raw = "POST /Auth/Keys/secretKeyValue?format=json failed"
        assertEquals("POST /Auth/Keys/[REDACTED]?format=json failed", redactSecrets(raw))

        val safe = "https://jf.example.com/Users/123/Items?SortBy=SortName"
        assertEquals(safe, redactSecrets(safe))
    }

    @Test
    fun redactUrlHandlesNullAndDelegatesNonNull() {
        assertEquals("null", redactUrl(null))
        assertEquals(
            "https://jf.example.com/stream?token=[REDACTED]",
            redactUrl("https://jf.example.com/stream?token=mySecretToken"),
        )
    }
}

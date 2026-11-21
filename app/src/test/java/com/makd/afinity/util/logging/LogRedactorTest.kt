package com.makd.afinity.util.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactorTest {

    @Test
    fun redactMasksJsonCredentialsQueryParamsAuthPathsBearerCookiesAndJwts() {
        val json = """{"accessToken":"secret123","refresh_token":"ref456","user":"alice"}"""
        assertEquals(
            """{"accessToken":"[REDACTED]","refresh_token":"[REDACTED]","user":"alice"}""",
            LogRedactor.redact(json),
        )

        val query = "GET https://jf.local/Items?X-Emby-Token=tok1&api_key=k2&limit=20"
        assertEquals(
            "GET https://jf.local/Items?X-Emby-Token=[REDACTED]&api_key=[REDACTED]&limit=20",
            LogRedactor.redact(query),
        )

        val authPath = "DELETE /Auth/Keys/deadbeef1234"
        assertEquals("DELETE /Auth/Keys/[REDACTED]", LogRedactor.redact(authPath))

        val bearer = "Authorization: Bearer abcDEF_123456.789"
        assertEquals("Authorization: Bearer [REDACTED]", LogRedactor.redact(bearer))

        val cookie = "Cookie: connect.sid=s%3Aabc123; jellyfin_session=xyz; theme=dark"
        assertEquals(
            "Cookie: connect.sid=[REDACTED]; jellyfin_session=[REDACTED]; theme=dark",
            LogRedactor.redact(cookie),
        )

        val jwt = "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig_value"
        assertEquals("token=[REDACTED]", LogRedactor.redact(jwt))
    }

    @Test
    fun redactPreservesEmptyAndCleanStrings() {
        assertEquals("", LogRedactor.redact(""))
        val clean = "Playback started for Dune (2021) at 1200ms"
        assertEquals(clean, LogRedactor.redact(clean))
    }
}

package com.makd.afinity.data.repository.server

import android.content.Context
import com.makd.afinity.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.sdk.model.ServerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerVersionSupportTest {

    @Test
    fun `isSupported enforces minimum Jellyfin 12_0_0 while allowing unknown or blank strings`() {
        assertEquals(ServerVersion(12, 0, 0), ServerVersionSupport.minimum)
        assertEquals("12.0", ServerVersionSupport.minimumDisplay)

        assertTrue(ServerVersionSupport.isSupported(null))
        assertTrue(ServerVersionSupport.isSupported("dev-build"))
        assertFalse(ServerVersionSupport.isSupported("10.10.3"))
        assertFalse(ServerVersionSupport.isSupported("11.9.9"))
        assertTrue(ServerVersionSupport.isSupported("12.0.0"))
        assertTrue(ServerVersionSupport.isSupported("12.1.4"))
    }

    @Test
    fun `unsupportedMessage formats known and unknown versions with minimum display string`() {
        val context: Context = mockk {
            every { getString(R.string.server_unsupported_version_unknown, "12.0") } returns
                "Unknown minimum 12.0"
            every { getString(R.string.server_unsupported_version, "10.10.3", "12.0") } returns
                "Version 10.10.3 requires 12.0"
        }

        assertEquals(
            "Unknown minimum 12.0",
            ServerVersionSupport.unsupportedMessage(context, null),
        )
        assertEquals(
            "Unknown minimum 12.0",
            ServerVersionSupport.unsupportedMessage(context, "   "),
        )
        assertEquals(
            "Version 10.10.3 requires 12.0",
            ServerVersionSupport.unsupportedMessage(context, "10.10.3"),
        )

        verify(exactly = 2) {
            context.getString(R.string.server_unsupported_version_unknown, "12.0")
        }
        verify(exactly = 1) {
            context.getString(R.string.server_unsupported_version, "10.10.3", "12.0")
        }
    }
}

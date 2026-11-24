package com.makd.afinity.ui.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceModeTest {

    @Test
    fun `isTelevision returns true when leanback system feature is present`() {
        val packageManager =
            mockk<PackageManager> {
                every { hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns true
            }
        val context = mockk<Context> { every { this@mockk.packageManager } returns packageManager }

        assertTrue(context.isTelevision())
    }

    @Test
    fun `isTelevision returns true when ui mode type is television`() {
        val packageManager =
            mockk<PackageManager> {
                every { hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
            }
        val uiModeManager =
            mockk<UiModeManager> {
                every { currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
            }
        val context =
            mockk<Context> {
                every { this@mockk.packageManager } returns packageManager
                every { getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
            }

        assertTrue(context.isTelevision())
    }

    @Test
    fun `isTelevision returns false on normal device`() {
        val packageManager =
            mockk<PackageManager> {
                every { hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
            }
        val uiModeManager =
            mockk<UiModeManager> {
                every { currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
            }
        val context =
            mockk<Context> {
                every { this@mockk.packageManager } returns packageManager
                every { getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
            }

        assertFalse(context.isTelevision())
    }

    @Test
    fun `isTelevision returns false when UiModeManager is unavailable`() {
        val packageManager =
            mockk<PackageManager> {
                every { hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
            }
        val context =
            mockk<Context> {
                every { this@mockk.packageManager } returns packageManager
                every { getSystemService(Context.UI_MODE_SERVICE) } returns null
            }

        assertFalse(context.isTelevision())
    }
}

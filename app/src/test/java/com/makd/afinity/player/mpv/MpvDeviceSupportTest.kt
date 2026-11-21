package com.makd.afinity.player.mpv

import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvDeviceSupportTest {

    private fun featureInfo(featureName: String, featureVersion: Int): FeatureInfo =
        mockk<FeatureInfo>(relaxed = true).apply {
            name = featureName
            version = featureVersion
        }

    private fun mockContext(
        hasVulkan: Boolean,
        vulkanRawVersion: Int?,
        hdrTypes: IntArray?,
        hasDisplayManager: Boolean = true,
    ): Context {
        val features =
            if (vulkanRawVersion != null) {
                arrayOf(
                    featureInfo(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, vulkanRawVersion),
                )
            } else {
                emptyArray()
            }
        val pm: PackageManager = mockk {
            every {
                hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x402000)
            } returns hasVulkan
            every { systemAvailableFeatures } returns features
        }
        val displayManager: DisplayManager? =
            if (!hasDisplayManager) {
                null
            } else if (hdrTypes == null) {
                mockk { every { getDisplay(Display.DEFAULT_DISPLAY) } returns null }
            } else {
                val mode: Display.Mode = mockk { every { supportedHdrTypes } returns hdrTypes }
                val display: Display = mockk { every { this@mockk.mode } returns mode }
                mockk { every { getDisplay(Display.DEFAULT_DISPLAY) } returns display }
            }
        return mockk {
            every { packageManager } returns pm
            every { getSystemService(DisplayManager::class.java) } returns displayManager
        }
    }

    @Test
    fun `capabilities decodes packed Vulkan version, HDR types, and signature`() {
        // 0x0040_3002 = major 1 ((1<<22)), minor 3 ((3<<12)), patch 2 -> "1.3.2"
        val rawVersion = (1 shl 22) or (3 shl 12) or 2
        val context =
            mockContext(
                hasVulkan = true,
                vulkanRawVersion = rawVersion,
                hdrTypes = intArrayOf(1, 2),
            )

        val caps = MpvDeviceSupport.capabilities(context)

        assertTrue(caps.vulkanSupported)
        assertEquals("1.3.2", caps.vulkanVersion)
        assertEquals(listOf(1, 2), caps.hdrTypes)
        assertEquals("vulkan=1.3.2;hdr=true", MpvDeviceSupport.signature(caps))
        assertTrue(MpvDeviceSupport.isVulkanVerified(caps, "vulkan=1.3.2;hdr=true"))
        assertFalse(MpvDeviceSupport.isVulkanVerified(caps, "vulkan=1.3.2;hdr=false"))
    }

    @Test
    fun `capabilities handles missing Vulkan feature, missing display manager, and unverified status`() {
        val context =
            mockContext(
                hasVulkan = false,
                vulkanRawVersion = null,
                hdrTypes = null,
                hasDisplayManager = false,
            )

        val caps = MpvDeviceSupport.capabilities(context)

        assertFalse(caps.vulkanSupported)
        assertNull(caps.vulkanVersion)
        assertTrue(caps.hdrTypes.isEmpty())
        assertEquals("vulkan=none;hdr=false", MpvDeviceSupport.signature(caps))
        assertFalse(MpvDeviceSupport.isVulkanVerified(caps, "vulkan=none;hdr=false"))
    }
}

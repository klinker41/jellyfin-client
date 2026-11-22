package com.makd.afinity.ui.components

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedNavigationDrawerTest {

    @Test
    fun `pinned drawer dimensions are slimmer than modal rail and inset by 16dp`() {
        assertEquals(240.dp, PinnedDrawerWidth)
        assertTrue(PinnedDrawerWidth < 360.dp)
        assertEquals(16.dp, PinnedDrawerInset)
        assertEquals(24.dp, PinnedDrawerCornerRadius)
        assertEquals(256.dp, PinnedDrawerStartOccupancy)
    }

    @Test
    fun `shouldPinNavigationDrawer returns false for phones and folded cover screens`() {
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 411.dp,
                windowHeight = 891.dp,
            ),
        )
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 344.dp,
                windowHeight = 840.dp,
            ),
        )
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 380.dp,
                windowHeight = 880.dp,
            ),
        )
    }

    @Test
    fun `shouldPinNavigationDrawer returns false for compact-height phone landscape`() {
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 891.dp,
                windowHeight = 411.dp,
            ),
        )
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 880.dp,
                windowHeight = 380.dp,
            ),
        )
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 891.dp,
                windowHeight = 499.dp,
            ),
        )
    }

    @Test
    fun `shouldPinNavigationDrawer returns false for narrow split-screen windows`() {
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 599.dp,
                windowHeight = 841.dp,
            ),
        )
    }

    @Test
    fun `shouldPinNavigationDrawer returns true for unfolded foldables and tablets`() {
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 600.dp,
                windowHeight = 841.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 673.dp,
                windowHeight = 841.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 827.dp,
                windowHeight = 853.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 841.dp,
                windowHeight = 673.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 841.dp,
                windowHeight = 500.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 800.dp,
                windowHeight = 1280.dp,
            ),
        )
        assertTrue(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = true,
                windowWidth = 1280.dp,
                windowHeight = 800.dp,
            ),
        )
    }

    @Test
    fun `shouldPinNavigationDrawer returns false when drawer setting is disabled`() {
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = false,
                windowWidth = 673.dp,
                windowHeight = 841.dp,
            ),
        )
        assertFalse(
            shouldPinNavigationDrawer(
                navigationDrawerEnabled = false,
                windowWidth = 1280.dp,
                windowHeight = 800.dp,
            ),
        )
    }

    @Test
    fun `computeBlurredLayerSizePx pads all sides and returns zero for non-positive dims`() {
        assertEquals(IntSize.Zero, computeBlurredLayerSizePx(0, 100, 20))
        assertEquals(IntSize.Zero, computeBlurredLayerSizePx(100, 0, 20))
        assertEquals(IntSize.Zero, computeBlurredLayerSizePx(-10, 100, 20))
        assertEquals(IntSize(240, 800), computeBlurredLayerSizePx(240, 800, -10))
        assertEquals(IntSize(280, 840), computeBlurredLayerSizePx(240, 800, 20))
    }

    @Test
    fun `computeClampedTintAlpha clamps out-of-range values`() {
        assertEquals(0f, computeClampedTintAlpha(-0.5f), 0.0001f)
        assertEquals(
            DefaultPinnedDrawerTintAlpha,
            computeClampedTintAlpha(DefaultPinnedDrawerTintAlpha),
            0.0001f,
        )
        assertEquals(1f, computeClampedTintAlpha(1.5f), 0.0001f)
    }
}

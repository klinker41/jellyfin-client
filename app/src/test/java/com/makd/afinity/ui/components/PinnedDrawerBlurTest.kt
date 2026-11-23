package com.makd.afinity.ui.components

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedDrawerBlurTest {

    @Test
    fun `computeDownscaledBlurLayerSizePx downscales dimensions and adds scaled padding`() {
        assertEquals(IntSize.Zero, computeDownscaledBlurLayerSizePx(0, 1000, 10))
        assertEquals(IntSize.Zero, computeDownscaledBlurLayerSizePx(400, 0, 10))
        assertEquals(IntSize.Zero, computeDownscaledBlurLayerSizePx(400, 1000, 10, scale = 0f))

        assertEquals(
            IntSize(171, 513),
            computeDownscaledBlurLayerSizePx(683, 2050, -5, PINNED_DRAWER_BLUR_DOWNSCALE),
        )
        assertEquals(
            IntSize(197, 539),
            computeDownscaledBlurLayerSizePx(683, 2050, 13, PINNED_DRAWER_BLUR_DOWNSCALE),
        )
    }
}

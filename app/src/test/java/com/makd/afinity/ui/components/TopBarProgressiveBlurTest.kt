package com.makd.afinity.ui.components

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarProgressiveBlurTest {

    @Test
    fun `quantizeBlurRadiusPx returns 0 below threshold and rounds to half pixels`() {
        assertEquals(0f, quantizeBlurRadiusPx(0f), 0.0001f)
        assertEquals(0f, quantizeBlurRadiusPx(0.49f), 0.0001f)
        assertEquals(0.5f, quantizeBlurRadiusPx(0.5f), 0.0001f)
        assertEquals(1.5f, quantizeBlurRadiusPx(1.4f), 0.0001f)
        assertEquals(2.0f, quantizeBlurRadiusPx(1.8f), 0.0001f)
    }

    @Test
    fun `computeTopBarBlurLayerSizePx downscales layer and returns zero for invalid bounds`() {
        assertEquals(IntSize.Zero, computeTopBarBlurLayerSizePx(0, 240f, 60f))
        assertEquals(IntSize.Zero, computeTopBarBlurLayerSizePx(1080, 0.5f, 60f))
        assertEquals(IntSize.Zero, computeTopBarBlurLayerSizePx(1080, 240f, 60f, scale = 0f))
        assertEquals(
            IntSize(270, 75),
            computeTopBarBlurLayerSizePx(1080, 240f, 60f, scale = TOP_BAR_BLUR_DOWNSCALE),
        )
        assertEquals(
            IntSize(519, 107),
            computeTopBarBlurLayerSizePx(2076, 366f, 60f, scale = TOP_BAR_BLUR_DOWNSCALE),
        )
    }

    @Test
    fun `computeTopBarScrimAlpha scales linearly with progress and clamps bounds`() {
        assertEquals(0f, computeTopBarScrimAlpha(0f, DefaultTopBarMaxScrimAlpha), 0.0001f)
        assertEquals(
            DefaultTopBarMaxScrimAlpha * 0.5f,
            computeTopBarScrimAlpha(0.5f, DefaultTopBarMaxScrimAlpha),
            0.0001f,
        )
        assertEquals(
            DefaultTopBarMaxScrimAlpha,
            computeTopBarScrimAlpha(1f, DefaultTopBarMaxScrimAlpha),
            0.0001f,
        )
        assertEquals(0f, computeTopBarScrimAlpha(-0.5f, DefaultTopBarMaxScrimAlpha), 0.0001f)
        assertEquals(
            DefaultTopBarMaxScrimAlpha,
            computeTopBarScrimAlpha(1.5f, DefaultTopBarMaxScrimAlpha),
            0.0001f,
        )
        assertEquals(0f, computeTopBarScrimAlpha(1f, -0.2f), 0.0001f)
        assertEquals(1f, computeTopBarScrimAlpha(1f, 2f), 0.0001f)
    }

    @Test
    fun `computeTopBarBlurHeightPx extends positive bar height and clamps negative extension`() {
        assertEquals(16.dp, DefaultTopBarBlurBottomExtension)
        assertEquals(
            288f,
            computeTopBarBlurHeightPx(barHeightPx = 240f, bottomExtensionPx = 48f),
            0.0001f,
        )
        assertEquals(
            240f,
            computeTopBarBlurHeightPx(barHeightPx = 240f, bottomExtensionPx = 0f),
            0.0001f,
        )
        assertEquals(
            240f,
            computeTopBarBlurHeightPx(barHeightPx = 240f, bottomExtensionPx = -24f),
            0.0001f,
        )
        assertEquals(
            0f,
            computeTopBarBlurHeightPx(barHeightPx = 0.5f, bottomExtensionPx = 48f),
            0.0001f,
        )
        assertEquals(
            0f,
            computeTopBarBlurHeightPx(barHeightPx = 0f, bottomExtensionPx = 48f),
            0.0001f,
        )
    }
}

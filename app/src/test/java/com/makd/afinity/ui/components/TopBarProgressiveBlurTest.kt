package com.makd.afinity.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `computeProgressiveBlurPasses returns empty for zero subthreshold radius or tiny height`() {
        assertTrue(computeProgressiveBlurPasses(0f, 240f).isEmpty())
        assertTrue(computeProgressiveBlurPasses(0.49f, 240f).isEmpty())
        assertTrue(computeProgressiveBlurPasses(60f, 0.5f).isEmpty())
        assertTrue(computeProgressiveBlurPasses(60f, 0f).isEmpty())
    }

    @Test
    fun `computeProgressiveBlurPasses emits 4 continuous semigroup passes at 60px radius`() {
        val maxRadiusPx = 60f
        val blurHeightPx = 240f
        val passes =
            computeProgressiveBlurPasses(maxRadiusPx = maxRadiusPx, blurHeightPx = blurHeightPx)

        assertEquals(4, passes.size)
        assertEquals(0f, passes.last().maskTopY, 0.0001f)
        assertEquals(blurHeightPx, passes.first().maskBottomY, 0.0001f)

        val sumOfSquares =
            passes.fold(0f) { acc, pass ->
                acc + pass.incrementalRadiusPx * pass.incrementalRadiusPx
            }
        assertEquals(maxRadiusPx * maxRadiusPx, sumOfSquares, 0.01f)

        for (i in 1 until passes.size) {
            assertTrue(passes[i].cumulativeRadiusPx > passes[i - 1].cumulativeRadiusPx)
        }
    }

    @Test
    fun `computeProgressiveBlurPasses skips subthreshold bands cleanly at small radii`() {
        for (radius in listOf(1f, 2f, 5f)) {
            val passes = computeProgressiveBlurPasses(maxRadiusPx = radius, blurHeightPx = 240f)
            assertTrue("Expected at least one pass for radius=$radius", passes.isNotEmpty())
            passes.forEach { pass ->
                assertTrue(pass.incrementalRadiusPx >= MIN_BLUR_RADIUS_PX)
                assertTrue(!pass.incrementalRadiusPx.isNaN())
                assertTrue(pass.maskBottomY > pass.maskTopY)
            }
        }
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

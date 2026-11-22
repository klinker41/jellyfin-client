package com.makd.afinity.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarScrollProgressTest {

    @Test
    fun `with hero slot returns 0 when unscrolled`() {
        val progress =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 1200,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(0f, progress, 0.0001f)
    }

    @Test
    fun `with hero slot ramps linearly up to hero threshold`() {
        val half =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 450,
                firstVisibleItemSizePx = 1200,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )
        val full =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 900,
                firstVisibleItemSizePx = 1200,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )
        val past =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 1100,
                firstVisibleItemSizePx = 1200,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(0.5f, half, 0.0001f)
        assertEquals(1f, full, 0.0001f)
        assertEquals(1f, past, 0.0001f)
    }

    @Test
    fun `with hero slot returns 1 once scrolled past first item`() {
        val progress =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(1f, progress, 0.0001f)
    }

    @Test
    fun `with hero slot shorter than threshold clamps effective threshold to hero height`() {
        val progress =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 300,
                firstVisibleItemSizePx = 600,
                hasHeroSlot = true,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(0.5f, progress, 0.0001f)
    }

    @Test
    fun `with hero slot and zero or negative threshold clamps cleanly to 1 when scrolled`() {
        val zeroThreshold =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 10,
                firstVisibleItemSizePx = 0,
                hasHeroSlot = true,
                heroThresholdPx = 0f,
                noHeroThresholdPx = 128f,
            )
        val negativeThreshold =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 10,
                firstVisibleItemSizePx = 0,
                hasHeroSlot = true,
                heroThresholdPx = -50f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(1f, zeroThreshold, 0.0001f)
        assertEquals(1f, negativeThreshold, 0.0001f)
    }

    @Test
    fun `without hero slot returns 0 at index 0 and ramps across index 1`() {
        val atZero =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 0,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )
        val atOneZeroOffset =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )
        val atOneHalf =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 64,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )
        val atTwo =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
            )

        assertEquals(0f, atZero, 0.0001f)
        assertEquals(0f, atOneZeroOffset, 0.0001f)
        assertEquals(0.5f, atOneHalf, 0.0001f)
        assertEquals(1f, atTwo, 0.0001f)
    }

    @Test
    fun `without hero slot and explicit rampItemIndex 0 ramps across index 0`() {
        val half =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 64,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
                rampItemIndex = 0,
            )
        val full =
            calculateTopBarScrollProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSizePx = 400,
                hasHeroSlot = false,
                heroThresholdPx = 900f,
                noHeroThresholdPx = 128f,
                rampItemIndex = 0,
            )

        assertEquals(0.5f, half, 0.0001f)
        assertEquals(1f, full, 0.0001f)
    }
}

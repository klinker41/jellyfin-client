package com.makd.afinity.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideSheetWindowTest {

    @Test
    fun `phone portrait and folded cover screen use bottom sheet`() {
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 411.dp,
                windowHeight = 891.dp,
            ),
        )
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 380.dp,
                windowHeight = 880.dp,
            ),
        )
    }

    @Test
    fun `landscape phones and folded screens use side sheet when enabled`() {
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 891.dp,
                windowHeight = 411.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 880.dp,
                windowHeight = 380.dp,
            ),
        )
    }

    @Test
    fun `unfolded foldable and tablet windows use side sheet in portrait and landscape`() {
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 600.dp,
                windowHeight = 841.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 673.dp,
                windowHeight = 841.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 827.dp,
                windowHeight = 853.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 841.dp,
                windowHeight = 841.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 841.dp,
                windowHeight = 673.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 800.dp,
                windowHeight = 1280.dp,
            ),
        )
        assertTrue(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 1280.dp,
                windowHeight = 800.dp,
            ),
        )
    }

    @Test
    fun `narrow portrait split-screen and sub-600dp windows use bottom sheet`() {
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 599.dp,
                windowHeight = 841.dp,
            ),
        )
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = true,
                windowWidth = 336.dp,
                windowHeight = 841.dp,
            ),
        )
    }

    @Test
    fun `disabling setting forces bottom sheet across all window sizes`() {
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = false,
                windowWidth = 891.dp,
                windowHeight = 411.dp,
            ),
        )
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = false,
                windowWidth = 673.dp,
                windowHeight = 841.dp,
            ),
        )
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = false,
                windowWidth = 841.dp,
                windowHeight = 673.dp,
            ),
        )
        assertFalse(
            shouldUseSideSheet(
                sideSheetEnabled = false,
                windowWidth = 1280.dp,
                windowHeight = 800.dp,
            ),
        )
    }
}

package com.makd.afinity.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.makd.afinity.navigation.LocalSideSheetEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SideSheetWindowRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `phone portrait defaults to bottom sheet`() {
        var result = true
        composeTestRule.setContent { result = shouldUseSideSheet() }
        composeTestRule.waitForIdle()
        assertFalse(result)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `phone landscape uses side sheet unless disabled`() {
        var enabledResult = false
        var disabledResult = true
        composeTestRule.setContent {
            enabledResult = shouldUseSideSheet()
            CompositionLocalProvider(LocalSideSheetEnabled provides false) {
                disabledResult = shouldUseSideSheet()
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(enabledResult)
        assertFalse(disabledResult)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `unfolded foldable uses side sheet unless disabled`() {
        var enabledResult = false
        var disabledResult = true
        composeTestRule.setContent {
            enabledResult = shouldUseSideSheet()
            CompositionLocalProvider(LocalSideSheetEnabled provides false) {
                disabledResult = shouldUseSideSheet()
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(enabledResult)
        assertFalse(disabledResult)
    }
}

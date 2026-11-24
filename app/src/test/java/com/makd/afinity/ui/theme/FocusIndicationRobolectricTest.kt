package com.makd.afinity.ui.theme

import android.app.Application
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.components.CardRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FocusIndicationRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `AFinityTheme provides FocusRingIndication via LocalIndication`() {
        var isFocusRingIndication = false

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                isFocusRingIndication = LocalIndication.current is FocusRingIndication
                Box(
                    modifier =
                        Modifier.size(80.dp).testTag("clickable_box").clickable(onClick = {}),
                ) {
                    Text("Item")
                }
            }
        }

        assertTrue(isFocusRingIndication)
        composeTestRule
            .onNodeWithTag("clickable_box")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("clickable_box").assertIsFocused()
    }

    @Test
    fun `focusOutline supports Material Card and indication-null clickable`() {
        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                Column {
                    Card(
                        onClick = {},
                        modifier =
                            Modifier.size(120.dp)
                                .testTag("focus_card")
                                .focusOutline(RoundedCornerShape(12.dp)),
                    ) {
                        Text("Card")
                    }

                    Box(
                        modifier =
                            Modifier.size(80.dp)
                                .testTag("no_indication_box")
                                .focusOutline(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                    ) {
                        Text("Header")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("focus_card")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("focus_card").assertIsFocused()

        composeTestRule
            .onNodeWithTag("no_indication_box")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("no_indication_box").assertIsFocused()
    }

    @Test
    fun `CardRow with focusRestorer renders items and accepts focus`() {
        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                CardRow {
                    items(3) { index ->
                        Card(
                            onClick = {},
                            modifier =
                                Modifier.size(100.dp).testTag("row_item_$index").focusOutline(),
                        ) {
                            Text("Item $index")
                        }
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("row_item_1")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("row_item_1").assertIsFocused()
    }
}

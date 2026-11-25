package com.makd.afinity.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.utils.LocalIsTelevision
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FocusIndicationRobolectricTest {

    private val touchModeResetRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                try {
                    base.evaluate()
                } finally {
                    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                }
            }
        }
    }

    val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(touchModeResetRule).around(composeTestRule)

    @Test
    fun `AFinityTheme provides FocusScaleIndication via LocalIndication`() {
        var isFocusScaleIndication = false

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                isFocusScaleIndication = LocalIndication.current is FocusScaleIndication
                Box(
                    modifier =
                        Modifier.size(80.dp).testTag("clickable_box").clickable(onClick = {}),
                ) {
                    Text("Item")
                }
            }
        }

        assertTrue(isFocusScaleIndication)
        composeTestRule
            .onNodeWithTag("clickable_box")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("clickable_box").assertIsFocused()
    }

    @Test
    fun `resolveFocusScale adapts to small buttons, medium cards, wide controls, and explicit overrides`() {
        assertEquals(1.14f, resolveFocusScale(widthDp = 48f, heightDp = 48f), 0.001f)
        assertEquals(1.14f, resolveFocusScale(widthDp = 64f, heightDp = 64f), 0.001f)
        assertEquals(1.06f, resolveFocusScale(widthDp = 140f, heightDp = 210f), 0.001f)
        assertEquals(1.03f, resolveFocusScale(widthDp = 320f, heightDp = 56f), 0.001f)
        assertEquals(
            1.09f,
            resolveFocusScale(widthDp = 140f, heightDp = 210f, explicitScale = 1.09f),
            0.001f,
        )
        assertEquals(
            1f,
            resolveFocusScale(widthDp = 48f, heightDp = 48f, explicitScale = 0.9f),
            0.001f,
        )
    }

    @Test
    fun `focusOutline supports Material Card, combinedClickable, and indication-null clickable`() {
        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
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
                            Modifier.size(100.dp)
                                .testTag("combined_box")
                                .focusOutline(RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = {}, onLongClick = {}),
                    ) {
                        Text("Combined")
                    }

                    Box(
                        modifier =
                            Modifier.size(80.dp)
                                .testTag("no_indication_box")
                                .focusOutline(
                                    shape = RoundedCornerShape(8.dp),
                                    strokeWidth = 4.dp,
                                    color = Color.Cyan,
                                )
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
            .onNodeWithTag("combined_box")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("combined_box").assertIsFocused()

        composeTestRule
            .onNodeWithTag("no_indication_box")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("no_indication_box").assertIsFocused()
    }

    @Test
    fun `CardRow with focusRestorer renders items and accepts focus`() {
        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
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

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `focus highlights are suppressed in touch mode on non-TV and shown on keyboard or TV`() {
        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var isTv by mutableStateOf(false)
        var outlineBounds: Rect? = null
        var indicationBounds: Rect? = null
        var outsideInsetPx = 0
        val blackArgb = Color.Black.toArgb()

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                outsideInsetPx = with(LocalDensity.current) { 2.dp.toPx().roundToInt() }

                CompositionLocalProvider(LocalIsTelevision provides isTv) {
                    Row(
                        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .testTag("forced_outline_btn")
                                    .onGloballyPositioned { outlineBounds = it.boundsInRoot() }
                                    .focusProperties { canFocus = true }
                                    .focusOutline(CircleShape)
                                    .clickable(onClick = {}),
                        ) {
                            Text("Outline")
                        }
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .testTag("forced_indication_btn")
                                    .onGloballyPositioned { indicationBounds = it.boundsInRoot() }
                                    .focusProperties { canFocus = true }
                                    .clickable(onClick = {}),
                        ) {
                            Text("Indication")
                        }
                    }
                }
            }
        }

        fun samplePixelOutsideTopEdge(bounds: Rect): Int {
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.advanceTimeBy(500)
            var pixel = 0
            composeTestRule.runOnIdle {
                val bitmap =
                    Bitmap.createBitmap(
                        hostView.width.coerceAtLeast(1),
                        hostView.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                try {
                    hostView.draw(Canvas(bitmap))
                    val x = bounds.center.x.roundToInt()
                    val y = (bounds.top - outsideInsetPx).roundToInt().coerceAtLeast(0)
                    pixel = bitmap.getPixel(x, y)
                } finally {
                    bitmap.recycle()
                }
            }
            return pixel
        }

        composeTestRule.waitForIdle()
        assertEquals(InputMode.Touch, inputModeManager.inputMode)
        val oBounds = outlineBounds
        val iBounds = indicationBounds
        assertNotNull(oBounds)
        assertNotNull(iBounds)

        // Touch mode on non-TV: focus is allowed by explicit override, but highlight must stay off.
        composeTestRule
            .onNodeWithTag("forced_outline_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("forced_outline_btn").assertIsFocused()
        assertEquals(blackArgb, samplePixelOutsideTopEdge(oBounds!!))

        composeTestRule
            .onNodeWithTag("forced_indication_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("forced_indication_btn").assertIsFocused()
        assertEquals(blackArgb, samplePixelOutsideTopEdge(iBounds!!))

        // TV mode in touch mode: highlight must render.
        isTv = true
        composeTestRule.waitForIdle()
        assertNotEquals(blackArgb, samplePixelOutsideTopEdge(iBounds))
        composeTestRule
            .onNodeWithTag("forced_outline_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("forced_outline_btn").assertIsFocused()
        assertNotEquals(blackArgb, samplePixelOutsideTopEdge(oBounds))

        // Non-TV in keyboard mode: highlight must render.
        isTv = false
        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeTestRule.waitForIdle()
        assertNotEquals(blackArgb, samplePixelOutsideTopEdge(oBounds))
        composeTestRule
            .onNodeWithTag("forced_indication_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("forced_indication_btn").assertIsFocused()
        assertNotEquals(blackArgb, samplePixelOutsideTopEdge(iBounds))
    }
}

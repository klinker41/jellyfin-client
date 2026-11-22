package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.utils.rememberTopBarScrollProgress
import com.makd.afinity.ui.utils.topBarScrollKey
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
class TopBarProgressiveBlurRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `scroll drives blur activation, layer recording, and cleanup cleanly`() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var blurState: TopBarBlurState
        lateinit var hostView: View
        var currentProgress = -1f
        var halfThresholdPx = 0
        var fullThresholdPx = 0
        var showSource by mutableStateOf(true)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                val density = LocalDensity.current
                halfThresholdPx = with(density) { 225.dp.toPx().roundToInt() }
                fullThresholdPx = with(density) { 450.dp.toPx().roundToInt() }

                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                val progress by rememberTopBarScrollProgress(listState, hasHeroSlot = true)
                currentProgress = progress
                blurState =
                    rememberTopBarBlurState(
                        progress = { progress },
                        scrollKey = { listState.topBarScrollKey() },
                    )

                BlurHarness(
                    listState = listState,
                    blurState = blurState,
                    showSource = showSource,
                )
            }
        }

        fun pumpDraw() {
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                val width = hostView.width.coerceAtLeast(1)
                val height = hostView.height.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                hostView.draw(Canvas(bitmap))
                bitmap.recycle()
            }
        }

        pumpDraw()
        assertEquals(0f, currentProgress, 0.001f)
        assertFalse(blurState.isBlurActive)
        assertFalse(blurState.hasRecordedContent)
        assertNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = halfThresholdPx) }
        }
        pumpDraw()
        assertEquals(0.5f, currentProgress, 0.02f)
        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(
            "Expected blurLayer.renderEffect to be set (progress=$currentProgress, hasRecorded=${blurState.hasRecordedContent}, srcPos=${blurState.sourcePositionInRoot}, bgPos=${blurState.backdropPositionInRoot})",
            blurState.blurLayer.renderEffect,
        )

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = fullThresholdPx) }
        }
        pumpDraw()
        assertEquals(1f, currentProgress, 0.001f)
        assertTrue(blurState.isBlurActive)
        assertNotNull(blurState.blurLayer.renderEffect)

        // Scrolling past threshold keeps progress at 1f and keeps blur active.
        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 1, scrollOffset = 120) }
        }
        pumpDraw()
        assertEquals(1f, currentProgress, 0.001f)
        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = 0) }
        }
        pumpDraw()
        assertEquals(0f, currentProgress, 0.001f)
        assertFalse(blurState.isBlurActive)
        assertFalse(blurState.hasRecordedContent)
        assertNull(blurState.blurLayer.renderEffect)

        // Detaching the source node while blur is active clears hasRecordedContent cleanly.
        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = fullThresholdPx) }
        }
        pumpDraw()
        assertTrue(blurState.hasRecordedContent)

        composeTestRule.runOnIdle { showSource = false }
        pumpDraw()
        assertFalse(blurState.hasRecordedContent)
        assertNull(blurState.blurLayer.renderEffect)
    }

    @Test
    @Config(qualifiers = "w840dp-h800dp")
    fun `renders cleanly at expanded tablet width`() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var blurState: TopBarBlurState
        lateinit var hostView: View
        var fullThresholdPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                fullThresholdPx = with(LocalDensity.current) { 450.dp.toPx().roundToInt() }
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                val progress by rememberTopBarScrollProgress(listState, hasHeroSlot = true)
                blurState = rememberTopBarBlurState(progress = { progress })

                BlurHarness(
                    listState = listState,
                    blurState = blurState,
                    showSource = true,
                )
            }
        }

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = fullThresholdPx) }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(
                    hostView.width.coerceAtLeast(1),
                    hostView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            hostView.draw(Canvas(bitmap))
            bitmap.recycle()
        }

        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)
    }

    @Test
    fun `renders cleanly with scrim disabled and transparent top bar fill`() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var blurState: TopBarBlurState
        lateinit var hostView: View
        var fullThresholdPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                fullThresholdPx = with(LocalDensity.current) { 450.dp.toPx().roundToInt() }
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                val progress by rememberTopBarScrollProgress(listState, hasHeroSlot = true)
                blurState =
                    rememberTopBarBlurState(
                        progress = { progress },
                        scrollKey = { listState.topBarScrollKey() },
                    )

                BlurHarness(
                    listState = listState,
                    blurState = blurState,
                    showSource = true,
                    maxScrimAlpha = 0f,
                )
            }
        }

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = fullThresholdPx) }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(
                    hostView.width.coerceAtLeast(1),
                    hostView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            hostView.draw(Canvas(bitmap))
            bitmap.recycle()
        }

        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)
    }

    @Composable
    private fun BlurHarness(
        listState: LazyListState,
        blurState: TopBarBlurState,
        showSource: Boolean,
        maxScrimAlpha: Float = DefaultTopBarMaxScrimAlpha,
    ) {
        val backgroundColor = MaterialTheme.colorScheme.background
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            if (showSource) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().topBarBlurSource(blurState),
                ) {
                    item(key = "hero") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(560.dp).background(Color.Red),
                        )
                    }
                    items(12) { index ->
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(140.dp)
                                    .background(if (index % 2 == 0) Color.Blue else Color.Green),
                        ) {
                            Text("Row $index")
                        }
                    }
                }
            }

            AfinityTopBar(
                title = { AfinityTopAppBarTitle("AFinity") },
                modifier =
                    Modifier.topBarBlurBackdrop(
                        blurState,
                        containerColor = backgroundColor,
                        maxScrimAlpha = maxScrimAlpha,
                    ),
                backgroundOpacity = { 0f },
            )
        }
    }
}

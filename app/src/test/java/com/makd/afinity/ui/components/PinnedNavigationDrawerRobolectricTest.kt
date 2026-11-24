package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.settings.SessionSwitcherViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.ui.utils.LocalIsTelevision
import io.mockk.every
import io.mockk.mockk
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PinnedNavigationDrawerRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `unfolded foldable pins slimmer 240dp card inset 16dp on left top and bottom`() {
        val sessionSwitcherViewModel = mockk<SessionSwitcherViewModel>(relaxed = true)
        every { sessionSwitcherViewModel.state } returns MutableStateFlow(SessionSwitcherState())

        var outerBounds: Rect? = null
        var cardBounds: Rect? = null
        var shouldPin = false
        var expectedLeftPx = 0f
        var expectedTopPx = 0f
        var expectedBottomPx = 0f
        var expectedWidthPx = 0f

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                expectedLeftPx = with(density) { PinnedDrawerInset.toPx() }
                expectedTopPx = with(density) { PinnedDrawerInset.toPx() }
                expectedBottomPx = with(density) { (841.dp - PinnedDrawerInset).toPx() }
                expectedWidthPx = with(density) { PinnedDrawerWidth.toPx() }
                shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)

                val blurState = rememberPinnedDrawerBlurState(enabled = { true })

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().pinnedDrawerBlurSource(blurState)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                    }

                    AppPinnedNavigationDrawer(
                        blurState = blurState,
                        modifier =
                            Modifier.align(Alignment.TopStart).onGloballyPositioned {
                                outerBounds = it.boundsInRoot()
                            },
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize().onGloballyPositioned {
                                    cardBounds = it.boundsInRoot()
                                },
                        ) {
                            AppNavigationDrawerContent(
                                currentDestination = null,
                                userName = "jklinker",
                                userProfileImageUrl = null,
                                serverName = "unraid-server",
                                connectionType = ConnectionType.REMOTE,
                                isAdmin = true,
                                isOffline = false,
                                favoritesCount = 2,
                                watchlistCount = 0,
                                isJellyseerrAuthenticated = false,
                                isAudiobookshelfAuthenticated = false,
                                hasLiveTvAccess = true,
                                librariesInDrawer = false,
                                libraries = emptyList(),
                                selectedLibraryId = null,
                                onDestinationClick = {},
                                onLibraryClick = {},
                                onSettingsClick = {},
                                onAddAccountClick = {},
                                onCloseDrawer = {},
                                isPinned = true,
                                sessionSwitcherViewModel = sessionSwitcherViewModel,
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(shouldPin)
        composeTestRule.onNodeWithText("jklinker").assertIsDisplayed()

        assertNotNull(outerBounds)
        val actualCard = cardBounds
        assertNotNull(actualCard)
        assertEquals(expectedLeftPx, actualCard!!.left, 1f)
        assertEquals(expectedTopPx, actualCard.top, 1f)
        assertEquals(expectedBottomPx, actualCard.bottom, 1f)
        assertEquals(expectedWidthPx, actualCard.width, 1f)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `pinned drawer records backdrop blur layer with padded sampling and clears cleanly`() {
        lateinit var hostView: View
        lateinit var blurState: PinnedDrawerBlurState
        var blurEnabled by mutableStateOf(true)
        var showSource by mutableStateOf(true)
        var cardWidthPx = 0
        var cardHeightPx = 0
        var blurPadPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                val density = LocalDensity.current
                cardWidthPx = with(density) { PinnedDrawerWidth.toPx().roundToInt() }
                cardHeightPx =
                    with(density) { (841.dp - PinnedDrawerInset * 2).toPx().roundToInt() }
                blurPadPx =
                    ceil(
                            with(density) { DefaultPinnedDrawerBlurRadius.toPx() } *
                                PINNED_DRAWER_BLUR_DOWNSCALE,
                        )
                        .toInt()

                blurState = rememberPinnedDrawerBlurState(enabled = { blurEnabled })

                Box(
                    modifier =
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    if (showSource) {
                        Box(modifier = Modifier.fillMaxSize().pinnedDrawerBlurSource(blurState)) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Magenta))
                        }
                    }

                    AppPinnedNavigationDrawer(
                        blurState = blurState,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) { Text("Pinned") }
                    }
                }
            }
        }

        fun pumpDraw() {
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
        }

        pumpDraw()
        assertTrue(blurState.isEnabled)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)
        val expectedLayerSize =
            computeDownscaledBlurLayerSizePx(cardWidthPx, cardHeightPx, blurPadPx)
        assertEquals(expectedLayerSize, blurState.blurLayer.size)

        composeTestRule.runOnIdle { blurEnabled = false }
        pumpDraw()
        assertFalse(blurState.isEnabled)
        assertFalse(blurState.hasRecordedContent)
        assertNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle { blurEnabled = true }
        pumpDraw()
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle { showSource = false }
        pumpDraw()
        assertFalse(blurState.hasRecordedContent)
        assertNull(blurState.blurLayer.renderEffect)

        // Late-attaching source after backdrop has drawn its fallback still wakes the backdrop.
        composeTestRule.runOnIdle { showSource = true }
        pumpDraw()
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `horizontal row starts to right of pinned drawer and scrolls behind it into 16dp margin`() {
        val windowWidth = 673.dp
        val expectedStart = PinnedDrawerStartOccupancy + CardDimensions.RowGutter
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(windowWidth)) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    listState = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HomeSectionHeader(
                            title = "Sci-Fi Essentials",
                            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
                        )
                        CardRow(
                            state = listState,
                            contentPadding = CardDimensions.RowContentPadding,
                            modifier = Modifier.fillMaxWidth().height(120.dp).testTag("card_row"),
                        ) {
                            items(10) { index ->
                                Box(
                                    modifier =
                                        Modifier.width(120.dp)
                                            .height(120.dp)
                                            .testTag("item_$index"),
                                ) {
                                    Text("Item $index")
                                }
                            }
                        }
                    }
                }
            }
        }

        val headerBounds = composeTestRule.onNodeWithText("Sci-Fi Essentials").getBoundsInRoot()
        assertDpEquals(expectedStart, headerBounds.left)

        val rowBounds = composeTestRule.onNodeWithTag("card_row").getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(windowWidth, rowBounds.right)

        val firstAtRest = composeTestRule.onNodeWithTag("item_0").getBoundsInRoot()
        assertDpEquals(expectedStart, firstAtRest.left)

        // Scroll so item_2 aligns to 272.dp, placing item_1 (140..260.dp) behind the drawer
        // and item_0 (8..128.dp) inside the 16.dp left margin and behind the drawer.
        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 2, scrollOffset = 0) }
        }
        composeTestRule.waitForIdle()

        val behindDrawerItem = composeTestRule.onNodeWithTag("item_1").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected item_1 to move behind drawer (< $PinnedDrawerStartOccupancy), got ${behindDrawerItem.left}",
            behindDrawerItem.left < PinnedDrawerStartOccupancy,
        )
        val leftMarginItem = composeTestRule.onNodeWithTag("item_0").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected item_0 to reach left 16dp margin (< $PinnedDrawerInset), got ${leftMarginItem.left}",
            leftMarginItem.left < PinnedDrawerInset,
        )
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `top app bar title starts clear of pinned drawer while top bar spans full width`() {
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    AfinityTopBar(
                        title = { AfinityTopAppBarTitle("Libraries") },
                        modifier = Modifier.fillMaxWidth().testTag("top_bar"),
                    )
                }
            }
        }

        val topBarBounds = composeTestRule.onNodeWithTag("top_bar").getBoundsInRoot()
        assertDpEquals(0.dp, topBarBounds.left)

        val titleBounds = composeTestRule.onNodeWithText("Libraries").getBoundsInRoot()
        assertTrue(
            "Expected top bar title to start clear of $PinnedDrawerStartOccupancy, got ${titleBounds.left}",
            titleBounds.left >= PinnedDrawerStartOccupancy,
        )
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `touches on pinned drawer card are consumed while 16dp margin and main pane receive clicks`() {
        var backgroundClicks = 0
        var drawerItemClicks = 0
        var leftMarginX = 0f
        var drawerItemX = 0f
        var drawerItemY = 0f
        var drawerEmptyX = 0f
        var drawerEmptyY = 0f
        var rightPaneX = 0f

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                leftMarginX = with(density) { 8.dp.toPx() }
                drawerItemX = with(density) { (PinnedDrawerInset + PinnedDrawerWidth / 2).toPx() }
                drawerItemY = with(density) { (PinnedDrawerInset + 24.dp).toPx() }
                drawerEmptyX = drawerItemX
                drawerEmptyY = with(density) { 400.dp.toPx() }
                rightPaneX = with(density) { (PinnedDrawerStartOccupancy + 40.dp).toPx() }
                val blurState = rememberPinnedDrawerBlurState(enabled = { true })

                Box(modifier = Modifier.fillMaxSize().testTag("root")) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().pinnedDrawerBlurSource(blurState).clickable {
                                backgroundClicks++
                            },
                    )

                    AppPinnedNavigationDrawer(
                        blurState = blurState,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth().height(48.dp).clickable {
                                        drawerItemClicks++
                                    },
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("root").performTouchInput {
            click(Offset(leftMarginX, drawerEmptyY))
        }
        assertEquals(1, backgroundClicks)

        // Sub-slop drag inside a clickable drawer item must still trigger the child click.
        composeTestRule.onNodeWithTag("root").performTouchInput {
            down(Offset(drawerItemX, drawerItemY))
            moveBy(Offset(2f, 2f))
            up()
        }
        assertEquals(1, drawerItemClicks)
        assertEquals(1, backgroundClicks)

        composeTestRule.onNodeWithTag("root").performTouchInput {
            click(Offset(drawerEmptyX, drawerEmptyY))
        }
        assertEquals(1, backgroundClicks)

        composeTestRule.onNodeWithTag("root").performTouchInput {
            click(Offset(rightPaneX, drawerEmptyY))
        }
        assertEquals(2, backgroundClicks)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `phone portrait does not pin navigation drawer`() {
        var shouldPin = true
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)
            }
        }
        assertFalse(shouldPin)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `phone landscape does not pin navigation drawer`() {
        var shouldPin = true
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)
            }
        }
        assertFalse(shouldPin)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `tablet landscape pins navigation drawer`() {
        var shouldPin = false
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)
            }
        }
        assertTrue(shouldPin)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television mode hides pinned drawer by default`() {
        var shouldPin = true
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)
                }
            }
        }
        assertFalse(shouldPin)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `pinned drawer uses elevated borderless tonal surface without perimeter outline`() {
        lateinit var hostView: View
        var cardBounds: Rect? = null
        var expectedContainerArgb = 0
        var backgroundArgb = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                expectedContainerArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
                backgroundArgb = MaterialTheme.colorScheme.background.toArgb()
                val blurState = rememberPinnedDrawerBlurState(enabled = { false })

                Box(
                    modifier =
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    AppPinnedNavigationDrawer(
                        blurState = blurState,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize().onGloballyPositioned {
                                    cardBounds = it.boundsInRoot()
                                },
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertNotNull(cardBounds)
        val bounds = cardBounds!!

        var interiorPixel = 0
        var leftEdgePixel = 0
        var rightEdgePixel = 0
        var bottomEdgePixel = 0

        composeTestRule.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(
                    hostView.width.coerceAtLeast(1),
                    hostView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            try {
                hostView.draw(Canvas(bitmap))
                val centerX = bounds.center.x.roundToInt()
                val centerY = bounds.center.y.roundToInt()
                interiorPixel = bitmap.getPixel(centerX, centerY)
                leftEdgePixel = bitmap.getPixel(bounds.left.roundToInt() + 1, centerY)
                rightEdgePixel = bitmap.getPixel(bounds.right.roundToInt() - 1, centerY)
                bottomEdgePixel = bitmap.getPixel(centerX, bounds.bottom.roundToInt() - 1)
            } finally {
                bitmap.recycle()
            }
        }

        assertEquals(expectedContainerArgb, interiorPixel)
        assertNotEquals(backgroundArgb, interiorPixel)
        assertEquals(interiorPixel, leftEdgePixel)
        assertEquals(interiorPixel, rightEdgePixel)
        assertEquals(interiorPixel, bottomEdgePixel)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `pinned navigation drawer header uses uniform container color without top gradient`() {
        lateinit var hostView: View
        var cardBounds: Rect? = null
        var expectedContainerArgb = 0
        var topSpacerOffsetPx = 0f
        var lowerBodyOffsetPx = 0f

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                val density = LocalDensity.current
                expectedContainerArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
                topSpacerOffsetPx = with(density) { 4.dp.toPx() }
                lowerBodyOffsetPx = with(density) { 80.dp.toPx() }
                val blurState = rememberPinnedDrawerBlurState(enabled = { false })

                Box(
                    modifier =
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    AppPinnedNavigationDrawer(
                        blurState = blurState,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        AppNavigationDrawerContent(
                            currentDestination = null,
                            userName = "jklinker",
                            userProfileImageUrl = null,
                            serverName = "unraid-server",
                            connectionType = ConnectionType.REMOTE,
                            isAdmin = false,
                            isOffline = false,
                            favoritesCount = 0,
                            watchlistCount = 0,
                            isJellyseerrAuthenticated = false,
                            isAudiobookshelfAuthenticated = false,
                            hasLiveTvAccess = false,
                            librariesInDrawer = false,
                            libraries = emptyList(),
                            selectedLibraryId = null,
                            sessionState = SessionSwitcherState(),
                            onDestinationClick = {},
                            onLibraryClick = {},
                            onSettingsClick = {},
                            onAddAccountClick = {},
                            onSwitchSession = { _, _ -> },
                            onForgetSession = {},
                            onCloseDrawer = {},
                            modifier =
                                Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() },
                            isPinned = true,
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertNotNull(cardBounds)
        val bounds = cardBounds!!

        var topHeaderPixel = 0
        var lowerBodyPixel = 0

        composeTestRule.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(
                    hostView.width.coerceAtLeast(1),
                    hostView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            try {
                hostView.draw(Canvas(bitmap))
                val centerX = bounds.center.x.roundToInt()
                topHeaderPixel =
                    bitmap.getPixel(centerX, (bounds.top + topSpacerOffsetPx).roundToInt())
                lowerBodyPixel =
                    bitmap.getPixel(centerX, (bounds.bottom - lowerBodyOffsetPx).roundToInt())
            } finally {
                bitmap.recycle()
            }
        }

        assertEquals(expectedContainerArgb, topHeaderPixel)
        assertEquals(expectedContainerArgb, lowerBodyPixel)
        assertEquals(lowerBodyPixel, topHeaderPixel)
    }

    private fun assertDpEquals(expected: Dp, actual: Dp, tolerance: Dp = 1.dp) {
        assertEquals(expected.value, actual.value, tolerance.value)
    }
}

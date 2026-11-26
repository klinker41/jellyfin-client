package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.navigation.LocalHeroStyle
import com.makd.afinity.tvlauncher.ui.TvLauncherTopBarActionsContent
import com.makd.afinity.ui.home.HERO_CAROUSEL_ROW_KEY
import com.makd.afinity.ui.home.HomeTvFocusTarget
import com.makd.afinity.ui.home.HomeTvHeroFocusScrollEffect
import com.makd.afinity.ui.home.HomeTvInitialFocusEffect
import com.makd.afinity.ui.home.HomeTvReturnFocusEffect
import com.makd.afinity.ui.home.HomeTvRowBox
import com.makd.afinity.ui.home.rememberHomeTvFocusState
import com.makd.afinity.ui.home.rememberHomeTvTopBarFocusState
import com.makd.afinity.ui.home.resolveInitialTvFocusTarget
import com.makd.afinity.ui.home.shouldShowHomeScrollToTop
import com.makd.afinity.ui.home.tvHeroPinBringIntoViewToTop
import com.makd.afinity.ui.item.components.shared.PlaybackSelectionButton
import com.makd.afinity.ui.item.components.shared.PrimaryPlaybackButton
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.settings.SessionSwitcherViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.ui.theme.focusOutline
import com.makd.afinity.ui.utils.LocalIsTelevision
import com.makd.afinity.ui.utils.LocalTopBarButtonsFocusable
import io.mockk.every
import io.mockk.mockk
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TvFocusNavigationRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `immersive top bar navigates down to hero and media cards and back up`() {
        val items = PreviewData.posterRow.take(3)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    AfinityScreen(
                        mode = AfinityScreenMode.Immersive,
                        topBar = {
                            AfinityTopBar(
                                title = {},
                                navigationIcon = {
                                    IconButton(
                                        onClick = {},
                                        modifier =
                                            Modifier.testTag("menu_btn").focusOutline(CircleShape),
                                    ) {
                                        Text("Menu")
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {},
                                        modifier =
                                            Modifier.testTag("profile_btn")
                                                .focusOutline(CircleShape),
                                    ) {
                                        Text("Profile")
                                    }
                                },
                            )
                        },
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                                    Row(
                                        modifier =
                                            Modifier.align(Alignment.BottomStart).padding(24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("hero_play_btn")
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Play")
                                        }
                                    }
                                }
                            }
                            item {
                                CardRow(
                                    modifier = Modifier.fillMaxWidth().height(240.dp),
                                ) {
                                    items(items, key = { it.id }) { item ->
                                        MediaItemCard(
                                            item = item,
                                            onClick = {},
                                            cardWidth = 140.dp,
                                            modifier = Modifier.testTag("poster_${item.name}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("menu_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("menu_btn").assertIsFocused()

        composeTestRule.onNodeWithTag("menu_btn").performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithTag("hero_play_btn").assertIsFocused()

        composeTestRule.onNodeWithTag("hero_play_btn").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        val firstCard =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("poster_${items[0].name}")) and hasClickAction(),
            )
        firstCard.assertIsFocused()

        firstCard.performKeyInput { pressKey(Key.DirectionRight) }
        val secondCard =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("poster_${items[1].name}")) and hasClickAction(),
            )
        secondCard.assertIsFocused()

        secondCard.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.onNodeWithTag("hero_play_btn").assertIsFocused()

        composeTestRule.onNodeWithTag("hero_play_btn").performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule
            .onNode(
                (hasTestTag("menu_btn") or hasTestTag("profile_btn")) and isFocused(),
            )
            .assertExists()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television top-left menu button opens and closes modal drawer`() {
        val sessionSwitcherViewModel = mockk<SessionSwitcherViewModel>(relaxed = true)
        every { sessionSwitcherViewModel.state } returns MutableStateFlow(SessionSwitcherState())

        var shouldPin = true
        lateinit var scope: CoroutineScope
        lateinit var railState: WideNavigationRailState

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    shouldPin = shouldPinNavigationDrawer(navigationDrawerEnabled = true)
                    val state = rememberWideNavigationRailState().also { railState = it }
                    scope = rememberCoroutineScope()

                    Box(modifier = Modifier.fillMaxSize()) {
                        AfinityScreen(
                            mode = AfinityScreenMode.Immersive,
                            topBar = {
                                AfinityTopBar(
                                    title = {},
                                    navigationIcon = {
                                        IconButton(
                                            onClick = { scope.launch { state.expand() } },
                                            modifier = Modifier.testTag("menu_btn"),
                                        ) {
                                            Text("Menu")
                                        }
                                    },
                                )
                            },
                        ) {}

                        AppModalNavigationDrawer(state = state) {
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
                                onDestinationClick = {},
                                onLibraryClick = {},
                                onSettingsClick = {},
                                onAddAccountClick = {},
                                onCloseDrawer = { scope.launch { state.collapse() } },
                                isPinned = false,
                                sessionSwitcherViewModel = sessionSwitcherViewModel,
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertFalse(shouldPin)
        composeTestRule.onNodeWithText("jklinker").assertDoesNotExist()

        composeTestRule.onNodeWithTag("menu_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("jklinker").assertIsDisplayed()

        composeTestRule.runOnIdle { scope.launch { railState.collapse() } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("jklinker").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `focused MediaItemCard and ContinueWatchingCard scale outward beyond unfocused bounds`() {
        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var mediaCardBounds: Rect? = null
        var cwCardBounds: Rect? = null
        val blackArgb = Color.Black.toArgb()
        var outsideInsetPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                outsideInsetPx = with(LocalDensity.current) { 2.dp.toPx().roundToInt() }

                Row(
                    modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    MediaItemCard(
                        item = PreviewData.movie(),
                        onClick = {},
                        cardWidth = 140.dp,
                        modifier =
                            Modifier.testTag("media_card").onGloballyPositioned {
                                mediaCardBounds = it.boundsInRoot()
                            },
                    )
                    ContinueWatchingCard(
                        item = PreviewData.landscapeRow.first(),
                        onClick = {},
                        cardWidth = 220.dp,
                        modifier =
                            Modifier.testTag("cw_card").onGloballyPositioned {
                                cwCardBounds = it.boundsInRoot()
                            },
                    )
                }
            }
        }

        fun samplePixelOutsideTopEdge(bounds: Rect): Int {
            composeTestRule.waitForIdle()
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
        val mediaBounds = mediaCardBounds
        val cwBounds = cwCardBounds
        assertNotNull(mediaBounds)
        assertNotNull(cwBounds)

        val unfocusedMediaPixel = samplePixelOutsideTopEdge(mediaBounds!!)
        assertEquals(blackArgb, unfocusedMediaPixel)

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        val mediaTarget =
            composeTestRule.onNode(hasAnyAncestor(hasTestTag("media_card")) and hasClickAction())
        mediaTarget.performSemanticsAction(SemanticsActions.RequestFocus)
        mediaTarget.assertIsFocused()
        val focusedMediaPixel = samplePixelOutsideTopEdge(mediaBounds)
        assertNotEquals(blackArgb, focusedMediaPixel)

        val unfocusedCwPixel = samplePixelOutsideTopEdge(cwBounds!!)
        assertEquals(blackArgb, unfocusedCwPixel)

        val cwTarget =
            composeTestRule.onNode(hasAnyAncestor(hasTestTag("cw_card")) and hasClickAction())
        cwTarget.performSemanticsAction(SemanticsActions.RequestFocus)
        cwTarget.assertIsFocused()
        val focusedCwPixel = samplePixelOutsideTopEdge(cwBounds)
        assertNotEquals(blackArgb, focusedCwPixel)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `focusable child inside unfocused focusOutline container still scales on focus`() {
        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var childBounds: Rect? = null
        val blackArgb = Color.Black.toArgb()
        var outsideInsetPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                outsideInsetPx = with(LocalDensity.current) { 2.dp.toPx().roundToInt() }

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black)
                            .padding(32.dp)
                            .focusOutline(),
                ) {
                    Box(
                        modifier =
                            Modifier.size(48.dp)
                                .testTag("nested_child_btn")
                                .onGloballyPositioned { childBounds = it.boundsInRoot() }
                                .background(Color.White)
                                .clickable(onClick = {}),
                    )
                }
            }
        }

        fun samplePixelOutsideTopEdge(bounds: Rect): Int {
            composeTestRule.waitForIdle()
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
        val bounds = childBounds
        assertNotNull(bounds)
        assertEquals(blackArgb, samplePixelOutsideTopEdge(bounds!!))

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeTestRule
            .onNodeWithTag("nested_child_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("nested_child_btn").assertIsFocused()

        assertNotEquals(blackArgb, samplePixelOutsideTopEdge(bounds))
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `CardDimensions rowHeight adds vertical focus padding only on television`() {
        var tvRowHeight = 0.dp
        var nonTvRowHeight = 0.dp

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    tvRowHeight =
                        CardDimensions.rowHeight(
                            cardWidth = 140.dp,
                            aspectRatio = CardDimensions.ASPECT_RATIO_PORTRAIT,
                        )
                }
                CompositionLocalProvider(LocalIsTelevision provides false) {
                    nonTvRowHeight =
                        CardDimensions.rowHeight(
                            cardWidth = 140.dp,
                            aspectRatio = CardDimensions.ASPECT_RATIO_PORTRAIT,
                        )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(nonTvRowHeight + CardDimensions.TvRowFocusVerticalPadding * 2, tvRowHeight)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television mode defaults initial focus to first item of first content row below hero`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        val cwItems = PreviewData.landscapeRow.take(2)
        val movieItems = PreviewData.posterRow.take(2)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    inputModeManager.requestInputMode(InputMode.Keyboard)
                    ready = true
                }

                if (ready) {
                    CompositionLocalProvider(
                        LocalIsTelevision provides true,
                        LocalHeroStyle provides HeroStyle.FULL_BLEED,
                    ) {
                        val lazyListState = rememberLazyListState()
                        val target =
                            resolveInitialTvFocusTarget(
                                isOffline = false,
                                hiddenRows = HomeRow.defaultHidden,
                                loadedPlaybackRows =
                                    setOf(HomeRow.CONTINUE_WATCHING, HomeRow.NEXT_UP),
                                nextUpMerged = false,
                                combineLibrarySections = true,
                                showTvLauncherApps = false,
                                showLibrariesSection = false,
                                showContinueWatchingSlot = true,
                                hasContinueWatchingItems = true,
                                showContinueReadingSlot = false,
                                hasContinueReadingItems = false,
                                showContinueListeningSlot = false,
                                hasContinueListeningItems = false,
                                offlineNextUpNonEmpty = false,
                                offlineContinueReadingNonEmpty = false,
                                offlineContinueListeningNonEmpty = false,
                                downloadedRowKeys = emptyList(),
                                showNextUpSlot = false,
                                hasNextUpItems = false,
                                latestMoviesNonEmpty = true,
                                latestTvNonEmpty = false,
                                latestLibraryRowKeys = emptyList(),
                                fallbackOnlineRows = emptyList(),
                            )
                        val tvFocus = rememberHomeTvFocusState(target)
                        var isHeroFocused by remember { mutableStateOf(false) }

                        HomeTvInitialFocusEffect(lazyListState = lazyListState, tvFocus = tvFocus)
                        HomeTvHeroFocusScrollEffect(
                            isHeroFocused = isHeroFocused,
                            lazyListState = lazyListState,
                        )

                        AfinityScreen(
                            mode = AfinityScreenMode.Immersive,
                            topBar = {
                                AfinityTopBar(
                                    title = {},
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("drawer_btn")
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Menu")
                                        }
                                    },
                                )
                            },
                        ) {
                            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                                item(key = "hero_carousel") {
                                    HeroCarousel(
                                        items = PreviewData.posterRow.take(2),
                                        onWatchNowClick = {},
                                        onPlayTrailerClick = {},
                                        onMoreInformationClick = {},
                                        modifier =
                                            Modifier.onFocusChanged { isHeroFocused = it.hasFocus },
                                    )
                                }
                                item(key = "continue_watching") {
                                    HomeTvRowBox(rowKey = "continue_watching", tvFocus = tvFocus) {
                                        CardRow(
                                            modifier =
                                                Modifier.testTag("cw_row")
                                                    .fillMaxWidth()
                                                    .height(220.dp),
                                        ) {
                                            items(cwItems, key = { it.id }) { item ->
                                                ContinueWatchingCard(
                                                    item = item,
                                                    onClick = {},
                                                    cardWidth = 200.dp,
                                                    modifier = Modifier.testTag("cw_${item.name}"),
                                                )
                                            }
                                        }
                                    }
                                }
                                item(key = "latest_movies_combined") {
                                    HomeTvRowBox(
                                        rowKey = "latest_movies_combined",
                                        tvFocus = tvFocus,
                                    ) {
                                        CardRow(
                                            modifier =
                                                Modifier.testTag("movies_row")
                                                    .fillMaxWidth()
                                                    .height(240.dp),
                                        ) {
                                            items(movieItems, key = { it.id }) { item ->
                                                MediaItemCard(
                                                    item = item,
                                                    onClick = {},
                                                    cardWidth = 140.dp,
                                                    modifier =
                                                        Modifier.testTag("poster_${item.name}"),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_btn").assertIsNotFocused()
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsNotFocused()
        composeTestRule
            .onNode(
                hasAnyAncestor(hasTestTag("cw_${cwItems[0].name}")) and hasClickAction(),
            )
            .assertIsFocused()

        composeTestRule
            .onNode(
                hasAnyAncestor(hasTestTag("cw_${cwItems[0].name}")) and hasClickAction(),
            )
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasAnyAncestor(hasTestTag("cw_row")) and isFocused())
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television mode waits for playback rows to finish loading then focuses latest movies when empty`() {
        val movieItems = PreviewData.posterRow.take(2)
        var playbackLoaded by mutableStateOf(false)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalHeroStyle provides HeroStyle.FULL_BLEED,
                ) {
                    val lazyListState = rememberLazyListState()
                    val target =
                        resolveInitialTvFocusTarget(
                            isOffline = false,
                            hiddenRows = HomeRow.defaultHidden,
                            loadedPlaybackRows =
                                if (playbackLoaded)
                                    setOf(HomeRow.CONTINUE_WATCHING, HomeRow.NEXT_UP)
                                else emptySet(),
                            nextUpMerged = false,
                            combineLibrarySections = true,
                            showTvLauncherApps = false,
                            showLibrariesSection = false,
                            showContinueWatchingSlot = false,
                            hasContinueWatchingItems = false,
                            showContinueReadingSlot = false,
                            hasContinueReadingItems = false,
                            showContinueListeningSlot = false,
                            hasContinueListeningItems = false,
                            offlineNextUpNonEmpty = false,
                            offlineContinueReadingNonEmpty = false,
                            offlineContinueListeningNonEmpty = false,
                            downloadedRowKeys = emptyList(),
                            showNextUpSlot = false,
                            hasNextUpItems = false,
                            latestMoviesNonEmpty = true,
                            latestTvNonEmpty = false,
                            latestLibraryRowKeys = emptyList(),
                            fallbackOnlineRows = emptyList(),
                        )
                    val tvFocus = rememberHomeTvFocusState(target)
                    HomeTvInitialFocusEffect(lazyListState = lazyListState, tvFocus = tvFocus)

                    AfinityScreen(
                        mode = AfinityScreenMode.Immersive,
                        topBar = {
                            AfinityTopBar(
                                title = {},
                                navigationIcon = {
                                    IconButton(
                                        onClick = {},
                                        modifier =
                                            Modifier.testTag("drawer_btn")
                                                .focusOutline(CircleShape),
                                    ) {
                                        Text("Menu")
                                    }
                                },
                            )
                        },
                    ) {
                        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                            item(key = "hero_carousel") {
                                HeroCarousel(
                                    items = PreviewData.posterRow.take(2),
                                    onWatchNowClick = {},
                                    onPlayTrailerClick = {},
                                    onMoreInformationClick = {},
                                )
                            }
                            item(key = "latest_movies_combined") {
                                HomeTvRowBox(
                                    rowKey = "latest_movies_combined",
                                    tvFocus = tvFocus,
                                ) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                        items(movieItems, key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("poster_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("drawer_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("drawer_btn").assertIsFocused()

        playbackLoaded = true
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                hasAnyAncestor(hasTestTag("poster_${movieItems[0].name}")) and hasClickAction(),
            )
            .assertIsFocused()
        composeTestRule.onNodeWithTag("drawer_btn").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `moving focus to hero carousel scrolls LazyColumn all the way to the top`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        val moreInfoDesc = context.getString(R.string.hero_btn_more_info)
        val playTrailerDesc = context.getString(R.string.hero_btn_play_trailer)
        lateinit var listState: LazyListState

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalHeroStyle provides HeroStyle.FULL_BLEED,
                ) {
                    listState = rememberLazyListState()
                    var isHeroFocused by remember { mutableStateOf(false) }

                    HomeTvHeroFocusScrollEffect(
                        isHeroFocused = isHeroFocused,
                        lazyListState = listState,
                    )

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "hero_carousel") {
                            HeroCarousel(
                                items = PreviewData.posterRow.take(2),
                                onWatchNowClick = {},
                                onPlayTrailerClick = {},
                                onMoreInformationClick = {},
                                modifier = Modifier.onFocusChanged { isHeroFocused = it.hasFocus },
                            )
                        }
                        items(6) { rowIndex ->
                            CardRow(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                items(
                                    PreviewData.posterRow.take(2),
                                    key = { "${rowIndex}_${it.id}" },
                                ) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = {},
                                        cardWidth = 140.dp,
                                        modifier =
                                            Modifier.testTag("scroll_row_${rowIndex}_${item.name}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { listState.requestScrollToItem(index = 0, scrollOffset = 220) }
        composeTestRule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertNotEquals(0, listState.firstVisibleItemScrollOffset)

        val firstRowCard =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("scroll_row_0_${PreviewData.posterRow[0].name}")) and
                    hasClickAction(),
            )
        firstRowCard.performSemanticsAction(SemanticsActions.RequestFocus)
        firstRowCard.assertIsFocused()

        firstRowCard.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                (hasContentDescription(watchNowDesc) or
                    hasContentDescription(moreInfoDesc) or
                    hasContentDescription(playTrailerDesc)) and isFocused(),
            )
            .assertExists()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `non-television mode does not auto-focus first content row, detail play button, or action row`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val playLabel = context.getString(R.string.action_play)
        val playDesc = context.getString(R.string.cd_play)
        val movie = PreviewData.movie()

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides false) {
                    val target =
                        resolveInitialTvFocusTarget(
                            isOffline = false,
                            hiddenRows = HomeRow.defaultHidden,
                            loadedPlaybackRows = setOf(HomeRow.CONTINUE_WATCHING, HomeRow.NEXT_UP),
                            nextUpMerged = false,
                            combineLibrarySections = true,
                            showTvLauncherApps = false,
                            showLibrariesSection = false,
                            showContinueWatchingSlot = false,
                            hasContinueWatchingItems = false,
                            showContinueReadingSlot = false,
                            hasContinueReadingItems = false,
                            showContinueListeningSlot = false,
                            hasContinueListeningItems = false,
                            offlineNextUpNonEmpty = false,
                            offlineContinueReadingNonEmpty = false,
                            offlineContinueListeningNonEmpty = false,
                            downloadedRowKeys = emptyList(),
                            showNextUpSlot = false,
                            hasNextUpItems = false,
                            latestMoviesNonEmpty = true,
                            latestTvNonEmpty = false,
                            latestLibraryRowKeys = emptyList(),
                            fallbackOnlineRows = emptyList(),
                        )
                    val tvFocus = rememberHomeTvFocusState(target)
                    Column {
                        HomeTvRowBox(rowKey = "latest_movies_combined", tvFocus = tvFocus) {
                            CardRow(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                                item {
                                    MediaItemCard(
                                        item = movie,
                                        onClick = {},
                                        cardWidth = 120.dp,
                                        modifier = Modifier.testTag("non_tv_card"),
                                    )
                                }
                            }
                        }
                        PrimaryPlaybackButton(
                            item = movie,
                            nextEpisode = null,
                            selectedMediaSource = null,
                            isLoading = false,
                            onPlayRequested = { _, _ -> },
                        )
                        DetailActionRow(onPlay = {}) {}
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasAnyAncestor(hasTestTag("non_tv_card")) and hasClickAction())
            .assertIsNotFocused()
        composeTestRule.onNodeWithText(playLabel).assertIsNotFocused()
        composeTestRule.onNodeWithContentDescription(playDesc).assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `detail PrimaryPlaybackButton focuses play immediately on TV once enabled`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val playLabel = context.getString(R.string.action_play)
        val movie = PreviewData.movie()
        var isLoading by mutableStateOf(true)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.testTag("other_btn"),
                        ) {
                            Text("Back")
                        }
                        PrimaryPlaybackButton(
                            item = movie,
                            nextEpisode = null,
                            selectedMediaSource = null,
                            isLoading = isLoading,
                            onPlayRequested = { _, _ -> },
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("other_btn")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithTag("other_btn").assertIsFocused()

        isLoading = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(playLabel).assertIsFocused()
        composeTestRule.onNodeWithTag("other_btn").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `DetailActionRow focuses play FAB immediately on TV`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val playDesc = context.getString(R.string.cd_play)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    DetailActionRow(
                        onPlay = {},
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.testTag("leading_btn")) {
                            Text("Fav")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(playDesc).assertIsFocused()
        composeTestRule.onNodeWithTag("leading_btn").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `episode side sheet moves TV focus from background list to play button`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val resumeLabel = context.getString(R.string.episode_resume)
        val episode = PreviewData.episode(progress = 0.4f)
        var showSheet by mutableStateOf(false)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ContinueWatchingCard(
                            item = episode,
                            onClick = { showSheet = true },
                            cardWidth = 220.dp,
                            modifier = Modifier.testTag("bg_episode_card"),
                        )

                        if (showSheet) {
                            ModalSideSheet(
                                onDismissRequest = { showSheet = false },
                                title = episode.name,
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    PlaybackSelectionButton(
                                        item = episode,
                                        buttonText = resumeLabel,
                                        buttonIcon =
                                            androidx.compose.ui.res.painterResource(
                                                id = R.drawable.ic_player_play_filled,
                                            ),
                                        onPlayClick = {},
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.testTag("sheet_secondary_btn"),
                                    ) {
                                        Text("Favorite")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val bgCard =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("bg_episode_card")) and hasClickAction(),
            )
        bgCard.performSemanticsAction(SemanticsActions.RequestFocus)
        bgCard.assertIsFocused()

        bgCard.performClick()
        composeTestRule.waitForIdle()

        val resumeButton =
            composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(resumeLabel))
        resumeButton.assertIsFocused()

        resumeButton.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_secondary_btn").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `hero carousel stays scrolled to top when navigating across all hero buttons under PivotBringIntoViewSpec`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        val playTrailerDesc = context.getString(R.string.hero_btn_play_trailer)
        val moreInfoDesc = context.getString(R.string.hero_btn_more_info)
        lateinit var listState: LazyListState

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalHeroStyle provides HeroStyle.FULL_BLEED,
                    LocalBringIntoViewSpec provides TestPivotBringIntoViewSpec,
                ) {
                    listState = rememberLazyListState()
                    var isHeroFocused by remember { mutableStateOf(false) }

                    HomeTvHeroFocusScrollEffect(
                        isHeroFocused = isHeroFocused,
                        lazyListState = listState,
                    )

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "hero_carousel") {
                            HeroCarousel(
                                items = PreviewData.posterRow.take(2),
                                onWatchNowClick = {},
                                onPlayTrailerClick = {},
                                onMoreInformationClick = {},
                                modifier =
                                    Modifier.tvHeroPinBringIntoViewToTop().onFocusChanged {
                                        isHeroFocused = it.hasFocus
                                    },
                            )
                        }
                        items(6) { rowIndex ->
                            CardRow(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                items(
                                    PreviewData.posterRow.take(2),
                                    key = { "${rowIndex}_${it.id}" },
                                ) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = {},
                                        cardWidth = 140.dp,
                                        modifier =
                                            Modifier.testTag(
                                                "pivot_scroll_row_${rowIndex}_${item.name}",
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { listState.requestScrollToItem(index = 0, scrollOffset = 220) }
        composeTestRule.waitForIdle()
        assertNotEquals(0, listState.firstVisibleItemScrollOffset)

        val firstRowCard =
            composeTestRule.onNode(
                hasAnyAncestor(
                    hasTestTag("pivot_scroll_row_0_${PreviewData.posterRow[0].name}"),
                ) and hasClickAction(),
            )
        firstRowCard.performSemanticsAction(SemanticsActions.RequestFocus)
        firstRowCard.assertIsFocused()

        firstRowCard.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                (hasContentDescription(watchNowDesc) or
                    hasContentDescription(moreInfoDesc) or
                    hasContentDescription(playTrailerDesc)) and isFocused(),
            )
            .assertExists()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)

        // Move to the leftmost hero button (More Info), then traverse right and back left across
        // all three hero buttons while verifying LazyColumn remains pinned at (0, 0).
        composeTestRule
            .onNodeWithContentDescription(moreInfoDesc)
            .performSemanticsAction(
                SemanticsActions.RequestFocus,
            )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(moreInfoDesc).assertIsFocused()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)

        composeTestRule.onNodeWithContentDescription(moreInfoDesc).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(playTrailerDesc).assertIsFocused()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)

        composeTestRule.onNodeWithContentDescription(playTrailerDesc).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsFocused()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)

        composeTestRule.onNodeWithContentDescription(watchNowDesc).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(playTrailerDesc).assertIsFocused()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `focused grid card draws visible highlight overlay and border on top of dark content`() {
        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var cardBounds: Rect? = null
        var borderInsetPx = 0
        val darkMovie = PreviewData.movie(name = "GridHighlight").copy(images = AfinityImages())

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                borderInsetPx = with(LocalDensity.current) { 2.dp.toPx().roundToInt() }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp)) {
                    Box(
                        modifier =
                            Modifier.width(140.dp)
                                .testTag("grid_card_wrapper")
                                .onGloballyPositioned { cardBounds = it.boundsInRoot() },
                    ) {
                        MediaItemGridCard(item = darkMovie, onClick = {})
                    }
                }
            }
        }

        fun samplePixel(x: Float, y: Float): Int {
            composeTestRule.waitForIdle()
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
                    pixel =
                        bitmap.getPixel(
                            x.roundToInt().coerceIn(0, bitmap.width - 1),
                            y.roundToInt().coerceIn(0, bitmap.height - 1),
                        )
                } finally {
                    bitmap.recycle()
                }
            }
            return pixel
        }

        composeTestRule.waitForIdle()
        val bounds = cardBounds
        assertNotNull(bounds)
        val posterHeight = bounds!!.width / CardDimensions.ASPECT_RATIO_PORTRAIT
        // When focused, the poster scales 1.06x around its center, moving the top border outward by
        // 3% of the poster height.
        val scaledTopY = bounds.top - (posterHeight * 0.03f)
        val borderX = bounds.center.x
        val borderY = scaledTopY + borderInsetPx
        val interiorX = bounds.center.x
        val interiorY = bounds.top + posterHeight * 0.25f

        val unfocusedBorderPixel = samplePixel(borderX, borderY)
        val unfocusedInteriorPixel = samplePixel(interiorX, interiorY)

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        val cardNode =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("grid_card_wrapper")) and hasClickAction(),
            )
        cardNode.performSemanticsAction(SemanticsActions.RequestFocus)
        cardNode.assertIsFocused()

        val focusedBorderPixel = samplePixel(borderX, borderY)
        val focusedInteriorPixel = samplePixel(interiorX, interiorY)

        assertTrue(
            "Expected brighter focus border on top of focused grid card",
            android.graphics.Color.luminance(focusedBorderPixel) >
                android.graphics.Color.luminance(unfocusedBorderPixel) + 0.3f,
        )
        assertTrue(
            "Expected brighter sheen highlight on top of focused grid card",
            android.graphics.Color.luminance(focusedInteriorPixel) >
                android.graphics.Color.luminance(unfocusedInteriorPixel) + 0.05f,
        )
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `home screen sections always focus first item when navigating vertically`() {
        val row0Items = List(10) { index -> PreviewData.movie(name = "Row0Movie$index") }
        val row1Items = List(10) { index -> PreviewData.movie(name = "Row1Movie$index") }
        lateinit var row0State: LazyListState
        lateinit var row1State: LazyListState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    val tvFocus =
                        rememberHomeTvFocusState(
                            target = HomeTvFocusTarget(rowKey = "row_0", itemIndex = 0),
                        )
                    row0State = rememberCardRowListState()
                    row1State = rememberCardRowListState()
                    scope = rememberCoroutineScope()

                    Column(modifier = Modifier.fillMaxSize()) {
                        HomeTvRowBox(rowKey = "row_0", tvFocus = tvFocus) {
                            CardRow(
                                state = row0State,
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            ) {
                                items(row0Items, key = { "r0_${it.id}" }) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = {},
                                        cardWidth = 140.dp,
                                        modifier = Modifier.testTag("section0_${item.name}"),
                                    )
                                }
                            }
                        }
                        HomeTvRowBox(rowKey = "row_1", tvFocus = tvFocus) {
                            CardRow(
                                state = row1State,
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            ) {
                                items(row1Items, key = { "r1_${it.id}" }) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = {},
                                        cardWidth = 140.dp,
                                        modifier = Modifier.testTag("section1_${item.name}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun card(section: Int, index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(
                    hasTestTag("section${section}_Row${section}Movie$index"),
                ) and hasClickAction(),
            )

        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()

        card(0, 0).performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        card(0, 1).assertIsFocused()

        card(0, 1).performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        card(0, 2).assertIsFocused()

        card(0, 2).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        card(1, 0).assertIsFocused()

        card(1, 0).performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        card(1, 1).performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        card(1, 2).assertIsFocused()

        card(1, 2).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()

        // Navigate back down, scroll row 0 past item 0, then return Up and verify row 0 resets to
        // 0.
        card(0, 0).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        card(1, 0).assertIsFocused()

        composeTestRule.runOnIdle { scope.launch { row0State.scrollToItem(3) } }
        composeTestRule.waitForIdle()
        assertEquals(3, row0State.firstVisibleItemIndex)

        card(1, 0).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        assertEquals(0, row0State.firstVisibleItemIndex)
        card(0, 0).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `horizontal focus movement in home card row keeps vertical scroll offset stable under PivotBringIntoViewSpec`() {
        val rowItems = PreviewData.posterRow.take(5)
        lateinit var listState: LazyListState
        val observedVerticalPositions = mutableListOf<Pair<Int, Int>>()

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalBringIntoViewSpec provides TestPivotBringIntoViewSpec,
                ) {
                    listState = rememberLazyListState()
                    val tvFocus =
                        rememberHomeTvFocusState(
                            target = HomeTvFocusTarget(rowKey = "row_0", itemIndex = 1),
                        )

                    LaunchedEffect(listState) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex to
                                listState.firstVisibleItemScrollOffset
                        }
                            .collect { observedVerticalPositions.add(it) }
                    }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "header") {
                            Box(modifier = Modifier.fillMaxWidth().height(260.dp))
                        }
                        items(4, key = { "row_$it" }) { rowIndex ->
                            HomeTvRowBox(rowKey = "row_$rowIndex", tvFocus = tvFocus) {
                                CardRow(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                    items(rowItems, key = { "r${rowIndex}_${it.id}" }) { item ->
                                        MediaItemCard(
                                            item = item,
                                            onClick = {},
                                            cardWidth = 140.dp,
                                            modifier =
                                                Modifier.testTag(
                                                    "stable_r${rowIndex}_${item.name}",
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun card(index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("stable_r0_${rowItems[index].name}")) and
                    hasClickAction(),
            )

        composeTestRule.waitForIdle()
        card(0).assertIsFocused()
        val baselinePosition =
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        observedVerticalPositions.clear()

        for (index in 0..2) {
            card(index).performKeyInput { pressKey(Key.DirectionRight) }
            composeTestRule.waitForIdle()
            card(index + 1).assertIsFocused()
        }
        for (index in 3 downTo 1) {
            card(index).performKeyInput { pressKey(Key.DirectionLeft) }
            composeTestRule.waitForIdle()
            card(index - 1).assertIsFocused()
        }

        assertEquals(
            baselinePosition,
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
        )
        assertTrue(
            "Expected zero vertical scroll bounce during horizontal focus movement, saw $observedVerticalPositions",
            observedVerticalPositions.all { it == baselinePosition },
        )
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `focused TV top app bar buttons draw highlight beyond all four edges without clipping`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val menuDesc = context.getString(R.string.cd_open_navigation_menu)
        val diceDesc = context.getString(R.string.cd_random_item)
        val searchDesc = context.getString(R.string.cd_search_icon)
        val profileDesc = context.getString(R.string.cd_profile_icon)
        val settingsDesc = context.getString(R.string.tv_launcher_cd_system_settings)
        val backDesc = context.getString(R.string.cd_back)

        val offlineModeManager: OfflineModeManager = mockk {
            every { connectionType } returns MutableStateFlow(ConnectionType.LOCAL)
            every { connectivity } returns MutableStateFlow(Connectivity.Online)
        }
        val topAppBarViewModel =
            AfinityTopAppBarViewModel(
                offlineModeManager = offlineModeManager,
                serverRepository = mockk<ServerRepository>(relaxed = true),
                preferencesRepository = mockk<PreferencesRepository>(relaxed = true),
            )

        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var outsideInsetPx = 0f

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                outsideInsetPx = with(LocalDensity.current) { 1.dp.toPx() }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        AfinityTopAppBar(
                            title = {},
                            onMenuClick = {},
                            onSearchClick = {},
                            onRandomClick = {},
                            profile =
                                AppBarProfile(
                                    onClick = {},
                                    name = null,
                                    imageUrl = null,
                                ),
                            viewModel = topAppBarViewModel,
                            actions = {
                                TvLauncherTopBarActionsContent(
                                    isNetworkAvailable = true,
                                    formattedTime = "9:41",
                                    onOpenSystemSettings = {},
                                )
                            },
                        )

                        AfinityTopBar(
                            title = {},
                            navigationIcon = { AfinityBackButton(onClick = {}) },
                        )
                    }
                }
            }
        }

        fun samplePixel(x: Float, y: Float): Int {
            composeTestRule.waitForIdle()
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
                    pixel =
                        bitmap.getPixel(
                            x.roundToInt().coerceIn(0, bitmap.width - 1),
                            y.roundToInt().coerceIn(0, bitmap.height - 1),
                        )
                } finally {
                    bitmap.recycle()
                }
            }
            return pixel
        }

        val targets =
            listOf(
                "menu" to composeTestRule.onNodeWithContentDescription(menuDesc),
                "dice" to composeTestRule.onNodeWithContentDescription(diceDesc),
                "search" to composeTestRule.onNodeWithContentDescription(searchDesc),
                "profile" to composeTestRule.onNodeWithContentDescription(profileDesc),
                "system_settings" to composeTestRule.onNodeWithContentDescription(settingsDesc),
                "back" to composeTestRule.onNodeWithContentDescription(backDesc),
            )

        composeTestRule.waitForIdle()
        val baselineByLabel = targets.associate { (label, node) ->
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val samplePoints =
                listOf(
                    "top" to (bounds.center.x to (bounds.top - outsideInsetPx)),
                    "bottom" to (bounds.center.x to (bounds.bottom + outsideInsetPx - 1f)),
                    "left" to ((bounds.left - outsideInsetPx) to bounds.center.y),
                    "right" to ((bounds.right + outsideInsetPx - 1f) to bounds.center.y),
                )
            val unfocusedPixels = samplePoints.associate { (edge, pt) ->
                edge to samplePixel(pt.first, pt.second)
            }
            label to (samplePoints to unfocusedPixels)
        }

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }

        for ((label, node) in targets) {
            val (samplePoints, unfocusedPixels) = baselineByLabel.getValue(label)
            node.performSemanticsAction(SemanticsActions.RequestFocus)
            composeTestRule.mainClock.advanceTimeBy(500)
            node.assertIsFocused()

            for ((edge, pt) in samplePoints) {
                val focusedPixel = samplePixel(pt.first, pt.second)
                val unfocusedPixel = unfocusedPixels.getValue(edge)
                assertTrue(
                    "Expected $label $edge edge outside bounds to brighten when focused (was 0x${Integer.toHexString(unfocusedPixel)}, became 0x${Integer.toHexString(focusedPixel)})",
                    android.graphics.Color.luminance(focusedPixel) >
                        android.graphics.Color.luminance(unfocusedPixel) + 0.2f,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `TV home top bar is only reachable from hero carousel and blocks upward jump from lower rows`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        val playTrailerDesc = context.getString(R.string.hero_btn_play_trailer)
        val moreInfoDesc = context.getString(R.string.hero_btn_more_info)
        val row1Items = List(6) { index -> PreviewData.movie(name = "Row1Movie$index") }
        val row2Items = List(6) { index -> PreviewData.movie(name = "Row2Movie$index") }

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalHeroStyle provides HeroStyle.FULL_BLEED,
                ) {
                    val lazyListState = rememberLazyListState()
                    val topBarFocusState = rememberHomeTvTopBarFocusState()
                    SideEffect {
                        topBarFocusState.syncListState(
                            isShowingHomeList = true,
                            topmostRowKey = HERO_CAROUSEL_ROW_KEY,
                            isListLoading = false,
                        )
                    }
                    val tvFocus =
                        rememberHomeTvFocusState(
                            target =
                                HomeTvFocusTarget(rowKey = "latest_movies_combined", itemIndex = 1),
                            topBarFocusState = topBarFocusState,
                        )
                    var isHeroFocused by remember { mutableStateOf(false) }

                    HomeTvInitialFocusEffect(lazyListState = lazyListState, tvFocus = tvFocus)
                    HomeTvHeroFocusScrollEffect(
                        isHeroFocused = isHeroFocused,
                        lazyListState = lazyListState,
                    )

                    AfinityScreen(
                        mode = AfinityScreenMode.Immersive,
                        topBar = {
                            CompositionLocalProvider(
                                LocalTopBarButtonsFocusable provides
                                    topBarFocusState.canFocusTopBar,
                            ) {
                                AfinityTopBar(
                                    title = {},
                                    modifier =
                                        Modifier.onFocusChanged {
                                            topBarFocusState.onTopBarFocusChanged(it.hasFocus)
                                        },
                                    navigationIcon = {
                                        val canFocus = LocalTopBarButtonsFocusable.current
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("top_menu_btn")
                                                    .focusProperties { this.canFocus = canFocus }
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Menu")
                                        }
                                    },
                                    actions = {
                                        val canFocus = LocalTopBarButtonsFocusable.current
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("top_search_btn")
                                                    .focusProperties { this.canFocus = canFocus }
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Search")
                                        }
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("top_profile_btn")
                                                    .focusProperties { this.canFocus = canFocus }
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Profile")
                                        }
                                    },
                                )
                            }
                        },
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier =
                                Modifier.fillMaxSize()
                                    .focusRequester(tvFocus.listFocusRequester)
                                    .focusGroup(),
                        ) {
                            item(key = HERO_CAROUSEL_ROW_KEY) {
                                HeroCarousel(
                                    items = PreviewData.posterRow.take(2),
                                    onWatchNowClick = {},
                                    onPlayTrailerClick = {},
                                    onMoreInformationClick = {},
                                    modifier =
                                        Modifier.tvHeroPinBringIntoViewToTop().onFocusChanged {
                                            isHeroFocused = it.hasFocus
                                            if (it.hasFocus) {
                                                tvFocus.onRowFocused(HERO_CAROUSEL_ROW_KEY)
                                            } else {
                                                tvFocus.onRowUnfocused(HERO_CAROUSEL_ROW_KEY)
                                            }
                                        },
                                )
                            }
                            item(key = "latest_movies_combined") {
                                HomeTvRowBox(
                                    rowKey = "latest_movies_combined",
                                    tvFocus = tvFocus,
                                ) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                        items(row1Items, key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("r1_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "latest_tv_combined") {
                                HomeTvRowBox(rowKey = "latest_tv_combined", tvFocus = tvFocus) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                        items(row2Items, key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("r2_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun r1Card(index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("r1_Row1Movie$index")) and hasClickAction(),
            )

        fun r2Card(index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("r2_Row2Movie$index")) and hasClickAction(),
            )

        composeTestRule.waitForIdle()
        r1Card(0).assertIsFocused()

        // Navigate right across row 1 (under the top bar action buttons) and press Up:
        // focus must move to HeroCarousel, never straight into the top bar.
        for (i in 0..3) {
            r1Card(i).performKeyInput { pressKey(Key.DirectionRight) }
            composeTestRule.waitForIdle()
        }
        r1Card(4).assertIsFocused()
        r1Card(4).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()

        val heroFocusedNode =
            composeTestRule.onNode(
                (hasContentDescription(watchNowDesc) or
                    hasContentDescription(moreInfoDesc) or
                    hasContentDescription(playTrailerDesc)) and isFocused(),
            )
        heroFocusedNode.assertExists()
        composeTestRule.onNodeWithTag("top_menu_btn").assertIsNotFocused()
        composeTestRule.onNodeWithTag("top_search_btn").assertIsNotFocused()
        composeTestRule.onNodeWithTag("top_profile_btn").assertIsNotFocused()

        // Pressing Up from HeroCarousel (topmost row) now enters the top bar.
        heroFocusedNode.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                (hasTestTag("top_menu_btn") or
                    hasTestTag("top_search_btn") or
                    hasTestTag("top_profile_btn")) and isFocused(),
            )
            .assertExists()

        // Pressing Down from top bar returns into home content, and moving down to row 2
        // keeps upward navigation within content rows.
        composeTestRule
            .onNode(
                (hasTestTag("top_menu_btn") or
                    hasTestTag("top_search_btn") or
                    hasTestTag("top_profile_btn")) and isFocused(),
            )
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()

        r1Card(0).performSemanticsAction(SemanticsActions.RequestFocus)
        r1Card(0).assertIsFocused()
        r1Card(0).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        r2Card(0).assertIsFocused()

        r2Card(0).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        r1Card(0).assertIsFocused()
        composeTestRule.onNodeWithTag("top_menu_btn").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `TV home top bar without hero is only reachable from topmost content row`() {
        val row0Items = List(4) { index -> PreviewData.movie(name = "NoHero0_$index") }
        val row1Items = List(4) { index -> PreviewData.movie(name = "NoHero1_$index") }

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    val lazyListState = rememberLazyListState()
                    val topBarFocusState = rememberHomeTvTopBarFocusState()
                    SideEffect {
                        topBarFocusState.syncListState(
                            isShowingHomeList = true,
                            topmostRowKey = "row_0",
                            isListLoading = false,
                        )
                    }
                    val tvFocus =
                        rememberHomeTvFocusState(
                            target = HomeTvFocusTarget(rowKey = "row_0", itemIndex = 0),
                            topBarFocusState = topBarFocusState,
                        )

                    AfinityScreen(
                        mode = AfinityScreenMode.Immersive,
                        topBar = {
                            CompositionLocalProvider(
                                LocalTopBarButtonsFocusable provides
                                    topBarFocusState.canFocusTopBar,
                            ) {
                                AfinityTopBar(
                                    title = {},
                                    modifier =
                                        Modifier.onFocusChanged {
                                            topBarFocusState.onTopBarFocusChanged(it.hasFocus)
                                        },
                                    navigationIcon = {
                                        val canFocus = LocalTopBarButtonsFocusable.current
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier.testTag("nohero_menu_btn")
                                                    .focusProperties { this.canFocus = canFocus }
                                                    .focusOutline(CircleShape),
                                        ) {
                                            Text("Menu")
                                        }
                                    },
                                )
                            }
                        },
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier =
                                Modifier.fillMaxSize()
                                    .focusRequester(tvFocus.listFocusRequester)
                                    .focusGroup(),
                        ) {
                            item(key = "row_0") {
                                HomeTvRowBox(
                                    rowKey = "row_0",
                                    tvFocus = tvFocus,
                                    modifier = Modifier.padding(top = 80.dp),
                                ) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                                        items(row0Items, key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("nh0_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "row_1") {
                                HomeTvRowBox(rowKey = "row_1", tvFocus = tvFocus) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                                        items(row1Items, key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("nh1_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun card(row: Int, index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("nh${row}_NoHero${row}_$index")) and hasClickAction(),
            )

        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()

        // Move down to row 1, then press Up: must return to row 0 (not top bar).
        card(0, 0).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        card(1, 0).assertIsFocused()

        card(1, 0).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()
        composeTestRule.onNodeWithTag("nohero_menu_btn").assertIsNotFocused()

        // From row 0 (topmost row), pressing Up enters top bar.
        card(0, 0).performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nohero_menu_btn").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `returning to TV home screen restores focus to home list instead of top app bar`() {
        val row0Items = List(3) { index -> PreviewData.movie(name = "Ret0_$index") }
        val row1Items = List(3) { index -> PreviewData.movie(name = "Ret1_$index") }
        var showDetail by mutableStateOf(false)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                val saveableStateHolder = rememberSaveableStateHolder()

                CompositionLocalProvider(LocalIsTelevision provides true) {
                    if (showDetail) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            IconButton(
                                onClick = { showDetail = false },
                                modifier = Modifier.testTag("detail_back_btn"),
                            ) {
                                Text("Back")
                            }
                        }
                    } else {
                        saveableStateHolder.SaveableStateProvider("home") {
                            val lazyListState = rememberLazyListState()
                            val topBarFocusState = rememberHomeTvTopBarFocusState()
                            SideEffect {
                                topBarFocusState.syncListState(
                                    isShowingHomeList = true,
                                    topmostRowKey = "ret_hero",
                                    isListLoading = false,
                                )
                            }
                            val tvFocus =
                                rememberHomeTvFocusState(
                                    target = HomeTvFocusTarget(rowKey = "row_0", itemIndex = 1),
                                    topBarFocusState = topBarFocusState,
                                )

                            HomeTvInitialFocusEffect(
                                lazyListState = lazyListState,
                                tvFocus = tvFocus,
                            )
                            HomeTvReturnFocusEffect(tvFocus = tvFocus)

                            AfinityScreen(
                                mode = AfinityScreenMode.Immersive,
                                topBar = {
                                    CompositionLocalProvider(
                                        LocalTopBarButtonsFocusable provides
                                            topBarFocusState.canFocusTopBar,
                                    ) {
                                        AfinityTopBar(
                                            title = {},
                                            modifier =
                                                Modifier.onFocusChanged {
                                                    topBarFocusState.onTopBarFocusChanged(
                                                        it.hasFocus,
                                                    )
                                                },
                                            navigationIcon = {
                                                val canFocus = LocalTopBarButtonsFocusable.current
                                                IconButton(
                                                    onClick = {},
                                                    modifier =
                                                        Modifier.testTag("ret_menu_btn")
                                                            .focusProperties {
                                                                this.canFocus = canFocus
                                                            }
                                                            .focusOutline(CircleShape),
                                                ) {
                                                    Text("Menu")
                                                }
                                            },
                                        )
                                    }
                                },
                            ) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .focusRequester(tvFocus.listFocusRequester)
                                            .focusGroup(),
                                ) {
                                    item(key = "ret_hero") {
                                        Box(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .height(180.dp)
                                                    .padding(top = 80.dp, start = 24.dp),
                                        ) {
                                            IconButton(
                                                onClick = {},
                                                modifier =
                                                    Modifier.testTag("ret_hero_btn")
                                                        .onFocusChanged {
                                                            if (it.hasFocus) {
                                                                tvFocus.onRowFocused("ret_hero")
                                                            } else {
                                                                tvFocus.onRowUnfocused("ret_hero")
                                                            }
                                                        }
                                                        .focusOutline(CircleShape),
                                            ) {
                                                Text("Hero")
                                            }
                                        }
                                    }
                                    item(key = "row_0") {
                                        HomeTvRowBox(
                                            rowKey = "row_0",
                                            tvFocus = tvFocus,
                                        ) {
                                            CardRow(
                                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                            ) {
                                                items(row0Items, key = { it.id }) { item ->
                                                    MediaItemCard(
                                                        item = item,
                                                        onClick = {},
                                                        cardWidth = 140.dp,
                                                        modifier =
                                                            Modifier.testTag("ret0_${item.name}"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "row_1") {
                                        HomeTvRowBox(rowKey = "row_1", tvFocus = tvFocus) {
                                            CardRow(
                                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                            ) {
                                                items(row1Items, key = { it.id }) { item ->
                                                    MediaItemCard(
                                                        item = item,
                                                        onClick = {},
                                                        cardWidth = 140.dp,
                                                        modifier =
                                                            Modifier.testTag("ret1_${item.name}"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun card(row: Int, index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("ret${row}_Ret${row}_$index")) and hasClickAction(),
            )

        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()

        // Returning while on the initial target row (isTarget && isReturnTarget) must restore
        // focus to row 0 instead of falling back to hero or top bar.
        showDetail = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_back_btn").assertExists()

        showDetail = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ret_menu_btn").assertIsNotFocused()
        composeTestRule.onNodeWithTag("ret_hero_btn").assertIsNotFocused()
        card(0, 0).assertIsFocused()

        // Move focus down to row 1, then navigate to a detail route and return.
        card(0, 0).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        card(1, 0).assertIsFocused()

        showDetail = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_back_btn").assertExists()

        showDetail = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ret_menu_btn").assertIsNotFocused()
        composeTestRule.onNodeWithTag("ret_hero_btn").assertIsNotFocused()
        card(1, 0).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `home scroll to top fab is never composed and cannot steal focus in TV mode`() {
        val rows =
            (0 until 8).map { rowIdx ->
                List(2) { itemIdx -> PreviewData.movie(name = "Stt${rowIdx}_$itemIdx") }
            }
        lateinit var lazyListState: LazyListState

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    lazyListState = rememberLazyListState()
                    val topBarFocusState = rememberHomeTvTopBarFocusState()
                    val showScrollToTop by
                        remember(lazyListState, topBarFocusState.isTelevision) {
                            computedStateOf {
                                shouldShowHomeScrollToTop(
                                    isTelevision = topBarFocusState.isTelevision,
                                    firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                                )
                            }
                        }
                    val tvFocus =
                        rememberHomeTvFocusState(
                            target = HomeTvFocusTarget("row_0", 0),
                            topBarFocusState = topBarFocusState,
                        )
                    HomeTvInitialFocusEffect(lazyListState = lazyListState, tvFocus = tvFocus)

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = lazyListState,
                            modifier =
                                Modifier.fillMaxSize()
                                    .focusRequester(tvFocus.listFocusRequester)
                                    .focusGroup(),
                        ) {
                            items(rows.size, key = { "row_$it" }) { rowIdx ->
                                HomeTvRowBox(rowKey = "row_$rowIdx", tvFocus = tvFocus) {
                                    CardRow(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                                        items(rows[rowIdx], key = { it.id }) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {},
                                                cardWidth = 140.dp,
                                                modifier = Modifier.testTag("stt_${item.name}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showScrollToTop,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            FloatingActionButton(
                                onClick = {},
                                modifier = Modifier.testTag("scroll_to_top_fab"),
                            ) {
                                Text("Top")
                            }
                        }
                    }
                }
            }
        }

        fun card(row: Int, index: Int) =
            composeTestRule.onNode(
                hasAnyAncestor(hasTestTag("stt_Stt${row}_$index")) and hasClickAction(),
            )

        composeTestRule.waitForIdle()
        card(0, 0).assertIsFocused()
        composeTestRule.onNodeWithTag("scroll_to_top_fab").assertDoesNotExist()

        for (row in 0 until 6) {
            card(row, 0).performKeyInput { pressKey(Key.DirectionDown) }
            composeTestRule.waitForIdle()
        }

        assertTrue(
            "Expected list to scroll past index 3, got ${lazyListState.firstVisibleItemIndex}",
            lazyListState.firstVisibleItemIndex > 3,
        )
        composeTestRule.onNodeWithTag("scroll_to_top_fab").assertDoesNotExist()
        card(6, 0).assertIsFocused()

        card(6, 0).performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        card(6, 1).assertIsFocused()
        composeTestRule.onNodeWithTag("scroll_to_top_fab").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `home scroll to top fab appears after scrolling past index 3 in non-TV mode`() {
        lateinit var lazyListState: LazyListState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalIsTelevision provides false) {
                    lazyListState = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    val topBarFocusState = rememberHomeTvTopBarFocusState()
                    val showScrollToTop by
                        remember(lazyListState, topBarFocusState.isTelevision) {
                            computedStateOf {
                                shouldShowHomeScrollToTop(
                                    isTelevision = topBarFocusState.isTelevision,
                                    firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                                )
                            }
                        }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                            items(10, key = { it }) { index ->
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                    Text("Row $index")
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showScrollToTop,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            FloatingActionButton(
                                onClick = {},
                                modifier = Modifier.testTag("scroll_to_top_fab"),
                            ) {
                                Text("Top")
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scroll_to_top_fab").assertDoesNotExist()

        composeTestRule.runOnIdle { scope.launch { lazyListState.scrollToItem(4) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scroll_to_top_fab").assertIsDisplayed()
    }
}

private val TestPivotBringIntoViewSpec =
    object : BringIntoViewSpec {
        private val parentFraction = 0.3f
        private val childFraction = 0f

        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float {
            val leading = offset
            val trailing = offset + size
            val itemSize = kotlin.math.abs(trailing - leading)
            val childSmallerThanParent = itemSize <= containerSize
            val initialTarget = parentFraction * containerSize - childFraction * itemSize
            val spaceAvailable = containerSize - initialTarget
            val targetForLeadingEdge =
                if (childSmallerThanParent && spaceAvailable < itemSize) {
                    containerSize - itemSize
                } else {
                    initialTarget
                }
            return leading - targetForLeadingEdge
        }
    }

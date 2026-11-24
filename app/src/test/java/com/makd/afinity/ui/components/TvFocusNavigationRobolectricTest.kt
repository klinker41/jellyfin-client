package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.LocalHeroStyle
import com.makd.afinity.ui.home.HomeTvHeroFocusScrollEffect
import com.makd.afinity.ui.home.HomeTvInitialFocusEffect
import com.makd.afinity.ui.home.HomeTvRowBox
import com.makd.afinity.ui.home.rememberHomeTvFocusState
import com.makd.afinity.ui.home.resolveInitialTvFocusTarget
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
}

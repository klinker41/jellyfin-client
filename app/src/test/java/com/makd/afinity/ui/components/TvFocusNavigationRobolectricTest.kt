package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.test.isEnabled
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
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.LocalHeroStyle
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.settings.SessionSwitcherViewModel
import com.makd.afinity.ui.theme.LocalFocusRingColor
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
    fun `focused MediaItemCard and ContinueWatchingCard render focus ring highlight`() {
        lateinit var hostView: View
        lateinit var inputModeManager: InputModeManager
        var mediaCardBounds: Rect? = null
        var cwCardBounds: Rect? = null
        var expectedRingArgb = 0
        var ringSampleInsetPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                inputModeManager = LocalInputModeManager.current
                expectedRingArgb = LocalFocusRingColor.current.toArgb()
                ringSampleInsetPx = with(LocalDensity.current) { 2.dp.toPx().roundToInt() }

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

        fun sampleTopEdgePixel(bounds: Rect): Int {
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
                    val y = (bounds.top + ringSampleInsetPx).roundToInt()
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

        val unfocusedMediaPixel = sampleTopEdgePixel(mediaBounds!!)
        assertNotEquals(expectedRingArgb, unfocusedMediaPixel)

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        val mediaTarget =
            composeTestRule.onNode(hasAnyAncestor(hasTestTag("media_card")) and hasClickAction())
        mediaTarget.performSemanticsAction(SemanticsActions.RequestFocus)
        mediaTarget.assertIsFocused()
        val focusedMediaPixel = sampleTopEdgePixel(mediaBounds)
        assertEquals(expectedRingArgb, focusedMediaPixel)

        val unfocusedCwPixel = sampleTopEdgePixel(cwBounds!!)
        assertNotEquals(expectedRingArgb, unfocusedCwPixel)

        val cwTarget =
            composeTestRule.onNode(hasAnyAncestor(hasTestTag("cw_card")) and hasClickAction())
        cwTarget.performSemanticsAction(SemanticsActions.RequestFocus)
        cwTarget.assertIsFocused()
        val focusedCwPixel = sampleTopEdgePixel(cwBounds)
        assertEquals(expectedRingArgb, focusedCwPixel)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television mode defaults initial focus to hero carousel play button instead of drawer button`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        val rowItems = PreviewData.posterRow.take(3)

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
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "hero_carousel") {
                                    HeroCarousel(
                                        items = PreviewData.posterRow.take(2),
                                        onWatchNowClick = {},
                                        onPlayTrailerClick = {},
                                        onMoreInformationClick = {},
                                    )
                                }
                                item(key = "media_row") {
                                    CardRow(
                                        modifier =
                                            Modifier.testTag("media_row")
                                                .fillMaxWidth()
                                                .height(240.dp),
                                    ) {
                                        items(rowItems, key = { it.id }) { item ->
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
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsFocused()
        composeTestRule.onNodeWithTag("drawer_btn").assertIsNotFocused()

        composeTestRule.onNodeWithContentDescription(watchNowDesc).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsNotFocused()
        composeTestRule
            .onNode(
                hasAnyAncestor(hasTestTag("media_row")) and hasClickAction() and isFocused(),
            )
            .assertExists()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `television mode moves focus to hero play button when carousel finishes loading`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)
        var showCarousel by mutableStateOf(false)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }

                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalHeroStyle provides HeroStyle.FULL_BLEED,
                ) {
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
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item(key = "hero_carousel") {
                                if (showCarousel) {
                                    HeroCarousel(
                                        items = PreviewData.posterRow.take(2),
                                        onWatchNowClick = {},
                                        onPlayTrailerClick = {},
                                        onMoreInformationClick = {},
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(320.dp))
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

        showCarousel = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsFocused()
        composeTestRule.onNodeWithTag("drawer_btn").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `peek hero carousel defaults focus to current slide play button on television`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)

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
                        LocalHeroStyle provides HeroStyle.PEEK,
                    ) {
                        HeroCarousel(
                            items = PreviewData.posterRow.take(3),
                            onWatchNowClick = {},
                            onPlayTrailerClick = {},
                            onMoreInformationClick = {},
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasContentDescription(watchNowDesc) and isEnabled())
            .assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun `non-television mode does not auto-focus hero carousel play button`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val watchNowDesc = context.getString(R.string.hero_btn_watch_now)

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
                        LocalIsTelevision provides false,
                        LocalHeroStyle provides HeroStyle.FULL_BLEED,
                    ) {
                        HeroCarousel(
                            items = PreviewData.posterRow.take(2),
                            onWatchNowClick = {},
                            onPlayTrailerClick = {},
                            onMoreInformationClick = {},
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(watchNowDesc).assertIsNotFocused()
    }
}

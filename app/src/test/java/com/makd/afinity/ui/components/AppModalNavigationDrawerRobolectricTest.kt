package com.makd.afinity.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.settings.SessionSwitcherViewModel
import com.makd.afinity.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppModalNavigationDrawerRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `phone portrait expanded drawer fills full 360dp rail width and height without trailing or top gap`() {
        assertDrawerContentMatchesRailBounds(expectedRailWidth = 360.dp)
    }

    @Test
    @Config(qualifiers = "w344dp-h840dp")
    fun `folded foldable narrow screen drawer fills full 344dp width without trailing gap`() {
        assertDrawerContentMatchesRailBounds(expectedRailWidth = 344.dp)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `phone landscape expanded drawer fills full 360dp rail width and height without trailing or top gap`() {
        assertDrawerContentMatchesRailBounds(expectedRailWidth = 360.dp)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `unfolded foldable expanded drawer fills full 360dp rail width without trailing gap`() {
        assertDrawerContentMatchesRailBounds(expectedRailWidth = 360.dp)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `expanded tablet drawer fills full 360dp rail width without trailing gap`() {
        assertDrawerContentMatchesRailBounds(expectedRailWidth = 360.dp)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `real AppNavigationDrawerContent renders edge to edge and hides when collapsed`() {
        val sessionSwitcherViewModel = mockk<SessionSwitcherViewModel>(relaxed = true)
        every { sessionSwitcherViewModel.state } returns MutableStateFlow(SessionSwitcherState())

        var railBounds: Rect? = null
        var contentBounds: Rect? = null
        var expectedRailWidthPx = 0
        lateinit var railState: WideNavigationRailState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                expectedRailWidthPx = with(LocalDensity.current) { 360.dp.toPx().roundToInt() }
                railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
                scope = rememberCoroutineScope()

                AppModalNavigationDrawer(
                    state = railState,
                    modifier = Modifier.onGloballyPositioned { railBounds = it.boundsInRoot() },
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
                        modifier =
                            Modifier.onGloballyPositioned { contentBounds = it.boundsInRoot() },
                        sessionSwitcherViewModel = sessionSwitcherViewModel,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("jklinker").assertIsDisplayed()

        val actualRailBounds = railBounds
        val actualContentBounds = contentBounds
        assertNotNull("Expected rail bounds to be measured", actualRailBounds)
        assertNotNull("Expected content bounds to be measured", actualContentBounds)
        assertEquals(expectedRailWidthPx.toFloat(), actualRailBounds!!.width, 1f)
        assertEquals(actualRailBounds, actualContentBounds)

        composeTestRule.runOnIdle { scope.launch { railState.collapse() } }
        composeTestRule.waitForIdle()

        assertEquals(WideNavigationRailValue.Collapsed, railState.currentValue)
        composeTestRule.onNodeWithText("jklinker").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `modal navigation drawer header uses uniform container color without top gradient`() {
        lateinit var hostView: View
        var contentBounds: Rect? = null
        var expectedContainerArgb = 0
        var topSpacerOffsetPx = 0f
        var lowerBodyOffsetPx = 0f

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                hostView = LocalView.current
                val density = LocalDensity.current
                expectedContainerArgb = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
                topSpacerOffsetPx = with(density) { 4.dp.toPx() }
                lowerBodyOffsetPx = with(density) { 80.dp.toPx() }

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
                    modifier = Modifier.onGloballyPositioned { contentBounds = it.boundsInRoot() },
                    isPinned = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        assertNotNull(contentBounds)
        val bounds = contentBounds!!

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

    private fun assertDrawerContentMatchesRailBounds(expectedRailWidth: Dp) {
        var railBounds: Rect? = null
        var contentBounds: Rect? = null
        var expectedWidthPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                expectedWidthPx =
                    with(LocalDensity.current) { expectedRailWidth.toPx().roundToInt() }
                val railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)

                AppModalNavigationDrawer(
                    state = railState,
                    modifier = Modifier.onGloballyPositioned { railBounds = it.boundsInRoot() },
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().onGloballyPositioned {
                                contentBounds = it.boundsInRoot()
                            },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val actualRailBounds = railBounds
        val actualContentBounds = contentBounds
        assertNotNull("Expected rail bounds to be measured", actualRailBounds)
        assertNotNull("Expected content bounds to be measured", actualContentBounds)
        assertTrue("Expected positive rail height", actualRailBounds!!.height > 0f)
        assertEquals(expectedWidthPx.toFloat(), actualRailBounds.width, 1f)
        assertEquals(actualRailBounds, actualContentBounds)
    }
}

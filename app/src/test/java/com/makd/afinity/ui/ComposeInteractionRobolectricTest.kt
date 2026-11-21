package com.makd.afinity.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.DetailPrimaryButton
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ComposeInteractionRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `DetailPrimaryButton fires onClick when enabled, ignores clicks when disabled, and shows loading indicator`() {
        var clickCount by mutableIntStateOf(0)
        var enabled by mutableStateOf(true)
        var loading by mutableStateOf(false)

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                DetailPrimaryButton(
                    text = "Watch Now",
                    onClick = { clickCount++ },
                    enabled = enabled,
                    isLoading = loading,
                )
            }
        }

        val button = composeTestRule.onNodeWithText("Watch Now")
        button.assertIsDisplayed()
        button.assertIsEnabled()
        button.performClick()
        assertEquals(1, clickCount)

        enabled = false
        composeTestRule.waitForIdle()
        button.assertIsNotEnabled()
        button.performClick()
        assertEquals(1, clickCount)

        composeTestRule.mainClock.autoAdvance = false
        loading = true
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(
                hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun `AfinityToggleChip updates selection semantics on click and respects enabled flag`() {
        var selected by mutableStateOf(false)
        var enabled by mutableStateOf(true)

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                AfinityToggleChip(
                    label = "4K HDR",
                    selected = selected,
                    onSelectedChange = { selected = it },
                    enabled = enabled,
                )
            }
        }

        val chip = composeTestRule.onNodeWithText("4K HDR")
        chip.assertIsDisplayed()
        chip.assertIsNotSelected()

        chip.performClick()
        composeTestRule.waitForIdle()
        chip.assertIsSelected()

        enabled = false
        composeTestRule.waitForIdle()
        chip.assertIsNotEnabled()
    }

    @Test
    fun `ActionButtonsRow triggers user data callbacks, updates icons, and disables watched without playable items`() {
        var trailerClicks = 0
        var watchlistClicks = 0
        var favoriteClicks = 0
        var watchedClicks = 0

        var inWatchlist by mutableStateOf(false)
        var movie by mutableStateOf(testMovie(played = false, favorite = false))
        var hasPlayableItems by mutableStateOf(true)

        val appContext = ApplicationProvider.getApplicationContext<Application>()
        val cdTrailer = appContext.getString(R.string.hero_btn_play_trailer)
        val cdWatchlistAdd = appContext.getString(R.string.cd_watchlist_add)
        val cdWatchlistRemove = appContext.getString(R.string.cd_watchlist_remove)
        val cdFavAdd = appContext.getString(R.string.cd_favorite_add)
        val cdFavRemove = appContext.getString(R.string.cd_favorite_remove)
        val cdWatchedMark = appContext.getString(R.string.cd_watched_mark)
        val cdWatchedUnmark = appContext.getString(R.string.cd_watched_unmark)

        composeTestRule.setContent {
            AFinityTheme(themeMode = ThemeMode.DARK.name, dynamicColor = false) {
                ActionButtonsRow(
                    item = movie,
                    isInWatchlist = inWatchlist,
                    hasTrailer = true,
                    downloadInfo = null,
                    onPlayTrailer = { trailerClicks++ },
                    onToggleWatchlist = {
                        watchlistClicks++
                        inWatchlist = !inWatchlist
                    },
                    onShufflePlay = {},
                    onToggleFavorite = {
                        favoriteClicks++
                        movie = movie.copy(favorite = !movie.favorite)
                    },
                    onToggleWatched = {
                        watchedClicks++
                        movie = movie.copy(played = !movie.played)
                    },
                    onDownloadClick = {},
                    onPauseDownload = {},
                    onResumeDownload = {},
                    onCancelDownload = {},
                    hasPlayableItems = hasPlayableItems,
                    isDownloadAllowedByServer = false,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(cdTrailer).performClick()
        composeTestRule.onNodeWithContentDescription(cdWatchlistAdd).performClick()
        composeTestRule.onNodeWithContentDescription(cdFavAdd).performClick()
        composeTestRule.onNodeWithContentDescription(cdWatchedMark).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, trailerClicks)
        assertEquals(1, watchlistClicks)
        assertEquals(1, favoriteClicks)
        assertEquals(1, watchedClicks)

        composeTestRule.onNodeWithContentDescription(cdWatchlistRemove).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(cdFavRemove).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(cdWatchedUnmark).assertIsDisplayed()

        // Disabling playable items marks the watched toggle visually unplayed and non-enabled
        movie = movie.copy(played = true)
        hasPlayableItems = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(cdWatchedMark).assertIsNotEnabled()
    }
}

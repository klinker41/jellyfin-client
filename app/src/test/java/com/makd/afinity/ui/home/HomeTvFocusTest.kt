package com.makd.afinity.ui.home

import com.makd.afinity.data.models.HomeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTvFocusTest {

    private fun resolve(
        isOffline: Boolean = false,
        hiddenRows: Set<HomeRow> = HomeRow.defaultHidden,
        loadedPlaybackRows: Set<HomeRow> =
            setOf(
                HomeRow.CONTINUE_WATCHING,
                HomeRow.CONTINUE_READING,
                HomeRow.CONTINUE_LISTENING,
                HomeRow.NEXT_UP,
            ),
        nextUpMerged: Boolean = false,
        combineLibrarySections: Boolean = false,
        showTvLauncherApps: Boolean = false,
        showLibrariesSection: Boolean = false,
        showContinueWatchingSlot: Boolean = false,
        hasContinueWatchingItems: Boolean = false,
        showContinueReadingSlot: Boolean = false,
        hasContinueReadingItems: Boolean = false,
        showContinueListeningSlot: Boolean = false,
        hasContinueListeningItems: Boolean = false,
        offlineNextUpNonEmpty: Boolean = false,
        offlineContinueReadingNonEmpty: Boolean = false,
        offlineContinueListeningNonEmpty: Boolean = false,
        downloadedRowKeys: List<String> = emptyList(),
        showNextUpSlot: Boolean = false,
        hasNextUpItems: Boolean = false,
        latestMoviesNonEmpty: Boolean = false,
        latestTvNonEmpty: Boolean = false,
        latestLibraryRowKeys: List<String> = emptyList(),
        fallbackOnlineRows: List<Pair<String, Boolean>> = emptyList(),
    ): HomeTvFocusTarget? =
        resolveInitialTvFocusTarget(
            isOffline = isOffline,
            hiddenRows = hiddenRows,
            loadedPlaybackRows = loadedPlaybackRows,
            nextUpMerged = nextUpMerged,
            combineLibrarySections = combineLibrarySections,
            showTvLauncherApps = showTvLauncherApps,
            showLibrariesSection = showLibrariesSection,
            showContinueWatchingSlot = showContinueWatchingSlot,
            hasContinueWatchingItems = hasContinueWatchingItems,
            showContinueReadingSlot = showContinueReadingSlot,
            hasContinueReadingItems = hasContinueReadingItems,
            showContinueListeningSlot = showContinueListeningSlot,
            hasContinueListeningItems = hasContinueListeningItems,
            offlineNextUpNonEmpty = offlineNextUpNonEmpty,
            offlineContinueReadingNonEmpty = offlineContinueReadingNonEmpty,
            offlineContinueListeningNonEmpty = offlineContinueListeningNonEmpty,
            downloadedRowKeys = downloadedRowKeys,
            showNextUpSlot = showNextUpSlot,
            hasNextUpItems = hasNextUpItems,
            latestMoviesNonEmpty = latestMoviesNonEmpty,
            latestTvNonEmpty = latestTvNonEmpty,
            latestLibraryRowKeys = latestLibraryRowKeys,
            fallbackOnlineRows = fallbackOnlineRows,
        )

    @Test
    fun `online prioritizes continue watching when populated`() {
        val target =
            resolve(
                showContinueWatchingSlot = true,
                hasContinueWatchingItems = true,
                showNextUpSlot = true,
                hasNextUpItems = true,
                latestLibraryRowKeys = listOf("movie_lib_1", "tv_lib_2"),
            )

        assertEquals(HomeTvFocusTarget("continue_watching", itemIndex = 1), target)
    }

    @Test
    fun `online waits while continue watching is still loading instead of jumping to latest movies`() {
        val target =
            resolve(
                loadedPlaybackRows = emptySet(),
                combineLibrarySections = true,
                latestMoviesNonEmpty = true,
            )

        assertNull(target)
    }

    @Test
    fun `online selects next up when continue watching is loaded and empty`() {
        val target =
            resolve(
                showContinueWatchingSlot = false,
                hasContinueWatchingItems = false,
                showNextUpSlot = true,
                hasNextUpItems = true,
                latestLibraryRowKeys = listOf("movie_lib_1"),
            )

        assertEquals(HomeTvFocusTarget("next_up", itemIndex = 1), target)
    }

    @Test
    fun `online waits while next up is still loading when not merged`() {
        val target =
            resolve(
                loadedPlaybackRows = setOf(HomeRow.CONTINUE_WATCHING),
                nextUpMerged = false,
                combineLibrarySections = true,
                latestMoviesNonEmpty = true,
            )

        assertNull(target)
    }

    @Test
    fun `online selects combined latest movies when continue watching and next up are empty`() {
        val target =
            resolve(
                combineLibrarySections = true,
                latestMoviesNonEmpty = true,
                latestTvNonEmpty = true,
            )

        assertEquals(HomeTvFocusTarget("latest_movies_combined", itemIndex = 1), target)
    }

    @Test
    fun `online selects combined latest tv when latest movies is empty`() {
        val target =
            resolve(
                combineLibrarySections = true,
                latestMoviesNonEmpty = false,
                latestTvNonEmpty = true,
            )

        assertEquals(HomeTvFocusTarget("latest_tv_combined", itemIndex = 1), target)
    }

    @Test
    fun `online selects first separate library row when combineLibrarySections is false`() {
        val target =
            resolve(
                combineLibrarySections = false,
                latestLibraryRowKeys = listOf("movie_lib_movies", "tv_lib_shows"),
            )

        assertEquals(HomeTvFocusTarget("movie_lib_movies", itemIndex = 1), target)
    }

    @Test
    fun `hidden continue watching and next up do not block latest movies`() {
        val target =
            resolve(
                hiddenRows = setOf(HomeRow.CONTINUE_WATCHING, HomeRow.NEXT_UP),
                loadedPlaybackRows = emptySet(),
                combineLibrarySections = true,
                latestMoviesNonEmpty = true,
            )

        assertEquals(HomeTvFocusTarget("latest_movies_combined", itemIndex = 1), target)
    }

    @Test
    fun `item index accounts for tv launcher apps and libraries rows above target`() {
        val target =
            resolve(
                showTvLauncherApps = true,
                showLibrariesSection = true,
                showContinueWatchingSlot = true,
                hasContinueWatchingItems = false,
                showNextUpSlot = true,
                hasNextUpItems = true,
            )

        // Index 0: hero_carousel, 1: tv_launcher_apps, 2: libraries_section,
        // 3: continue_watching (skeleton), 4: next_up
        assertEquals(HomeTvFocusTarget("next_up", itemIndex = 4), target)
    }

    @Test
    fun `online falls back to fallback rows then libraries then tv launcher apps`() {
        val fallbackTarget =
            resolve(
                showLibrariesSection = true,
                showTvLauncherApps = true,
                fallbackOnlineRows =
                    listOf(
                        "podcast_lib_empty" to false,
                        "pending_1" to false,
                        "custom_1" to true,
                    ),
            )
        assertEquals(HomeTvFocusTarget("custom_1", itemIndex = 5), fallbackTarget)

        val librariesTarget = resolve(showLibrariesSection = true, showTvLauncherApps = true)
        assertEquals(HomeTvFocusTarget("libraries_section", itemIndex = 2), librariesTarget)

        val launcherOnlyTarget = resolve(showTvLauncherApps = true)
        assertEquals(HomeTvFocusTarget("tv_launcher_apps", itemIndex = 1), launcherOnlyTarget)
    }

    @Test
    fun `offline prioritizes continue watching then next up then downloaded rows`() {
        assertEquals(
            HomeTvFocusTarget("continue_watching", itemIndex = 1),
            resolve(
                isOffline = true,
                showContinueWatchingSlot = true,
                hasContinueWatchingItems = true,
                offlineNextUpNonEmpty = true,
                downloadedRowKeys = listOf("downloaded_movies"),
            ),
        )

        assertEquals(
            HomeTvFocusTarget("offline_next_up", itemIndex = 1),
            resolve(
                isOffline = true,
                offlineNextUpNonEmpty = true,
                downloadedRowKeys = listOf("downloaded_movies"),
            ),
        )

        assertEquals(
            HomeTvFocusTarget("downloaded_movies", itemIndex = 1),
            resolve(
                isOffline = true,
                downloadedRowKeys = listOf("downloaded_movies", "downloaded_shows"),
            ),
        )
    }
}

package com.makd.afinity.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.ui.utils.LocalCardRowFocusFirstOnEnter
import com.makd.afinity.ui.utils.LocalCardRowInitialFocusRequester
import com.makd.afinity.ui.utils.LocalIsTelevision
import com.makd.afinity.ui.utils.requestFocusSafely

data class HomeTvFocusTarget(val rowKey: String, val itemIndex: Int)

@Stable
class HomeTvFocusState(
    val isTelevision: Boolean,
    val target: HomeTvFocusTarget?,
    val initialFocusRequester: FocusRequester,
    private val hasAppliedInitialFocusState: MutableState<Boolean>,
) {
    val hasAppliedInitialFocus: Boolean
        get() = hasAppliedInitialFocusState.value

    fun isTarget(rowKey: String): Boolean = target?.rowKey == rowKey

    fun markInitialFocusApplied() {
        hasAppliedInitialFocusState.value = true
    }
}

internal fun resolveInitialTvFocusTarget(
    isOffline: Boolean,
    hiddenRows: Set<HomeRow>,
    loadedPlaybackRows: Set<HomeRow>,
    nextUpMerged: Boolean,
    combineLibrarySections: Boolean,
    showTvLauncherApps: Boolean,
    showLibrariesSection: Boolean,
    showContinueWatchingSlot: Boolean,
    hasContinueWatchingItems: Boolean,
    showContinueReadingSlot: Boolean,
    hasContinueReadingItems: Boolean,
    showContinueListeningSlot: Boolean,
    hasContinueListeningItems: Boolean,
    offlineNextUpNonEmpty: Boolean,
    offlineContinueReadingNonEmpty: Boolean,
    offlineContinueListeningNonEmpty: Boolean,
    downloadedRowKeys: List<String>,
    showNextUpSlot: Boolean,
    hasNextUpItems: Boolean,
    latestMoviesNonEmpty: Boolean,
    latestTvNonEmpty: Boolean,
    latestLibraryRowKeys: List<String>,
    fallbackOnlineRows: List<Pair<String, Boolean>>,
): HomeTvFocusTarget? {
    // Index 0 in HomeScreen's LazyColumn is always "hero_carousel".
    var index = 1

    val tvLauncherIndex = if (showTvLauncherApps) index++ else null
    val librariesIndex = if (showLibrariesSection) index++ else null

    val continueWatchingIndex = if (showContinueWatchingSlot) index++ else null
    val continueReadingIndex = if (showContinueReadingSlot) index++ else null
    val continueListeningIndex = if (showContinueListeningSlot) index++ else null

    val offlineNextUpIndex = if (isOffline && offlineNextUpNonEmpty) index++ else null
    val offlineContinueReadingIndex =
        if (isOffline && offlineContinueReadingNonEmpty) index++ else null
    val offlineContinueListeningIndex =
        if (isOffline && offlineContinueListeningNonEmpty) index++ else null

    val firstDownloadedTarget =
        if (isOffline && downloadedRowKeys.isNotEmpty()) {
            val firstIndex = index
            index += downloadedRowKeys.size
            HomeTvFocusTarget(downloadedRowKeys.first(), firstIndex)
        } else {
            null
        }

    val nextUpIndex = if (showNextUpSlot) index++ else null

    val latestMoviesCombinedIndex =
        if (
            !isOffline &&
                combineLibrarySections &&
                HomeRow.LATEST_MOVIES !in hiddenRows &&
                latestMoviesNonEmpty
        ) {
            index++
        } else {
            null
        }
    val latestTvCombinedIndex =
        if (
            !isOffline &&
                combineLibrarySections &&
                HomeRow.LATEST_TV !in hiddenRows &&
                latestTvNonEmpty
        ) {
            index++
        } else {
            null
        }

    val firstLatestLibraryTarget =
        if (!isOffline && !combineLibrarySections && latestLibraryRowKeys.isNotEmpty()) {
            val firstIndex = index
            index += latestLibraryRowKeys.size
            HomeTvFocusTarget(latestLibraryRowKeys.first(), firstIndex)
        } else {
            null
        }

    var firstFallbackTarget: HomeTvFocusTarget? = null
    if (!isOffline) {
        for ((rowKey, focusable) in fallbackOnlineRows) {
            val currentIndex = index++
            if (focusable && firstFallbackTarget == null) {
                firstFallbackTarget = HomeTvFocusTarget(rowKey, currentIndex)
            }
        }
    }

    if (isOffline) {
        if (
            HomeRow.CONTINUE_WATCHING !in hiddenRows &&
                hasContinueWatchingItems &&
                continueWatchingIndex != null
        ) {
            return HomeTvFocusTarget("continue_watching", continueWatchingIndex)
        }
        if (offlineNextUpIndex != null) {
            return HomeTvFocusTarget("offline_next_up", offlineNextUpIndex)
        }
        if (offlineContinueReadingIndex != null) {
            return HomeTvFocusTarget("offline_continue_reading", offlineContinueReadingIndex)
        }
        if (offlineContinueListeningIndex != null) {
            return HomeTvFocusTarget("offline_continue_listening", offlineContinueListeningIndex)
        }
        if (firstDownloadedTarget != null) {
            return firstDownloadedTarget
        }
        if (tvLauncherIndex != null) {
            return HomeTvFocusTarget("tv_launcher_apps", tvLauncherIndex)
        }
        return null
    }

    if (HomeRow.CONTINUE_WATCHING !in hiddenRows) {
        if (hasContinueWatchingItems && continueWatchingIndex != null) {
            return HomeTvFocusTarget("continue_watching", continueWatchingIndex)
        }
        if (HomeRow.CONTINUE_WATCHING !in loadedPlaybackRows) {
            return null
        }
    }

    if (
        HomeRow.CONTINUE_READING !in hiddenRows &&
            hasContinueReadingItems &&
            continueReadingIndex != null
    ) {
        return HomeTvFocusTarget("continue_reading", continueReadingIndex)
    }

    if (
        HomeRow.CONTINUE_LISTENING !in hiddenRows &&
            hasContinueListeningItems &&
            continueListeningIndex != null
    ) {
        return HomeTvFocusTarget("continue_listening", continueListeningIndex)
    }

    if (!nextUpMerged && HomeRow.NEXT_UP !in hiddenRows) {
        if (hasNextUpItems && nextUpIndex != null) {
            return HomeTvFocusTarget("next_up", nextUpIndex)
        }
        if (HomeRow.NEXT_UP !in loadedPlaybackRows) {
            return null
        }
    }

    if (combineLibrarySections) {
        if (latestMoviesCombinedIndex != null) {
            return HomeTvFocusTarget("latest_movies_combined", latestMoviesCombinedIndex)
        }
        if (latestTvCombinedIndex != null) {
            return HomeTvFocusTarget("latest_tv_combined", latestTvCombinedIndex)
        }
    } else if (firstLatestLibraryTarget != null) {
        return firstLatestLibraryTarget
    }

    if (firstFallbackTarget != null) {
        return firstFallbackTarget
    }

    if (librariesIndex != null) {
        return HomeTvFocusTarget("libraries_section", librariesIndex)
    }

    if (tvLauncherIndex != null) {
        return HomeTvFocusTarget("tv_launcher_apps", tvLauncherIndex)
    }

    return null
}

@Composable
@ReadOnlyComposable
internal fun rememberHomeTvFocusTarget(
    uiState: HomeUiState,
    isTvLauncherUiActive: Boolean,
    showLibrariesSection: Boolean,
    showContinueWatching: Boolean,
    hasContinueWatchingItems: Boolean,
    showContinueReadingSlot: Boolean,
    showContinueListeningSlot: Boolean,
    hasContinueListeningItems: Boolean,
    showNextUpSlot: Boolean,
    showUpcomingSlot: Boolean,
    showHighestRatedSlot: Boolean,
    showFranchisesSlot: Boolean,
    showWatchAgainSlot: Boolean,
    showPopularStudiosSlot: Boolean,
    nextUpMerged: Boolean,
    latestLibraryRowKeys: List<String>,
): HomeTvFocusTarget? {
    if (!LocalIsTelevision.current) return null

    val downloadedRowKeys =
        if (uiState.isOffline) {
            buildList {
                if (uiState.downloadedMovies.isNotEmpty()) add("downloaded_movies")
                if (uiState.downloadedShows.isNotEmpty()) add("downloaded_shows")
                if (uiState.downloadedVideos.isNotEmpty()) add("downloaded_videos")
                if (uiState.downloadedBooks.isNotEmpty()) add("downloaded_books")
                if (uiState.downloadedJellyfinAudiobooks.isNotEmpty()) {
                    add("downloaded_jellyfin_audiobooks")
                }
                if (uiState.downloadedAudiobooks.isNotEmpty()) add("downloaded_audiobooks")
                if (uiState.downloadedPodcastEpisodes.isNotEmpty()) add("downloaded_podcasts")
                if (uiState.downloadedMusicAlbums.isNotEmpty()) add("downloaded_music_albums")
                if (uiState.downloadedMusicTracks.isNotEmpty()) add("downloaded_music_tracks")
            }
        } else {
            emptyList()
        }

    val fallbackOnlineRows =
        if (!uiState.isOffline) {
            buildList {
                if (HomeRow.LATEST_BOOKS !in uiState.hiddenRows) {
                    uiState.latestBookSections.forEach { (library, _) ->
                        add("book_lib_${library.id}" to true)
                    }
                }
                if (HomeRow.LATEST_AUDIOBOOKS !in uiState.hiddenRows) {
                    uiState.latestAudiobookSections.forEach { (library, _) ->
                        add("audiobook_lib_${library.id}" to true)
                    }
                }
                if (HomeRow.LATEST_PODCASTS !in uiState.hiddenRows) {
                    uiState.latestPodcastSections.forEach { (library, episodes) ->
                        add("podcast_lib_${library.id}" to episodes.any { !it.played })
                    }
                }
                if (HomeRow.LATEST_HOME_VIDEOS !in uiState.hiddenRows) {
                    uiState.latestHomeVideoSections.forEach { (library, _) ->
                        add("home_video_lib_${library.id}" to true)
                    }
                }
                if (HomeRow.LATEST_MUSIC_VIDEOS !in uiState.hiddenRows) {
                    uiState.latestMusicVideoSections.forEach { (library, _) ->
                        add("music_video_lib_${library.id}" to true)
                    }
                }
                if (showUpcomingSlot) {
                    add("upcoming_episodes" to uiState.upcomingEpisodes.isNotEmpty())
                }
                if (showHighestRatedSlot) {
                    add("highest_rated" to uiState.highestRated.isNotEmpty())
                }
                if (showFranchisesSlot) {
                    add("franchises" to uiState.franchises.isNotEmpty())
                }
                if (showWatchAgainSlot) {
                    add("watch_again" to uiState.watchAgain.isNotEmpty())
                }
                if (showPopularStudiosSlot) {
                    add("popular_studios" to uiState.popularStudios.isNotEmpty())
                }
                uiState.pinnedSections.forEach { section ->
                    val focusable =
                        section is HomeSection.Items &&
                            section.cardStyle != CustomSectionCardStyle.SPOTLIGHT
                    add(section.key to focusable)
                }
                uiState.combinedSections.forEach { section ->
                    val focusable =
                        when (section) {
                            is HomeSection.Person,
                            is HomeSection.Movie,
                            is HomeSection.PersonFromMovie,
                            is HomeSection.Items,
                            is HomeSection.Ranked -> true
                            is HomeSection.Genre ->
                                when (section.genreItem.type) {
                                    GenreType.MOVIE ->
                                        uiState.genreMovies[section.genreItem.name]?.isNotEmpty() ==
                                            true
                                    GenreType.SHOW ->
                                        uiState.genreShows[section.genreItem.name]?.isNotEmpty() ==
                                            true
                                }
                            is HomeSection.Pending,
                            is HomeSection.Spotlight -> false
                        }
                    add(section.key to focusable)
                }
            }
        } else {
            emptyList()
        }

    return resolveInitialTvFocusTarget(
        isOffline = uiState.isOffline,
        hiddenRows = uiState.hiddenRows,
        loadedPlaybackRows = uiState.loadedPlaybackRows,
        nextUpMerged = nextUpMerged,
        combineLibrarySections = uiState.combineLibrarySections,
        showTvLauncherApps = isTvLauncherUiActive,
        showLibrariesSection = showLibrariesSection,
        showContinueWatchingSlot = showContinueWatching,
        hasContinueWatchingItems = hasContinueWatchingItems,
        showContinueReadingSlot = showContinueReadingSlot,
        hasContinueReadingItems = uiState.continueReading.isNotEmpty(),
        showContinueListeningSlot = showContinueListeningSlot,
        hasContinueListeningItems = hasContinueListeningItems,
        offlineNextUpNonEmpty = uiState.offlineNextUp.isNotEmpty(),
        offlineContinueReadingNonEmpty = uiState.offlineContinueReading.isNotEmpty(),
        offlineContinueListeningNonEmpty = uiState.offlineContinueListening.isNotEmpty(),
        downloadedRowKeys = downloadedRowKeys,
        showNextUpSlot = showNextUpSlot,
        hasNextUpItems = uiState.nextUp.isNotEmpty(),
        latestMoviesNonEmpty = uiState.latestMovies.isNotEmpty(),
        latestTvNonEmpty = uiState.latestTvSeries.isNotEmpty(),
        latestLibraryRowKeys = latestLibraryRowKeys,
        fallbackOnlineRows = fallbackOnlineRows,
    )
}

@Composable
fun rememberHomeTvFocusState(target: HomeTvFocusTarget?): HomeTvFocusState {
    val isTelevision = LocalIsTelevision.current
    val initialFocusRequester = remember { FocusRequester() }
    val hasAppliedInitialFocusState = rememberSaveable { mutableStateOf(false) }
    return remember(isTelevision, target, initialFocusRequester, hasAppliedInitialFocusState) {
        HomeTvFocusState(
            isTelevision = isTelevision,
            target = target,
            initialFocusRequester = initialFocusRequester,
            hasAppliedInitialFocusState = hasAppliedInitialFocusState,
        )
    }
}

@Composable
fun HomeTvInitialFocusEffect(lazyListState: LazyListState, tvFocus: HomeTvFocusState) {
    val target = tvFocus.target
    val hasApplied = tvFocus.hasAppliedInitialFocus
    LaunchedEffect(tvFocus.isTelevision, target, hasApplied) {
        if (!tvFocus.isTelevision || target == null || hasApplied) return@LaunchedEffect
        withFrameNanos {}
        if (tvFocus.hasAppliedInitialFocus) return@LaunchedEffect
        val targetVisible =
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == target.rowKey }
        if (!targetVisible) {
            lazyListState.scrollToItem((target.itemIndex - 1).coerceAtLeast(0))
            withFrameNanos {}
            if (
                !tvFocus.hasAppliedInitialFocus &&
                    lazyListState.layoutInfo.visibleItemsInfo.none { it.key == target.rowKey }
            ) {
                lazyListState.scrollToItem(target.itemIndex)
            }
        }
    }
}

@Composable
fun HomeTvHeroFocusScrollEffect(isHeroFocused: Boolean, lazyListState: LazyListState) {
    LaunchedEffect(isHeroFocused) {
        if (!isHeroFocused) return@LaunchedEffect
        withFrameNanos {}
        if (
            lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > 0
        ) {
            lazyListState.animateScrollToItem(0, 0)
        }
    }
}

fun Modifier.tvHeroPinBringIntoViewToTop(): Modifier = this then TvHeroPinBringIntoViewToTopElement

private object TvHeroPinBringIntoViewToTopElement :
    ModifierNodeElement<TvHeroPinBringIntoViewToTopNode>() {
    override fun create(): TvHeroPinBringIntoViewToTopNode = TvHeroPinBringIntoViewToTopNode()

    override fun update(node: TvHeroPinBringIntoViewToTopNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun InspectorInfo.inspectableProperties() {
        name = "tvHeroPinBringIntoViewToTop"
    }
}

private class TvHeroPinBringIntoViewToTopNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        if (!isAttached) return
        bringIntoView {
            val coordinates = requireLayoutCoordinates()
            if (!coordinates.isAttached) {
                null
            } else {
                val width = coordinates.size.width.toFloat().coerceAtLeast(1f)
                Rect(left = 0f, top = 0f, right = width, bottom = 1f)
            }
        }
    }
}

@Composable
fun HomeTvRowBox(
    rowKey: String,
    tvFocus: HomeTvFocusState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isTarget = tvFocus.isTelevision && tvFocus.isTarget(rowKey)
    if (isTarget) {
        val isWindowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(rowKey, tvFocus.hasAppliedInitialFocus, isWindowFocused) {
            if (tvFocus.hasAppliedInitialFocus) return@LaunchedEffect
            val focused = requestFocusSafely(tvFocus.initialFocusRequester)
            if (focused && isWindowFocused) {
                tvFocus.markInitialFocusApplied()
            }
        }
    }
    CompositionLocalProvider(
        LocalCardRowInitialFocusRequester provides
            if (isTarget) tvFocus.initialFocusRequester else null,
        LocalCardRowFocusFirstOnEnter provides tvFocus.isTelevision,
    ) {
        Box(modifier = modifier, content = content)
    }
}

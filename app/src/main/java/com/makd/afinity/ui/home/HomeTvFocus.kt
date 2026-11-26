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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
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

internal const val HERO_CAROUSEL_ROW_KEY = "hero_carousel"

data class HomeTvFocusTarget(val rowKey: String, val itemIndex: Int)

internal fun shouldAllowHomeTopBarFocus(
    isTelevision: Boolean,
    isShowingHomeList: Boolean,
    isTopBarFocused: Boolean,
    focusedRowKey: String?,
    topmostRowKey: String?,
    isListLoading: Boolean,
): Boolean {
    if (!isTelevision) return true
    if (!isShowingHomeList) return true
    if (isTopBarFocused) return true
    if (topmostRowKey == null) return !isListLoading
    return focusedRowKey == topmostRowKey
}

internal fun shouldShowHomeScrollToTop(
    isTelevision: Boolean,
    firstVisibleItemIndex: Int,
): Boolean = !isTelevision && firstVisibleItemIndex > 3

@Stable
class HomeTvTopBarFocusState(val isTelevision: Boolean) {
    private var isShowingHomeList by mutableStateOf(true)
    private var isTopBarFocused by mutableStateOf(false)
    var focusedRowKey by mutableStateOf<String?>(null)
        private set

    private var topmostRowKey by mutableStateOf<String?>(null)
    private var isListLoading by mutableStateOf(true)

    val canFocusTopBar by derivedStateOf {
        shouldAllowHomeTopBarFocus(
            isTelevision = isTelevision,
            isShowingHomeList = isShowingHomeList,
            isTopBarFocused = isTopBarFocused,
            focusedRowKey = focusedRowKey,
            topmostRowKey = topmostRowKey,
            isListLoading = isListLoading,
        )
    }

    fun onTopBarFocusChanged(hasFocus: Boolean) {
        isTopBarFocused = hasFocus
    }

    fun onRowFocused(rowKey: String) {
        focusedRowKey = rowKey
    }

    fun onRowUnfocused(rowKey: String) {
        if (focusedRowKey == rowKey) {
            focusedRowKey = null
        }
    }

    fun syncListState(isShowingHomeList: Boolean, topmostRowKey: String?, isListLoading: Boolean) {
        this.isShowingHomeList = isShowingHomeList
        this.topmostRowKey = topmostRowKey
        this.isListLoading = isListLoading
    }
}

@Composable
fun rememberHomeTvTopBarFocusState(): HomeTvTopBarFocusState {
    val isTelevision = LocalIsTelevision.current
    return remember(isTelevision) { HomeTvTopBarFocusState(isTelevision) }
}

@Stable
class HomeTvFocusState(
    val isTelevision: Boolean,
    val target: HomeTvFocusTarget?,
    val initialFocusRequester: FocusRequester,
    private val hasAppliedInitialFocusState: MutableState<Boolean>,
    val returnFocusRequester: FocusRequester = FocusRequester(),
    val listFocusRequester: FocusRequester = FocusRequester(),
    private val lastFocusedRowKeyState: MutableState<String?> = mutableStateOf(null),
    private val hasRestoredEntryFocusState: MutableState<Boolean> = mutableStateOf(false),
    private val topBarFocusState: HomeTvTopBarFocusState? = null,
) {
    val hasAppliedInitialFocus: Boolean
        get() = hasAppliedInitialFocusState.value

    val focusedRowKey: String?
        get() = topBarFocusState?.focusedRowKey

    val hasRestoredEntryFocus: Boolean
        get() = hasRestoredEntryFocusState.value

    fun isTarget(rowKey: String): Boolean = target?.rowKey == rowKey

    fun isReturnTarget(rowKey: String): Boolean =
        hasAppliedInitialFocus && !hasRestoredEntryFocus && lastFocusedRowKeyState.value == rowKey

    fun markInitialFocusApplied() {
        hasAppliedInitialFocusState.value = true
        hasRestoredEntryFocusState.value = true
    }

    fun markEntryFocusRestored() {
        hasRestoredEntryFocusState.value = true
    }

    fun onRowFocused(rowKey: String) {
        topBarFocusState?.onRowFocused(rowKey)
        lastFocusedRowKeyState.value = rowKey
        hasRestoredEntryFocusState.value = true
    }

    fun onRowUnfocused(rowKey: String) {
        topBarFocusState?.onRowUnfocused(rowKey)
    }

    suspend fun focusHomeListTop(): Boolean {
        if (requestFocusSafely(initialFocusRequester)) return true
        return requestFocusSafely(listFocusRequester)
    }
}

internal fun resolveTopmostTvFocusRowKey(
    showCarousel: Boolean,
    isOffline: Boolean,
    hiddenRows: Set<HomeRow>,
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
): String? {
    if (showCarousel) return HERO_CAROUSEL_ROW_KEY
    if (showTvLauncherApps) return "tv_launcher_apps"
    if (showLibrariesSection) return "libraries_section"
    if (showContinueWatchingSlot && hasContinueWatchingItems) return "continue_watching"
    if (showContinueReadingSlot && hasContinueReadingItems) return "continue_reading"
    if (showContinueListeningSlot && hasContinueListeningItems) return "continue_listening"
    if (isOffline) {
        if (offlineNextUpNonEmpty) return "offline_next_up"
        if (offlineContinueReadingNonEmpty) return "offline_continue_reading"
        if (offlineContinueListeningNonEmpty) return "offline_continue_listening"
        return downloadedRowKeys.firstOrNull()
    }
    if (showNextUpSlot && hasNextUpItems) return "next_up"
    if (combineLibrarySections) {
        if (HomeRow.LATEST_MOVIES !in hiddenRows && latestMoviesNonEmpty) {
            return "latest_movies_combined"
        }
        if (HomeRow.LATEST_TV !in hiddenRows && latestTvNonEmpty) {
            return "latest_tv_combined"
        }
    } else {
        latestLibraryRowKeys.firstOrNull()?.let {
            return it
        }
    }
    return fallbackOnlineRows.firstOrNull { (_, focusable) -> focusable }?.first
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

internal data class HomeTvFocusPlan(
    val target: HomeTvFocusTarget?,
    val topmostRowKey: String?,
    val isListLoading: Boolean,
)

private data class FallbackOnlineRow(
    val key: String,
    val focusableInitial: Boolean,
    val focusableTopmost: Boolean,
)

private fun buildDownloadedRowKeys(uiState: HomeUiState): List<String> =
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

private fun buildFallbackOnlineRows(
    uiState: HomeUiState,
    showUpcomingSlot: Boolean,
    showHighestRatedSlot: Boolean,
    showFranchisesSlot: Boolean,
    showWatchAgainSlot: Boolean,
    showPopularStudiosSlot: Boolean,
): List<FallbackOnlineRow> =
    if (!uiState.isOffline) {
        buildList {
            if (HomeRow.LATEST_BOOKS !in uiState.hiddenRows) {
                uiState.latestBookSections.forEach { (library, _) ->
                    add(
                        FallbackOnlineRow(
                            "book_lib_${library.id}",
                            focusableInitial = true,
                            focusableTopmost = true,
                        ),
                    )
                }
            }
            if (HomeRow.LATEST_AUDIOBOOKS !in uiState.hiddenRows) {
                uiState.latestAudiobookSections.forEach { (library, _) ->
                    add(
                        FallbackOnlineRow(
                            "audiobook_lib_${library.id}",
                            focusableInitial = true,
                            focusableTopmost = true,
                        ),
                    )
                }
            }
            if (HomeRow.LATEST_PODCASTS !in uiState.hiddenRows) {
                uiState.latestPodcastSections.forEach { (library, episodes) ->
                    val unplayed = episodes.any { !it.played }
                    add(
                        FallbackOnlineRow(
                            "podcast_lib_${library.id}",
                            focusableInitial = unplayed,
                            focusableTopmost = unplayed,
                        ),
                    )
                }
            }
            if (HomeRow.LATEST_HOME_VIDEOS !in uiState.hiddenRows) {
                uiState.latestHomeVideoSections.forEach { (library, _) ->
                    add(
                        FallbackOnlineRow(
                            "home_video_lib_${library.id}",
                            focusableInitial = true,
                            focusableTopmost = true,
                        ),
                    )
                }
            }
            if (HomeRow.LATEST_MUSIC_VIDEOS !in uiState.hiddenRows) {
                uiState.latestMusicVideoSections.forEach { (library, _) ->
                    add(
                        FallbackOnlineRow(
                            "music_video_lib_${library.id}",
                            focusableInitial = true,
                            focusableTopmost = true,
                        ),
                    )
                }
            }
            if (showUpcomingSlot) {
                val nonEmpty = uiState.upcomingEpisodes.isNotEmpty()
                add(
                    FallbackOnlineRow(
                        "upcoming_episodes",
                        focusableInitial = nonEmpty,
                        focusableTopmost = nonEmpty,
                    ),
                )
            }
            if (showHighestRatedSlot) {
                val nonEmpty = uiState.highestRated.isNotEmpty()
                add(
                    FallbackOnlineRow(
                        "highest_rated",
                        focusableInitial = nonEmpty,
                        focusableTopmost = nonEmpty,
                    ),
                )
            }
            if (showFranchisesSlot) {
                val nonEmpty = uiState.franchises.isNotEmpty()
                add(
                    FallbackOnlineRow(
                        "franchises",
                        focusableInitial = nonEmpty,
                        focusableTopmost = nonEmpty,
                    ),
                )
            }
            if (showWatchAgainSlot) {
                val nonEmpty = uiState.watchAgain.isNotEmpty()
                add(
                    FallbackOnlineRow(
                        "watch_again",
                        focusableInitial = nonEmpty,
                        focusableTopmost = nonEmpty,
                    ),
                )
            }
            if (showPopularStudiosSlot) {
                val nonEmpty = uiState.popularStudios.isNotEmpty()
                add(
                    FallbackOnlineRow(
                        "popular_studios",
                        focusableInitial = nonEmpty,
                        focusableTopmost = nonEmpty,
                    ),
                )
            }
            uiState.pinnedSections.forEach { section ->
                val isSpotlight =
                    section is HomeSection.Items &&
                        section.cardStyle == CustomSectionCardStyle.SPOTLIGHT
                val hasContent =
                    section is HomeSection.Items &&
                        (section.items.isNotEmpty() || section.customSectionId != null)
                add(
                    FallbackOnlineRow(
                        key = section.key,
                        focusableInitial = section is HomeSection.Items && !isSpotlight,
                        focusableTopmost =
                            if (isSpotlight) section.items.isNotEmpty() else hasContent,
                    ),
                )
            }
            uiState.combinedSections.forEach { section ->
                val focusable =
                    when (section) {
                        is HomeSection.Person,
                        is HomeSection.Movie,
                        is HomeSection.PersonFromMovie,
                        is HomeSection.Ranked -> true
                        is HomeSection.Items -> true
                        is HomeSection.Genre ->
                            when (section.genreItem.type) {
                                GenreType.MOVIE ->
                                    uiState.genreMovies[section.genreItem.name]?.isNotEmpty() ==
                                        true
                                GenreType.SHOW ->
                                    uiState.genreShows[section.genreItem.name]?.isNotEmpty() == true
                            }
                        is HomeSection.Pending,
                        is HomeSection.Spotlight -> false
                    }
                val focusableTopmost =
                    when (section) {
                        is HomeSection.Items ->
                            section.items.isNotEmpty() || section.customSectionId != null
                        is HomeSection.Spotlight -> section.items.isNotEmpty()
                        else -> focusable
                    }
                add(
                    FallbackOnlineRow(
                        key = section.key,
                        focusableInitial = focusable,
                        focusableTopmost = focusableTopmost,
                    ),
                )
            }
        }
    } else {
        emptyList()
    }

@Composable
@ReadOnlyComposable
internal fun rememberHomeTvFocusPlan(
    showCarousel: Boolean,
    heroPending: Boolean,
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
): HomeTvFocusPlan {
    if (!LocalIsTelevision.current) {
        return HomeTvFocusPlan(target = null, topmostRowKey = null, isListLoading = false)
    }

    val downloadedRowKeys = buildDownloadedRowKeys(uiState)
    val fallbackOnlineRows =
        buildFallbackOnlineRows(
            uiState = uiState,
            showUpcomingSlot = showUpcomingSlot,
            showHighestRatedSlot = showHighestRatedSlot,
            showFranchisesSlot = showFranchisesSlot,
            showWatchAgainSlot = showWatchAgainSlot,
            showPopularStudiosSlot = showPopularStudiosSlot,
        )
    val hasContinueReadingItems = uiState.continueReading.isNotEmpty()
    val offlineNextUpNonEmpty = uiState.offlineNextUp.isNotEmpty()
    val offlineContinueReadingNonEmpty = uiState.offlineContinueReading.isNotEmpty()
    val offlineContinueListeningNonEmpty = uiState.offlineContinueListening.isNotEmpty()
    val hasNextUpItems = uiState.nextUp.isNotEmpty()
    val latestMoviesNonEmpty = uiState.latestMovies.isNotEmpty()
    val latestTvNonEmpty = uiState.latestTvSeries.isNotEmpty()

    val target =
        resolveInitialTvFocusTarget(
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
            fallbackOnlineRows = fallbackOnlineRows.map { it.key to it.focusableInitial },
        )

    val topmostRowKey =
        resolveTopmostTvFocusRowKey(
            showCarousel = showCarousel,
            isOffline = uiState.isOffline,
            hiddenRows = uiState.hiddenRows,
            combineLibrarySections = uiState.combineLibrarySections,
            showTvLauncherApps = isTvLauncherUiActive,
            showLibrariesSection = showLibrariesSection,
            showContinueWatchingSlot = showContinueWatching,
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
            fallbackOnlineRows = fallbackOnlineRows.map { it.key to it.focusableTopmost },
        )

    val isListLoading =
        uiState.isLoading ||
            heroPending ||
            (!uiState.isOffline &&
                ((HomeRow.CONTINUE_WATCHING !in uiState.hiddenRows &&
                    HomeRow.CONTINUE_WATCHING !in uiState.loadedPlaybackRows) ||
                    (!nextUpMerged &&
                        HomeRow.NEXT_UP !in uiState.hiddenRows &&
                        HomeRow.NEXT_UP !in uiState.loadedPlaybackRows)))

    return HomeTvFocusPlan(
        target = target,
        topmostRowKey = topmostRowKey,
        isListLoading = isListLoading,
    )
}

@Composable
fun rememberHomeTvFocusState(
    target: HomeTvFocusTarget?,
    topBarFocusState: HomeTvTopBarFocusState? = null,
): HomeTvFocusState {
    val isTelevision = LocalIsTelevision.current
    val initialFocusRequester = remember { FocusRequester() }
    val returnFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }
    val hasAppliedInitialFocusState = rememberSaveable { mutableStateOf(false) }
    val lastFocusedRowKeyState = rememberSaveable { mutableStateOf<String?>(null) }
    val hasRestoredEntryFocusState = remember { mutableStateOf(false) }
    return remember(
        isTelevision,
        target,
        initialFocusRequester,
        returnFocusRequester,
        listFocusRequester,
        hasAppliedInitialFocusState,
        lastFocusedRowKeyState,
        hasRestoredEntryFocusState,
        topBarFocusState,
    ) {
        HomeTvFocusState(
            isTelevision = isTelevision,
            target = target,
            initialFocusRequester = initialFocusRequester,
            hasAppliedInitialFocusState = hasAppliedInitialFocusState,
            returnFocusRequester = returnFocusRequester,
            listFocusRequester = listFocusRequester,
            lastFocusedRowKeyState = lastFocusedRowKeyState,
            hasRestoredEntryFocusState = hasRestoredEntryFocusState,
            topBarFocusState = topBarFocusState,
        )
    }
}

@Composable
fun HomeTvReturnFocusEffect(tvFocus: HomeTvFocusState) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val shouldRestore =
        tvFocus.isTelevision &&
            tvFocus.hasAppliedInitialFocus &&
            !tvFocus.hasRestoredEntryFocus &&
            isWindowFocused
    LaunchedEffect(shouldRestore) {
        if (!shouldRestore) return@LaunchedEffect
        // Wait two frames so HomeTvRowBox's requestFocusSafely (and its 1-frame retry) can claim
        // the saved row before falling back to the LazyColumn container.
        withFrameNanos {}
        withFrameNanos {}
        if (tvFocus.hasRestoredEntryFocus || tvFocus.focusedRowKey != null) return@LaunchedEffect
        if (requestFocusSafely(tvFocus.listFocusRequester)) {
            tvFocus.markEntryFocusRestored()
        }
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
    val isReturnTarget = tvFocus.isTelevision && tvFocus.isReturnTarget(rowKey)
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val rowFocusRequester =
        when {
            isTarget -> tvFocus.initialFocusRequester
            isReturnTarget -> tvFocus.returnFocusRequester
            else -> null
        }

    if (isTarget) {
        LaunchedEffect(rowKey, tvFocus.hasAppliedInitialFocus, isWindowFocused) {
            if (tvFocus.hasAppliedInitialFocus) return@LaunchedEffect
            val focused = requestFocusSafely(tvFocus.initialFocusRequester)
            if (focused && isWindowFocused) {
                tvFocus.markInitialFocusApplied()
            }
        }
    }
    if (isReturnTarget && rowFocusRequester != null) {
        LaunchedEffect(rowKey, tvFocus.hasRestoredEntryFocus, isWindowFocused) {
            if (tvFocus.hasRestoredEntryFocus || !isWindowFocused) return@LaunchedEffect
            val focused = requestFocusSafely(rowFocusRequester)
            if (focused) {
                tvFocus.markEntryFocusRestored()
            }
        }
    }

    val focusTrackingModifier =
        if (tvFocus.isTelevision) {
            Modifier.onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    tvFocus.onRowFocused(rowKey)
                } else {
                    tvFocus.onRowUnfocused(rowKey)
                }
            }
        } else {
            Modifier
        }

    CompositionLocalProvider(
        LocalCardRowInitialFocusRequester provides rowFocusRequester,
        LocalCardRowFocusFirstOnEnter provides tvFocus.isTelevision,
    ) {
        Box(modifier = modifier.then(focusTrackingModifier), content = content)
    }
}

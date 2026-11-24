@file:UnstableApi

package com.makd.afinity.ui.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.book.plainOverview
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailActionRow
import com.makd.afinity.ui.components.DetailCoverImage
import com.makd.afinity.ui.components.DetailCoverSize
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.DownloadConfirmDialog
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.components.formatTimeLeftTicks
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.rememberDetailTopBarBlurState
import com.makd.afinity.ui.components.rememberDownloadVolumes
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.item.components.StorageLocationDialog
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.theme.isDarkSurface
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlin.math.roundToInt

private val LandscapeCoverMinHeight = 140.dp
private const val LandscapeCoverHeightFraction = 0.5f

@Composable
fun PodcastEpisodeDetailScreen(
    onHomeClick: () -> Unit,
    onShowClick: (UUID) -> Unit,
    onOpenMusicPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastEpisodeDetailViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()
    val allowedByServer by viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val allowedOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val downloadVolumes = rememberDownloadVolumes()
    var pendingConfirm by remember { mutableStateOf<DownloadConfirm?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var selectedVolumeId by remember { mutableStateOf<String?>(null) }

    if (showLocationDialog) {
        StorageLocationDialog(
            volumes = downloadVolumes.volumes,
            selectedVolumeId = selectedVolumeId,
            onVolumeSelected = { selectedVolumeId = it },
            onConfirm = { viewModel.startDownload(selectedVolumeId) },
            onDismiss = { showLocationDialog = false },
            showQuality = false,
        )
    }

    pendingConfirm?.let { confirm ->
        DownloadConfirmDialog(
            confirm = confirm,
            onConfirm = {
                pendingConfirm = null
                viewModel.cancelOrDeleteDownload()
            },
            onDismiss = { pendingConfirm = null },
        )
    }

    PodcastEpisodeDetailScaffold(
        uiState = uiState,
        isOffline = isOffline,
        downloadInfo = downloadInfo,
        allowedByServer = allowedByServer,
        allowedOnNetwork = allowedOnNetwork,
        onRetry = viewModel::retry,
        onShowClick = onShowClick,
        onPlayClick = { episode, startPositionMs ->
            startMusicService(context)
            playerViewModel.playQueue(
                tracks = listOf(episode),
                startIndex = 0,
                startPositionMs = startPositionMs,
            )
            onOpenMusicPlayer()
        },
        onTogglePlayed = viewModel::togglePlayed,
        onToggleFavorite = viewModel::toggleFavorite,
        onDownloadClick = { viewModel.startDownload() },
        onDownloadLongClick = {
            if (downloadVolumes.hasChoice) {
                selectedVolumeId = downloadVolumes.defaultSelection
                showLocationDialog = true
            } else {
                viewModel.startDownload()
            }
        },
        onPauseDownload = viewModel::pauseDownload,
        onResumeDownload = viewModel::resumeDownload,
        onCancelDownload = { title -> pendingConfirm = viewModel.downloadConfirm(title) },
        modifier = modifier,
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onHomeClick,
                backgroundOpacity = backgroundOpacity,
            )
        },
    )
}

@Composable
fun PodcastEpisodeDetailScaffold(
    uiState: PodcastEpisodeDetailUiState,
    isOffline: Boolean,
    downloadInfo: DownloadInfo?,
    allowedByServer: Boolean,
    allowedOnNetwork: Boolean,
    onRetry: () -> Unit,
    onShowClick: (UUID) -> Unit,
    onPlayClick: (AfinityTrack, Long) -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadLongClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable (backgroundOpacity: () -> Float) -> Unit = {},
) {
    val lazyListState = rememberLazyListState()
    val isLandscape = isLandscapeWindow()
    val topBarBlurState = rememberDetailTopBarBlurState(lazyListState, hasHeroSlot = !isLandscape)

    AfinityScreen(
        topBar = topBar,
        modifier = modifier,
        mode = AfinityScreenMode.Hero,
        topBarBlurState = topBarBlurState,
    ) { _ ->
        val episode = uiState.episode
        when {
            episode != null ->
                PodcastEpisodeDetailContent(
                    episode = episode,
                    isOffline = isOffline,
                    downloadInfo = downloadInfo,
                    allowedByServer = allowedByServer,
                    allowedOnNetwork = allowedOnNetwork,
                    lazyListState = lazyListState,
                    isLandscape = isLandscape,
                    onShowClick = onShowClick,
                    onPlayClick = onPlayClick,
                    onTogglePlayed = onTogglePlayed,
                    onToggleFavorite = onToggleFavorite,
                    onDownloadClick = onDownloadClick,
                    onDownloadLongClick = onDownloadLongClick,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onCancelDownload = { onCancelDownload(episode.name) },
                )
            uiState.isLoading -> FullScreenLoading()
            else ->
                FullScreenError(
                    message = uiState.errorRes?.let { stringResource(it) },
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = onRetry,
                )
        }
    }
}

@Composable
internal fun PodcastEpisodeDetailContent(
    episode: AfinityTrack,
    isOffline: Boolean,
    downloadInfo: DownloadInfo?,
    allowedByServer: Boolean,
    allowedOnNetwork: Boolean,
    onShowClick: (UUID) -> Unit,
    onPlayClick: (AfinityTrack, Long) -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadLongClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    isLandscape: Boolean = isLandscapeWindow(),
) {
    val coverUrl = episode.images.primary?.toString()
    val backdrop = backdropChainOf(coverUrl, episode.images.primaryImageBlurHash)
    val albumId = episode.albumId
    val showLinkEnabled = albumId != null && !isOffline
    val playFromStartLabel = stringResource(R.string.podcast_action_play_from_start)
    val goToShowLabel = stringResource(R.string.podcast_action_go_to_show)

    val moreActions =
        remember(
            episode,
            showLinkEnabled,
            albumId,
            playFromStartLabel,
            goToShowLabel,
            onPlayClick,
            onShowClick,
        ) {
            buildList {
                if (!episode.played && episode.playbackPositionTicks > 0L) {
                    add(
                        DetailMenuAction(
                            label = playFromStartLabel,
                            iconRes = R.drawable.ic_player_play_filled,
                            onClick = { onPlayClick(episode, 0L) },
                        ),
                    )
                }
                if (albumId != null && !isOffline) {
                    add(
                        DetailMenuAction(
                            label = goToShowLabel,
                            iconRes = R.drawable.ic_list_details,
                            onClick = { onShowClick(albumId) },
                        ),
                    )
                }
            }
        }

    val actions: @Composable (Modifier) -> Unit = { actionModifier ->
        DetailActionRow(
            onPlay = { onPlayClick(episode, episode.resumePositionMs()) },
            modifier = actionModifier.fillMaxWidth().padding(vertical = 8.dp),
            moreActions = moreActions,
            playContentDescription = stringResource(R.string.cd_play),
        ) {
            if (!isOffline) {
                WatchedToggleButton(isPlayed = episode.played, onClick = onTogglePlayed)
                FavoriteToggleButton(isFavorite = episode.favorite, onClick = onToggleFavorite)
            }
            if (allowedByServer || downloadInfo != null) {
                DownloadProgressIndicator(
                    downloadInfo = downloadInfo,
                    onDownloadClick = onDownloadClick,
                    onPauseClick = onPauseDownload,
                    onResumeClick = onResumeDownload,
                    onCancelClick = onCancelDownload,
                    canDownload = allowedOnNetwork,
                    onDownloadLongClick = onDownloadLongClick,
                )
            }
        }
    }

    val body: LazyListScope.() -> Unit = { podcastEpisodeDetailBody(episode) }

    if (isLandscape) {
        val coverSize =
            (windowHeightDp() * LandscapeCoverHeightFraction).coerceIn(
                LandscapeCoverMinHeight,
                DetailCoverSize,
            )
        LandscapeDetailLayout(
            backdropStyle = BackdropStyle.BlurredCover,
            backdrop = backdrop,
            listState = lazyListState,
            modifier = modifier,
            arrangement = LandscapeDetailArrangement.Split,
            pane = {
                PodcastEpisodeHeader(
                    episode = episode,
                    coverSize = coverSize,
                    onShowClick = if (showLinkEnabled) onShowClick else null,
                )
            },
            items = {
                item(key = "actions") { actions(Modifier.padding(horizontal = DetailGutter)) }
                body()
            },
        )
    } else {
        PortraitDetailLayout(
            hero = PortraitHero.Cover(backdrop),
            listState = lazyListState,
            modifier = modifier,
            header = {
                PodcastEpisodeHeader(
                    episode = episode,
                    coverSize = DetailCoverSize,
                    onShowClick = if (showLinkEnabled) onShowClick else null,
                )
                Spacer(modifier = Modifier.height(16.dp))
                actions(Modifier)
            },
        ) {
            body()
        }
    }
}

@Composable
private fun PodcastEpisodeHeader(
    episode: AfinityTrack,
    coverSize: Dp,
    onShowClick: ((UUID) -> Unit)?,
) {
    val showName =
        episode.album?.takeIf { it.isNotBlank() } ?: episode.artist?.takeIf { it.isNotBlank() }
    val albumId = episode.albumId

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DetailCoverImage(
            imageUrl = episode.images.primary?.toString(),
            contentDescription = stringResource(R.string.cd_podcast_episode_cover),
            blurHash = episode.images.primaryImageBlurHash,
            width = coverSize,
            height = coverSize,
        )

        DetailCoverTitle(
            title = episode.name.ifBlank { stringResource(R.string.unknown_title) },
            subtitle = null,
        )

        if (showName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = showName,
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (onShowClick != null && albumId != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier =
                    if (onShowClick != null && albumId != null) {
                        Modifier.clickable { onShowClick(albumId) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        PodcastEpisodeMetaLine(episode)
    }
}

@Composable
private fun PodcastEpisodeMetaLine(episode: AfinityTrack) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val displayDate =
        episode.premiereDate?.takeIf { it.year > 1 } ?: episode.dateCreated?.takeIf { it.year > 1 }
    val progress =
        if (episode.runtimeTicks > 0L) {
            (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks).coerceIn(0f, 1f)
        } else {
            null
        }
    val segments =
        buildList<@Composable () -> Unit> {
            displayDate?.format(dateFormatter)?.let { dateText ->
                add {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            episode.runtimeTicks
                .takeIf { it > 0L }
                ?.let { runtime ->
                    add {
                        Text(
                            text = formatRuntimeTicks(runtime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            when {
                episode.played -> {
                    add {
                        val finishedColor =
                            if (isDarkSurface()) Color(0xFF81C784) else Color(0xFF1B6E22)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = finishedColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = stringResource(R.string.abs_finished),
                                style = MaterialTheme.typography.labelMedium,
                                color = finishedColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                progress != null && episode.playbackPositionTicks > 0L -> {
                    add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFFFFC107),
                                strokeWidth = 2.dp,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.audiobook_progress_percent_fmt,
                                        (progress * 100).roundToInt().coerceIn(1, 100),
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    val ticksLeft = episode.runtimeTicks - episode.playbackPositionTicks
                    if (ticksLeft > 0L) {
                        add {
                            Text(
                                text = formatTimeLeftTicks(ticksLeft),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    if (segments.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        segments.forEachIndexed { index, segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                }
                segment()
            }
        }
    }
}

private fun LazyListScope.podcastEpisodeDetailBody(episode: AfinityTrack) {
    val overview = plainOverview(episode.overview.orEmpty())
    if (overview.isNotEmpty()) {
        detailItem("overview", DetailGutter) { OverviewSection(overview = overview) }
    }

    detailItem("details", DetailGutter) { PodcastEpisodeDetailsSection(episode) }
}

@Composable
private fun PodcastEpisodeDetailsSection(episode: AfinityTrack) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val showName = episode.album?.takeIf { it.isNotBlank() }
    val hostName =
        episode.artist?.takeIf { it.isNotBlank() && !it.equals(showName, ignoreCase = true) }
    val releasedDate = episode.premiereDate?.takeIf { it.year > 1 }
    val addedDate = episode.dateCreated?.takeIf { it.year > 1 }
    val rows =
        listOfNotNull(
            showName?.let { stringResource(R.string.podcast_details_show) to it },
            hostName?.let { stringResource(R.string.podcast_details_host) to it },
            episode.indexNumber?.let { stringResource(R.string.podcast_details_episode) to "#$it" },
            releasedDate?.let {
                stringResource(R.string.book_details_released) to it.format(dateFormatter)
            },
            addedDate
                ?.takeIf { releasedDate == null }
                ?.let {
                    stringResource(R.string.podcast_details_added) to it.format(dateFormatter)
                },
            episode.runtimeTicks
                .takeIf { it > 0L }
                ?.let {
                    stringResource(R.string.audiobook_details_length) to formatRuntimeTicks(it)
                },
        )
    if (rows.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        DetailSectionTitle(text = stringResource(R.string.abs_details))
        Spacer(modifier = Modifier.height(12.dp))
        rows.forEach { (label, value) ->
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("$label: ")
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(value)
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

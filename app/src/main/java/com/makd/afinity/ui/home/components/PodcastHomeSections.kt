package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.podcasts.ContinueListeningEntry
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.formatTimeLeftTicks
import com.makd.afinity.ui.components.rememberCardRowListState
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun ContinueListeningRowSection(
    title: String,
    entries: List<ContinueListeningEntry>,
    onAudiobookClick: (AfinityAudiobook) -> Unit,
    onPodcastClick: (AfinityTrack) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_SQUARE,
            metadataHeight = CardDimensions.subtitleLine,
        )

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
        )

        val scrollState = rememberAutoResetCardRowState(entries.firstOrNull()?.id)

        CardRow(
            state = scrollState,
            modifier = Modifier.fillMaxWidth().height(fixedRowHeight),
            contentPadding = CardDimensions.RowContentPadding,
        ) {
            items(
                items = entries,
                key = { entry -> "continue_listening_${entry.id}" },
                contentType = { "continue_listening_card" },
            ) { entry ->
                when (entry) {
                    is ContinueListeningEntry.Audiobook -> {
                        val book = entry.book
                        val ticksLeft = book.runtimeTicks - book.playbackPositionTicks
                        val subtitle =
                            if (book.playbackPositionTicks > 0L && ticksLeft > 0L) {
                                formatTimeLeftTicks(ticksLeft)
                            } else {
                                book.authorLine
                            }
                        SquareListeningCard(
                            imageUrl = book.images.primaryImageUrl,
                            blurHash = book.images.primaryBlurHash,
                            title = book.name,
                            subtitle = subtitle,
                            progress = book.listeningProgress ?: 0f,
                            cardWidth = cardWidth,
                            onClick = { onAudiobookClick(book) },
                        )
                    }
                    is ContinueListeningEntry.PodcastEpisode -> {
                        val track = entry.track
                        val progress =
                            if (track.runtimeTicks > 0L) {
                                (track.playbackPositionTicks.toFloat() / track.runtimeTicks)
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        val ticksLeft = track.runtimeTicks - track.playbackPositionTicks
                        val subtitle =
                            if (track.playbackPositionTicks > 0L && ticksLeft > 0L) {
                                formatTimeLeftTicks(ticksLeft)
                            } else {
                                track.album ?: track.artist.orEmpty()
                            }
                        SquareListeningCard(
                            imageUrl = track.images.primary?.toString(),
                            blurHash = track.images.primaryImageBlurHash,
                            title = track.name,
                            subtitle = subtitle,
                            progress = progress,
                            cardWidth = cardWidth,
                            onClick = { onPodcastClick(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LatestPodcastRowSection(
    title: String,
    episodes: List<AfinityTrack>,
    onEpisodeClick: (AfinityTrack) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_SQUARE,
            metadataHeight = CardDimensions.subtitleLine,
        )

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
        )

        val scrollState = rememberAutoResetCardRowState(episodes.firstOrNull()?.id)

        CardRow(
            state = scrollState,
            modifier = Modifier.fillMaxWidth().height(fixedRowHeight),
            contentPadding = CardDimensions.RowContentPadding,
        ) {
            items(
                items = episodes,
                key = { episode -> "podcast_${episode.id}" },
                contentType = { "podcast_episode_card" },
            ) { episode ->
                val progress =
                    if (
                        !episode.played &&
                            episode.playbackPositionTicks > 0L &&
                            episode.runtimeTicks > 0L
                    ) {
                        (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks).coerceIn(
                            0f,
                            1f,
                        )
                    } else {
                        0f
                    }
                SquareListeningCard(
                    imageUrl = episode.images.primary?.toString(),
                    blurHash = episode.images.primaryImageBlurHash,
                    title = episode.name,
                    subtitle = episode.album ?: episode.artist.orEmpty(),
                    progress = progress,
                    cardWidth = cardWidth,
                    onClick = { onEpisodeClick(episode) },
                )
            }
        }
    }
}

@Composable
private fun rememberAutoResetCardRowState(firstId: Any?): LazyListState {
    val scrollState = rememberCardRowListState()
    val lastKnownFirstId = remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(firstId) {
        val previous = lastKnownFirstId.value
        if (firstId != null && previous != null && firstId != previous) {
            scrollState.scrollToItem(0)
        }
        if (firstId != null) {
            lastKnownFirstId.value = firstId
        }
    }
    return scrollState
}

@Composable
private fun SquareListeningCard(
    imageUrl: String?,
    blurHash: String?,
    title: String,
    subtitle: String,
    progress: Float,
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_SQUARE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = imageUrl,
                    blurHash = blurHash,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth,
                )
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

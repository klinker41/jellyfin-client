@file:UnstableApi

package com.makd.afinity.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.cast.CastManager
import com.makd.afinity.player.AudioService
import com.makd.afinity.player.audiobookshelf.AudiobookSource
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.ui.player.AudioMiniPlayer
import com.makd.afinity.ui.player.AudioMiniPlayerState
import timber.log.Timber

/** Isolates high-frequency audio playback progress ticks from the root [MainNavigation] layout. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AudioMiniPlayerOverlay(
    visible: Boolean,
    audiobookshelfPlaybackManager: AudiobookshelfPlaybackManager,
    musicPlaybackManager: MusicPlaybackManager,
    audiobookshelfPlayer: AudiobookshelfPlayer,
    castManager: CastManager,
    navController: NavController,
    onMeasuredHeightDp: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val audiobookshelfPlaybackState by
            audiobookshelfPlaybackManager.playbackState.collectAsStateWithLifecycle()
        val musicPlaybackState by musicPlaybackManager.state.collectAsStateWithLifecycle()
        val currentTrack = musicPlaybackState.currentTrack
        val miniPlayerState: AudioMiniPlayerState? =
            when {
                audiobookshelfPlaybackState.sessionId != null ->
                    AudioMiniPlayerState.Abs(
                        title = audiobookshelfPlaybackState.displayTitle,
                        author = audiobookshelfPlaybackState.displayAuthor,
                        currentChapter = audiobookshelfPlaybackState.currentChapter,
                        coverUrl = audiobookshelfPlaybackState.coverUrl,
                        currentTime = audiobookshelfPlaybackState.currentTime,
                        duration = audiobookshelfPlaybackState.duration,
                        isPlaying = audiobookshelfPlaybackState.isPlaying,
                        isBuffering = audiobookshelfPlaybackState.isBuffering,
                    )

                currentTrack != null ->
                    AudioMiniPlayerState.Music(
                        title = currentTrack.name,
                        artist = currentTrack.artist ?: currentTrack.artists.firstOrNull(),
                        coverUrl = currentTrack.images.primary?.toString(),
                        blurHash = currentTrack.images.primaryImageBlurHash,
                        positionMs = musicPlaybackState.positionMs,
                        durationMs = musicPlaybackState.durationMs,
                        isPlaying = musicPlaybackState.isPlaying,
                        isBuffering = musicPlaybackState.isBuffering,
                    )

                else -> null
            }

        if (miniPlayerState != null) {
            val density = LocalDensity.current
            AudioMiniPlayer(
                state = miniPlayerState,
                modifier =
                    Modifier.navigationBarsPadding().onSizeChanged { size ->
                        if (size.height > 0) {
                            onMeasuredHeightDp(with(density) { size.height.toDp() })
                        }
                    },
                animatedVisibilityScope = this@AnimatedVisibility,
                onPlayPauseClick = {
                    when (miniPlayerState) {
                        is AudioMiniPlayerState.Abs ->
                            if (audiobookshelfPlayer.isPlaying()) audiobookshelfPlayer.pause()
                            else audiobookshelfPlayer.play()

                        is AudioMiniPlayerState.Music ->
                            if (musicPlaybackState.isPlaying) musicPlaybackManager.pause()
                            else musicPlaybackManager.play()
                    }
                },
                onBackward = {
                    when (miniPlayerState) {
                        is AudioMiniPlayerState.Abs -> audiobookshelfPlayer.skipBackward()
                        is AudioMiniPlayerState.Music -> musicPlaybackManager.skipToPrevious()
                    }
                },
                onForward = {
                    when (miniPlayerState) {
                        is AudioMiniPlayerState.Abs -> audiobookshelfPlayer.skipForward()
                        is AudioMiniPlayerState.Music -> musicPlaybackManager.skipToNext()
                    }
                },
                onCloseClick = {
                    when (miniPlayerState) {
                        is AudioMiniPlayerState.Abs -> {
                            Timber.tag("ABS-MiniPlayer")
                                .d(
                                    "MainNavigation: ABS onCloseClick — calling pause()+closeSession()",
                                )
                            audiobookshelfPlayer.pause()
                            audiobookshelfPlayer.closeSession()
                        }

                        is AudioMiniPlayerState.Music -> {
                            Timber.tag("ABS-MiniPlayer")
                                .d(
                                    "MainNavigation: Music onCloseClick — calling stop()+ACTION_STOP",
                                )
                            if (castManager.castState.value.isMusicCasting) {
                                castManager.stop()
                            }
                            musicPlaybackManager.stop()
                            navController.context.startService(
                                Intent(navController.context, AudioService::class.java)
                                    .setAction(AudioService.ACTION_STOP),
                            )
                        }
                    }
                },
                onClick = {
                    when (miniPlayerState) {
                        is AudioMiniPlayerState.Abs -> {
                            val itemId = audiobookshelfPlaybackState.itemId
                            val episodeId = audiobookshelfPlaybackState.episodeId
                            val source =
                                Destination.AUDIOBOOK_SOURCE_JELLYFIN.takeIf {
                                    audiobookshelfPlaybackState.source == AudiobookSource.JELLYFIN
                                }
                            if (itemId != null) {
                                navController.navigate(
                                    Destination.createAudiobookshelfPlayerRoute(
                                        itemId = itemId,
                                        episodeId = episodeId,
                                        source = source,
                                    ),
                                )
                            }
                        }

                        is AudioMiniPlayerState.Music ->
                            navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                    }
                },
            )
        }
    }
}

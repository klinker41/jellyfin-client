package com.makd.afinity.player.music

import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.playback.PlaybackRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class MusicProgressReporter
internal constructor(
    private val playbackRepository: PlaybackRepository,
    private val podcastProgressTracker: PodcastProgressTracker,
    private val progressInterval: Duration = 10.seconds,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(
        playbackRepository: PlaybackRepository,
        podcastProgressTracker: PodcastProgressTracker,
    ) : this(playbackRepository, podcastProgressTracker, 10.seconds, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val reportOrder = Mutex()
    private val countedSessions = mutableMapOf<String, Boolean>()
    private val podcastSessions = mutableSetOf<String>()
    private var latestTrack: AfinityTrack? = null
    private var progressJob: Job? = null
    @Volatile private var currentTrackId: UUID? = null
    @Volatile private var currentTrack: AfinityTrack? = null
    @Volatile private var playSessionId: String = UUID.randomUUID().toString()
    @Volatile private var playMethod: String = "DirectPlay"

    private fun inOrder(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reportOrder.withLock { block() } }
    }

    fun onPlaybackStarted(
        trackId: UUID,
        playSessionId: String? = null,
        playMethod: String = "DirectPlay",
        track: AfinityTrack? = null,
    ) {
        this.playSessionId = playSessionId ?: UUID.randomUUID().toString()
        this.playMethod = playMethod
        currentTrackId = trackId
        currentTrack = track
        val sessionId = this.playSessionId
        inOrder {
            latestTrack = track
            val isPodcast = track != null && podcastProgressTracker.isPodcastTrack(track)
            if (isPodcast) {
                podcastSessions.add(sessionId)
                return@inOrder
            }
            podcastSessions.remove(sessionId)
            countedSessions[sessionId] =
                runCatching {
                        playbackRepository.reportPlaybackStart(
                            itemId = trackId,
                            sessionId = sessionId,
                            mediaSourceId = trackId.toString(),
                            playMethod = playMethod,
                            canSeek = true,
                        )
                    }
                    .onFailure { Timber.w(it, "Failed to report playback start for $trackId") }
                    .getOrDefault(false)
        }
    }

    fun updatePlayMethod(trackId: UUID, playMethod: String) {
        if (currentTrackId != trackId) return
        this.playMethod = playMethod
    }

    fun startProgressUpdates(getPositionMs: () -> Long, isPaused: () -> Boolean) {
        progressJob?.cancel()
        progressJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(progressInterval)
                reportOrder.withLock {
                    val trackId = currentTrackId ?: return@withLock
                    val sessionId = playSessionId
                    val method = playMethod
                    val posMs = getPositionMs()
                    val positionTicks = posMs * 10_000L
                    val paused = isPaused()
                    val track =
                        latestTrack?.takeIf { it.id == trackId }
                            ?: currentTrack?.takeIf { it.id == trackId }
                    val isPodcast =
                        sessionId in podcastSessions ||
                            (track != null && podcastProgressTracker.isPodcastTrack(track))
                    if (isPodcast) {
                        podcastSessions.add(sessionId)
                        ticks++
                        if (track != null) {
                            val updated =
                                podcastProgressTracker.syncPeriodicIfDue(
                                    track = track,
                                    positionMs = posMs,
                                    isPaused = paused,
                                    tickCount = ticks,
                                )
                            if (
                                updated != null &&
                                    updated.id == track.id &&
                                    currentTrackId == track.id
                            ) {
                                latestTrack = updated
                                currentTrack = updated
                            }
                        }
                        return@withLock
                    }
                    runCatching {
                        playbackRepository.reportPlaybackProgress(
                            itemId = trackId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                            isPaused = paused,
                            playMethod = method,
                            isMusic = true,
                        )
                    }
                        .onFailure { Timber.w(it, "Failed to report playback progress") }
                }
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun onPlaybackPaused(positionMs: Long) {
        val trackId = currentTrackId ?: return
        val fallbackTrack = currentTrack
        inOrder {
            val track = latestTrack?.takeIf { it.id == trackId } ?: fallbackTrack ?: return@inOrder
            val updated =
                podcastProgressTracker.syncProgress(
                    track,
                    positionMs,
                    isEnded = false,
                    allowCompletion = false,
                )
            if (updated != null && currentTrackId == track.id) {
                latestTrack = updated
                currentTrack = updated
            }
        }
    }

    fun onTrackTransition(previousPositionMs: Long, previousEnded: Boolean) {
        reportStop(previousPositionMs, previousEnded)
    }

    fun onPlaybackStopped(
        positionMs: Long,
        isEnded: Boolean = false,
        allowCompletion: Boolean = true,
    ) {
        reportStop(positionMs, isEnded, allowCompletion)
        stopProgressUpdates()
    }

    private fun reportStop(positionMs: Long, isEnded: Boolean, allowCompletion: Boolean = true) {
        val trackId = currentTrackId ?: return
        val fallbackTrack = currentTrack
        currentTrackId = null
        currentTrack = null
        val sessionId = playSessionId
        inOrder {
            val track = latestTrack?.takeIf { it.id == trackId } ?: fallbackTrack
            latestTrack = null
            val counted = countedSessions.remove(sessionId) == true
            val isPodcast =
                podcastSessions.remove(sessionId) ||
                    (track != null && podcastProgressTracker.isPodcastTrack(track))
            if (isPodcast) {
                if (track != null) {
                    podcastProgressTracker.syncProgress(
                        track,
                        positionMs,
                        isEnded = isEnded,
                        allowCompletion = allowCompletion,
                    )
                }
                return@inOrder
            }
            runCatching {
                playbackRepository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = sessionId,
                    positionTicks = positionMs * 10_000L,
                    mediaSourceId = trackId.toString(),
                    isEnded = isEnded,
                    isMusic = true,
                    playCountedOnServer = counted,
                )
            }
                .onFailure { Timber.w(it, "Failed to report playback stop for $trackId") }
        }
    }
}

package com.makd.afinity.player

import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.podcasts.ContinueListeningEntry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ContinueListeningQueueItem {
    val itemId: UUID

    data class Audiobook(val bookId: UUID) : ContinueListeningQueueItem {
        override val itemId: UUID
            get() = bookId
    }

    data class PodcastEpisode(val track: AfinityTrack) : ContinueListeningQueueItem {
        override val itemId: UUID
            get() = track.id
    }
}

fun ContinueListeningEntry.toQueueItem(): ContinueListeningQueueItem =
    when (this) {
        is ContinueListeningEntry.Audiobook ->
            ContinueListeningQueueItem.Audiobook(book.playableParts.first().id)
        is ContinueListeningEntry.PodcastEpisode -> ContinueListeningQueueItem.PodcastEpisode(track)
    }

@Singleton
class ContinueListeningQueue @Inject constructor() {

    data class PendingAdvance(val item: ContinueListeningQueueItem, val generation: Long)

    private val lock = Any()
    private var activeItem: ContinueListeningQueueItem? = null
    private val upcoming = ArrayDeque<ContinueListeningQueueItem>()
    private var generation: Long = 0L

    private val _isAdvancing = MutableStateFlow(false)
    val isAdvancing: StateFlow<Boolean> = _isAdvancing.asStateFlow()

    private val _advancedTo =
        MutableSharedFlow<PendingAdvance>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val advancedTo: SharedFlow<PendingAdvance> = _advancedTo.asSharedFlow()

    fun startFrom(entries: List<ContinueListeningEntry>, clickedIndex: Int) {
        startFromItems(entries.map { it.toQueueItem() }, clickedIndex)
    }

    fun startFromAudiobooks(books: List<AfinityAudiobook>, clickedIndex: Int) {
        startFromItems(
            books.map { ContinueListeningQueueItem.Audiobook(it.playableParts.first().id) },
            clickedIndex,
        )
    }

    fun startFromItems(items: List<ContinueListeningQueueItem>, clickedIndex: Int) {
        synchronized(lock) {
            generation++
            upcoming.clear()
            _isAdvancing.value = false
            if (clickedIndex !in items.indices || clickedIndex >= items.lastIndex) {
                activeItem = null
                return
            }
            activeItem = items[clickedIndex]
            upcoming.addAll(items.subList(clickedIndex + 1, items.size))
        }
    }

    fun consumeNextForAudiobook(endedBookId: String?): PendingAdvance? {
        if (endedBookId == null) return null
        return consumeNextIf { current ->
            current is ContinueListeningQueueItem.Audiobook &&
                current.bookId.toString() == endedBookId
        }
    }

    fun consumeNextForPodcast(endedTrackId: UUID?): PendingAdvance? {
        if (endedTrackId == null) return null
        return consumeNextIf { current ->
            current is ContinueListeningQueueItem.PodcastEpisode && current.track.id == endedTrackId
        }
    }

    private inline fun consumeNextIf(
        matches: (ContinueListeningQueueItem) -> Boolean,
    ): PendingAdvance? =
        synchronized(lock) {
            val current = activeItem ?: return@synchronized null
            if (!matches(current)) return@synchronized null
            val next = upcoming.removeFirstOrNull()
            if (next != null) {
                activeItem = next
                _isAdvancing.value = true
                PendingAdvance(next, generation)
            } else {
                activeItem = null
                null
            }
        }

    fun consumeNextAfterFailure(generation: Long): PendingAdvance? =
        synchronized(lock) {
            if (this.generation != generation) return@synchronized null
            val next = upcoming.removeFirstOrNull()
            activeItem = next
            if (next == null) {
                _isAdvancing.value = false
            }
            next?.let { PendingAdvance(it, generation) }
        }

    fun isStillActive(generation: Long): Boolean =
        synchronized(lock) { this.generation == generation }

    fun notifyAdvanced(generation: Long, item: ContinueListeningQueueItem) {
        val clearImmediately =
            synchronized(lock) {
                if (this.generation != generation) return
                _advancedTo.tryEmit(PendingAdvance(item, generation))
                _advancedTo.subscriptionCount.value == 0
            }
        if (clearImmediately) {
            finishAdvancing(generation)
        }
    }

    fun finishAdvancing(generation: Long) {
        synchronized(lock) {
            if (this.generation == generation) {
                _isAdvancing.value = false
            }
        }
    }

    fun clearIfDifferentAudiobook(bookId: String) {
        synchronized(lock) {
            val current = activeItem
            if (
                current is ContinueListeningQueueItem.Audiobook &&
                    current.bookId.toString() == bookId
            ) {
                return
            }
            clearLocked()
        }
    }

    fun clearIfDifferentPodcast(tracks: List<AfinityTrack>) {
        synchronized(lock) {
            val current = activeItem
            if (
                tracks.size == 1 &&
                    current is ContinueListeningQueueItem.PodcastEpisode &&
                    current.track.id == tracks[0].id
            ) {
                return
            }
            clearLocked()
        }
    }

    fun clear() {
        synchronized(lock) { clearLocked() }
    }

    private fun clearLocked() {
        activeItem = null
        upcoming.clear()
        generation++
        _isAdvancing.value = false
    }
}

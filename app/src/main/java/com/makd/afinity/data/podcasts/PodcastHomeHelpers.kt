package com.makd.afinity.data.podcasts

import androidx.compose.runtime.Immutable
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.music.AfinityTrack
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.ItemFields

const val LATEST_PODCASTS_LIMIT = 16
const val CONTINUE_LISTENING_LIMIT = 12
val PODCAST_EPISODE_FIELDS = listOf(ItemFields.DATE_CREATED, ItemFields.PARENT_ID)

fun AfinityCollection.isPodcastLibrary(): Boolean =
    type == CollectionType.Music && name.contains("podcast", ignoreCase = true)

fun List<AfinityCollection>.podcastLibraries(): List<AfinityCollection> = filter {
    it.isPodcastLibrary()
}

@Immutable
sealed interface ContinueListeningEntry {
    val id: UUID
    val lastPlayedDate: LocalDateTime?

    @Immutable
    data class Audiobook(val book: AfinityAudiobook) : ContinueListeningEntry {
        override val id: UUID
            get() = book.id

        override val lastPlayedDate: LocalDateTime?
            get() = book.lastPlayedDate
    }

    @Immutable
    data class PodcastEpisode(val track: AfinityTrack) : ContinueListeningEntry {
        override val id: UUID
            get() = track.id

        override val lastPlayedDate: LocalDateTime?
            get() = track.lastPlayedDate
    }
}

fun mergeContinueListening(
    books: List<AfinityAudiobook>,
    podcasts: List<AfinityTrack>,
    limit: Int = CONTINUE_LISTENING_LIMIT,
): List<ContinueListeningEntry> {
    val bookEntries =
        books
            .filter { !it.played && it.playbackPositionTicks > 0L }
            .map { ContinueListeningEntry.Audiobook(it) }
    val podcastEntries =
        podcasts
            .filter { !it.played && it.playbackPositionTicks > 0L }
            .map { ContinueListeningEntry.PodcastEpisode(it) }
    return (bookEntries + podcastEntries)
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<ContinueListeningEntry> { it.lastPlayedDate != null }
                .thenByDescending { it.lastPlayedDate },
        )
        .take(limit)
}

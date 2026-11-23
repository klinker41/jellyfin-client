package com.makd.afinity.data.autodownload

import com.makd.afinity.data.manager.MediaChangeEvent
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityCollection

val AUTO_DOWNLOAD_SUPPORTED_COLLECTION_TYPES =
    setOf(
        CollectionType.Books,
        CollectionType.Music,
        CollectionType.Movies,
        CollectionType.TvShows,
        CollectionType.HomeVideos,
        CollectionType.MusicVideos,
        CollectionType.Mixed,
    )

const val FAILED_RETRY_COOLDOWN_MS = 6L * 60 * 60 * 1000
const val MAX_AUTO_DOWNLOAD_ITEMS_PER_LIBRARY = 500
const val AUTO_DOWNLOAD_PAGE_SIZE = 200

fun List<AfinityCollection>.autoDownloadableLibraries(): List<AfinityCollection> = filter {
    it.type in AUTO_DOWNLOAD_SUPPORTED_COLLECTION_TYPES
}

fun CollectionType.autoDownloadItemTypes(): List<String> =
    when (this) {
        CollectionType.Books -> listOf("AUDIO_BOOK", "BOOK")
        CollectionType.Music -> listOf("AUDIO")
        CollectionType.Movies -> listOf("MOVIE")
        CollectionType.TvShows -> listOf("EPISODE")
        CollectionType.HomeVideos -> listOf("VIDEO")
        CollectionType.MusicVideos -> listOf("MUSIC_VIDEO")
        CollectionType.Mixed ->
            listOf("MOVIE", "EPISODE", "VIDEO", "MUSIC_VIDEO", "AUDIO", "AUDIO_BOOK", "BOOK")
        else -> emptyList()
    }

fun isEligibleForAutoDownload(played: Boolean, playbackPositionTicks: Long): Boolean =
    !played || playbackPositionTicks > 0L

fun shouldSkipExistingDownload(
    status: DownloadStatus?,
    updatedAt: Long,
    nowMs: Long,
): Boolean =
    when (status) {
        null,
        DownloadStatus.CANCELLED -> false
        DownloadStatus.COMPLETED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.QUEUED,
        DownloadStatus.PAUSED -> true
        DownloadStatus.FAILED -> nowMs - updatedAt < FAILED_RETRY_COOLDOWN_MS
    }

fun isMarkedPlayedEvent(event: MediaChangeEvent): Boolean {
    val positionTicks =
        event.patch?.playbackPositionTicks
            ?: event.userData?.playbackPositionTicks
            ?: event.updatedItem?.playbackPositionTicks
    if (positionTicks != null && positionTicks > 0L) {
        return false
    }
    return event.patch?.played == true ||
        event.userData?.played == true ||
        event.updatedItem?.played == true
}

fun isMarkedUnplayedEvent(event: MediaChangeEvent): Boolean =
    event.patch?.played == false ||
        event.userData?.played == false ||
        event.updatedItem?.played == false

package com.makd.afinity.ui.audiobookshelf.libraries

import com.makd.afinity.data.models.audiobookshelf.LibraryItem

internal const val ABS_CONTINUE_LISTENING_SECTION_ID = "continue-listening"

internal sealed interface AbsHomeClickTarget {
    data class Player(val itemId: String, val episodeId: String?) : AbsHomeClickTarget

    data class ItemDetail(val itemId: String) : AbsHomeClickTarget
}

/**
 * Resolves taps on Audiobookshelf home shelves: Continue Listening books start playback directly,
 * episodes with a recentEpisode resume playback, and all other items open their detail screen.
 */
internal fun resolveAbsHomeClick(sectionId: String, item: LibraryItem): AbsHomeClickTarget {
    val episodeId = item.recentEpisode?.id
    return when {
        episodeId != null -> AbsHomeClickTarget.Player(item.id, episodeId)
        sectionId == ABS_CONTINUE_LISTENING_SECTION_ID && item.mediaType != "podcast" ->
            AbsHomeClickTarget.Player(item.id, null)
        else -> AbsHomeClickTarget.ItemDetail(item.id)
    }
}

package com.makd.afinity.ui.audiobookshelf.libraries

import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.Media
import com.makd.afinity.data.models.audiobookshelf.MediaMetadata
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class AbsHomeClickTargetTest {

    private fun testItem(
        id: String = "item-1",
        mediaType: String = "book",
        recentEpisodeId: String? = null,
    ): LibraryItem =
        LibraryItem(
            id = id,
            libraryId = "lib-1",
            mediaType = mediaType,
            media = Media(metadata = MediaMetadata(title = "Title")),
            recentEpisode = recentEpisodeId?.let { PodcastEpisode(id = it, title = "Episode") },
        )

    @Test
    fun `continue listening book resolves directly to player`() {
        val target =
            resolveAbsHomeClick(
                sectionId = ABS_CONTINUE_LISTENING_SECTION_ID,
                item = testItem(id = "book-1", mediaType = "book"),
            )
        assertEquals(AbsHomeClickTarget.Player(itemId = "book-1", episodeId = null), target)
    }

    @Test
    fun `continue listening podcast with recent episode resolves directly to player`() {
        val target =
            resolveAbsHomeClick(
                sectionId = ABS_CONTINUE_LISTENING_SECTION_ID,
                item = testItem(id = "pod-1", mediaType = "podcast", recentEpisodeId = "ep-9"),
            )
        assertEquals(AbsHomeClickTarget.Player(itemId = "pod-1", episodeId = "ep-9"), target)
    }

    @Test
    fun `continue listening podcast without recent episode opens item detail`() {
        val target =
            resolveAbsHomeClick(
                sectionId = ABS_CONTINUE_LISTENING_SECTION_ID,
                item = testItem(id = "pod-2", mediaType = "podcast", recentEpisodeId = null),
            )
        assertEquals(AbsHomeClickTarget.ItemDetail(itemId = "pod-2"), target)
    }

    @Test
    fun `non continue listening book opens item detail while podcast episode resumes`() {
        assertEquals(
            AbsHomeClickTarget.ItemDetail(itemId = "book-2"),
            resolveAbsHomeClick(
                sectionId = "recently-added",
                item = testItem(id = "book-2", mediaType = "book"),
            ),
        )
        assertEquals(
            AbsHomeClickTarget.Player(itemId = "pod-3", episodeId = "ep-3"),
            resolveAbsHomeClick(
                sectionId = "continue-listening-podcast",
                item = testItem(id = "pod-3", mediaType = "podcast", recentEpisodeId = "ep-3"),
            ),
        )
    }
}

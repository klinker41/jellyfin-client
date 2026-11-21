package com.makd.afinity.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastPlaybackStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun TestScope.createStore(): LastPlaybackStore {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tempFolder.newFolder().resolve("last_playback.preferences_pb")
            }
        return LastPlaybackStore(dataStore, backgroundScope)
    }

    private suspend fun LastPlaybackStore.awaitSnapshot(
        predicate: (LastPlaybackSnapshot) -> Boolean,
    ): LastPlaybackSnapshot =
        withContext(Dispatchers.Default) { withTimeout(2_000L) { snapshot.first(predicate) } }

    @Test
    fun `recordMusic, recordMusicPosition, and recordDisplay persist and emit snapshot updates`() =
        runTest {
            val store = createStore()
            val key = LastPlaybackStore.sessionKey("srv-1", testUuid(7))
            assertEquals("srv-1|${testUuid(7)}", key)

            assertEquals(LastPlaybackSnapshot(null, null, null), store.snapshot.first())

            store.recordMusic(key, positionMs = -500L)
            store.recordDisplay("Midnight", "Artist A", "https://jf.example.com/art")
            val first = store.awaitSnapshot {
                it.last == LastPlayback.Music(key, 0L) && it.display?.title == "Midnight"
            }
            assertEquals(
                ResumeDisplay("Midnight", "Artist A", "https://jf.example.com/art"),
                first.display,
            )
            assertEquals(key, first.music?.sessionKey)

            store.recordMusicPosition(42_500L)
            store.awaitSnapshot { it.last == LastPlayback.Music(key, 42_500L) }
            assertEquals(42_500L, store.readMusicPosition())
            assertEquals(LastPlayback.Music(key, 42_500L), store.read())
            assertEquals("Midnight", store.readDisplay()?.title)
        }

    @Test
    fun `recordAbs and recordJellyfinAudiobook overwrite active engine without corrupting each other`() =
        runTest {
            val store = createStore()
            val key = LastPlaybackStore.sessionKey("abs-1", testUuid(2))

            store.recordMusic(key, 15_000L)
            store.awaitSnapshot { it.last == LastPlayback.Music(key, 15_000L) }

            store.recordAbs(key, itemId = "book-1", episodeId = "ep-9")
            store.awaitSnapshot { it.last == LastPlayback.Abs(key, "book-1", "ep-9") }
            assertEquals(LastPlayback.Abs(key, "book-1", "ep-9"), store.read())

            // When active engine is not MUSIC, recordMusicPosition leaves the active engine intact
            store.recordMusicPosition(99_000L)
            store.recordDisplay("Barrier", null, null)
            store.awaitSnapshot { it.display?.title == "Barrier" }
            assertEquals(LastPlayback.Abs(key, "book-1", "ep-9"), store.read())
            assertEquals(15_000L, store.readMusicPosition())

            // Clearing episodeId removes the stored episode key
            store.recordAbs(key, itemId = "book-2", episodeId = null)
            store.awaitSnapshot { it.last == LastPlayback.Abs(key, "book-2", null) }
            assertNull((store.read() as LastPlayback.Abs).episodeId)

            store.recordJellyfinAudiobook(key, itemId = "jf-book-3")
            store.awaitSnapshot { it.last == LastPlayback.JellyfinAudiobook(key, "jf-book-3") }
            assertEquals(LastPlayback.JellyfinAudiobook(key, "jf-book-3"), store.read())
        }
}

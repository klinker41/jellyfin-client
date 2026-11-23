package com.makd.afinity.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.floatPreferencesKey
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPlaybackSpeedStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun TestScope.createDataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFolder().resolve("speed_prefs.preferences_pb")
        }

    @Test
    fun `default speed is 1_0f and speeds persist per library independently`() = runTest {
        val dataStore = createDataStore()
        val store = LibraryPlaybackSpeedStore(dataStore, backgroundScope)

        val absBookKey = LibraryPlaybackSpeedStore.absLibraryKey("lib-books", "book")
        val absPodcastKey = LibraryPlaybackSpeedStore.absLibraryKey("lib-podcasts", "podcast")
        val jfBookKey = LibraryPlaybackSpeedStore.jellyfinAudiobookKey(testUuid(10))
        val jfPodcastKey1 = LibraryPlaybackSpeedStore.jellyfinPodcastKey(testUuid(20))
        val jfPodcastKey2 = LibraryPlaybackSpeedStore.jellyfinPodcastKey(testUuid(21))

        assertEquals(1.0f, store.getSpeed(absBookKey), 0.001f)
        assertEquals(1.0f, store.getSpeed(absPodcastKey), 0.001f)
        assertEquals(1.0f, store.getSpeed(jfBookKey), 0.001f)
        assertEquals(1.0f, store.getSpeed(jfPodcastKey1), 0.001f)
        assertEquals(1.0f, store.getSpeed(jfPodcastKey2), 0.001f)

        store.setSpeed(absBookKey, 1.25f)
        store.saveSpeed(absPodcastKey, 1.75f)
        store.setSpeed(jfBookKey, 1.15f)
        store.saveSpeed(jfPodcastKey1, 1.5f)
        store.setSpeed(jfPodcastKey2, 2.0f)
        advanceUntilIdle()

        // Fresh store instance reading the same persisted DataStore
        val reloaded = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        assertEquals(1.25f, reloaded.getSpeed(absBookKey), 0.001f)
        assertEquals(1.75f, reloaded.getSpeed(absPodcastKey), 0.001f)
        assertEquals(1.15f, reloaded.getSpeed(jfBookKey), 0.001f)
        assertEquals(1.5f, reloaded.getSpeed(jfPodcastKey1), 0.001f)
        assertEquals(2.0f, reloaded.getSpeed(jfPodcastKey2), 0.001f)
    }

    @Test
    fun `speeds are clamped to supported range and reset to 1_0f removes override`() = runTest {
        val dataStore = createDataStore()
        val store = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        val key = LibraryPlaybackSpeedStore.absLibraryKey("lib-1", "book")

        store.setSpeed(key, 0.1f)
        assertEquals(LibraryPlaybackSpeedStore.MIN_SPEED, store.getSpeed(key), 0.001f)

        store.setSpeed(key, 5.0f)
        assertEquals(LibraryPlaybackSpeedStore.MAX_SPEED, store.getSpeed(key), 0.001f)

        store.setSpeed(key, 1.0f)
        val reloaded = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        assertEquals(1.0f, reloaded.getSpeed(key), 0.001f)
    }

    @Test
    fun `cacheJellyfinItemLibrary round-trips item library association`() = runTest {
        val dataStore = createDataStore()
        val store = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        val itemId = testUuid(100)
        val libId = testUuid(200)

        assertNull(store.getCachedJellyfinItemLibrary(itemId))
        store.cacheJellyfinItemLibrary(itemId, libId)

        val reloaded = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        assertEquals(libId, reloaded.getCachedJellyfinItemLibrary(itemId))
    }

    @Test
    fun `rapid sequential saveSpeed calls persist latest speed`() = runTest {
        val dataStore = createDataStore()
        val store = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        val key = LibraryPlaybackSpeedStore.jellyfinPodcastKey(testUuid(300))

        store.saveSpeed(key, 1.25f)
        store.saveSpeed(key, 1.5f)
        store.saveSpeed(key, 2.0f)
        advanceUntilIdle()
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) { dataStore.data.first { it[floatPreferencesKey(key)] == 2.0f } }
        }

        val reloaded = LibraryPlaybackSpeedStore(dataStore, backgroundScope)
        assertEquals(2.0f, reloaded.getSpeed(key), 0.001f)
    }
}

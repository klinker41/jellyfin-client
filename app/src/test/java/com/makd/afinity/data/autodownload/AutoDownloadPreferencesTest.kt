package com.makd.afinity.data.autodownload

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoDownloadPreferencesTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `preferences scope libraries, managed items, and podcast albums per server and user`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { File(tempFolder.root, "autodownload.preferences_pb") },
                )
            val prefs = AutoDownloadPreferences(dataStore)
            val user1 = testUuid(1)
            val user2 = testUuid(2)
            val libA = testUuid(10)
            val libB = testUuid(11)
            val item1 = testUuid(100)
            val item2 = testUuid(101)
            val item3 = testUuid(102)

            assertEquals(emptySet<java.util.UUID>(), prefs.getEnabledLibraryIds("srv-1", user1))

            prefs.setLibraryEnabled("srv-1", user1, libA, enabled = true)
            prefs.setLibraryEnabled("srv-1", user1, libB, enabled = true)
            prefs.setLibraryEnabled("srv-2", user1, libB, enabled = true)
            prefs.setLibraryEnabled("srv-1", user2, libB, enabled = true)

            assertEquals(
                setOf(libA, libB),
                prefs.getEnabledLibraryIdsFlow("srv-1", user1).first(),
            )
            assertEquals(setOf(libB), prefs.getEnabledLibraryIds("srv-2", user1))
            assertEquals(setOf(libB), prefs.getEnabledLibraryIds("srv-1", user2))

            prefs.setManagedItemIds("srv-1", user1, libA, setOf(item1, item2))
            prefs.setManagedItemIds("srv-1", user1, libB, setOf(item3))

            assertTrue(prefs.isItemManagedInEnabledLibraries("srv-1", user1, item1))
            assertTrue(prefs.isItemManagedInEnabledLibraries("srv-1", user1, item3))
            assertFalse(prefs.isItemManagedInEnabledLibraries("srv-1", user2, item1))

            prefs.removeManagedItem("srv-1", user1, item1)
            assertFalse(prefs.isItemManagedInEnabledLibraries("srv-1", user1, item1))
            assertEquals(setOf(item2), prefs.getManagedItemIds("srv-1", user1, libA))

            // Disabling libA also clears its managed items
            prefs.setLibraryEnabled("srv-1", user1, libA, enabled = false)
            assertEquals(setOf(libB), prefs.getEnabledLibraryIds("srv-1", user1))
            assertEquals(emptySet<java.util.UUID>(), prefs.getManagedItemIds("srv-1", user1, libA))
            assertFalse(prefs.isItemManagedInEnabledLibraries("srv-1", user1, item2))
            assertTrue(prefs.isItemManagedInEnabledLibraries("srv-1", user1, item3))

            val album1 = testUuid(200)
            val album2 = testUuid(201)
            prefs.setCachedPodcastAlbumIds("srv-1", user1, setOf(album1, album2))
            assertEquals(setOf(album1, album2), prefs.getCachedPodcastAlbumIds("srv-1", user1))
            assertEquals(
                emptySet<java.util.UUID>(),
                prefs.getCachedPodcastAlbumIds("srv-1", user2),
            )
        }
}

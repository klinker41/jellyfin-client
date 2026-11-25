package com.makd.afinity.tvlauncher

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TvPinnedAppsStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `pinned packages default empty and persist pin order across updates`() = runTest {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tempFolder.root, "tv_pinned_apps.preferences_pb") },
            )
        val store = TvPinnedAppsStore(dataStore)

        assertEquals(emptyList<String>(), store.pinnedPackages.first())

        store.setPinned("com.example.first", pinned = true)
        store.setPinned("com.example.second", pinned = true)
        store.setPinned("com.example.first", pinned = true)
        store.setPinned("   ", pinned = true)
        assertEquals(
            listOf("com.example.first", "com.example.second"),
            store.pinnedPackages.first(),
        )

        store.setPinned("com.example.first", pinned = false)
        assertEquals(listOf("com.example.second"), store.pinnedPackages.first())

        store.setPinned("com.example.second", pinned = false)
        assertEquals(emptyList<String>(), store.pinnedPackages.first())
    }

    @Test
    fun `parsePinnedPackages trims, skips blanks, and deduplicates`() {
        assertEquals(emptyList<String>(), parsePinnedPackages(null))
        assertEquals(emptyList<String>(), parsePinnedPackages("   "))
        assertEquals(
            listOf("com.a", "com.b", "com.c"),
            parsePinnedPackages(" com.a , , com.b, com.a, com.c "),
        )
    }
}

package com.makd.afinity.data.repository.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.makd.afinity.data.models.common.DrawerLibrariesMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NavigationPreferencesDefaultsTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun TestScope.createRepository(): PreferencesRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tempFolder.newFolder().resolve("settings.preferences_pb")
            }
        return PreferencesRepositoryImpl(dataStore)
    }

    @Test
    fun unsetNavigationPreferencesReturnExpectedDefaults() = runTest {
        val repository = createRepository()

        assertTrue(repository.getNavigationDrawerEnabled())
        assertTrue(repository.getNavigationDrawerEnabledFlow().first())
        assertEquals(DrawerLibrariesMode.EACH, repository.getDrawerLibrariesModeFlow().first())
        assertTrue(repository.getSideSheetEnabled())
        assertTrue(repository.getSideSheetEnabledFlow().first())
    }

    @Test
    fun navigationPreferencesPersistExplicitUpdates() = runTest {
        val repository = createRepository()

        repository.setNavigationDrawerEnabled(false)
        assertFalse(repository.getNavigationDrawerEnabled())
        assertFalse(repository.getNavigationDrawerEnabledFlow().first())

        repository.setNavigationDrawerEnabled(true)
        assertTrue(repository.getNavigationDrawerEnabled())
        assertTrue(repository.getNavigationDrawerEnabledFlow().first())

        repository.setSideSheetEnabled(false)
        assertFalse(repository.getSideSheetEnabled())
        assertFalse(repository.getSideSheetEnabledFlow().first())

        repository.setSideSheetEnabled(true)
        assertTrue(repository.getSideSheetEnabled())
        assertTrue(repository.getSideSheetEnabledFlow().first())

        repository.setDrawerLibrariesMode(DrawerLibrariesMode.OFF)
        assertEquals(DrawerLibrariesMode.OFF, repository.getDrawerLibrariesModeFlow().first())

        repository.setDrawerLibrariesMode(DrawerLibrariesMode.SCREEN)
        assertEquals(DrawerLibrariesMode.SCREEN, repository.getDrawerLibrariesModeFlow().first())

        repository.setDrawerLibrariesMode(DrawerLibrariesMode.EACH)
        assertEquals(DrawerLibrariesMode.EACH, repository.getDrawerLibrariesModeFlow().first())
    }

    @Test
    fun legacyLibrariesInDrawerWithoutIndividualKeyResolvesToScreen() = runTest {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tempFolder.newFolder().resolve("legacy_settings.preferences_pb")
            }
        dataStore.edit { prefs -> prefs[booleanPreferencesKey("libraries_in_drawer")] = true }
        val repository = PreferencesRepositoryImpl(dataStore)

        assertEquals(DrawerLibrariesMode.SCREEN, repository.getDrawerLibrariesModeFlow().first())
    }

    @Test
    fun resolveDrawerLibrariesModeCoversAllPreferenceCombinations() {
        assertEquals(DrawerLibrariesMode.EACH, resolveDrawerLibrariesMode(null, null))
        assertEquals(DrawerLibrariesMode.EACH, resolveDrawerLibrariesMode(null, true))
        assertEquals(DrawerLibrariesMode.SCREEN, resolveDrawerLibrariesMode(null, false))

        assertEquals(DrawerLibrariesMode.OFF, resolveDrawerLibrariesMode(false, null))
        assertEquals(DrawerLibrariesMode.OFF, resolveDrawerLibrariesMode(false, true))
        assertEquals(DrawerLibrariesMode.OFF, resolveDrawerLibrariesMode(false, false))

        assertEquals(DrawerLibrariesMode.SCREEN, resolveDrawerLibrariesMode(true, null))
        assertEquals(DrawerLibrariesMode.SCREEN, resolveDrawerLibrariesMode(true, false))
        assertEquals(DrawerLibrariesMode.EACH, resolveDrawerLibrariesMode(true, true))
    }
}

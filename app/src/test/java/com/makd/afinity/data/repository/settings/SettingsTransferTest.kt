package com.makd.afinity.data.repository.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.models.AfinitySettingsExport
import com.makd.afinity.data.models.HomePayload
import com.makd.afinity.data.models.SettingsSection
import com.makd.afinity.data.repository.home.HomeConfigTransfer
import com.makd.afinity.data.repository.home.ImportPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTransferTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun TestScope.createDataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFolder().resolve("settings.preferences_pb")
        }

    @Test
    fun `export and parse round-trip portable sections and omit unknown keys`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("theme_mode")] = "AMOLED"
            prefs[booleanPreferencesKey("auto_play")] = false
            prefs[intPreferencesKey("items_per_page")] = 80
            prefs[stringPreferencesKey("current_server_id")] = "non-portable"
        }

        val homePayload = HomePayload(hiddenRows = listOf("latest_tv"))
        val importPlan: ImportPlan = mockk(relaxed = true)
        val homeConfigTransfer: HomeConfigTransfer =
            mockk(relaxed = true) {
                coEvery { exportPayload() } returns homePayload
                coEvery { planFor(homePayload) } returns importPlan
            }
        val transfer = SettingsTransfer(dataStore, homeConfigTransfer)

        val raw = transfer.export()
        assertFalse(raw.contains("non-portable"))

        val parsed = transfer.parse(raw) as SettingsImportResult.Ready
        assertEquals(BuildConfig.VERSION_NAME, parsed.preview.appVersion)
        assertTrue(SettingsSection.HOME in parsed.preview.sections)
        assertTrue(SettingsSection.APPEARANCE in parsed.preview.sections)
        assertTrue(SettingsSection.PLAYBACK in parsed.preview.sections)
        assertTrue(SettingsSection.LIBRARY in parsed.preview.sections)
        assertEquals(1, parsed.preview.prefCounts[SettingsSection.APPEARANCE])
        assertEquals(1, parsed.preview.prefCounts[SettingsSection.PLAYBACK])
        assertEquals(1, parsed.preview.prefCounts[SettingsSection.LIBRARY])
        assertEquals(importPlan, parsed.preview.homePlan)
    }

    @Test
    fun `parse validates malformed JSON, format identifier, and newer schema versions`() = runTest {
        val dataStore = createDataStore()
        val homeConfigTransfer: HomeConfigTransfer = mockk(relaxed = true)
        val transfer = SettingsTransfer(dataStore, homeConfigTransfer)

        assertEquals(
            SettingsImportResult.Failed(SettingsImportFailure.UNREADABLE),
            transfer.parse("{not-json"),
        )

        val wrongFormat =
            Json.encodeToString(
                AfinitySettingsExport.serializer(),
                AfinitySettingsExport(
                    format = "other.app",
                    schemaVersion = AfinitySettingsExport.SCHEMA_VERSION,
                    exportedAt = "2026-01-01T00:00:00Z",
                    appVersion = "1.0.0",
                ),
            )
        assertEquals(
            SettingsImportResult.Failed(SettingsImportFailure.NOT_AFINITY_BACKUP),
            transfer.parse(wrongFormat),
        )

        val newerSchema =
            Json.encodeToString(
                AfinitySettingsExport.serializer(),
                AfinitySettingsExport(
                    format = AfinitySettingsExport.FORMAT,
                    schemaVersion = AfinitySettingsExport.SCHEMA_VERSION + 5,
                    exportedAt = "2026-01-01T00:00:00Z",
                    appVersion = "99.0.0",
                ),
            )
        assertEquals(
            SettingsImportResult.Failed(SettingsImportFailure.NEWER_SCHEMA),
            transfer.parse(newerSchema),
        )
    }

    @Test
    fun `apply restores only selected sections and preserves unselected preferences`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("auto_play")] = true
            prefs[stringPreferencesKey("theme_mode")] = "DARK"
        }

        val homePlan: ImportPlan = mockk(relaxed = true)
        val homeConfigTransfer: HomeConfigTransfer = mockk(relaxed = true)
        val transfer = SettingsTransfer(dataStore, homeConfigTransfer)

        val payload =
            AfinitySettingsExport(
                exportedAt = "2026-01-01T00:00:00Z",
                appVersion = "1.0.0",
                preferences =
                    mapOf(
                        SettingsSection.APPEARANCE.key to
                            buildJsonObject {
                                put("theme_mode", "LIGHT")
                                put("unknown_pref_ignored", true)
                            },
                        SettingsSection.PLAYBACK.key to buildJsonObject { put("auto_play", false) },
                    ),
            )
        val preview =
            SettingsImportPreview(
                appVersion = "1.0.0",
                sections = listOf(SettingsSection.APPEARANCE, SettingsSection.PLAYBACK),
                prefCounts = mapOf(SettingsSection.APPEARANCE to 1, SettingsSection.PLAYBACK to 1),
                homePlan = homePlan,
                payload = payload,
            )

        // Apply only APPEARANCE (do not apply HOME or PLAYBACK)
        transfer.apply(preview, selected = setOf(SettingsSection.APPEARANCE))

        val stored = dataStore.data.first()
        assertEquals("LIGHT", stored[stringPreferencesKey("theme_mode")])
        assertEquals(true, stored[booleanPreferencesKey("auto_play")])
        assertNull(stored[booleanPreferencesKey("unknown_pref_ignored")])
        coVerify(exactly = 0) { homeConfigTransfer.apply(any()) }

        // Applying HOME delegates to HomeConfigTransfer.
        transfer.apply(preview, selected = setOf(SettingsSection.HOME))
        coVerify(exactly = 1) { homeConfigTransfer.apply(homePlan) }
    }
}

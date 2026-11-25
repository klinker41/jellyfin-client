package com.makd.afinity.tvlauncher

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.di.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class TvPinnedAppsStore
@Inject
constructor(
    @param:AppPreferences private val dataStore: DataStore<Preferences>,
) {
    val pinnedPackages: Flow<List<String>> =
        dataStore.data
            .catch { e ->
                if (e is CancellationException) throw e
                Timber.w(e, "Failed to read pinned TV apps")
                emit(emptyPreferences())
            }
            .map { prefs -> parsePinnedPackages(prefs[PINNED_APPS_KEY]) }
            .distinctUntilChanged()

    suspend fun setPinned(packageName: String, pinned: Boolean) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return

        try {
            dataStore.edit { prefs ->
                val current = parsePinnedPackages(prefs[PINNED_APPS_KEY])
                if (pinned) {
                    if (trimmed !in current) {
                        prefs[PINNED_APPS_KEY] = (current + trimmed).joinToString(",")
                    }
                } else {
                    val next = current.filterNot { it == trimmed }
                    if (next.isEmpty()) {
                        prefs.remove(PINNED_APPS_KEY)
                    } else {
                        prefs[PINNED_APPS_KEY] = next.joinToString(",")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to update pinned TV app: $trimmed")
        }
    }

    private companion object {
        val PINNED_APPS_KEY = stringPreferencesKey("tv_launcher_pinned_apps")
    }
}

internal fun parsePinnedPackages(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

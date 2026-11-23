package com.makd.afinity.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.makd.afinity.di.AppPreferences
import com.makd.afinity.di.ApplicationScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class LibraryPlaybackSpeedStore
@Inject
constructor(
    @param:AppPreferences private val dataStore: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val speedCache = ConcurrentHashMap<String, Float>()

    suspend fun getSpeed(libraryKey: String): Float {
        speedCache[libraryKey]?.let {
            return it
        }
        return try {
            val stored = dataStore.data.first()[floatPreferencesKey(libraryKey)]
            val resolved = (stored ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED)
            speedCache.putIfAbsent(libraryKey, resolved) ?: resolved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to read playback speed for key=$libraryKey")
            DEFAULT_SPEED
        }
    }

    suspend fun setSpeed(libraryKey: String, speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        speedCache[libraryKey] = clamped
        persistSpeed(libraryKey, clamped)
    }

    fun saveSpeed(libraryKey: String, speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        speedCache[libraryKey] = clamped
        scope.launch { persistSpeed(libraryKey, clamped) }
    }

    private suspend fun persistSpeed(libraryKey: String, speed: Float) {
        val prefKey = floatPreferencesKey(libraryKey)
        try {
            dataStore.edit { prefs ->
                val latest = speedCache[libraryKey] ?: speed
                if (latest == DEFAULT_SPEED) {
                    prefs.remove(prefKey)
                } else {
                    prefs[prefKey] = latest
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist playback speed for key=$libraryKey")
        }
    }

    suspend fun getCachedJellyfinItemLibrary(itemId: UUID): UUID? {
        return try {
            val raw = dataStore.data.first()[itemLibraryPrefKey(itemId)] ?: return null
            runCatching { UUID.fromString(raw) }.getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to read cached library for item=$itemId")
            null
        }
    }

    suspend fun cacheJellyfinItemLibrary(itemId: UUID, libraryId: UUID) {
        try {
            dataStore.edit { prefs -> prefs[itemLibraryPrefKey(itemId)] = libraryId.toString() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to cache library for item=$itemId")
        }
    }

    private fun itemLibraryPrefKey(itemId: UUID) =
        stringPreferencesKey("jf_audiobook_library_$itemId")

    companion object {
        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f

        fun absLibraryKey(libraryId: String?, mediaType: String? = null): String {
            val trimmed = libraryId?.trim().orEmpty()
            return if (trimmed.isNotEmpty()) {
                "playback_speed_abs_$trimmed"
            } else {
                val typeKey = mediaType?.trim()?.ifEmpty { "unknown" } ?: "unknown"
                "playback_speed_abs_type_$typeKey"
            }
        }

        fun jellyfinAudiobookKey(libraryId: UUID?): String =
            if (libraryId != null) "playback_speed_jf_$libraryId"
            else "playback_speed_jf_audiobooks"

        fun jellyfinPodcastKey(libraryId: UUID?): String =
            if (libraryId != null) "playback_speed_jf_$libraryId" else "playback_speed_jf_podcasts"
    }
}

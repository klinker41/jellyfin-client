package com.makd.afinity.data.autodownload

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.makd.afinity.di.AppPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class AutoDownloadPreferences
@Inject
constructor(@param:AppPreferences private val dataStore: DataStore<Preferences>) {

    private fun enabledLibrariesKey(serverId: String, userId: UUID) =
        stringSetPreferencesKey("auto_download_libraries_${serverId}_$userId")

    private fun managedItemsKey(serverId: String, userId: UUID, libraryId: UUID) =
        stringSetPreferencesKey("auto_download_lib_items_${serverId}_${userId}_$libraryId")

    private fun cachedPodcastAlbumsKey(serverId: String, userId: UUID) =
        stringSetPreferencesKey("cached_podcast_album_ids_${serverId}_$userId")

    private fun cachedPodcastAlbumLibrariesKey(serverId: String, userId: UUID) =
        stringSetPreferencesKey("cached_podcast_album_libs_${serverId}_$userId")

    fun getEnabledLibraryIdsFlow(serverId: String, userId: UUID): Flow<Set<UUID>> =
        dataStore.data
            .map { prefs -> prefs[enabledLibrariesKey(serverId, userId)].toUuidSet() }
            .distinctUntilChanged()

    suspend fun getEnabledLibraryIds(serverId: String, userId: UUID): Set<UUID> =
        dataStore.data.first()[enabledLibrariesKey(serverId, userId)].toUuidSet()

    suspend fun setLibraryEnabled(
        serverId: String,
        userId: UUID,
        libraryId: UUID,
        enabled: Boolean,
    ) {
        val libsKey = enabledLibrariesKey(serverId, userId)
        val itemsKey = managedItemsKey(serverId, userId, libraryId)
        dataStore.edit { prefs ->
            val current = prefs[libsKey].orEmpty()
            val libStr = libraryId.toString()
            if (enabled) {
                prefs[libsKey] = current + libStr
            } else {
                val next = current - libStr
                if (next.isEmpty()) prefs.remove(libsKey) else prefs[libsKey] = next
                prefs.remove(itemsKey)
            }
        }
    }

    suspend fun getManagedItemIds(serverId: String, userId: UUID, libraryId: UUID): Set<UUID> =
        dataStore.data.first()[managedItemsKey(serverId, userId, libraryId)].toUuidSet()

    suspend fun setManagedItemIds(
        serverId: String,
        userId: UUID,
        libraryId: UUID,
        itemIds: Set<UUID>,
    ) {
        val key = managedItemsKey(serverId, userId, libraryId)
        dataStore.edit { prefs ->
            if (itemIds.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = itemIds.mapTo(HashSet()) { it.toString() }
            }
        }
    }

    suspend fun isItemManagedInEnabledLibraries(
        serverId: String,
        userId: UUID,
        itemId: UUID,
    ): Boolean {
        val prefs = dataStore.data.first()
        val enabledIds = prefs[enabledLibrariesKey(serverId, userId)].toUuidSet()
        if (enabledIds.isEmpty()) return false
        val target = itemId.toString()
        return enabledIds.any { libId ->
            target in prefs[managedItemsKey(serverId, userId, libId)].orEmpty()
        }
    }

    suspend fun removeManagedItem(serverId: String, userId: UUID, itemId: UUID) {
        val target = itemId.toString()
        dataStore.edit { prefs ->
            val enabledIds = prefs[enabledLibrariesKey(serverId, userId)].toUuidSet()
            enabledIds.forEach { libId ->
                val key = managedItemsKey(serverId, userId, libId)
                val current = prefs[key] ?: return@forEach
                if (target in current) {
                    val next = current - target
                    if (next.isEmpty()) prefs.remove(key) else prefs[key] = next
                }
            }
        }
    }

    suspend fun getCachedPodcastAlbumIds(serverId: String, userId: UUID): Set<UUID> =
        dataStore.data.first()[cachedPodcastAlbumsKey(serverId, userId)].toUuidSet()

    suspend fun setCachedPodcastAlbumIds(serverId: String, userId: UUID, albumIds: Set<UUID>) {
        val key = cachedPodcastAlbumsKey(serverId, userId)
        dataStore.edit { prefs ->
            if (albumIds.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = albumIds.mapTo(HashSet()) { it.toString() }
            }
        }
    }

    suspend fun getCachedPodcastAlbumLibraries(serverId: String, userId: UUID): Map<UUID, UUID> {
        val raw = dataStore.data.first()[cachedPodcastAlbumLibrariesKey(serverId, userId)]
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = HashMap<UUID, UUID>(raw.size)
        for (entry in raw) {
            val sep = entry.indexOf('|')
            if (sep <= 0 || sep >= entry.lastIndex) continue
            val albumId = runCatching { UUID.fromString(entry.substring(0, sep)) }.getOrNull()
            val libId = runCatching { UUID.fromString(entry.substring(sep + 1)) }.getOrNull()
            if (albumId != null && libId != null) {
                result[albumId] = libId
            }
        }
        return result
    }

    suspend fun setCachedPodcastAlbumLibraries(
        serverId: String,
        userId: UUID,
        albumLibraries: Map<UUID, UUID>,
    ) {
        val key = cachedPodcastAlbumLibrariesKey(serverId, userId)
        dataStore.edit { prefs ->
            if (albumLibraries.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] =
                    albumLibraries.entries.mapTo(HashSet(albumLibraries.size)) {
                        "${it.key}|${it.value}"
                    }
            }
        }
    }

    private fun Set<String>?.toUuidSet(): Set<UUID> {
        if (this.isNullOrEmpty()) return emptySet()
        return mapNotNullTo(HashSet()) { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
    }
}

package com.makd.afinity.data.repository

/** Atomic snapshot of encrypted Jellyfin session credentials loaded in a single DataStore read. */
data class SavedAuthSnapshot(
    val accessToken: String?,
    val userId: String?,
    val serverId: String?,
    val serverUrl: String?,
    val username: String?,
) {
    val hasBasicAuthData: Boolean
        get() = !accessToken.isNullOrBlank() && !serverUrl.isNullOrBlank()

    val isComplete: Boolean
        get() = hasBasicAuthData && !userId.isNullOrBlank() && !username.isNullOrBlank()
}

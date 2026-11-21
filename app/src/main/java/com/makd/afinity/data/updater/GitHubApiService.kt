package com.makd.afinity.data.updater

import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.di.GitHubClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@Singleton
class GitHubApiService
@Inject
constructor(@param:GitHubClient private val okHttpClient: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val GITHUB_TAG_URL =
            "https://api.github.com/repos/MakD/AFinity/releases/tags/"
        private const val HTTP_NOT_FOUND = 404
        private const val USER_AGENT = "AFinity-Android-App"
    }

    suspend fun getReleaseByTag(tag: String): Result<GitHubRelease?> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(GITHUB_TAG_URL + tag)
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                val response =
                    try {
                        okHttpClient.newCall(request).execute()
                    } catch (e: IOException) {
                        Timber.d(e, "Release $tag not reachable, trying the HTTP cache")
                        okHttpClient
                            .newCall(
                                request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build(),
                            )
                            .execute()
                    }

                response.use {
                    when {
                        it.code == HTTP_NOT_FOUND -> Result.success(null)
                        !it.isSuccessful -> {
                            Timber.e("GitHub API request failed: ${it.code}")
                            Result.failure(
                                Exception("Failed to fetch release $tag: HTTP ${it.code}"),
                            )
                        }
                        else ->
                            Result.success(json.decodeFromString<GitHubRelease>(it.body.string()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error fetching release $tag")
                Result.failure(e)
            }
        }
}

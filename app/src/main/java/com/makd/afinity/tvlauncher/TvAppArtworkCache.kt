package com.makd.afinity.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class TvAppArtwork(
    val image: ImageBitmap,
    val isBanner: Boolean,
)

object TvAppArtworkCache {
    private const val MAX_CACHE_ENTRIES = 48
    private const val BANNER_WIDTH_PX = 320
    private const val BANNER_HEIGHT_PX = 180
    private const val ICON_SIZE_PX = 128

    private val cache = LruCache<String, TvAppArtwork>(MAX_CACHE_ENTRIES)

    fun getCached(packageName: String): TvAppArtwork? =
        synchronized(cache) { cache.get(packageName) }

    fun evict(packageName: String) {
        synchronized(cache) { cache.remove(packageName) }
    }

    suspend fun load(context: Context, app: TvInstalledApp): TvAppArtwork? {
        getCached(app.packageName)?.let {
            return it
        }

        val loaded =
            withContext(Dispatchers.IO) { loadFromPackageManager(context.packageManager, app) }
                ?: return null

        synchronized(cache) { cache.put(app.packageName, loaded) }
        return loaded
    }

    private fun loadFromPackageManager(
        pm: PackageManager,
        app: TvInstalledApp,
    ): TvAppArtwork? {
        val component = ComponentName(app.packageName, app.activityName)
        val activityInfo = runCatching {
            pm.getActivityInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0L),
            )
        }
            .getOrNull()
        val appInfo =
            activityInfo?.applicationInfo
                ?: runCatching {
                    pm.getApplicationInfo(
                        app.packageName,
                        PackageManager.ApplicationInfoFlags.of(0L),
                    )
                }
                    .getOrNull()

        val banner =
            runCatching { activityInfo?.loadBanner(pm) }.getOrNull()
                ?: runCatching { appInfo?.loadBanner(pm) }.getOrNull()
        if (banner != null) {
            val bitmap = runCatching {
                banner.toBitmap(width = BANNER_WIDTH_PX, height = BANNER_HEIGHT_PX)
            }
                .getOrNull()
            if (bitmap != null) {
                return TvAppArtwork(image = bitmap.asImageBitmap(), isBanner = true)
            }
        }

        val icon =
            runCatching { activityInfo?.loadIcon(pm) }.getOrNull()
                ?: runCatching { appInfo?.loadIcon(pm) }.getOrNull()
                ?: return null
        val bitmap =
            runCatching {
                icon.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
            }
                .getOrNull() ?: return null
        return TvAppArtwork(image = bitmap.asImageBitmap(), isBanner = false)
    }
}

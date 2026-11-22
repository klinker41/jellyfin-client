package com.makd.afinity

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastPlaybackBridge
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.CacheMaintenance
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.sync.PendingJellyfinSync
import com.makd.afinity.di.ImageClient
import com.makd.afinity.ui.components.IMAGE_CROSSFADE_MILLIS
import com.makd.afinity.util.logging.CrashFileExporter
import com.makd.afinity.util.logging.RingBufferTree
import com.makd.afinity.util.logging.SdkLogBridge
import com.makd.afinity.util.perfTrace
import dagger.hilt.android.HiltAndroidApp
import io.github.oshai.kotlinlogging.Level
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber

@HiltAndroidApp
class AfinityApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>

    @Inject lateinit var preferencesRepository: dagger.Lazy<PreferencesRepository>

    @Inject lateinit var castManager: dagger.Lazy<CastManager>

    @Inject lateinit var castPlaybackBridge: dagger.Lazy<CastPlaybackBridge>

    @Inject @ImageClient lateinit var imageOkHttpClient: dagger.Lazy<OkHttpClient>

    @Inject lateinit var cacheMaintenance: dagger.Lazy<CacheMaintenance>

    @Inject lateinit var pendingJellyfinSync: dagger.Lazy<PendingJellyfinSync>

    @Inject lateinit var sessionManager: dagger.Lazy<SessionManager>

    @Inject lateinit var serverRepository: dagger.Lazy<ServerRepository>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var imageCachePrefsDeferred: Deferred<Pair<Boolean, Int>>
    var ringBufferTree: RingBufferTree? = null
        private set

    override fun onCreate() {
        super.onCreate()

        val tree = RingBufferTree()
        ringBufferTree = tree
        Timber.plant(tree)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Afinity Application started")
        }
        SdkLogBridge.install(if (BuildConfig.DEBUG) Level.DEBUG else Level.WARN)

        Thread.setDefaultUncaughtExceptionHandler(
            CrashFileExporter(this, ringBufferTree, Thread.getDefaultUncaughtExceptionHandler()),
        )

        perfTrace("App.onCreate") {
            imageCachePrefsDeferred =
                applicationScope.async(Dispatchers.IO) {
                    try {
                        val prefs = preferencesRepository.get()
                        prefs.getImageCacheEnabled() to prefs.getImageCacheSizeMb()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to read ImageLoader prefs, using defaults")
                        true to 512
                    }
                }

            applicationScope.launch { castManager.get().initialize(this@AfinityApplication) }
            applicationScope.launch { castPlaybackBridge.get().start() }

            applicationScope.launch(Dispatchers.IO) {
                runCatching { pendingJellyfinSync.get().initMonitoring() }
                    .onFailure { Timber.w(it, "Failed to start pending user data sync observer") }
            }

            // Cancel any periodic GitHub update checks scheduled by pre-Play Store builds.
            applicationScope.launch(Dispatchers.IO) {
                perfTrace("App.cancelUpdateWorker") {
                    WorkManager.getInstance(this@AfinityApplication)
                        .cancelUniqueWork("update_check_worker")
                }
            }

            applicationScope.launch(Dispatchers.IO) {
                runCatching { cacheMaintenance.get().pruneExpiredData() }
                    .onFailure { Timber.w(it, "Cache pruning failed") }
            }

            observeForegroundReconnect()
        }
    }

    private fun observeForegroundReconnect() {
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        val sm = sessionManager.get()
                        if (sm.currentSession.value == null) return
                        if (sm.isServerReachable.value) return
                        applicationScope.launch(Dispatchers.IO) {
                            Timber.d("App foregrounded while server unreachable, re-resolving")
                            runCatching { serverRepository.get().forceReconnect() }
                                .onFailure { Timber.w(it, "Foreground re-resolution failed") }
                        }
                    }
                },
            )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory.get()).build()

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val (isCacheEnabled, cacheSizeMb) =
            if (::imageCachePrefsDeferred.isInitialized) {
                @OptIn(ExperimentalCoroutinesApi::class)
                if (imageCachePrefsDeferred.isCompleted) {
                    imageCachePrefsDeferred.getCompleted()
                } else {
                    perfTrace("App.awaitImagePrefs") {
                        runBlocking { imageCachePrefsDeferred.await() }
                    }
                }
            } else {
                true to 512
            }
        Timber.d(
            "ImageLoader: creating singleton (cacheEnabled=$isCacheEnabled, cacheSizeMb=$cacheSizeMb)",
        )

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient.get() }))
                add(SvgDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCachePolicy(if (isCacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(cacheSizeMb * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(IMAGE_CROSSFADE_MILLIS)
            .build()
    }
}

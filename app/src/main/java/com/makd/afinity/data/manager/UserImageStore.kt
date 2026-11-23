package com.makd.afinity.data.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.makd.afinity.di.ImageClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

internal const val AVATAR_MAX_DIMENSION_PX = 256
internal const val AVATAR_JPEG_QUALITY = 85
internal const val MAX_CACHED_AVATAR_BYTES = 64L * 1024L

internal fun buildAvatarUrl(baseUrl: String, userId: UUID, tag: String): String =
    "${baseUrl.removeSuffix("/")}/Users/$userId/Images/Primary?tag=$tag&maxWidth=$AVATAR_MAX_DIMENSION_PX&quality=$AVATAR_JPEG_QUALITY"

@Singleton
class UserImageStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:ImageClient private val okHttpClient: OkHttpClient,
) {
    private val dir: File by lazy { File(context.filesDir, "user_avatars").apply { mkdirs() } }

    fun localAvatar(userId: UUID, tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val file = File(dir, "${userId}_$tag.jpg")
        return if (file.exists() && file.length() > 0) "file://${file.absolutePath}" else null
    }

    suspend fun cacheAvatar(userId: UUID, baseUrl: String, tag: String?) =
        withContext(Dispatchers.IO) {
            if (tag.isNullOrBlank() || baseUrl.isBlank()) return@withContext
            val target = File(dir, "${userId}_$tag.jpg")
            if (target.exists() && target.length() in 1L..MAX_CACHED_AVATAR_BYTES) {
                return@withContext
            }
            val tmp = File.createTempFile("avatar_${userId}_", ".tmp", dir)
            try {
                val url = buildAvatarUrl(baseUrl, userId, tag)
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    response.body.byteStream().use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() > MAX_CACHED_AVATAR_BYTES) {
                        downsampleAvatarIfNeeded(tmp)
                    }
                    if (tmp.length() > 0 && tmp.renameTo(target)) {
                        dir.listFiles { f ->
                                f.name.startsWith("${userId}_") && f.name != target.name
                            }
                            ?.forEach { it.delete() }
                    } else {
                        tmp.delete()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to cache avatar for $userId")
            } finally {
                tmp.delete()
            }
        }

    private fun downsampleAvatarIfNeeded(file: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return

            var sampleSize = 1
            while (
                width / (sampleSize * 2) >= AVATAR_MAX_DIMENSION_PX ||
                    height / (sampleSize * 2) >= AVATAR_MAX_DIMENSION_PX
            ) {
                sampleSize *= 2
            }
            val decoded =
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                ) ?: return
            val scale =
                AVATAR_MAX_DIMENSION_PX.toFloat() / max(decoded.width, decoded.height).toFloat()
            val finalBitmap =
                if (scale < 1f) {
                    val dstW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                    val dstH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
                    decoded.scale(dstW, dstH).also { if (it !== decoded) decoded.recycle() }
                } else {
                    decoded
                }
            try {
                FileOutputStream(file).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out)
                }
            } finally {
                finalBitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep downloaded bytes if Android BitmapFactory isn't available or decode fails
        }
    }
}

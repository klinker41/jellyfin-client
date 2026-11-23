package com.makd.afinity.data.manager

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserImageStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `buildAvatarUrl appends bounded width and jpeg quality parameters`() {
        val userId = UUID(0L, 42L)
        val url = buildAvatarUrl("https://jf.example.com/", userId, "tag-7")
        assertEquals(
            "https://jf.example.com/Users/$userId/Images/Primary?tag=tag-7&maxWidth=$AVATAR_MAX_DIMENSION_PX&quality=$AVATAR_JPEG_QUALITY",
            url,
        )
    }

    @Test
    fun `cacheAvatar skips network when cached file is compact and replaces oversized cached files`() =
        runTest {
            val filesDir = tempFolder.newFolder("files")
            val context: Context =
                mockk(relaxed = true) { every { this@mockk.filesDir } returns filesDir }
            val userId = UUID(0L, 99L)
            var requests = 0
            val payload = ByteArray(1024) { 1 }
            val client =
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requests++
                        assertTrue(
                            chain
                                .request()
                                .url
                                .toString()
                                .contains("maxWidth=$AVATAR_MAX_DIMENSION_PX"),
                        )
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(payload.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }
                    .build()

            val store = UserImageStore(context, client)
            assertNull(store.localAvatar(userId, "tag-1"))

            // First fetch populates compact cached avatar.
            store.cacheAvatar(userId, "https://jf.example.com", "tag-1")
            assertEquals(1, requests)
            assertEquals(
                "file://${File(filesDir, "user_avatars/${userId}_tag-1.jpg").absolutePath}",
                store.localAvatar(userId, "tag-1"),
            )

            // Subsequent call skips network because file is already within MAX_CACHED_AVATAR_BYTES.
            store.cacheAvatar(userId, "https://jf.example.com", "tag-1")
            assertEquals(1, requests)

            // Legacy oversized avatar file (> 64 KB) triggers re-fetch and shrinks down.
            val target = File(filesDir, "user_avatars/${userId}_tag-1.jpg")
            target.writeBytes(ByteArray((MAX_CACHED_AVATAR_BYTES + 4096).toInt()) { 2 })
            store.cacheAvatar(userId, "https://jf.example.com", "tag-1")
            assertEquals(2, requests)
            assertEquals(payload.size.toLong(), target.length())
            assertEquals(
                listOf("${userId}_tag-1.jpg"),
                File(filesDir, "user_avatars").listFiles()?.map { it.name },
            )
        }
}

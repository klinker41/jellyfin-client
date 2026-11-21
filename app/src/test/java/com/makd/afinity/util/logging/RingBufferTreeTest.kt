package com.makd.afinity.util.logging

import android.util.Log
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class RingBufferTreeTest {

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun filtersVerboseRedactsSecretsAndEvictsOldestBeyondCapacity() {
        val tree = RingBufferTree(maxLines = 3)
        Timber.plant(tree)

        Timber.tag("Net").v("should be ignored")
        Timber.tag("Net").d("GET https://jf.local?api_key=secret1")
        Timber.tag("Net").i("Info line")
        Timber.tag("Net").w("Warn line")
        Timber.tag("Net").e("Error line")

        val snap = tree.snapshot()
        assertEquals(3, snap.size)
        assertEquals(listOf("Info line", "Warn line", "Error line"), snap.map { it.message })
        assertEquals(listOf(1L, 2L, 3L), snap.map { it.sequence })
        assertEquals(listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR), snap.map { it.level })

        val treeFull = RingBufferTree(maxLines = 10)
        Timber.uprootAll()
        Timber.plant(treeFull)
        Timber.tag("Auth").d("Bearer superSecretTokenValue")
        assertEquals("Bearer [REDACTED]", treeFull.snapshot().single().message)
    }

    @Test
    fun truncatesStackTraceFormatsDumpClearsAndEmitsUpdates() = runTest {
        val tree = RingBufferTree(maxLines = 5)
        Timber.plant(tree)

        val deepException = RuntimeException("GET https://jf.local?token=secret123")
        deepException.stackTrace =
            Array(30) { idx -> StackTraceElement("Cls", "m$idx", "File.kt", idx + 1) }

        tree.updates.test {
            Timber.tag("Player").e(deepException, "Playback failed")
            awaitItem()

            val entry = tree.snapshot().single()
            assertEquals(Log.ERROR, entry.priority)
            val trace = entry.stackTrace
            assertNotNull(trace)
            assertEquals(20, trace!!.lines().size)
            assertFalse(trace.contains("secret123"))
            assertTrue(trace.contains("token=[REDACTED]"))
            assertTrue(tree.dump().contains("E/Player: Playback failed"))

            tree.clear()
            awaitItem()
            assertTrue(tree.snapshot().isEmpty())
            assertEquals("", tree.dump())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

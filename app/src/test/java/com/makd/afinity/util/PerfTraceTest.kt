package com.makd.afinity.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfTraceTest {
    private class RecordingSink(private val traceEnabled: Boolean) : PerfTraceSink {
        val events = mutableListOf<String>()

        override fun isEnabled(): Boolean = traceEnabled

        override fun beginSection(name: String) {
            events.add("begin:$name")
        }

        override fun endSection() {
            events.add("end")
        }

        override fun beginAsyncSection(name: String, cookie: Int) {
            events.add("beginAsync:$name:$cookie")
        }

        override fun endAsyncSection(name: String, cookie: Int) {
            events.add("endAsync:$name:$cookie")
        }
    }

    @Test
    fun runWithSyncTraceSkipsSinkWhenDisabled() {
        val sink = RecordingSink(false)

        val result =
            runWithSyncTrace(enabled = false, sectionName = "Test.sync", sink = sink) { 42 }

        assertEquals(42, result)
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun runWithSyncTraceTruncatesNameAndEndsSectionOnException() {
        val sink = RecordingSink(true)
        val longName = "A".repeat(200)

        var caught: IllegalStateException? = null
        try {
            runWithSyncTrace(enabled = true, sectionName = longName, sink = sink) {
                throw IllegalStateException("boom")
            }
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertEquals("boom", caught.message)
        assertEquals(listOf("begin:${"A".repeat(MAX_TRACE_SECTION_LENGTH)}", "end"), sink.events)
    }

    @Test
    fun runWithAsyncTraceSkipsSinkWhenDisabled() = runTest {
        val sink = RecordingSink(false)

        val result =
            runWithAsyncTrace(enabled = false, sectionName = "Test.async", sink = sink) { "ok" }

        assertEquals("ok", result)
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun runWithAsyncTraceUsesMatchingNonZeroCookieAndCleansUpOnException() = runTest {
        val sink = RecordingSink(true)
        val longName = "B".repeat(180)

        val value = runWithAsyncTrace(enabled = true, sectionName = "First", sink = sink) { "done" }
        assertEquals("done", value)

        var caught: IllegalArgumentException? = null
        try {
            runWithAsyncTrace(enabled = true, sectionName = longName, sink = sink) {
                throw IllegalArgumentException("bad")
            }
        } catch (e: IllegalArgumentException) {
            caught = e
        }

        assertEquals("bad", caught.message)
        assertEquals(4, sink.events.size)

        val firstCookie = sink.events[0].substringAfterLast(':').toInt()
        assertEquals("beginAsync:First:$firstCookie", sink.events[0])
        assertEquals("endAsync:First:$firstCookie", sink.events[1])

        val secondCookie = sink.events[2].substringAfterLast(':').toInt()
        assertFalse(firstCookie == secondCookie)
        assertEquals(
            "beginAsync:${"B".repeat(MAX_TRACE_SECTION_LENGTH)}:$secondCookie",
            sink.events[2],
        )
        assertEquals(
            "endAsync:${"B".repeat(MAX_TRACE_SECTION_LENGTH)}:$secondCookie",
            sink.events[3],
        )
    }

    @Test
    fun publicHelpersReturnBlockValues() = runTest {
        assertEquals(99, perfTrace("Unit.sync") { 99 })
        assertEquals("async-value", perfTraceAsync("Unit.async") { "async-value" })
    }
}

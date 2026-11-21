package com.makd.afinity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropTrackerTest {

    @Test
    fun selectNextBackdropReturnsNullForNullOrEmptyInput() {
        val tracker = BackdropTracker()
        assertNull(tracker.selectNextBackdrop(null, genreId = 1))
        assertNull(tracker.selectNextBackdrop(emptyList(), genreId = 1))
        assertEquals(0, tracker.getUsedCount())
    }

    @Test
    fun selectNextBackdropMemoizesPerGenreAndMediaKindAndPrefersUnused() {
        val tracker = BackdropTracker()
        val pool = listOf("b1", "b2", "b3")

        val actionMovie = tracker.selectNextBackdrop(pool, genreId = 28, isMovie = true)
        val comedyMovie = tracker.selectNextBackdrop(pool, genreId = 35, isMovie = true)
        val actionShow = tracker.selectNextBackdrop(pool, genreId = 28, isMovie = false)

        assertEquals("b1", actionMovie)
        assertEquals("b2", comedyMovie)
        assertEquals("b3", actionShow)
        assertEquals(3, tracker.getUsedCount())

        // Memoized assignment ignores new candidate lists for same key
        assertEquals(
            "b1",
            tracker.selectNextBackdrop(listOf("b99"), genreId = 28, isMovie = true),
        )
        assertEquals(
            "b3",
            tracker.selectNextBackdrop(listOf("b99"), genreId = 28, isMovie = false),
        )
        assertNotEquals(actionMovie, actionShow)
    }

    @Test
    fun selectNextBackdropWrapsDeterministicallyWhenExhaustedAndResetsCleanly() {
        val tracker = BackdropTracker()
        val pool = listOf("a", "b")

        assertEquals("a", tracker.selectNextBackdrop(pool, genreId = 1, isMovie = true))
        assertEquals("b", tracker.selectNextBackdrop(pool, genreId = 2, isMovie = true))

        // Exhausted: falls back to (genreId.absoluteValue + offset) % size
        val wrappedNegative = tracker.selectNextBackdrop(pool, genreId = -3, isMovie = true)
        assertEquals("b", wrappedNegative)

        val wrappedShow = tracker.selectNextBackdrop(pool, genreId = 1, isMovie = false)
        assertEquals("b", wrappedShow)

        tracker.reset()
        assertEquals(0, tracker.getUsedCount())
        assertEquals("a", tracker.selectNextBackdrop(pool, genreId = 2, isMovie = true))
    }
}

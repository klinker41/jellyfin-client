package com.makd.afinity.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CardRowStateTest {
    @Test
    fun nestedPrefetchCountMatchesWindowWidthBreakpoints() {
        assertEquals(3, nestedPrefetchCountForWidth(0))
        assertEquals(3, nestedPrefetchCountForWidth(390))
        assertEquals(3, nestedPrefetchCountForWidth(599))

        assertEquals(5, nestedPrefetchCountForWidth(600))
        assertEquals(5, nestedPrefetchCountForWidth(720))
        assertEquals(5, nestedPrefetchCountForWidth(839))

        assertEquals(6, nestedPrefetchCountForWidth(840))
        assertEquals(6, nestedPrefetchCountForWidth(900))
        assertEquals(6, nestedPrefetchCountForWidth(1280))
    }
}

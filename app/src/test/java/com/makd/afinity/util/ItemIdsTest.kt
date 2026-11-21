package com.makd.afinity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemIdsTest {

    @Test
    fun normalizeTrimsStripsDashesAndLowercases() {
        assertEquals(
            "550e8400e29b41d4a716446655440000",
            ItemIds.normalize("  550E8400-E29B-41D4-A716-446655440000  "),
        )
        assertEquals(
            "550e8400e29b41d4a716446655440000",
            ItemIds.normalize("550e8400e29b41d4a716446655440000"),
        )
    }

    @Test
    fun canonicalFormatsNormalized32CharIdWithHyphens() {
        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            ItemIds.canonical("550E8400E29B41D4A716446655440000"),
        )
        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            ItemIds.canonical(" 550e8400-e29b-41d4-a716-446655440000 "),
        )
    }

    @Test
    fun normalizeAndCanonicalReturnNullWhenResultLengthIsNot32() {
        assertNull(ItemIds.normalize(""))
        assertNull(ItemIds.normalize("550e8400"))
        assertNull(ItemIds.canonical("not-a-valid-id"))
        assertNull(ItemIds.canonical("550e8400e29b41d4a7164466554400001"))
    }
}

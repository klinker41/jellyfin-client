package com.makd.afinity.util

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UuidUtilsTest {

    @Test
    fun parseDashlessUuidParsesLowerAndUppercase32CharHex() {
        val expected = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(expected, parseDashlessUuid("550e8400e29b41d4a716446655440000"))
        assertEquals(expected, parseDashlessUuid("550E8400E29B41D4A716446655440000"))
    }

    @Test
    fun parseDashlessUuidRejectsInvalidLengthAndNonHex() {
        assertThrows(IllegalArgumentException::class.java) { parseDashlessUuid("too-short") }
        assertThrows(IllegalArgumentException::class.java) {
            parseDashlessUuid("550e8400-e29b-41d4-a716-446655440000")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseDashlessUuid("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")
        }
    }
}

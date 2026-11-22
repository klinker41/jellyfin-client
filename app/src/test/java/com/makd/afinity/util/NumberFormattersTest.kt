package com.makd.afinity.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormattersTest {
    @Test
    fun formatsDoubleValuesToOneDecimalPlace() {
        assertEquals("0.0", formatOneDecimal(0.0))
        assertEquals("7.0", formatOneDecimal(7.0))
        assertEquals("8.4", formatOneDecimal(8.36))
        assertEquals("8.3", formatOneDecimal(8.34))
        assertEquals("10.0", formatOneDecimal(9.96))
        assertEquals("-2.5", formatOneDecimal(-2.54))
        assertEquals("0.0", formatOneDecimal(-0.04))
    }

    @Test
    fun formatsFloatValuesToOneDecimalPlace() {
        assertEquals("0.0", formatOneDecimal(0.0f))
        assertEquals("9.2", formatOneDecimal(9.23f))
        assertEquals("10.0", formatOneDecimal(9.99f))
        assertEquals("-1.2", formatOneDecimal(-1.24f))
    }

    @Test
    fun handlesNonFiniteAndLargeFallbackValues() {
        assertEquals(String.format(Locale.US, "%.1f", Double.NaN), formatOneDecimal(Double.NaN))
        assertEquals(
            String.format(Locale.US, "%.1f", Double.POSITIVE_INFINITY),
            formatOneDecimal(Double.POSITIVE_INFINITY),
        )
        assertEquals(
            String.format(Locale.US, "%.1f", Float.NEGATIVE_INFINITY),
            formatOneDecimal(Float.NEGATIVE_INFINITY),
        )
        assertEquals(String.format(Locale.US, "%.1f", 1e16), formatOneDecimal(1e16))
    }
}

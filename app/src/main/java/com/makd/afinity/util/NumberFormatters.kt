package com.makd.afinity.util

import java.util.Locale
import kotlin.math.roundToLong

private const val MAX_FAST_FORMAT_ABS = 9e15

/**
 * Formats [value] to one decimal place without `Formatter` regex parsing overhead on hot card and
 * list scroll paths.
 */
fun formatOneDecimal(value: Double): String {
    if (!value.isFinite()) {
        return String.format(Locale.US, "%.1f", value)
    }
    val negative = value < 0.0
    val absValue = if (negative) -value else value
    if (absValue >= MAX_FAST_FORMAT_ABS) {
        return String.format(Locale.US, "%.1f", value)
    }
    val scaled = (absValue * 10.0).roundToLong()
    val whole = scaled / 10L
    val frac = (scaled % 10L).toInt()
    return buildString(8) {
        if (negative && scaled != 0L) append('-')
        append(whole)
        append('.')
        append(('0'.code + frac).toChar())
    }
}

/** Overload for [Float] ratings/scores without `Formatter` allocation overhead. */
fun formatOneDecimal(value: Float): String = formatOneDecimal(value.toDouble())

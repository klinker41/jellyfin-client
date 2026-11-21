package com.makd.afinity.util

import android.content.Context
import com.makd.afinity.R
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeAndDateSkeletonTest {

    @Test
    fun formatFileSizeFormatsAllMagnitudeThresholds() {
        val context = mockk<Context>()
        stubFormat(context, R.string.file_size_b_fmt, "%1\$d B")
        stubFormat(context, R.string.file_size_kb_fmt, "%1\$.1f KB")
        stubFormat(context, R.string.file_size_mb_fmt, "%1\$.1f MB")
        stubFormat(context, R.string.file_size_gb_fmt, "%1\$.2f GB")
        stubFormat(context, R.string.file_size_tb_fmt, "%1\$.2f TB")

        assertEquals("0 B", formatFileSize(context, 0L))
        assertEquals("1023 B", formatFileSize(context, 1023L))
        assertEquals("1.0 KB", formatFileSize(context, 1024L))
        assertEquals("1.5 MB", formatFileSize(context, (1.5 * 1024 * 1024).toLong()))
        assertEquals("2.50 GB", formatFileSize(context, (2.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals(
            "1.25 TB",
            formatFileSize(context, (1.25 * 1024 * 1024 * 1024 * 1024).toLong()),
        )
    }

    @Test
    fun dateSkeletonWithTimeAppends12Or24HourSuffix() {
        assertEquals("MMMdHm", DateSkeleton.withTime(DateSkeleton.MONTH_DAY, is24Hour = true))
        assertEquals("MMMdhm", DateSkeleton.withTime(DateSkeleton.MONTH_DAY, is24Hour = false))
        assertEquals(
            "EEEMMMdHm",
            DateSkeleton.withTime(DateSkeleton.WEEKDAY_MONTH_DAY, is24Hour = true),
        )
    }

    private fun stubFormat(context: Context, resId: Int, pattern: String) {
        every { context.getString(resId, *varargAll { true }) } answers
            {
                val varargs = invocation.args[1] as Array<*>
                String.format(Locale.US, pattern, *varargs)
            }
    }
}

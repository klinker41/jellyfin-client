package com.makd.afinity.util

import android.os.Trace
import androidx.annotation.VisibleForTesting
import com.makd.afinity.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

@PublishedApi internal const val MAX_TRACE_SECTION_LENGTH = 127

@PublishedApi internal val asyncTraceCookie = AtomicInteger(1)

@PublishedApi
internal interface PerfTraceSink {
    fun isEnabled(): Boolean

    fun beginSection(name: String)

    fun endSection()

    fun beginAsyncSection(name: String, cookie: Int)

    fun endAsyncSection(name: String, cookie: Int)
}

@PublishedApi
internal object AndroidPerfTraceSink : PerfTraceSink {
    override fun isEnabled(): Boolean = Trace.isEnabled()

    @Suppress("UnclosedTrace") override fun beginSection(name: String) = Trace.beginSection(name)

    override fun endSection() = Trace.endSection()

    override fun beginAsyncSection(name: String, cookie: Int) =
        Trace.beginAsyncSection(name, cookie)

    override fun endAsyncSection(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)
}

@PublishedApi
@VisibleForTesting
internal inline fun <T> runWithSyncTrace(
    enabled: Boolean,
    sectionName: String,
    sink: PerfTraceSink,
    block: () -> T,
): T {
    if (!enabled || !sink.isEnabled()) {
        return block()
    }
    sink.beginSection(sectionName.take(MAX_TRACE_SECTION_LENGTH))
    try {
        return block()
    } finally {
        sink.endSection()
    }
}

@PublishedApi
@VisibleForTesting
internal suspend inline fun <T> runWithAsyncTrace(
    enabled: Boolean,
    sectionName: String,
    sink: PerfTraceSink,
    crossinline block: suspend () -> T,
): T {
    if (!enabled || !sink.isEnabled()) {
        return block()
    }
    val truncated = sectionName.take(MAX_TRACE_SECTION_LENGTH)
    val cookie = asyncTraceCookie.getAndIncrement()
    sink.beginAsyncSection(truncated, cookie)
    try {
        return block()
    } finally {
        sink.endAsyncSection(truncated, cookie)
    }
}

/** Records a synchronous Perfetto trace section in nightly builds only. */
inline fun <T> perfTrace(sectionName: String, block: () -> T): T {
    if (!BuildConfig.IS_NIGHTLY) {
        return block()
    }
    return runWithSyncTrace(
        enabled = true,
        sectionName = sectionName,
        sink = AndroidPerfTraceSink,
        block = block,
    )
}

/** Records a coroutine-safe asynchronous Perfetto trace section in nightly builds only. */
suspend inline fun <T> perfTraceAsync(
    sectionName: String,
    crossinline block: suspend () -> T,
): T {
    if (!BuildConfig.IS_NIGHTLY) {
        return block()
    }
    return runWithAsyncTrace(
        enabled = true,
        sectionName = sectionName,
        sink = AndroidPerfTraceSink,
        block = block,
    )
}

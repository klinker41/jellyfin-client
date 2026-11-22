package com.makd.afinity.util

import androidx.activity.compose.ReportDrawnAfter
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.withFrameNanos

/** Reports app startup fully drawn once [isReady] returns true. */
@Composable
@NonRestartableComposable
fun ReportAppStartupReady(isReady: () -> Boolean) {
    ReportDrawnWhen(isReady)
}

/** Keeps startup draw reporting open until home content satisfies [isReady]. */
@Composable
@NonRestartableComposable
fun ReportHomeDrawn(isReady: () -> Boolean) {
    ReportDrawnWhen(isReady)
}

/** Defers draw reporting until [awaitContent] finishes and the next frame commits. */
@Composable
fun ReportDrawnAfterFirstFrame(awaitContent: suspend () -> Unit = {}) {
    ReportDrawnAfter {
        awaitContent()
        withFrameNanos {}
    }
}

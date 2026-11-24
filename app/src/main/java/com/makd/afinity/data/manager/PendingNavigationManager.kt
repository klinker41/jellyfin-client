package com.makd.afinity.data.manager

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PendingNavigationManager @Inject constructor() {

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    private val _homeRequested =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val homeRequested: SharedFlow<Unit> = _homeRequested.asSharedFlow()

    fun navigateTo(route: String) {
        _pendingRoute.value = route
    }

    fun consume() {
        _pendingRoute.value = null
    }

    fun requestHome() {
        _homeRequested.tryEmit(Unit)
    }
}

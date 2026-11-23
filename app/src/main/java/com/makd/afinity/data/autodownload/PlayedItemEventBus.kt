package com.makd.afinity.data.autodownload

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class PlayedItemEventBus @Inject constructor() {

    private val _playedItems = MutableSharedFlow<UUID>(extraBufferCapacity = 64)
    val playedItems: SharedFlow<UUID> = _playedItems.asSharedFlow()

    fun emitPlayed(itemId: UUID) {
        _playedItems.tryEmit(itemId)
    }
}

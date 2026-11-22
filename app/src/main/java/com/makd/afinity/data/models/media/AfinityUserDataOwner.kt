package com.makd.afinity.data.models.media

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
interface AfinityUserDataOwner {
    val id: UUID
    val played: Boolean
    val favorite: Boolean
    val liked: Boolean
    val playbackPositionTicks: Long
}

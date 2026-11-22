package com.makd.afinity.data.models.media

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.UUID
import org.jellyfin.sdk.model.api.PersonKind

@Immutable data class AfinityPersonImage(val uri: Uri?, val blurHash: String?)

@Immutable
data class AfinityPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: AfinityPersonImage,
)

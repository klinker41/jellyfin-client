package com.makd.afinity.data.models.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat

data class SubtitlePreferences(
    val textColor: Int = Color.WHITE,
    val textSize: Float = 1.0f,
    val bold: Boolean = false,
    val outlineStyle: SubtitleOutlineStyle = SubtitleOutlineStyle.NONE,
    val outlineColor: Int = Color.BLACK,
    val windowColor: Int = Color.TRANSPARENT,
) {
    companion object {
        val DEFAULT = SubtitlePreferences()

        fun hexToColor(hex: String): Int {
            return try {
                hex.toColorInt()
            } catch (e: IllegalArgumentException) {
                Color.WHITE
            }
        }
    }

    fun toExoPlayerFractionalSize(): Float {
        return 0.0625f * textSize
    }
}

enum class SubtitleOutlineStyle(val displayName: String) {
    NONE("None"),
    OUTLINE("Outline"),
    DROP_SHADOW("Drop Shadow"),
    RAISED("Raised"),
    DEPRESSED("Depressed");

    companion object {
        fun fromString(value: String): SubtitleOutlineStyle {
            return entries.find { it.name == value } ?: NONE
        }
    }

    @OptIn(UnstableApi::class)
    fun toExoPlayerEdgeType(): Int {
        return when (this) {
            NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
            DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
        }
    }
}

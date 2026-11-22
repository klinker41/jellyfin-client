package com.makd.afinity.data.models

import androidx.compose.runtime.Immutable

enum class GenreType {
    MOVIE,
    SHOW,
}

@Immutable data class GenreItem(val name: String, val type: GenreType)

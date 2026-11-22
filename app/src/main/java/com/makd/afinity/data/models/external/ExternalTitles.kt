package com.makd.afinity.data.models.external

import androidx.compose.runtime.Immutable
import com.makd.afinity.data.models.jellyseerr.SearchResultItem

enum class ExternalTitlesSource {
    SEERR,
    TMDB,
}

@Immutable
data class ExternalTitles(val source: ExternalTitlesSource, val items: List<SearchResultItem>)

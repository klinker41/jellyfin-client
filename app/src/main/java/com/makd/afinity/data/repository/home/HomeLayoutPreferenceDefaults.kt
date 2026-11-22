package com.makd.afinity.data.repository.home

import com.makd.afinity.data.database.entities.HomeLayoutPreferenceEntity
import com.makd.afinity.data.models.HomeRow

internal fun resolveHiddenHomeRows(rows: List<HomeLayoutPreferenceEntity>): Set<HomeRow> {
    val visibilityByRow =
        rows
            .mapNotNull { entity ->
                HomeRow.fromKey(entity.sectionKey)
                    ?.takeUnless { it.mandatory }
                    ?.let { it to entity.visible }
            }
            .toMap()
    return HomeRow.configurable
        .filterNot { row -> visibilityByRow[row] ?: row.defaultVisible }
        .toSet()
}

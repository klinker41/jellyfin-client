package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.models.common.DrawerLibrariesMode

internal const val DEFAULT_NAVIGATION_DRAWER_ENABLED = true

internal val DEFAULT_DRAWER_LIBRARIES_MODE = DrawerLibrariesMode.EACH

internal fun resolveDrawerLibrariesMode(
    librariesInDrawer: Boolean?,
    individualLibrariesInDrawer: Boolean?,
): DrawerLibrariesMode =
    when {
        librariesInDrawer == false -> DrawerLibrariesMode.OFF
        librariesInDrawer == null ->
            if (individualLibrariesInDrawer == false) {
                DrawerLibrariesMode.SCREEN
            } else {
                DEFAULT_DRAWER_LIBRARIES_MODE
            }
        individualLibrariesInDrawer == true -> DrawerLibrariesMode.EACH
        else -> DrawerLibrariesMode.SCREEN
    }

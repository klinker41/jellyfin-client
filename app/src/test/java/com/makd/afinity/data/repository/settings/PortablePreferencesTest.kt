package com.makd.afinity.data.repository.settings

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortablePreferencesTest {

    @Test
    fun updaterPreferencesAreNotExportable() {
        assertNull(PortablePreferences.find("update_check_frequency"))
        assertNull(PortablePreferences.find("last_update_check"))
    }

    @Test
    fun existingPrivacyPreferencesRemainExportable() {
        assertNotNull(PortablePreferences.find("crash_reporting"))
        assertNotNull(PortablePreferences.find("usage_analytics"))
    }

    @Test
    fun removedMpvPreferencesAreNotExportable() {
        assertNull(PortablePreferences.find("use_exo_player"))
        assertNull(PortablePreferences.find("mpv_hw_dec"))
        assertNull(PortablePreferences.find("subtitle_italic"))
        assertNull(PortablePreferences.find("subtitle_background_color"))
    }
}

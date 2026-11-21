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
}

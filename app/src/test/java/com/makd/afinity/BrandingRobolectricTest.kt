package com.makd.afinity

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.core.AppConstants
import com.makd.afinity.ui.admin.devices.OWN_APP_NAME
import com.makd.afinity.util.logging.LogExporter
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrandingRobolectricTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `app constants and own device name use Jellyfin branding`() {
        assertEquals("Jellyfin", AppConstants.APP_NAME)
        assertEquals("Jellyfin", OWN_APP_NAME)
        assertTrue(LogExporter.buildHeader(context).startsWith("Jellyfin "))
    }

    @Test
    fun `key branding string resources use Jellyfin`() {
        assertEquals("Jellyfin", context.getString(R.string.app_name))
        assertEquals("About Jellyfin", context.getString(R.string.pref_about_app))
        assertEquals("Jellyfin player", context.getString(R.string.widget_audio_name))
        assertEquals("Saved to Pictures/Jellyfin", context.getString(R.string.photo_saved))
    }

    @Test
    fun `no English string resource contains afinity branding`() {
        val offending =
            R.string::class
                .java
                .fields
                .filter { field ->
                    Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType
                }
                .mapNotNull { field ->
                    val resId = field.getInt(null)
                    val value = context.getString(resId)
                    if (value.contains("afinity", ignoreCase = true)) {
                        "${field.name}=$value"
                    } else {
                        null
                    }
                }
        assertEquals(emptyList<String>(), offending)
    }
}

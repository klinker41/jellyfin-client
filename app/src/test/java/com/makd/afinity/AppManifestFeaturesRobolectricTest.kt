package com.makd.afinity

import android.app.Application
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppManifestFeaturesRobolectricTest {

    @Test
    fun `wifi hardware feature is declared as optional in manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_CONFIGURATIONS.toLong()),
            )
        val wifiFeature =
            packageInfo.reqFeatures?.firstOrNull { it.name == PackageManager.FEATURE_WIFI }

        assertNotNull(wifiFeature)
        assertEquals(0, wifiFeature!!.flags and FeatureInfo.FLAG_REQUIRED)
    }
}

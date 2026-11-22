package com.makd.afinity.data.repository.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.makd.afinity.data.models.HeroContentMode
import com.makd.afinity.data.models.HeroContentSource
import com.makd.afinity.data.models.HeroIndicator
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.player.AssRenderMode
import com.makd.afinity.data.models.player.SkipMode
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryImplTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun createRepo(name: String): Pair<PreferencesRepositoryImpl, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tempFolder.root, "$name.preferences_pb") },
            )
        return PreferencesRepositoryImpl(dataStore) to scope
    }

    @Test
    fun `defaults match documented baseline before any writes`() =
        runTest(timeout = 30.seconds) {
            val (repo, scope) = createRepo("defaults")
            try {
                assertNull(repo.getCurrentServerId())
                assertNull(repo.getCurrentUserId())
                assertTrue(repo.getRememberLogin())
                assertEquals(SortBy.NAME, repo.getDefaultSortBy())
                assertFalse(repo.getSortDescending())
                assertTrue(repo.getAutoPlay())
                assertFalse(repo.getNeverTranscode())
                assertFalse(repo.getCombineLibrarySections())
                assertTrue(repo.getHomeSortByDateAdded())
                assertFalse(repo.getOfflineMode())
                assertEquals(SkipMode.BUTTON, repo.getSegmentSkipMode(AfinitySegmentType.INTRO))
                assertEquals(SkipMode.BUTTON, repo.getSegmentSkipMode(AfinitySegmentType.OUTRO))
                assertEquals(HeroContentSource(), repo.getHeroContentSource("srv-1", "usr-1"))
                assertNull(repo.getNavFavoritesCount("srv-1", "usr-1"))
                assertNull(repo.getNavHasLiveTv("srv-1", "usr-1"))
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `playback, UI, download, and per-account scoped keys round-trip across reads and flows`() =
        runTest(timeout = 30.seconds) {
            val (repo, scope) = createRepo("roundtrip")
            try {
                repo.setCurrentServerId("srv-42")
                repo.setCurrentUserId("usr-99")
                repo.setRememberLogin(true)
                assertEquals("srv-42", repo.getCurrentServerId())
                assertEquals("usr-99", repo.getCurrentUserId())
                assertTrue(repo.getRememberLogin())

                repo.setAutoPlay(false)
                repo.setNeverTranscode(true)
                repo.setDefaultSortBy(SortBy.DATE_ADDED)
                repo.setSortDescending(true)
                repo.setPreferredAudioLanguage("jpn")
                repo.setPreferredSubtitleLanguage("eng")
                repo.setPreferSdhSubtitles(true)
                repo.setAssRenderMode(AssRenderMode.OVERLAY)
                repo.setSegmentSkipMode(AfinitySegmentType.INTRO, SkipMode.AUTO_SKIP)
                repo.setAppFont("JetBrains Mono")
                repo.setImageCacheEnabled(false)
                repo.setImageCacheSizeMb(512)

                assertFalse(repo.getAutoPlay())
                assertTrue(repo.getNeverTranscode())
                assertEquals(SortBy.DATE_ADDED, repo.getDefaultSortBy())
                assertTrue(repo.getSortDescending())
                assertEquals("jpn", repo.getPreferredAudioLanguage())
                assertEquals("eng", repo.getPreferredSubtitleLanguageFlow().first())
                assertTrue(repo.getPreferSdhSubtitles())
                assertEquals(AssRenderMode.OVERLAY, repo.getAssRenderMode())
                assertEquals(
                    SkipMode.AUTO_SKIP,
                    repo.getSegmentSkipModeFlow(AfinitySegmentType.INTRO).first(),
                )
                assertEquals("JetBrains Mono", repo.getAppFontFlow().first())
                assertFalse(repo.getImageCacheEnabled())
                assertEquals(512, repo.getImageCacheSizeMb())

                val updatedSubs =
                    repo
                        .getSubtitlePreferences()
                        .copy(
                            textSize = 1.4f,
                            bold = true,
                            outlineStyle = SubtitleOutlineStyle.OUTLINE,
                        )
                repo.setSubtitlePreferences(updatedSubs)
                assertEquals(1.4f, repo.getSubtitlePreferencesFlow().first().textSize, 0.001f)
                assertTrue(repo.getSubtitlePreferences().bold)
                assertEquals(
                    SubtitleOutlineStyle.OUTLINE,
                    repo.getSubtitlePreferences().outlineStyle,
                )

                repo.setDownloadOverWifiOnly(false)
                repo.setMaxDownloads(5)
                assertFalse(repo.getDownloadOverWifiOnly())
                assertEquals(5, repo.getMaxDownloads())

                // Scoped per serverId & userId keys remain isolated between accounts
                val heroA =
                    HeroContentSource(
                        mode = HeroContentMode.RECENTLY_ADDED,
                        slideSeconds = 12,
                        slideCount = 8,
                        indicator = HeroIndicator.COUNTER,
                    )
                repo.setHeroContentSource("srv-1", "usr-1", heroA)
                assertEquals(heroA, repo.getHeroContentSourceFlow("srv-1", "usr-1").first())
                assertEquals(HeroContentSource(), repo.getHeroContentSource("srv-1", "usr-2"))

                repo.setWikidataEnabled("srv-1", "usr-1", true)
                repo.setNavFavoritesCount("srv-1", "usr-1", 17)
                repo.setNavHasLiveTv("srv-1", "usr-1", true)
                assertTrue(repo.getWikidataEnabledFlow("srv-1", "usr-1").first())
                assertFalse(repo.getWikidataEnabled("srv-2", "usr-1"))
                assertEquals(17, repo.getNavFavoritesCount("srv-1", "usr-1"))
                assertEquals(true, repo.getNavHasLiveTv("srv-1", "usr-1"))

                repo.clearUserPreferences()
                assertNull(repo.getCurrentUserId())
                repo.clearAllPreferences()
                assertNull(repo.getCurrentServerId())
                assertTrue(repo.getAutoPlay())
            } finally {
                scope.cancel()
            }
        }
}

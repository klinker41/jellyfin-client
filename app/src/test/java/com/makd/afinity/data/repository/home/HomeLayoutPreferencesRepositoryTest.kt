package com.makd.afinity.data.repository.home

import com.makd.afinity.data.database.dao.HomeLayoutPreferenceDao
import com.makd.afinity.data.database.entities.HomeLayoutPreferenceEntity
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.ui.home.HomeUiState
import com.makd.afinity.ui.settings.home.CustomSectionsUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPreferencesRepositoryTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val sessionKey = "server1_$userId"
    private val activeSession =
        Session(serverId = "server1", userId = userId, serverUrl = "https://jellyfin.local")

    private fun entity(
        sectionKey: String,
        visible: Boolean = true,
        position: Int = 0,
        maxCount: Int? = null,
    ) =
        HomeLayoutPreferenceEntity(
            sessionKey = sessionKey,
            sectionKey = sectionKey,
            position = position,
            visible = visible,
            maxCount = maxCount,
        )

    @Test
    fun resolveHiddenHomeRowsAppliesDefaultsOverridesAndIgnoresMandatoryOrOtherKeys() {
        assertEquals(HomeRow.defaultHidden, resolveHiddenHomeRows(emptyList()))

        val withEnabledDefaultOffRow =
            resolveHiddenHomeRows(
                listOf(
                    entity(HomeRow.LATEST_BOOKS.key, visible = true),
                    entity(HomeRow.NEXT_UP.key, visible = false),
                    entity(HomeRow.LIBRARIES.key, visible = false),
                    entity(HomeRow.CONTINUE_WATCHING.key, visible = false),
                    entity(DiscoverySection.DENSITY_KEY, visible = false),
                    entity(DiscoverySection.GENRES.key, visible = false),
                    entity("unknown_row_key", visible = false),
                ),
            )

        assertFalse(HomeRow.LATEST_BOOKS in withEnabledDefaultOffRow)
        assertTrue(HomeRow.NEXT_UP in withEnabledDefaultOffRow)
        assertFalse(HomeRow.LIBRARIES in withEnabledDefaultOffRow)
        assertFalse(HomeRow.CONTINUE_WATCHING in withEnabledDefaultOffRow)
        assertEquals(
            (HomeRow.defaultHidden - HomeRow.LATEST_BOOKS) + HomeRow.NEXT_UP,
            withEnabledDefaultOffRow,
        )
    }

    @Test
    fun missingOrBlankSessionReturnsDefaultHiddenRowsAndDiscoveryOff() = runTest {
        val sessionFlow = MutableStateFlow<Session?>(null)
        val dao: HomeLayoutPreferenceDao = mockk(relaxed = true)
        val sessionManager: SessionManager = mockk { every { currentSession } returns sessionFlow }
        val repository = HomeLayoutPreferencesRepository(dao, sessionManager)

        assertEquals(HomeRow.defaultHidden, repository.hiddenRows.first())
        assertEquals(HomeRow.defaultHidden, repository.getHiddenRows())
        assertEquals(
            DiscoveryConfig(density = DiscoveryDensity.OFF),
            repository.discoveryConfig.first(),
        )
        assertEquals(
            DiscoveryConfig(density = DiscoveryDensity.OFF),
            repository.getDiscoveryConfig(),
        )

        sessionFlow.value = Session(serverId = "", userId = userId, serverUrl = "https://local")
        assertEquals(HomeRow.defaultHidden, repository.hiddenRows.first())
        assertEquals(HomeRow.defaultHidden, repository.getHiddenRows())
        assertEquals(DiscoveryDensity.OFF, repository.discoveryConfig.first().density)
        assertEquals(DiscoveryDensity.OFF, repository.getDiscoveryConfig().density)
    }

    @Test
    fun activeSessionWithEmptyDbReturnsDefaultHiddenRowsAndDiscoveryOff() = runTest {
        val dbFlow = MutableStateFlow<List<HomeLayoutPreferenceEntity>>(emptyList())
        val dao: HomeLayoutPreferenceDao =
            mockk(relaxed = true) {
                every { observeForSession(sessionKey) } returns dbFlow
                coEvery { getForSession(sessionKey) } returns emptyList()
            }
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns MutableStateFlow(activeSession)
        }
        val repository = HomeLayoutPreferencesRepository(dao, sessionManager)

        assertEquals(HomeRow.defaultHidden, repository.hiddenRows.first())
        assertEquals(HomeRow.defaultHidden, repository.getHiddenRows())
        assertEquals(DiscoveryDensity.OFF, repository.discoveryConfig.first().density)
        assertEquals(DiscoveryDensity.OFF, repository.getDiscoveryConfig().density)
    }

    @Test
    fun activeSessionReflectsSavedRowAndDensityOverrides() = runTest {
        val savedRows =
            listOf(
                entity(HomeRow.LATEST_BOOKS.key, visible = true),
                entity(HomeRow.NEXT_UP.key, visible = false),
                entity(
                    DiscoverySection.DENSITY_KEY,
                    visible = true,
                    position = DiscoveryDensity.BALANCED.ordinal,
                ),
            )
        val dbFlow = MutableStateFlow(savedRows)
        val dao: HomeLayoutPreferenceDao =
            mockk(relaxed = true) {
                every { observeForSession(sessionKey) } returns dbFlow
                coEvery { getForSession(sessionKey) } returns savedRows
            }
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns MutableStateFlow(activeSession)
        }
        val repository = HomeLayoutPreferencesRepository(dao, sessionManager)

        val expectedHidden = (HomeRow.defaultHidden - HomeRow.LATEST_BOOKS) + HomeRow.NEXT_UP
        assertEquals(expectedHidden, repository.hiddenRows.first())
        assertEquals(expectedHidden, repository.getHiddenRows())
        assertEquals(DiscoveryDensity.BALANCED, repository.discoveryConfig.first().density)
        assertEquals(DiscoveryDensity.BALANCED, repository.getDiscoveryConfig().density)
    }

    @Test
    fun daoExceptionsFallBackToDefaultHiddenRowsAndDiscoveryOff() = runTest {
        val dao: HomeLayoutPreferenceDao =
            mockk(relaxed = true) {
                coEvery { getForSession(sessionKey) } throws IllegalStateException("db failure")
            }
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns MutableStateFlow(activeSession)
        }
        val repository = HomeLayoutPreferencesRepository(dao, sessionManager)

        assertEquals(HomeRow.defaultHidden, repository.getHiddenRows())
        assertEquals(
            DiscoveryConfig(density = DiscoveryDensity.OFF),
            repository.getDiscoveryConfig(),
        )
    }

    @Test
    fun setRowVisibleAndResetRowsDelegateToDao() = runTest {
        val dao: HomeLayoutPreferenceDao = mockk(relaxed = true)
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns MutableStateFlow(activeSession)
        }
        val repository = HomeLayoutPreferencesRepository(dao, sessionManager)

        repository.setRowVisible(HomeRow.LIBRARIES, false)
        coVerify(exactly = 0) { dao.upsert(any()) }

        repository.setRowVisible(HomeRow.LATEST_BOOKS, true)
        coVerify(exactly = 1) {
            dao.upsert(
                HomeLayoutPreferenceEntity(
                    sessionKey = sessionKey,
                    sectionKey = HomeRow.LATEST_BOOKS.key,
                    position = HomeRow.LATEST_BOOKS.ordinal,
                    visible = true,
                    maxCount = null,
                ),
            )
        }

        repository.resetRows()
        coVerify(exactly = 1) { dao.deleteForSession(sessionKey) }
    }

    @Test
    fun initialUiStatesUseExpectedHomeLayoutDefaults() {
        val customSectionsState = CustomSectionsUiState()
        assertEquals(HomeRow.defaultHidden, customSectionsState.hiddenRows)
        assertEquals(DiscoveryDensity.OFF, customSectionsState.discovery.density)

        val homeState = HomeUiState()
        assertEquals(HomeRow.defaultHidden, homeState.hiddenRows)
    }
}

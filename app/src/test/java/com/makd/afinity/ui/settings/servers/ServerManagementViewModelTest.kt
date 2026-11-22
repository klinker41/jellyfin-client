package com.makd.afinity.ui.settings.servers

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.data.database.entities.JellyseerrAddressEntity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.AddressCheck
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.Locality
import com.makd.afinity.util.NetworkLocality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import javax.inject.Provider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ServerManagementViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val userId = testUuid(1)
        val server1 = Server("srv-1", "Home", "10.10.3", "https://home.example.com")
        val server2 = Server("srv-2", "Cabin", "10.10.3", "http://192.168.1.20:8096")
        val sessionFlow =
            MutableStateFlow<Session?>(
                Session(server1.id, userId, server1.address, server = server1, isAdmin = true),
            )
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }
        val offlineModeManager: OfflineModeManager =
            mockk(relaxed = true) {
                every { isOffline } returns MutableStateFlow(false)
                every { connectionType } returns MutableStateFlow(ConnectionType.REMOTE)
                coEvery { isCurrentlyOffline() } returns false
            }
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                coEvery { getAllServers() } returns listOf(server1, server2)
                coEvery { getUsersForServer(server1.id) } returns
                    listOf(User(userId, "Alex", server1.id, isAdmin = true))
                coEvery { getUsersForServer(server2.id) } returns
                    listOf(User(testUuid(2), "Alex", server2.id))
                coEvery { getServerAddresses(server1.id) } returns
                    listOf(ServerAddress(testUuid(10), server1.id, server1.address))
                coEvery { getServerAddresses(server2.id) } returns
                    listOf(ServerAddress(testUuid(11), server2.id, server2.address))
            }
        val jellyseerrDao: JellyseerrDao =
            mockk(relaxed = true) {
                coEvery { getConfig(any(), any()) } returns null
                coEvery { getAddresses(any(), any()) } returns emptyList()
            }
        val audiobookshelfDao: AudiobookshelfDao =
            mockk(relaxed = true) {
                coEvery { getConfig(any(), any()) } returns null
                coEvery { getAddresses(any(), any()) } returns emptyList()
            }
        val securePreferencesRepository: SecurePreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getTmdbApiKey(any(), any()) } returns null
                coEvery { getMdbListApiKey(any(), any()) } returns null
                coEvery { getOmdbApiKey(any(), any()) } returns null
                coEvery { getLastFmApiKey(any(), any()) } returns null
                coEvery { getCachedJellyseerrServerUrl() } returns null
                coEvery { getCachedAudiobookshelfServerUrl() } returns null
            }
        val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
        val authRepository: AuthRepository =
            mockk(relaxed = true) {
                coEvery { revokeToken(any(), any()) } returns Result.success(Unit)
            }
        val serverRepository: ServerRepository =
            mockk(relaxed = true) {
                every { currentBaseUrl } returns MutableStateFlow(server1.address)
            }
        val jellyseerrRepository: JellyseerrRepository = mockk(relaxed = true)
        val audiobookshelfRepository: AudiobookshelfRepository = mockk(relaxed = true)
        val jellyfinRepository: JellyfinRepository =
            mockk(relaxed = true) {
                every { getServerStorageFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
                every { getLibraryStatsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
            }
        val networkLocality: NetworkLocality =
            mockk(relaxed = true) {
                coEvery { resolve("https://home.example.com") } returns Locality.PUBLIC
                coEvery { resolve("http://192.168.1.20:8096") } returns Locality.ON_LINK
            }
        private val store = ViewModelStore()

        fun createViewModel(): ServerManagementViewModel {
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ServerManagementViewModel(
                            context = context,
                            sessionManager = sessionManager,
                            offlineModeManager = offlineModeManager,
                            databaseRepository = databaseRepository,
                            jellyseerrDao = jellyseerrDao,
                            audiobookshelfDao = audiobookshelfDao,
                            securePreferencesRepository = securePreferencesRepository,
                            preferencesRepository = preferencesRepository,
                            authRepository = authRepository,
                            serverRepository = serverRepository,
                            jellyseerrRepositoryProvider = Provider { jellyseerrRepository },
                            audiobookshelfRepositoryProvider =
                                Provider { audiobookshelfRepository },
                            jellyfinRepositoryProvider = Provider { jellyfinRepository },
                            networkLocality = networkLocality,
                        )
                            as T
                }
            return ViewModelProvider(store, factory)[ServerManagementViewModel::class.java]
        }

        fun dispose() {
            store.clear()
        }
    }

    @Test
    fun `loadServers classifies locality, blocks deleting active server, and deletes inactive server`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val vm = h.createViewModel()
                advanceUntilIdle()

                assertFalse(vm.state.value.isLoading)
                assertEquals(2, vm.state.value.servers.size)
                val active = vm.state.value.servers.first { it.isActiveServer }
                assertEquals(h.server1.id, active.server.id)
                assertEquals(ConnectionType.REMOTE, active.addressType)
                val inactive = vm.state.value.servers.first { !it.isActiveServer }
                assertEquals(ConnectionType.LOCAL, inactive.addressType)

                // Deleting active server sets localized error without calling DB delete
                vm.deleteServer(h.server1.id)
                advanceUntilIdle()
                assertEquals(
                    h.context.getString(R.string.error_delete_active_server),
                    vm.state.value.error,
                )
                coVerify(exactly = 0) { h.databaseRepository.deleteServer(h.server1.id) }

                vm.clearError()
                assertNull(vm.state.value.error)

                // Deleting inactive server clears DB & tokens
                vm.showDeleteConfirmation(inactive)
                assertEquals(inactive, vm.state.value.serverToDelete)
                vm.hideDeleteConfirmation()
                assertNull(vm.state.value.serverToDelete)

                vm.deleteServer(h.server2.id)
                advanceUntilIdle()
                coVerify { h.databaseRepository.deleteServer(h.server2.id) }
                coVerify { h.databaseRepository.clearServerData(h.server2.id) }
                coVerify { h.securePreferencesRepository.clearAllServerTokens(h.server2.id) }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `addJellyseerrAddress validates, prompts on indeterminate, confirms, and deletes address`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val vm = h.createViewModel()
                advanceUntilIdle()

                coEvery { h.jellyseerrRepository.verifyServer(any()) } returns
                    com.makd.afinity.data.models.jellyseerr.PublicSettings(
                        applicationTitle = "Seerr",
                    )
                coEvery { h.jellyseerrDao.getAddressByUrl(any(), any(), any()) } returns null
                coEvery { h.jellyseerrRepository.verifyAddressIdentity(any()) } returns
                    AddressCheck.INDETERMINATE

                vm.addJellyseerrAddress(h.server1.id, "seerr.example.com")
                advanceUntilIdle()

                val pending = vm.state.value.pendingAddress
                assertNotNull(pending)
                assertEquals(PendingAddressKind.JELLYSEERR, pending?.kind)

                vm.confirmPendingAddress()
                advanceUntilIdle()
                assertNull(vm.state.value.pendingAddress)
                coVerify { h.jellyseerrDao.insertAddress(any<JellyseerrAddressEntity>()) }

                val addrId = UUID.randomUUID()
                vm.deleteJellyseerrAddress(addrId)
                advanceUntilIdle()
                coVerify { h.jellyseerrDao.deleteAddress(addrId) }

                val absId = UUID.randomUUID()
                vm.deleteAudiobookshelfAddress(absId)
                advanceUntilIdle()
                coVerify { h.audiobookshelfDao.deleteAddress(absId) }
            } finally {
                h.dispose()
            }
        }
}

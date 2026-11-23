package com.makd.afinity.data.manager

import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.ServerUserToken
import com.makd.afinity.data.repository.server.AddressResolutionResult
import com.makd.afinity.data.repository.server.ServerAddressResolver
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClientFactory
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.LibraryOptionsResultDto
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @Before
    fun setUp() {
        mockkConstructor(UserApi::class, LibraryApi::class)
    }

    @After
    fun tearDown() {
        unmockkConstructor(UserApi::class, LibraryApi::class)
    }

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val userId = testUuid(1)
        val server = Server(id = "srv-1", name = "Home", address = "https://jf.example.com")
        val user = User(id = userId, name = "Alex", serverId = "srv-1", isAdmin = false)

        val apiClient: ApiClient =
            mockk(relaxed = true) {
                every { baseUrl } returns "https://jf.example.com"
                every { accessToken } returns "tok-1"
                every { webSocket } returns mockk(relaxed = true)
            }
        val probeClient: ApiClient = mockk(relaxed = true)
        val backgroundApiClient: ApiClient = mockk(relaxed = true)

        val serverRepository: ServerRepository = mockk(relaxed = true)
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                coEvery { getUser(userId) } returns user
                coEvery { getServer("srv-1") } returns server
            }
        val securePrefsRepository: SecurePreferencesRepository = mockk(relaxed = true)
        val jellyseerrRepository: JellyseerrRepository = mockk(relaxed = true)
        val audiobookshelfRepository: AudiobookshelfRepository = mockk(relaxed = true)
        val serverAddressResolver: ServerAddressResolver = mockk(relaxed = true)
        val mockContext: android.content.Context = mockk(relaxed = true)
        val deviceInfo = DeviceInfo(id = "dev-1", name = "Pixel")
        val jellyfin =
            Jellyfin(
                JellyfinOptions(
                    context = mockContext,
                    clientInfo = ClientInfo(name = "AFinity", version = "1.0"),
                    deviceInfo = deviceInfo,
                    apiClientFactory =
                        object : ApiClientFactory {
                            override fun create(
                                baseUrl: String?,
                                accessToken: String?,
                                clientInfo: ClientInfo,
                                deviceInfo: DeviceInfo,
                                httpClientOptions: HttpClientOptions,
                                socketConnectionFactory: SocketConnectionFactory,
                            ): ApiClient =
                                if (accessToken != null) backgroundApiClient else apiClient
                        },
                    socketConnectionFactory = mockk(relaxed = true),
                    minimumServerVersion = ServerVersion(10, 10, 0),
                ),
            )
        val proberJellyfin =
            Jellyfin(
                JellyfinOptions(
                    context = mockContext,
                    clientInfo = ClientInfo(name = "AFinity", version = "1.0"),
                    deviceInfo = deviceInfo,
                    apiClientFactory =
                        object : ApiClientFactory {
                            override fun create(
                                baseUrl: String?,
                                accessToken: String?,
                                clientInfo: ClientInfo,
                                deviceInfo: DeviceInfo,
                                httpClientOptions: HttpClientOptions,
                                socketConnectionFactory: SocketConnectionFactory,
                            ): ApiClient = probeClient
                        },
                    socketConnectionFactory = mockk(relaxed = true),
                    minimumServerVersion = ServerVersion(10, 10, 0),
                ),
            )

        val manager =
            SessionManager(
                serverRepository = serverRepository,
                databaseRepository = databaseRepository,
                securePrefsRepository = securePrefsRepository,
                jellyseerrRepository = jellyseerrRepository,
                audiobookshelfRepository = audiobookshelfRepository,
                serverAddressResolver = serverAddressResolver,
                jellyfin = jellyfin,
                deviceInfo = deviceInfo,
                proberJellyfin = proberJellyfin,
                sessionScope = scope,
            )
    }

    @Test
    fun `startSession pre-validated activates session, refreshes policy, and links services`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(backgroundScope)
            val policy: UserPolicy =
                mockk(relaxed = true) {
                    every { isAdministrator } returns true
                    every { enableLiveTvAccess } returns true
                    every { enableLiveTvManagement } returns true
                    every { enableContentDownloading } returns true
                    every { enableCollectionManagement } returns true
                    every { enableSubtitleManagement } returns true
                    every { enableContentDeletion } returns true
                }
            val userDto: UserDto =
                mockk(relaxed = true) {
                    every { id } returns h.userId
                    every { name } returns "Alex"
                    every { primaryImageTag } returns "tag-9"
                    every { this@mockk.policy } returns policy
                }
            val optionsDto: LibraryOptionsResultDto =
                mockk(relaxed = true) {
                    every { subtitleFetchers } returns listOf(mockk(relaxed = true))
                }
            coEvery { anyConstructed<UserApi>().getCurrentUser() } returns
                Response(userDto, 200, emptyMap())
            coEvery { anyConstructed<LibraryApi>().getLibraryOptionsInfo() } returns
                Response(optionsDto, 200, emptyMap())

            val result =
                h.manager.startSession(
                    serverUrl = "https://jf.example.com",
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "tok-1",
                    urlPreValidated = true,
                )

            assertTrue(result.isSuccess)
            assertTrue(h.manager.isServerReachable.value)
            val session = h.manager.currentSession.value!!
            assertEquals("srv-1", session.serverId)
            assertEquals(true, session.isAdmin)
            assertEquals(true, session.hasSubtitleProvider)
            assertSame(h.apiClient, h.manager.getCurrentApiClient())
            assertSame(h.backgroundApiClient, h.manager.getBackgroundApiClient())
            coVerify { h.jellyseerrRepository.setActiveJellyfinSession("srv-1", h.userId) }
            coVerify { h.audiobookshelfRepository.setActiveJellyfinSession("srv-1", h.userId) }
            coVerify { h.serverRepository.setBaseUrl("https://jf.example.com") }
        }

    @Test
    fun `startSession surfaces 401 unauthorized token failure and unreachable reasons`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(backgroundScope)

            coEvery { anyConstructed<UserApi>().getCurrentUser() } throws
                InvalidStatusException(401, null)
            coEvery { h.serverAddressResolver.resolveAddress("srv-1", any()) } coAnswers
                {
                    val validator = secondArg<suspend (String) -> Boolean>()
                    assertFalse(validator("https://jf.example.com"))
                    AddressResolutionResult.AllFailed("srv-1", listOf("https://jf.example.com"))
                }

            val rejected =
                h.manager.startSession(
                    serverUrl = "https://jf.example.com",
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "expired",
                )
            assertTrue(rejected.isFailure)
            assertTrue(rejected.exceptionOrNull() is InvalidStatusException)

            // When permission missing without 401, starts offline and sets permission flag.
            coEvery { h.serverAddressResolver.resolveAddress("srv-1", any()) } returns
                AddressResolutionResult.PermissionRequired("srv-1", listOf("http://192.168.1.2"))

            val offline =
                h.manager.startSession(
                    serverUrl = "http://192.168.1.2",
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "tok-1",
                )
            assertTrue(offline.isSuccess)
            assertFalse(h.manager.isServerReachable.value)
            assertEquals(
                UnreachableReason.PERMISSION_REQUIRED,
                h.manager.unreachableReason.value,
            )
            assertTrue(h.manager.needsLocalNetworkPermission.value)
            h.manager.clearLocalNetworkPermissionPrompt()
            assertFalse(h.manager.needsLocalNetworkPermission.value)
        }

    @Test
    fun `session updates, background restore, switchUser, and logout behave consistently`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(backgroundScope)
            val userDto: UserDto =
                mockk(relaxed = true) {
                    every { id } returns h.userId
                    every { name } returns "Alex"
                    every { policy } returns null
                }
            coEvery { anyConstructed<UserApi>().getCurrentUser() } returns
                Response(userDto, 200, emptyMap())
            coEvery { h.serverAddressResolver.resolveAddress("srv-1", any()) } returns
                AddressResolutionResult.Success("https://alt.example.com", "srv-1")

            h.manager.startSession("https://jf.example.com", "srv-1", h.userId, "tok-1", true)

            h.manager.updateSessionUrl("https://new.example.com")
            assertEquals("https://new.example.com", h.manager.currentSession.value?.serverUrl)
            verify {
                h.apiClient.update(
                    baseUrl = "https://new.example.com",
                    accessToken = any(),
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }

            h.manager.updateCurrentUserImageTag(h.userId, "new-tag")
            assertEquals("new-tag", h.manager.currentSession.value?.user?.primaryImageTag)

            val renamedServer = h.server.copy(name = "Studio")
            h.manager.updateSessionServer(renamedServer)
            assertEquals("Studio", h.manager.currentSession.value?.server?.name)

            // Switching to user without stored token fails immediately.
            val missingUser = UUID(0L, 99L)
            coEvery { h.securePrefsRepository.getServerUserToken("srv-1", missingUser) } returns
                null
            assertTrue(h.manager.switchUser("srv-1", missingUser).isFailure)

            // Valid switch succeeds and returns isSwitchingSession to false.
            val user2 = UUID(0L, 2L)
            coEvery { h.securePrefsRepository.getServerUserToken("srv-1", user2) } returns "tok-2"
            coEvery { h.securePrefsRepository.getAllServerUserTokens() } returns
                listOf(ServerUserToken("srv-1", user2, "tok-2", "Sam", "https://alt.example.com"))
            assertTrue(h.manager.switchUser("srv-1", user2).isSuccess)
            assertFalse(h.manager.isSwitchingSession.value)
            assertEquals(user2, h.manager.currentSession.value?.userId)

            // Detached client & restore client
            assertNotNull(h.manager.getDetachedApiClient("srv-1", user2))
            h.manager.logout()
            assertNull(h.manager.currentSession.value)
            assertNull(h.manager.getCurrentApiClient())

            // Restoring from background rebuilds client for server with token.
            assertNotNull(h.manager.getOrRestoreApiClient("srv-1"))
        }

    @Test
    fun `optimisticStartup activates session immediately while address validation resolves in background`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(backgroundScope)
            val gate = CompletableDeferred<Unit>()
            val policy: UserPolicy =
                mockk(relaxed = true) { every { isAdministrator } returns true }
            val userDto: UserDto =
                mockk(relaxed = true) {
                    every { id } returns h.userId
                    every { name } returns "Alex"
                    every { this@mockk.policy } returns policy
                }
            coEvery { anyConstructed<UserApi>().getCurrentUser() } returns
                Response(userDto, 200, emptyMap())
            coEvery { h.serverAddressResolver.resolveAddress("srv-1", any()) } coAnswers
                {
                    gate.await()
                    AddressResolutionResult.Success("https://alt.example.com", "srv-1")
                }

            val result =
                h.manager.startSession(
                    serverUrl = "https://jf.example.com",
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "tok-1",
                    optimisticStartup = true,
                )

            assertTrue(result.isSuccess)
            assertEquals("https://jf.example.com", h.manager.currentSession.value?.serverUrl)
            assertTrue(h.manager.isServerReachable.value)
            verify {
                h.securePrefsRepository.setCachedJellyfinAuth("tok-1", "https://jf.example.com")
            }
            coVerify(exactly = 0) { anyConstructed<UserApi>().getCurrentUser() }

            gate.complete(Unit)
            yield()

            assertEquals("https://alt.example.com", h.manager.currentSession.value?.serverUrl)
            assertEquals(true, h.manager.currentSession.value?.isAdmin)
            coVerify { h.serverRepository.setBaseUrl("https://alt.example.com") }
            coVerify {
                h.securePrefsRepository.saveAuthenticationData(
                    accessToken = "tok-1",
                    userId = h.userId,
                    serverId = "srv-1",
                    serverUrl = "https://alt.example.com",
                    username = any(),
                )
                h.securePrefsRepository.saveServerUserToken(
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "tok-1",
                    username = any(),
                    serverUrl = "https://alt.example.com",
                )
            }
        }

    @Test
    fun `optimisticStartup logs out on background 401 and marks offline on unreachable error`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(backgroundScope)
            val gate401 = CompletableDeferred<Unit>()
            coEvery { anyConstructed<UserApi>().getCurrentUser() } throws
                InvalidStatusException(401, null)
            coEvery { h.serverAddressResolver.resolveAddress("srv-1", any()) } coAnswers
                {
                    val validator = secondArg<suspend (String) -> Boolean>()
                    gate401.await()
                    assertFalse(validator("https://jf.example.com"))
                    AddressResolutionResult.AllFailed("srv-1", listOf("https://jf.example.com"))
                }

            val res1 =
                h.manager.startSession(
                    serverUrl = "https://jf.example.com",
                    serverId = "srv-1",
                    userId = h.userId,
                    accessToken = "expired",
                    optimisticStartup = true,
                )
            assertTrue(res1.isSuccess)
            assertNotNull(h.manager.currentSession.value)

            gate401.complete(Unit)
            yield()
            assertNull(h.manager.currentSession.value)
            coVerify { h.securePrefsRepository.clearAuthenticationData() }

            // Unreachable without 401 keeps session active in offline mode.
            val h2 = Harness(backgroundScope)
            coEvery { h2.serverAddressResolver.resolveAddress("srv-1", any()) } returns
                AddressResolutionResult.AllFailed("srv-1", listOf("https://jf.example.com"))

            val res2 =
                h2.manager.startSession(
                    serverUrl = "https://jf.example.com",
                    serverId = "srv-1",
                    userId = h2.userId,
                    accessToken = "tok-1",
                    optimisticStartup = true,
                )
            yield()
            assertTrue(res2.isSuccess)
            assertNotNull(h2.manager.currentSession.value)
            assertFalse(h2.manager.isServerReachable.value)
            assertEquals(
                UnreachableReason.ALL_ADDRESSES_FAILED,
                h2.manager.unreachableReason.value,
            )

            // Calling logout() while optimistic validation is suspended cancels validation cleanly.
            val h3 = Harness(backgroundScope)
            val suspendGate = CompletableDeferred<Unit>()
            var validationCancelled = false
            coEvery { h3.serverAddressResolver.resolveAddress("srv-1", any()) } coAnswers
                {
                    try {
                        suspendGate.await()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        validationCancelled = true
                        throw e
                    }
                    AddressResolutionResult.Success("https://should-not-apply.example", "srv-1")
                }

            h3.manager.startSession(
                serverUrl = "https://jf.example.com",
                serverId = "srv-1",
                userId = h3.userId,
                accessToken = "tok-1",
                optimisticStartup = true,
            )
            h3.manager.logout()
            assertTrue(validationCancelled)
            assertNull(h3.manager.currentSession.value)
        }
}

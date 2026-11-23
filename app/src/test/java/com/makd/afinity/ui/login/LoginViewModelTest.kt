package com.makd.afinity.ui.login

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.makd.afinity.data.manager.ForgetUserUseCase
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.AddressResolutionResult
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerAddressResolver
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.LocalNetworkPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        savedServers: List<Server> = emptyList(),
        passedServerUrl: String? = null,
        baseUrl: String = "",
    ) {
        val isAuthenticatedFlow = MutableStateFlow(false)
        val server = Server(id = "srv-1", name = "Home", address = "https://jf.example.com")
        val usersByServer = mutableMapOf<String, List<User>>()
        val tokens = mutableMapOf<Pair<String, java.util.UUID>, String>()

        val context: Context =
            mockk(relaxed = true) {
                every { getString(any()) } returns "Error message"
                every { getString(any(), any()) } returns "Formatted error"
                every { getString(any(), any(), any()) } returns "Formatted connect error"
            }
        val savedStateHandle =
            if (passedServerUrl != null) SavedStateHandle(mapOf("serverUrl" to passedServerUrl))
            else SavedStateHandle()
        val jellyfinRepository: JellyfinRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns baseUrl
                coEvery { refreshServerInfo() } returns Unit
                coEvery { getPublicUsers(any()) } returns emptyList()
                coEvery { discoverServersFlow() } returns emptyFlow()
            }
        val authRepository: AuthRepository =
            mockk(relaxed = true) { every { isAuthenticated } returns isAuthenticatedFlow }
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                coEvery { getAllServers() } returns savedServers
                coEvery { getServer(any()) } returns server
                coEvery { getServerAddressByUrl(any(), any()) } returns null
                coEvery { getUsersForServer(any()) } answers
                    {
                        usersByServer[firstArg()] ?: emptyList()
                    }
            }
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                coEvery { startSession(any(), any(), any(), any(), any(), any()) } returns
                    Result.success(Unit)
            }
        val securePreferencesRepository: SecurePreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getServerUserToken(any(), any()) } answers
                    {
                        tokens[firstArg<String>() to secondArg()]
                    }
            }
        val serverAddressResolver: ServerAddressResolver = mockk {
            coEvery { resolveAddress(any()) } returns
                AddressResolutionResult.Success("https://jf.example.com", "srv-1")
        }
        val forgetUser: ForgetUserUseCase = mockk {
            coEvery { this@mockk.invoke(any(), any()) } returns Result.success(Unit)
        }
        val localNetworkPermission: LocalNetworkPermission =
            mockk(relaxed = true) { every { isSatisfied() } returns true }

        fun createViewModel() =
            LoginViewModel(
                context = context,
                savedStateHandle = savedStateHandle,
                jellyfinRepository = jellyfinRepository,
                authRepository = authRepository,
                databaseRepository = databaseRepository,
                sessionManager = sessionManager,
                securePreferencesRepository = securePreferencesRepository,
                serverAddressResolver = serverAddressResolver,
                forgetUser = forgetUser,
                localNetworkPermission = localNetworkPermission,
            )
    }

    @Test
    fun `setServerUrl validates whitespace and clears stale connection`() = runTest {
        val harness = Harness()
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        viewModel.setServerUrl("https://bad url.com")
        assertEquals("Invalid URL format", viewModel.uiState.value.serverUrlError)

        viewModel.setServerUrl("  https://jf.example.com  ")
        assertEquals("https://jf.example.com", viewModel.serverUrl.value)
        assertNull(viewModel.uiState.value.serverUrlError)
    }

    @Test
    fun `connectToServer handles Success, Error, and LocalNetworkPermissionRequired`() = runTest {
        val harness = Harness()
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        coEvery { harness.jellyfinRepository.validateServer("https://jf.example.com") } returns
            JellyfinServerRepository.ServerConnectionResult.Success(
                server = harness.server,
                serverAddress = "https://jf.example.com",
                version = "10.10.3",
                isQuickConnectEnabled = true,
            )

        viewModel.setServerUrl("https://jf.example.com")
        viewModel.connectToServer()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isConnectedToServer)
        assertFalse(viewModel.uiState.value.isConnecting)
        coVerify { harness.databaseRepository.insertServer(harness.server) }
        coVerify { harness.databaseRepository.insertServerAddress(any()) }

        coEvery { harness.jellyfinRepository.validateServer("https://bad.local") } returns
            JellyfinServerRepository.ServerConnectionResult.Error("Unreachable")
        viewModel.setServerUrl("https://bad.local")
        viewModel.connectToServer()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isConnectedToServer)
        assertEquals("Formatted error", viewModel.uiState.value.error)

        coEvery { harness.jellyfinRepository.validateServer("http://192.168.1.2") } returns
            JellyfinServerRepository.ServerConnectionResult.LocalNetworkPermissionRequired
        viewModel.setServerUrl("http://192.168.1.2")
        viewModel.connectToServer()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsLocalNetworkPermission)
    }

    @Test
    fun `selectServer loads saved users filtered by token and forgetSavedUser refreshes`() =
        runTest {
            val userWithToken = User(id = testUuid(1), name = "Alex", serverId = "srv-1")
            val userWithoutToken = User(id = testUuid(2), name = "Guest", serverId = "srv-1")
            val harness =
                Harness(
                    savedServers =
                        listOf(
                            Server(id = "srv-1", name = "Home", address = "https://jf.example.com"),
                        ),
                )
            harness.usersByServer["srv-1"] = listOf(userWithToken, userWithoutToken)
            harness.tokens["srv-1" to userWithToken.id] = "tok-1"

            val viewModel = harness.createViewModel()
            advanceUntilIdle()
            assertEquals(1, viewModel.savedServers.value.size)

            viewModel.selectServer(harness.server)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isConnectedToServer)
            assertEquals(listOf(userWithToken), viewModel.savedUsers.value)

            harness.usersByServer["srv-1"] = emptyList()
            viewModel.forgetSavedUser(userWithToken)
            advanceUntilIdle()

            coVerify { harness.forgetUser("srv-1", userWithToken.id) }
            assertTrue(viewModel.savedUsers.value.isEmpty())
        }

    @Test
    fun `login validates username and starts session on success`() = runTest {
        val harness = Harness()
        val userId = testUuid(7)
        val userDto = mockk<UserDto>(relaxed = true) { every { id } returns userId }
        val authResult =
            mockk<AuthenticationResult>(relaxed = true) {
                every { accessToken } returns "secret-tok"
                every { user } returns userDto
                every { serverId } returns "srv-1"
            }
        coEvery { harness.jellyfinRepository.validateServer(any()) } returns
            JellyfinServerRepository.ServerConnectionResult.Success(
                server = harness.server,
                serverAddress = "https://jf.example.com",
                version = "10.10.3",
                isQuickConnectEnabled = true,
            )
        coEvery {
            harness.authRepository.authenticateByName("https://jf.example.com", "Alex", "pw")
        } returns AuthRepository.AuthResult.Success(authResult)

        val viewModel = harness.createViewModel()
        viewModel.setServerUrl("https://jf.example.com")
        viewModel.connectToServer()
        advanceUntilIdle()

        viewModel.login()
        assertEquals("Error message", viewModel.uiState.value.error)

        viewModel.loginState.test {
            awaitItem()
            viewModel.updateUsername("Alex")
            viewModel.updatePassword("pw")
            viewModel.login()
            advanceUntilIdle()
            val latest = expectMostRecentItem()
            assertTrue(latest.isConnectedToServer)
            assertTrue(latest.uiState.isLoggedIn)
            assertEquals("", latest.uiState.password)
        }
    }

    @Test
    fun `loginWithSavedUser starts session when token exists and errors when missing`() = runTest {
        val user = User(id = testUuid(3), name = "Sam", serverId = "srv-1")
        val harness = Harness()
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        viewModel.loginWithSavedUser(user)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals("Error message", viewModel.uiState.value.error)

        harness.tokens["srv-1" to user.id] = "tok-saved"
        viewModel.loginWithSavedUser(user)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        coVerify {
            harness.sessionManager.startSession(
                "https://jf.example.com",
                "srv-1",
                user.id,
                "tok-saved",
                any(),
                any(),
            )
        }
    }
}

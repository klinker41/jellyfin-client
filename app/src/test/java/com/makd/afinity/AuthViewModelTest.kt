package com.makd.afinity

import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        initialAuthenticated: Boolean = false,
        initialSwitching: Boolean = false,
        private val restoreResult: suspend () -> AuthRepository.RestoreResult = {
            AuthRepository.RestoreResult.Failed
        },
    ) {
        val isAuthenticatedFlow = MutableStateFlow(initialAuthenticated)
        val isSwitchingSessionFlow = MutableStateFlow(initialSwitching)

        val authRepository: AuthRepository = mockk {
            every { isAuthenticated } returns isAuthenticatedFlow
            every { isSwitchingSession } returns isSwitchingSessionFlow
            coEvery { ensureAuthenticationRestored() } coAnswers { restoreResult() }
        }

        fun createViewModel() = AuthViewModel(authRepository)
    }

    @Test
    fun `restore success sets Authenticated state`() = runTest {
        val gate = CompletableDeferred<AuthRepository.RestoreResult>()
        val harness = Harness(initialAuthenticated = false, restoreResult = { gate.await() })
        val viewModel = harness.createViewModel()
        assertSame(AuthenticationState.Loading, viewModel.authenticationState.value)

        gate.complete(AuthRepository.RestoreResult.Success)
        advanceUntilIdle()

        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)
    }

    @Test
    fun `restore degraded sets Authenticated state`() = runTest {
        val gate = CompletableDeferred<AuthRepository.RestoreResult>()
        val harness = Harness(initialAuthenticated = false, restoreResult = { gate.await() })
        val viewModel = harness.createViewModel()
        assertSame(AuthenticationState.Loading, viewModel.authenticationState.value)

        gate.complete(AuthRepository.RestoreResult.Degraded(IllegalStateException("offline")))
        advanceUntilIdle()

        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)
    }

    @Test
    fun `restore failed sets NotAuthenticated state`() = runTest {
        val harness =
            Harness(
                initialAuthenticated = false,
                restoreResult = { AuthRepository.RestoreResult.Failed },
            )
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        assertSame(AuthenticationState.NotAuthenticated, viewModel.authenticationState.value)
    }

    @Test
    fun `restore exception sets NotAuthenticated state`() = runTest {
        val harness =
            Harness(
                restoreResult = { throw RuntimeException("network error") },
            )
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        assertSame(AuthenticationState.NotAuthenticated, viewModel.authenticationState.value)
    }

    @Test
    fun `session switching moves state to Loading and resumes Authenticated`() = runTest {
        val harness =
            Harness(
                initialAuthenticated = true,
                restoreResult = { AuthRepository.RestoreResult.Success },
            )
        val viewModel = harness.createViewModel()
        advanceUntilIdle()
        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)

        harness.isSwitchingSessionFlow.value = true
        advanceUntilIdle()
        assertSame(AuthenticationState.Loading, viewModel.authenticationState.value)

        harness.isSwitchingSessionFlow.value = false
        advanceUntilIdle()
        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)
    }

    @Test
    fun `logout transition moves Authenticated to NotAuthenticated`() = runTest {
        val harness =
            Harness(
                initialAuthenticated = true,
                restoreResult = { AuthRepository.RestoreResult.Success },
            )
        val viewModel = harness.createViewModel()
        advanceUntilIdle()
        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)

        harness.isAuthenticatedFlow.value = false
        advanceUntilIdle()
        assertSame(AuthenticationState.NotAuthenticated, viewModel.authenticationState.value)

        harness.isAuthenticatedFlow.value = true
        advanceUntilIdle()
        assertSame(AuthenticationState.Authenticated, viewModel.authenticationState.value)
    }
}

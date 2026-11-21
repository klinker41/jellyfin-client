package com.makd.afinity.util

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressProberTest {

    @Test
    fun emptyAddressListReturnsAllFailed() = runTest {
        val result =
            probeAddresses(
                addresses = emptyList(),
                preferLocal = true,
                logTag = "Test",
            ) {
                true
            }
        assertEquals(ProbeResult.AllFailed, result)
    }

    @Test
    fun preferLocalPrefersLocalAddressWhenBothSucceedWithinGracePeriod() = runTest {
        val local = "http://192.168.1.10:8096"
        val remote = "https://jf.example.com"

        val result =
            probeAddresses(
                addresses = listOf(remote, local),
                preferLocal = true,
                logTag = "Test",
            ) { address ->
                if (address == local) {
                    delay(50)
                    true
                } else {
                    true
                }
            }

        assertEquals(ProbeResult.Success(local), result)
    }

    @Test
    fun preferLocalFallsBackToExternalWhenLocalExceedsGraceOrFails() = runTest {
        val local = "http://192.168.1.10:8096"
        val remote = "https://jf.example.com"

        val resultWhenLocalSlow =
            probeAddresses(
                addresses = listOf(local, remote),
                preferLocal = true,
                logTag = "Test",
            ) { address ->
                if (address == local) {
                    delay(1_500)
                    true
                } else {
                    true
                }
            }
        assertEquals(ProbeResult.Success(remote), resultWhenLocalSlow)

        val resultWhenLocalFails =
            probeAddresses(
                addresses = listOf(local, remote),
                preferLocal = true,
                logTag = "Test",
            ) { address ->
                address == remote
            }
        assertEquals(ProbeResult.Success(remote), resultWhenLocalFails)
    }

    @Test
    fun preferExternalAndValidatorExceptionHandling() = runTest {
        val local = "http://192.168.1.10:8096"
        val remote = "https://jf.example.com"

        val result =
            probeAddresses(
                addresses = listOf(local, remote),
                preferLocal = false,
                logTag = "Test",
            ) { address ->
                if (address == local) error("unreachable") else true
            }
        assertEquals(ProbeResult.Success(remote), result)

        val allThrowing =
            probeAddresses(
                addresses = listOf(local, remote),
                preferLocal = false,
                logTag = "Test",
            ) {
                throw IllegalStateException("network down")
            }
        assertEquals(ProbeResult.AllFailed, allThrowing)
    }

    @Test
    fun rememberedAddressIsProbedFirstWhenPresent() = runTest {
        val probedOrder = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        val result =
            probeAddresses(
                addresses = listOf("https://a.example.com", "https://b.example.com"),
                preferLocal = false,
                logTag = "Test",
                rememberedAddress = "https://b.example.com",
            ) { address ->
                probedOrder += address
                if (address == "https://b.example.com") {
                    true
                } else {
                    gate.await()
                    false
                }
            }

        assertEquals(ProbeResult.Success("https://b.example.com"), result)
        assertEquals("https://b.example.com", probedOrder.first())
    }

    @Test
    fun networkLocalitySkipsUnroutableLocalAddressesAndReturnsNoRoute() = runTest {
        val local = "http://192.168.1.10:8096"
        val locality = mockk<NetworkLocality>()
        coEvery { locality.resolve(local) } returns Locality.PUBLIC

        var validatorCalled = false
        val result =
            probeAddresses(
                addresses = listOf(local),
                preferLocal = true,
                logTag = "Test",
                networkLocality = locality,
            ) {
                validatorCalled = true
                true
            }

        assertEquals(ProbeResult.NoRoute, result)
        assertTrue(!validatorCalled)
    }

    @Test
    fun networkLocalityTreatsOnLinkPublicHostnameAsLocalAndFallsBackToShapeOnTimeout() = runTest {
        val localAlias = "https://jellyfin.example.com"
        val remote = "https://cdn.example.com"
        val onLinkLocality = mockk<NetworkLocality>()
        coEvery { onLinkLocality.resolve(localAlias) } returns Locality.ON_LINK
        coEvery { onLinkLocality.resolve(remote) } returns Locality.PUBLIC

        val onLinkResult =
            probeAddresses(
                addresses = listOf(remote, localAlias),
                preferLocal = true,
                logTag = "Test",
                networkLocality = onLinkLocality,
            ) { address ->
                if (address == localAlias) {
                    delay(50)
                    true
                } else {
                    true
                }
            }
        assertEquals(ProbeResult.Success(localAlias), onLinkResult)

        val localIp = "http://192.168.1.10:8096"
        val slowLocality = mockk<NetworkLocality>()
        coEvery { slowLocality.resolve(any()) } coAnswers
            {
                delay(1_000)
                Locality.PUBLIC
            }

        val fallbackResult =
            probeAddresses(
                addresses = listOf(remote, localIp),
                preferLocal = true,
                logTag = "Test",
                networkLocality = slowLocality,
            ) { address ->
                if (address == localIp) {
                    delay(50)
                    true
                } else {
                    true
                }
            }
        assertEquals(ProbeResult.Success(localIp), fallbackResult)
    }

    @Test
    fun cancellationPropagatesOutOfProbeAddresses() = runTest {
        val started = CompletableDeferred<Unit>()
        val job = async {
            probeAddresses(
                addresses = listOf("https://a.example.com"),
                preferLocal = false,
                logTag = "Test",
            ) {
                started.complete(Unit)
                CompletableDeferred<Boolean>().await()
            }
        }
        started.await()
        job.cancel()
        val outcome = runCatching { job.await() }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }
}

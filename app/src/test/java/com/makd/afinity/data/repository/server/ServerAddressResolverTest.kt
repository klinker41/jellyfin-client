package com.makd.afinity.data.repository.server

import com.makd.afinity.data.database.dao.ServerAddressMemoryDao
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.server.ServerAddressMemory
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.LocalNetworkPermission
import com.makd.afinity.util.Locality
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressResolverTest {

    private class Harness(
        server: Server? =
            Server(id = "srv-1", name = "Home", address = "https://remote.example.com"),
        alternates: List<ServerAddress> = emptyList(),
        onLocalNetwork: Boolean = false,
        networkKey: String? = "wifi:home",
        rememberedAddress: String? = null,
        permissionExplainsFailure: Boolean = false,
    ) {
        val databaseRepository: DatabaseRepository = mockk {
            coEvery { getServer("srv-1") } returns server
            coEvery { getServerAddresses("srv-1") } returns alternates
        }
        val serverRepository: ServerRepository = mockk {
            coEvery { pingServer(any(), any()) } returns false
        }
        val networkConnectivityMonitor: NetworkConnectivityMonitor = mockk {
            every { isOnLocalNetwork() } returns onLocalNetwork
            every { currentNetworkKey() } returns networkKey
        }
        val networkLocality: NetworkLocality = mockk {
            coEvery { resolve(any()) } returns Locality.PUBLIC
        }
        val localNetworkPermission: LocalNetworkPermission = mockk {
            every { mayExplainFailure(any(), any()) } returns permissionExplainsFailure
        }
        val addressMemoryDao: ServerAddressMemoryDao =
            mockk(relaxed = true) {
                coEvery { this@mockk.get("srv-1", any()) } returns
                    rememberedAddress?.let {
                        ServerAddressMemory("srv-1", networkKey ?: "wifi", it, lastSucceededAt = 1L)
                    }
            }

        val resolver =
            ServerAddressResolver(
                databaseRepository = databaseRepository,
                serverRepository = serverRepository,
                networkConnectivityMonitor = networkConnectivityMonitor,
                networkLocality = networkLocality,
                localNetworkPermission = localNetworkPermission,
                addressMemoryDao = addressMemoryDao,
            )
    }

    @Test
    fun `resolveAddress returns AllFailed immediately when server does not exist`() = runTest {
        val h = Harness(server = null)
        val result = h.resolver.resolveAddress("srv-1") { true }
        assertEquals(AddressResolutionResult.AllFailed("srv-1", emptyList()), result)
    }

    @Test
    fun `resolveAddress deduplicates primary address, returns working alternate, and remembers it`() =
        runTest {
            val primary = "https://remote.example.com"
            val alternate = "http://192.168.1.50:8096"
            val h =
                Harness(
                    server = Server(id = "srv-1", name = "Home", address = primary),
                    alternates =
                        listOf(
                            ServerAddress(testUuid(1), "srv-1", primary),
                            ServerAddress(testUuid(2), "srv-1", alternate),
                        ),
                    onLocalNetwork = true,
                    networkKey = "wifi:home",
                )
            coEvery { h.networkLocality.resolve(alternate) } returns Locality.ON_LINK
            coEvery { h.networkLocality.resolve(primary) } returns Locality.PUBLIC

            val probed = mutableListOf<String>()
            val result =
                h.resolver.resolveAddress("srv-1") { addr ->
                    probed += addr
                    addr == alternate
                }

            assertEquals(AddressResolutionResult.Success(alternate, "srv-1"), result)
            assertEquals(1, probed.count { it == primary })

            val savedSlot = slot<ServerAddressMemory>()
            coVerify { h.addressMemoryDao.upsert(capture(savedSlot)) }
            assertEquals("srv-1", savedSlot.captured.serverId)
            assertEquals("wifi:home", savedSlot.captured.networkKey)
            assertEquals(alternate, savedSlot.captured.address)
        }

    @Test
    fun `resolveAddress maps PermissionRequired, NoRoute, and AllFailed failures cleanly`() =
        runTest {
            val localOnly = "http://192.168.1.50:8096"

            val permissionHarness =
                Harness(
                    server = Server(id = "srv-1", name = "Home", address = localOnly),
                    onLocalNetwork = true,
                    permissionExplainsFailure = true,
                )
            val permResult = permissionHarness.resolver.resolveAddress("srv-1") { false }
            assertTrue(permResult is AddressResolutionResult.PermissionRequired)

            val noRouteHarness =
                Harness(
                    server = Server(id = "srv-1", name = "Home", address = localOnly),
                    onLocalNetwork = false,
                    permissionExplainsFailure = false,
                )
            coEvery { noRouteHarness.networkLocality.resolve(localOnly) } returns Locality.PUBLIC
            assertEquals(
                AddressResolutionResult.NoRoute("srv-1", listOf(localOnly)),
                noRouteHarness.resolver.resolveAddress("srv-1") { true },
            )

            val failedHarness = Harness(onLocalNetwork = false, permissionExplainsFailure = false)
            assertEquals(
                AddressResolutionResult.AllFailed("srv-1", listOf("https://remote.example.com")),
                failedHarness.resolver.resolveAddress("srv-1"),
            )
        }
}

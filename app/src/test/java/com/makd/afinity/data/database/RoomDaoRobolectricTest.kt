package com.makd.afinity.data.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.makd.afinity.data.database.entities.CustomHomeSectionEntity
import com.makd.afinity.data.database.entities.MusicQueueEntity
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RoomDaoRobolectricTest {

    private lateinit var db: AfinityDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AfinityDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `server, serverAddress, and user DAOs handle upserts, relations, and cascade queries`() =
        runTest {
            val serverDao = db.serverDao()
            val addressDao = db.serverAddressDao()
            val userDao = db.userDao()

            val server =
                Server(
                    id = "srv-1",
                    name = "Home Server",
                    version = "10.10.3",
                    address = "https://jf.example.com",
                )
            serverDao.upsertServer(server)
            serverDao.upsertServer(server.copy(name = "Studio Server"))
            assertEquals("Studio Server", serverDao.getServer("srv-1")?.name)

            val addr1 = ServerAddress(testUuid(1), "srv-1", "https://jf.example.com")
            val addr2 = ServerAddress(testUuid(2), "srv-1", "http://192.168.1.5:8096")
            addressDao.insertServerAddresses(listOf(addr1, addr2))
            assertEquals(
                addr2,
                addressDao.getServerAddressByUrl("srv-1", "http://192.168.1.5:8096"),
            )

            val user1 = User(testUuid(10), "Alex", "srv-1", accessToken = "tok-a", isAdmin = true)
            val user2 = User(testUuid(11), "Sam", "srv-1", accessToken = null)
            userDao.insertUsers(listOf(user1, user2))

            assertEquals(2, userDao.getUsersForServer("srv-1").size)
            assertEquals(user1, userDao.getCurrentUser())

            val relations = serverDao.getServerWithAddressesAndUsers("srv-1")
            assertNotNull(relations)
            assertEquals("Studio Server", relations!!.server.name)
            assertEquals(2, relations.addresses.size)
            assertEquals(2, relations.users.size)

            addressDao.deleteServerAddressesByServerId("srv-1")
            assertTrue(addressDao.getServerAddresses("srv-1").isEmpty())
        }

    @Test
    fun `userDataDao filters favorites, continue watching, unsynced, and respects toBeSynced lock`() =
        runTest {
            val userDataDao = db.userDataDao()
            val userId = testUuid(1)
            val favId = testUuid(20)
            val cwSyncedId = testUuid(21)
            val cwUnsyncedId = testUuid(22)

            userDataDao.insertUserDataList(
                listOf(
                    AfinityUserDataDto(
                        userId = userId,
                        itemId = favId,
                        serverId = "srv-1",
                        played = true,
                        favorite = true,
                        likes = false,
                        playbackPositionTicks = 0L,
                        toBeSynced = false,
                    ),
                    AfinityUserDataDto(
                        userId = userId,
                        itemId = cwSyncedId,
                        serverId = "srv-1",
                        played = false,
                        favorite = false,
                        likes = true,
                        playbackPositionTicks = 5_000_000L,
                        toBeSynced = false,
                    ),
                    AfinityUserDataDto(
                        userId = userId,
                        itemId = cwUnsyncedId,
                        serverId = "srv-1",
                        played = false,
                        favorite = false,
                        likes = false,
                        playbackPositionTicks = 2_000_000L,
                        toBeSynced = true,
                    ),
                ),
            )

            assertEquals(
                listOf(favId),
                userDataDao.getFavoriteItems(userId, "srv-1").map { it.itemId },
            )
            assertEquals(
                setOf(cwSyncedId, cwUnsyncedId),
                userDataDao.getContinueWatchingItems(userId, "srv-1").map { it.itemId }.toSet(),
            )
            assertEquals(1, userDataDao.getUnsyncedUserData(userId, "srv-1").size)

            // updateUserDataLocally must NOT overwrite a row marked toBeSynced = 1
            val unsyncedRowsUpdated =
                userDataDao.updateUserDataLocally(
                    itemId = cwUnsyncedId,
                    userId = userId,
                    serverId = "srv-1",
                    isPlayed = true,
                    positionTicks = 0L,
                    isFavorite = true,
                    isLiked = true,
                    unplayedItemCount = null,
                    playCount = null,
                )
            assertEquals(0, unsyncedRowsUpdated)
            assertEquals(false, userDataDao.getUserData(userId, cwUnsyncedId, "srv-1")?.played)

            // After marking synced, local patch succeeds
            userDataDao.markUserDataSynced(userId, cwUnsyncedId, "srv-1")
            assertEquals(0, userDataDao.countUserDataToSync())
            val syncedRowsUpdated =
                userDataDao.updateUserDataLocally(
                    itemId = cwUnsyncedId,
                    userId = userId,
                    serverId = "srv-1",
                    isPlayed = true,
                    positionTicks = 9_000_000L,
                    isFavorite = false,
                    isLiked = false,
                    unplayedItemCount = 0,
                    playCount = 1,
                )
            assertEquals(1, syncedRowsUpdated)
            assertEquals(true, userDataDao.getUserData(userId, cwUnsyncedId, "srv-1")?.played)
        }

    @Test
    fun `customHomeSectionDao and musicQueueDao maintain ordering and clean up properly`() =
        runTest {
            val sectionDao = db.customHomeSectionDao()
            val sec1 =
                CustomHomeSectionEntity(
                    id = "sec-1",
                    sessionKey = "srv-1|u1",
                    position = 0,
                    title = "Action",
                    sourceType = "GENRE",
                    sourceValue = "Action",
                    includeItemTypes = "MOVIE",
                    itemLimit = 20,
                    sortBy = "DATE_CREATED",
                    sortDescending = true,
                    randomOrder = false,
                    cardStyle = "PORTRAIT",
                    enabled = true,
                    seasonStart = null,
                    seasonEnd = null,
                )
            val sec2 = sec1.copy(id = "sec-2", position = 1, title = "Sci-Fi")
            sectionDao.upsert(sec1)
            sectionDao.upsert(sec2)

            assertEquals(1, sectionDao.maxPosition("srv-1|u1"))
            assertEquals(-1, sectionDao.maxPosition("srv-other|u1"))

            sectionDao.observeForSession("srv-1|u1").test {
                assertEquals(listOf("sec-1", "sec-2"), awaitItem().map { it.id })

                sectionDao.applyOrder(listOf("sec-2", "sec-1"))
                assertEquals(listOf("sec-2", "sec-1"), awaitItem().map { it.id })
            }

            sectionDao.deleteById("sec-1")
            assertNull(sectionDao.getById("sec-1"))

            val queueDao = db.musicQueueDao()
            val q0 =
                MusicQueueEntity(
                    position = 0,
                    trackId = testUuid(100).toString(),
                    name = "First",
                    artist = "Artist",
                    albumId = null,
                    album = "Album",
                    durationMs = 120_000L,
                    imageUrl = null,
                    normalizationGain = null,
                    albumNormalizationGain = null,
                    indexNumber = 1,
                    discNumber = 1,
                    serverId = "srv-1",
                    artistId = null,
                )
            val q1 = q0.copy(position = 1, trackId = testUuid(101).toString(), name = "Second")
            queueDao.insertAll(listOf(q1, q0))
            assertEquals(listOf("First", "Second"), queueDao.getQueue().map { it.name })

            queueDao.removeAt(0)
            assertEquals(listOf("Second"), queueDao.getQueue().map { it.name })

            queueDao.clearQueue()
            assertTrue(queueDao.getQueue().isEmpty())
        }
}

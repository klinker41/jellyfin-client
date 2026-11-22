package com.makd.afinity.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DatabaseMigrationsRobolectricTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun locateSchemaFile(version: Int): File {
        val rel = "schemas/com.makd.afinity.data.database.AfinityDatabase/$version.json"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.exists() }
            ?: error("Could not locate Room schema $rel")
    }

    private fun openSqliteDb(
        dbFile: File,
        version: Int,
        onCreateSql: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper {
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(context())
                .name(dbFile.absolutePath)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            onCreateSql(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun createSchemaFromJson(db: SupportSQLiteDatabase, version: Int) {
        val root = JSONObject(locateSchemaFile(version).readText()).getJSONObject("database")
        val entities = root.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            db.execSQL(createSql)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val idxSql =
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", tableName)
                db.execSQL(idxSql)
            }
        }
    }

    private fun listColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                cols += cursor.getString(nameIdx)
            }
        }
        return cols
    }

    private fun listIndices(db: SupportSQLiteDatabase, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names += cursor.getString(nameIdx)
            }
        }
        return names
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table),
            )
            .use { cursor ->
                return cursor.moveToFirst()
            }
    }

    @Test
    fun `v78 to v91 migrations preserve rows, rebuild segments PK, and validate against Room`() =
        runTest {
            val dbFile = tempFolder.newFile("afinity_v78.db")
            val serverId = "srv-78"
            val userId = testUuid(1).toString()
            val movieId = testUuid(10).toString()
            val showId = testUuid(20).toString()
            val seasonId = testUuid(21).toString()
            val episodeId = testUuid(22).toString()
            val sourceId = "src-10"
            val downloadId = testUuid(30).toString()

            openSqliteDb(dbFile, 78) { db ->
                    createSchemaFromJson(db, 78)
                    db.execSQL(
                        "INSERT INTO servers (id, name, version, address) VALUES (?, 'Home', '10.10.3', 'https://jf.example.com')",
                        arrayOf(serverId),
                    )
                    db.execSQL(
                        "INSERT INTO users (id, name, serverId, accessToken, primaryImageTag, isAdmin, canAccessLiveTv, canDownload) VALUES (?, 'Alex', ?, 'tok', NULL, 1, 1, 1)",
                        arrayOf(userId, serverId),
                    )
                    db.execSQL(
                        "INSERT INTO movies (id, serverId, name, originalTitle, overview, runtimeTicks, premiereDate, dateCreated, communityRating, officialRating, criticRating, status, productionYear, endDate, chapters, images, genres, tagline, people, tmdbReviews, mdbRatings) VALUES (?, ?, 'Inception', NULL, 'Dreams', 88800000000, NULL, NULL, 8.8, 'PG-13', 87.0, 'Released', 2010, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
                        arrayOf(movieId, serverId),
                    )
                    db.execSQL(
                        "INSERT INTO shows (id, serverId, name, originalTitle, overview, runtimeTicks, communityRating, officialRating, status, productionYear, premiereDate, dateCreated, dateLastContentAdded, endDate, images, genres, people, tmdbReviews, mdbRatings) VALUES (?, ?, 'Dark', NULL, 'Time', 0, 8.7, 'TV-MA', 'Ended', 2017, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
                        arrayOf(showId, serverId),
                    )
                    db.execSQL(
                        "INSERT INTO seasons (id, serverId, seriesId, name, seriesName, overview, indexNumber, images) VALUES (?, ?, ?, 'Season 1', 'Dark', 'S1', 1, NULL)",
                        arrayOf(seasonId, serverId, showId),
                    )
                    db.execSQL(
                        "INSERT INTO episodes (id, serverId, seasonId, seriesId, name, seriesName, overview, indexNumber, indexNumberEnd, parentIndexNumber, runtimeTicks, premiereDate, communityRating, chapters, images) VALUES (?, ?, ?, ?, 'Secrets', 'Dark', 'Ep1', 1, NULL, 1, 30000000000, NULL, 8.5, NULL, NULL)",
                        arrayOf(episodeId, serverId, seasonId, showId),
                    )
                    db.execSQL(
                        "INSERT INTO sources (id, itemId, name, type, path, downloadId) VALUES (?, ?, 'Primary', 'REMOTE', '/movies/inception.mkv', NULL)",
                        arrayOf(sourceId, movieId),
                    )
                    db.execSQL(
                        "INSERT INTO mediastreams (id, sourceId, title, displayTitle, language, type, codec, isExternal, path, channelLayout, videoRangeType, height, width, videoDoViTitle, hdr10PlusPresentFlag, `index`, channels, isDefault, isForced, isHearingImpaired, isOriginal, profile, localizedLanguage, localizedForced, localizedExternal, localizedHearingImpaired, localizedOriginal, downloadId) VALUES ('ms-1', ?, 'English', 'English AAC', 'eng', 'Audio', 'aac', 0, '', 'stereo', NULL, NULL, NULL, NULL, 0, 1, 2, 1, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
                        arrayOf(sourceId),
                    )
                    db.execSQL(
                        "INSERT INTO segments (itemId, type, startTicks, endTicks) VALUES (?, 'INTRO', 1000, 30000)",
                        arrayOf(episodeId),
                    )
                    db.execSQL(
                        "INSERT INTO downloads (id, itemId, itemName, itemType, sourceId, sourceName, status, progress, bytesDownloaded, totalBytes, filePath, error, createdAt, updatedAt, serverId, userId, imageUrl, seriesImageUrl, seriesName, seasonNumber, episodeNumber, releaseYear, runtimeTicks, folderPath, seriesId, storageVolumeId, playlistId) VALUES (?, ?, 'Inception', 'Movie', ?, '1080p', 'COMPLETED', 1.0, 500, 500, '/dl/m.mkv', NULL, 100, 200, ?, ?, NULL, NULL, NULL, NULL, NULL, '2010', 88800000000, NULL, NULL, 'primary', NULL)",
                        arrayOf(downloadId, movieId, sourceId, serverId, userId),
                    )
                    db.execSQL(
                        "INSERT INTO audiobookshelf_progress (id, jellyfinServerId, jellyfinUserId, libraryItemId, episodeId, currentTime, duration, progress, isFinished, lastUpdate, startedAt, finishedAt, pendingSync) VALUES ('prog-1', ?, ?, 'lib-1', NULL, 120.0, 600.0, 0.2, 0, 1000, 900, NULL, 0)",
                        arrayOf(serverId, userId),
                    )
                    db.execSQL(
                        "INSERT INTO music_queue (position, trackId, name, artist, albumId, album, durationMs, imageUrl, normalizationGain, albumNormalizationGain, indexNumber, discNumber, serverId) VALUES (0, 'trk-1', 'Song', 'Artist', 'alb-1', 'Album', 180000, NULL, NULL, NULL, 1, 1, ?)",
                        arrayOf(serverId),
                    )
                    db.execSQL(
                        "INSERT INTO music_albums (id, serverId, userId, name, artist, artistId, productionYear, songCount, runtimeTicks, images, localImagePath) VALUES ('alb-1', ?, ?, 'Album', 'Artist', 'art-1', 2024, 10, 24000000000, NULL, NULL)",
                        arrayOf(serverId, userId),
                    )
                    db.execSQL(
                        "INSERT INTO item_metadata_cache (itemId, serverId, userId, tmdbReviews, mdbRatings, mdbRatingBadges, omdbAwards, lastUpdated) VALUES (?, ?, ?, '[]', '[]', '{}', NULL, 12345)",
                        arrayOf(movieId, serverId, userId),
                    )
                }
                .use { helper ->
                    val db = helper.writableDatabase
                    DatabaseMigrations.ALL_MIGRATIONS.filter { it.startVersion >= 78 }
                        .sortedBy { it.startVersion }
                        .forEach { migration -> migration.migrate(db) }
                    db.version = AFINITY_DB_VERSION

                    // Verify MIGRATION_80_81 defaults on audiobookshelf_progress
                    db.query(
                            "SELECT pendingTimeListened, localSessionId FROM audiobookshelf_progress WHERE id='prog-1'",
                        )
                        .use { c ->
                            assertTrue(c.moveToFirst())
                            assertEquals(0.0, c.getDouble(0), 0.0001)
                            assertTrue(c.isNull(1))
                        }

                    // Verify MIGRATION_85_86 composite PK allows multiple segments with same
                    // (itemId, type)
                    db.execSQL(
                        "INSERT INTO segments (itemId, type, startTicks, endTicks) VALUES (?, 'INTRO', 50000, 70000)",
                        arrayOf(episodeId),
                    )
                    db.query("SELECT COUNT(*) FROM segments WHERE itemId=?", arrayOf(episodeId))
                        .use { c ->
                            assertTrue(c.moveToFirst())
                            assertEquals(2, c.getInt(0))
                        }

                    // Verify MIGRATION_89_90 and MIGRATION_90_91 tables and indices
                    assertTrue(tableExists(db, "offline_plays"))
                    assertTrue(tableExists(db, "book_annotations"))
                    assertTrue(tableExists(db, "videos"))
                    assertTrue(tableExists(db, "music_videos"))
                    assertTrue(tableExists(db, "books"))
                    assertTrue(tableExists(db, "audiobook_parts"))
                    assertTrue(
                        listIndices(db, "offline_plays")
                            .contains("index_offline_plays_userId_serverId"),
                    )
                    assertTrue(
                        listIndices(db, "book_annotations")
                            .contains("index_book_annotations_serverId_userId_itemId"),
                    )
                    assertTrue(listColumns(db, "mediastreams").contains("isInterlaced"))
                    assertTrue(
                        listColumns(db, "sources").containsAll(listOf("bitrate", "container")),
                    )
                }

            // Open through Room to trigger Room's full v91 schema validation and query migrated
            // rows.
            val roomDb =
                Room.databaseBuilder(context(), AfinityDatabase::class.java, dbFile.absolutePath)
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .allowMainThreadQueries()
                    .build()
            try {
                assertEquals("Home", roomDb.serverDao().getServer(serverId)?.name)
                assertEquals(
                    "Inception",
                    roomDb.movieDao().getMovie(testUuid(10), serverId)?.name,
                )
                assertEquals(
                    2,
                    roomDb.openHelper.readableDatabase
                        .query("SELECT COUNT(*) FROM segments WHERE itemId=?", arrayOf(episodeId))
                        .use { c ->
                            c.moveToFirst()
                            c.getInt(0)
                        },
                )
                val showName = roomDb.showDao().getShow(testUuid(20), serverId)?.name
                assertEquals("Dark", showName)
                assertTrue(
                    roomDb.openHelper.readableDatabase
                        .query(
                            "SELECT * FROM segments WHERE type=?",
                            arrayOf(AfinitySegmentType.INTRO.name),
                        )
                        .use { it.count == 2 },
                )
            } finally {
                roomDb.close()
            }
        }

    @Test
    fun `full v1 to v91 migration chain executes cleanly, backfills serverId, and passes integrity check`() {
        val dbFile = tempFolder.newFile("afinity_v1.db")
        val serverId = "srv-1"
        val userId = testUuid(1).toString()
        val movieId = testUuid(2).toString()
        val showId = testUuid(3).toString()
        val seasonId = testUuid(4).toString()
        val episodeId = testUuid(5).toString()
        val sourceId = "src-1"

        openSqliteDb(dbFile, 1) { db ->
                // Core v1 tables before MIGRATION_1_2..MIGRATION_90_91
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `servers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `currentServerAddressId` TEXT, `currentUserId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `serverAddresses` (`id` TEXT NOT NULL, `serverId` TEXT NOT NULL, `address` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_serverAddresses_serverId` ON `serverAddresses` (`serverId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `serverId` TEXT NOT NULL, `accessToken` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_users_serverId` ON `users` (`serverId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `movies` (`id` TEXT NOT NULL, `serverId` TEXT, `name` TEXT NOT NULL, `originalTitle` TEXT, `overview` TEXT NOT NULL, `runtimeTicks` INTEGER NOT NULL, `premiereDate` TEXT, `communityRating` REAL, `officialRating` TEXT, `criticRating` REAL, `status` TEXT NOT NULL, `productionYear` INTEGER, `endDate` TEXT, `chapters` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shows` (`id` TEXT NOT NULL, `serverId` TEXT, `name` TEXT NOT NULL, `originalTitle` TEXT, `overview` TEXT NOT NULL, `runtimeTicks` INTEGER NOT NULL, `communityRating` REAL, `officialRating` TEXT, `status` TEXT NOT NULL, `productionYear` INTEGER, `premiereDate` TEXT, `endDate` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `seasons` (`id` TEXT NOT NULL, `seriesId` TEXT NOT NULL, `name` TEXT NOT NULL, `seriesName` TEXT NOT NULL, `overview` TEXT NOT NULL, `indexNumber` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episodes` (`id` TEXT NOT NULL, `serverId` TEXT, `seasonId` TEXT NOT NULL, `seriesId` TEXT NOT NULL, `name` TEXT NOT NULL, `seriesName` TEXT NOT NULL, `overview` TEXT NOT NULL, `indexNumber` INTEGER NOT NULL, `indexNumberEnd` INTEGER, `parentIndexNumber` INTEGER NOT NULL, `runtimeTicks` INTEGER NOT NULL, `premiereDate` TEXT, `communityRating` REAL, `chapters` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sources` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `path` TEXT NOT NULL, `downloadId` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mediastreams` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `title` TEXT NOT NULL, `displayTitle` TEXT, `language` TEXT NOT NULL, `type` TEXT NOT NULL, `codec` TEXT NOT NULL, `isExternal` INTEGER NOT NULL, `path` TEXT NOT NULL, `channelLayout` TEXT, `videoRangeType` TEXT, `height` INTEGER, `width` INTEGER, `videoDoViTitle` TEXT, `index` INTEGER NOT NULL, `channels` INTEGER, `isDefault` INTEGER NOT NULL, `downloadId` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trickplayInfos` (`sourceId` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `tileWidth` INTEGER NOT NULL, `tileHeight` INTEGER NOT NULL, `thumbnailCount` INTEGER NOT NULL, `interval` INTEGER NOT NULL, `bandwidth` INTEGER NOT NULL, PRIMARY KEY(`sourceId`), FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `segments` (`itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `startTicks` INTEGER NOT NULL, `endTicks` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `type`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userdata` (`userId` TEXT NOT NULL, `itemId` TEXT NOT NULL, `played` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `playbackPositionTicks` INTEGER NOT NULL, `toBeSynced` INTEGER NOT NULL, PRIMARY KEY(`userId`, `itemId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `jellyseerr_config` (`id` INTEGER PRIMARY KEY NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `jellyseerr_requests` (`id` INTEGER PRIMARY KEY NOT NULL)",
                )

                // Seed v1 rows (including NULL serverId to verify MIGRATION_22_23 backfill)
                db.execSQL(
                    "INSERT INTO servers (id, name) VALUES (?, 'Legacy Server')",
                    arrayOf(serverId),
                )
                db.execSQL(
                    "INSERT INTO users (id, name, serverId, accessToken) VALUES (?, 'LegacyUser', ?, 'tok-1')",
                    arrayOf(userId, serverId),
                )
                db.execSQL(
                    "INSERT INTO movies (id, serverId, name, originalTitle, overview, runtimeTicks, status) VALUES (?, NULL, 'Legacy Movie', NULL, 'Plot', 1000, 'Released')",
                    arrayOf(movieId),
                )
                db.execSQL(
                    "INSERT INTO shows (id, serverId, name, originalTitle, overview, runtimeTicks, status) VALUES (?, ?, 'Legacy Show', NULL, 'Plot', 0, 'Continuing')",
                    arrayOf(showId, serverId),
                )
                db.execSQL(
                    "INSERT INTO seasons (id, seriesId, name, seriesName, overview, indexNumber) VALUES (?, ?, 'Season 1', 'Legacy Show', 'S1', 1)",
                    arrayOf(seasonId, showId),
                )
                db.execSQL(
                    "INSERT INTO episodes (id, serverId, seasonId, seriesId, name, seriesName, overview, indexNumber, parentIndexNumber, runtimeTicks) VALUES (?, NULL, ?, ?, 'Pilot', 'Legacy Show', 'Ep1', 1, 1, 500)",
                    arrayOf(episodeId, seasonId, showId),
                )
                db.execSQL(
                    "INSERT INTO userdata (userId, itemId, played, favorite, playbackPositionTicks, toBeSynced) VALUES (?, ?, 1, 1, 4200, 0)",
                    arrayOf(userId, movieId),
                )
                db.execSQL(
                    "INSERT INTO sources (id, itemId, name, type, path) VALUES (?, ?, 'Main', 'REMOTE', '/m.mkv')",
                    arrayOf(sourceId, movieId),
                )
                db.execSQL(
                    "INSERT INTO mediastreams (id, sourceId, title, language, type, codec, isExternal, path, videoRangeType, `index`, isDefault) VALUES ('stream-hdr', ?, 'HDR', 'und', 'Video', 'hevc', 0, '', 'HDR10_PLUS', 0, 1)",
                    arrayOf(sourceId),
                )
            }
            .use { helper ->
                val db = helper.writableDatabase
                val sorted = DatabaseMigrations.ALL_MIGRATIONS.sortedBy { it.startVersion }

                for (migration in sorted) {
                    migration.migrate(db)

                    if (migration.endVersion == 23) {
                        // Movie & episode NULL serverId backfilled; seasons & userdata got default
                        // serverId
                        db.query("SELECT serverId FROM movies WHERE id=?", arrayOf(movieId)).use { c
                            ->
                            assertTrue(c.moveToFirst())
                            assertEquals("default-server-migration", c.getString(0))
                        }
                        db.query("SELECT serverId FROM seasons WHERE id=?", arrayOf(seasonId))
                            .use { c ->
                                assertTrue(c.moveToFirst())
                                assertEquals("default-server-migration", c.getString(0))
                            }
                        db.query("SELECT serverId FROM userdata WHERE itemId=?", arrayOf(movieId))
                            .use { c ->
                                assertTrue(c.moveToFirst())
                                assertEquals("default-server-migration", c.getString(0))
                            }
                    }

                    if (migration.endVersion == 25) {
                        // Seed one invalid download (empty serverId) and one valid download to test
                        // MIGRATION_39_40 cleanup
                        db.execSQL(
                            "INSERT INTO downloads (id, itemId, itemName, itemType, sourceId, sourceName, status, progress, bytesDownloaded, totalBytes, createdAt, updatedAt, serverId, userId) VALUES ('dl-bad', ?, 'Bad', 'Movie', 's1', '1080p', 'FAILED', 0.0, 0, 100, 1, 1, '', ?)",
                            arrayOf(movieId, userId),
                        )
                        db.execSQL(
                            "INSERT INTO downloads (id, itemId, itemName, itemType, sourceId, sourceName, status, progress, bytesDownloaded, totalBytes, createdAt, updatedAt, serverId, userId) VALUES ('dl-good', ?, 'Good', 'Movie', 's1', '1080p', 'COMPLETED', 1.0, 100, 100, 1, 1, ?, ?)",
                            arrayOf(movieId, serverId, userId),
                        )
                    }

                    if (migration.endVersion == 40) {
                        db.query("SELECT id FROM downloads").use { c ->
                            val ids = mutableListOf<String>()
                            while (c.moveToNext()) ids += c.getString(0)
                            assertEquals(listOf("dl-good"), ids)
                        }
                    }

                    if (migration.endVersion == 57) {
                        // Seed custom home section with blank includeItemTypes to verify
                        // MIGRATION_59_60 backfill
                        db.execSQL(
                            "INSERT INTO custom_home_sections (id, sessionKey, position, title, sourceType, sourceValue, includeItemTypes, itemLimit, sortBy, sortDescending, randomOrder, cardStyle, enabled) VALUES ('cs-1', 'sk', 0, 'All', 'GENRE', 'Action', '', 10, 'NAME', 0, 0, 'PORTRAIT', 1)",
                        )
                    }

                    if (migration.endVersion == 60) {
                        db.query(
                                "SELECT includeItemTypes FROM custom_home_sections WHERE id='cs-1'",
                            )
                            .use { c ->
                                assertTrue(c.moveToFirst())
                                assertEquals("MOVIE,SERIES", c.getString(0))
                            }
                    }

                    if (migration.endVersion == 77) {
                        db.query("SELECT videoRangeType FROM mediastreams WHERE id='stream-hdr'")
                            .use { c ->
                                assertTrue(c.moveToFirst())
                                assertEquals("HDR10Plus", c.getString(0))
                            }
                    }
                }

                // Final assertions at v91
                db.query("PRAGMA integrity_check").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("ok", c.getString(0))
                }
                assertFalse(tableExists(db, "watchlist"))
                assertFalse(tableExists(db, "library_cache"))
                assertFalse(tableExists(db, "studio_cache"))
                assertTrue(listIndices(db, "movies").contains("idx_movies_server"))
                assertTrue(listIndices(db, "episodes").contains("idx_episodes_server"))
                assertTrue(listIndices(db, "userdata").contains("idx_userdata_server_user"))

                db.query("SELECT name FROM movies WHERE id=?", arrayOf(movieId)).use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Legacy Movie", c.getString(0))
                }
                db.query("SELECT name FROM episodes WHERE id=?", arrayOf(episodeId)).use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Pilot", c.getString(0))
                }
                assertTrue(tableExists(db, "books"))
                assertTrue(listColumns(db, "books").isNotEmpty())
            }
    }
}

package com.makd.afinity.navigation

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationRouteTest {

    @Test
    fun libraryRouteDispatchesMusicAndVideoLibrariesAndEscapesSlashes() {
        val movies =
            testCollection(id = testUuid(1), name = "Action/Sci-Fi", type = CollectionType.Movies)
        val music =
            testCollection(id = testUuid(2), name = "Lossless/FLAC", type = CollectionType.Music)

        assertEquals(
            "library_content/${testUuid(1)}/Action%2FSci-Fi",
            Destination.createLibraryRoute(movies),
        )
        assertEquals(
            "music/library/${testUuid(2)}/Lossless%2FFLAC",
            Destination.createLibraryRoute(music),
        )
        assertEquals(
            "music/library/${testUuid(2)}/Lossless%2FFLAC/browse/albums",
            Destination.createMusicBrowseRoute(testUuid(2).toString(), "Lossless/FLAC", "albums"),
        )
    }

    @Test
    fun playerItemDetailAndPlaylistRoutesBuildExpectedQueryStrings() {
        assertEquals("player/m1/s1", Destination.createPlayerRoute("m1", "s1"))
        assertEquals(
            "player/m1/s1?audioStreamIndex=2&subtitleStreamIndex=4&startPositionMs=12000",
            Destination.createPlayerRoute(
                itemId = "m1",
                mediaSourceId = "s1",
                audioStreamIndex = 2,
                subtitleStreamIndex = 4,
                startPositionMs = 12_000L,
            ),
        )
        // startPositionMs = 0 is omitted.
        assertEquals(
            "player/m1/s1?audioStreamIndex=1",
            Destination.createPlayerRoute("m1", "s1", audioStreamIndex = 1, startPositionMs = 0L),
        )

        assertEquals("item_detail/i1", Destination.createItemDetailRoute("i1"))
        assertEquals(
            "item_detail/i1?itemType=Movie&seriesId=s9",
            Destination.createItemDetailRoute("i1", itemType = "Movie", seriesId = "s9"),
        )
        assertEquals(
            "item_detail/season1?itemType=Season&seriesId=show1",
            Destination.createEpisodeListRoute("season1", "Season 1", "show1"),
        )
        assertEquals("playlist/p1?audioOnly=false", Destination.createPlaylistRoute("p1"))
        assertEquals(
            "playlist/p1?audioOnly=true",
            Destination.createPlaylistRoute("p1", audioOnly = true),
        )
    }

    @Test
    fun musicGenreAndFolderRoutesEncodeSpecialCharactersAndOptionalParams() {
        val genreId = testUuid(10)
        val libraryId = testUuid(20)
        assertEquals("music/genre/Rock%2FRoll", Destination.createMusicGenreRoute("Rock/Roll"))
        assertEquals(
            "music/genre/Jazz?imageUrl=https%3A%2F%2Fimg%2Fa+b.jpg&genreId=$genreId&libraryId=$libraryId",
            Destination.createMusicGenreRoute(
                genreName = "Jazz",
                imageUrl = "https://img/a b.jpg",
                genreId = genreId,
                libraryId = libraryId,
            ),
        )

        assertEquals(
            "folder_content/f1/My+Folder%2F2025",
            Destination.createFolderContentRoute("f1", "My Folder/2025", gallery = false),
        )
        assertEquals(
            "folder_content/f1/Photos?gallery=true",
            Destination.createFolderContentRoute("f1", "Photos", gallery = true),
        )
    }

    @Test
    fun audiobookshelfMediaCategoryAndAuthRoutesFormatProperly() {
        assertEquals("audiobookshelf/login", Destination.createAudiobookshelfLoginRoute())
        assertEquals("audiobookshelf/libraries", Destination.createAudiobookshelfLibrariesRoute())
        assertEquals(
            "audiobookshelf/series-browse",
            Destination.createAudiobookshelfSeriesListRoute(),
        )
        assertEquals(
            "audiobookshelf/library/l1/Sci%2FFi",
            Destination.createAudiobookshelfLibraryRoute("l1", "Sci/Fi"),
        )
        assertEquals("audiobookshelf/item/ab1", Destination.createAudiobookshelfItemRoute("ab1"))
        assertEquals(
            "audiobookshelf/series/s1/l1/Series%2FOne",
            Destination.createAudiobookshelfSeriesRoute("s1", "l1", "Series/One"),
        )
        assertEquals(
            "audiobookshelf/genre/Non%2FFiction",
            Destination.createAudiobookshelfGenreResultsRoute("Non/Fiction"),
        )
        assertEquals(
            "audiobookshelf/player/item1",
            Destination.createAudiobookshelfPlayerRoute("item1"),
        )
        assertEquals(
            "audiobookshelf/player/item1?source=jellyfin&autoPlay=true",
            Destination.createAudiobookshelfPlayerRoute(
                itemId = "item1",
                source = Destination.AUDIOBOOK_SOURCE_JELLYFIN,
                autoPlay = true,
            ),
        )
        assertEquals(
            "audiobookshelf/player/item1?episodeId=ep2&startPosition=42.5&episodeSort=asc&includePlayed=true&source=jellyfin",
            Destination.createAudiobookshelfPlayerRoute(
                itemId = "item1",
                episodeId = "ep2",
                startPosition = 42.5,
                episodeSort = "asc",
                includePlayed = true,
                source = Destination.AUDIOBOOK_SOURCE_JELLYFIN,
            ),
        )

        assertEquals(
            "studio_content/A24%2FFilms",
            Destination.createStudioContentRoute("A24/Films"),
        )
        assertEquals(
            "genre_results/Action%2FComedy",
            Destination.createGenreResultsRoute("Action/Comedy"),
        )
        assertEquals(
            "filtered_media/studio/7/Warner%2FBros",
            Destination.createFilteredMediaRoute("studio", 7, "Warner/Bros"),
        )
        assertEquals("seerr_media/movie/550", Destination.createSeerrMediaRoute("MOVIE", 550))
        assertEquals("add_edit_server", Destination.createAddEditServerRoute())
        assertEquals("add_edit_server?serverId=srv1", Destination.createAddEditServerRoute("srv1"))
        assertEquals("login", Destination.createLoginRoute())
        assertEquals(
            "login?serverUrl=https://jf.local",
            Destination.createLoginRoute("https://jf.local"),
        )
        assertEquals("services_hub?entry=settings", Destination.createServicesHubRoute("settings"))
        assertEquals(
            "photo_viewer/p1/s1?sortBy=Date&desc=true",
            Destination.createPhotoViewerRoute("p1", "s1", "Date", true),
        )
    }

    @Test
    fun staticAndSimpleDynamicRoutesReturnExpectedStrings() {
        assertEquals("home", Destination.HOME.route)
        assertEquals(Destination.LOGS_ROUTE, Destination.createLogsRoute())
        assertEquals("live_tv/program/p1", Destination.createLiveTvProgramRoute("p1"))
        assertEquals("live_tv/channel/c1", Destination.createLiveTvChannelRoute("c1"))
        assertEquals(
            "admin/server/activity?filter=all",
            Destination.createAdminActivityRoute("all"),
        )
        assertEquals(
            "admin/server/settings/general",
            Destination.createAdminSettingsRoute("general"),
        )
        assertEquals("admin/server/users/u1", Destination.createAdminUserDetailRoute("u1"))
        assertEquals(
            "admin/server/users/u1/permissions",
            Destination.createAdminUserPermissionsRoute("u1"),
        )
        assertEquals(
            "admin/server/users/u1/parental",
            Destination.createAdminUserParentalRoute("u1"),
        )
        assertEquals(
            "admin/server/plugins/detail/plug1",
            Destination.createAdminPluginDetailRoute("plug1"),
        )
        assertEquals("admin/edit_metadata/i1", Destination.createEditMetadataRoute("i1"))
        assertEquals("admin/identify/i1/Movie", Destination.createIdentifyItemRoute("i1", "Movie"))
        assertEquals("admin/edit_images/i1", Destination.createEditImagesRoute("i1"))
        assertEquals("admin/edit_lyrics/i1", Destination.createEditLyricsRoute("i1"))
        assertEquals("person/p9", Destination.createPersonRoute("p9"))
        assertEquals("franchise/fr1", Destination.createFranchiseRoute("fr1"))
        assertEquals(
            "custom_section_content/cs1",
            Destination.createCustomSectionContentRoute("cs1"),
        )
        assertEquals("book/b1", Destination.createBookDetailRoute("b1"))
        assertEquals("book/b1/read", Destination.createBookReaderRoute("b1"))
        assertEquals("audiobook/ab9", Destination.createAudiobookDetailRoute("ab9"))
        assertEquals("music/album/al1", Destination.createMusicAlbumRoute("al1"))
        assertEquals("music/artist/ar1", Destination.createMusicArtistRoute("ar1"))
        assertEquals(
            "favorites_category/Movies",
            Destination.createFavoritesCategoryRoute("Movies"),
        )
        assertEquals(
            "downloaded_category/Shows",
            Destination.createDownloadedCategoryRoute("Shows"),
        )
        assertEquals("watchlist_category/All", Destination.createWatchlistCategoryRoute("All"))
        assertEquals(Destination.SETTINGS_ROUTE, Destination.createSettingsRoute())
        assertEquals(Destination.DOWNLOAD_SETTINGS_ROUTE, Destination.createDownloadSettingsRoute())
        assertEquals(Destination.PLAYER_OPTIONS_ROUTE, Destination.createPlayerOptionsRoute())
        assertEquals(
            Destination.APPEARANCE_OPTIONS_ROUTE,
            Destination.createAppearanceOptionsRoute(),
        )
        assertEquals(Destination.CUSTOM_SECTIONS_ROUTE, Destination.createCustomSectionsRoute())
        assertEquals(Destination.LICENSES_ROUTE, Destination.createLicensesRoute())
        assertEquals(Destination.SERVER_MANAGEMENT_ROUTE, Destination.createServerManagementRoute())
        assertEquals(Destination.SEARCH_ROUTE, Destination.createSearchRoute())
    }
}

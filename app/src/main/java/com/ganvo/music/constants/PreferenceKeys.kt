package com.ganvo.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val SlimNavBarKey = booleanPreferencesKey("slimNavBar")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")

enum class SliderStyle {
    DEFAULT, SQUIGGLY, SLIM
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val YtmSyncKey = booleanPreferencesKey("ytmSync")

val AudioQualityKey = stringPreferencesKey("audioQuality")

enum class AudioQuality {
    AUTO, HIGH, LOW
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val SimilarContent = booleanPreferencesKey("similarContent")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")

val PlayerTextAlignmentKey = stringPreferencesKey("playerTextAlignment")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

// Missing Keys restored
val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

// Lyrics Preferences
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val ExperimentalLyricsKey = booleanPreferencesKey("experimental_lyrics")
val GlowingLyricsKey = booleanPreferencesKey("glowing_lyrics")
val WordByWordStyleKey = stringPreferencesKey("word_by_word_style")
val LyricsTextSizeKey = floatPreferencesKey("lyrics_text_size")
val LyricsLineSpacingKey = floatPreferencesKey("lyrics_line_spacing")
val RespectAgentPositioningKey = booleanPreferencesKey("respect_agent_positioning")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")

enum class LibraryViewType {
    LIST, GRID;
    fun toggle() = when (this) { LIST -> GRID; GRID -> LIST }
}

enum class SongFilter { LIBRARY, LIKED, DOWNLOADED }
enum class ArtistFilter { LIBRARY, LIKED }
enum class AlbumFilter { LIBRARY, LIKED }

enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class AutoPlaylistSongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }
enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }
enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }
enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }
enum class MixSortType { CREATE_DATE, NAME, LAST_UPDATED }

enum class GridItemSize { SMALL, BIG }

enum class QuickPicks { QUICK_PICKS, LAST_LISTEN }

enum class PreferredLyricsProvider { LRCLIB, KUGOU, MUSIXMATCH }
enum class WordByWordStyle { FADE, SCALE, NONE }

enum class PlayerBackgroundStyle { DEFAULT, GRADIENT, BLUR }
enum class PlayerButtonsStyle { DEFAULT, SECONDARY }

// Missing MyTopFilter restored
enum class MyTopFilter {
    ALL_TIME, DAY, WEEK, MONTH, YEAR;
    
    fun toTimeMillis(): Long =
        when (this) {
            DAY -> LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            WEEK -> LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            MONTH -> LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            YEAR -> LocalDateTime.now().minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
            ALL_TIME -> 0
        }
}

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")
val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")

enum class SearchSource {
    LOCAL, ONLINE;
    fun toggle() = when (this) { LOCAL -> ONLINE; ONLINE -> LOCAL }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

// Restricted Languages
val LanguageCodeToName = mapOf(
    "en" to "English",
    "es" to "Spanish (Español)",
    "zh-CN" to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)",
    "ja" to "Japanese (日本語)",
    "ko" to "Korean (한국어)",
)
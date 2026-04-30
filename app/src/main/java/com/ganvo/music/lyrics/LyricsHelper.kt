package com.ganvo.music.lyrics

import android.content.Context
import android.util.LruCache
import com.ganvo.music.constants.PreferredLyricsProvider
import com.ganvo.music.constants.PreferredLyricsProviderKey
import com.ganvo.music.db.MusicDatabase
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.utils.dataStore
import com.ganvo.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val lyricsProviders = listOf(
        MusixmatchLyricsProvider,
        NeteaseLyricsProvider,
        PaxsenixLyricsProvider, 
        KugouLyricsProvider,
        LrclibLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider
    )

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    // Cleans title so "Song (feat. Artist) [Official Music Video]" becomes "Song" for better API matching
    private fun cleanQuery(text: String): String {
        return text.replace(Regex("(?i)\\s*\\(.*?\\)\\s*|\\s*\\[.*?\\]\\s*"), "")
            .replace(Regex("(?i)lyric|video|audio|official", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        // 1. Check Database first (For edited/saved lyrics)
        val dbLyrics = database.lyrics(mediaMetadata.id).firstOrNull()
        if (dbLyrics != null && dbLyrics.lyrics.isNotBlank() && dbLyrics.lyrics != LYRICS_NOT_FOUND) {
            return if (dbLyrics.lyrics.startsWith("PROVIDER:")) {
                dbLyrics.lyrics
            } else {
                "PROVIDER:Local Database\n${dbLyrics.lyrics}"
            }
        }

        // 2. Check Session Cache
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return "PROVIDER:${cached.providerName}\n${cached.lyrics}"
        }
        
        // 3. Fetch from API prioritizing User Setting
        val preferredProviderEnum = context.dataStore.data
            .map { it[PreferredLyricsProviderKey] ?: PreferredLyricsProvider.MUSIXMATCH.name }
            .first()
            
        // Force the preferred provider to the absolute top of the queue
        val sortedProviders = lyricsProviders.toMutableList()
        val preferred = sortedProviders.find { it.name.contains(preferredProviderEnum, ignoreCase = true) }
        if (preferred != null) {
            sortedProviders.remove(preferred)
            sortedProviders.add(0, preferred)
        }

        val cleanTitle = cleanQuery(mediaMetadata.title)
        val cleanArtist = mediaMetadata.artists.firstOrNull()?.name ?: ""

        sortedProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        cleanTitle,
                        cleanArtist,
                        mediaMetadata.duration,
                    ).onSuccess { lyrics ->
                        val res = LyricsResult(provider.name, lyrics)
                        cache.put(mediaMetadata.id, listOf(res))
                        
                        // Save successfully fetched lyrics to the DB so we don't query again next time
                        database.query { 
                            upsert(com.ganvo.music.db.entities.LyricsEntity(mediaMetadata.id, "PROVIDER:${provider.name}\n$lyrics")) 
                        }
                        
                        return "PROVIDER:${provider.name}\n$lyrics"
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
        return LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        
        val preferredProviderEnum = context.dataStore.data
            .map { it[PreferredLyricsProviderKey] ?: PreferredLyricsProvider.MUSIXMATCH.name }
            .first()
            
        val sortedProviders = lyricsProviders.toMutableList()
        val preferred = sortedProviders.find { it.name.contains(preferredProviderEnum, ignoreCase = true) }
        if (preferred != null) {
            sortedProviders.remove(preferred)
            sortedProviders.add(0, preferred)
        }
        
        val cleanTitle = cleanQuery(songTitle)
        val cleanArtist = songArtists.split(",").firstOrNull()?.trim() ?: songArtists

        sortedProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, cleanTitle, cleanArtist, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
package com.ganvo.music.lyrics

import android.content.Context
import android.util.LruCache
import com.ganvo.music.constants.PreferredLyricsProvider
import com.ganvo.music.constants.PreferredLyricsProviderKey
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.utils.dataStore
import com.ganvo.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val lyricsProviders = listOf(
        MusixmatchLyricsProvider,
        KugouLyricsProvider,
        LrclibLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider
    )

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        
        // Fetch preferred provider from DataStore
        val preferredProviderEnum = context.dataStore.data
            .map { it[PreferredLyricsProviderKey] ?: PreferredLyricsProvider.LRCLIB.name }
            .first()
            
        // Sort providers based on user preference so it's tested first
        val sortedProviders = lyricsProviders.sortedByDescending { 
            it.name.equals(preferredProviderEnum, ignoreCase = true) 
        }

        sortedProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                    ).onSuccess { lyrics ->
                        return lyrics
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
        
        // Fetch preferred provider from DataStore
        val preferredProviderEnum = context.dataStore.data
            .map { it[PreferredLyricsProviderKey] ?: PreferredLyricsProvider.LRCLIB.name }
            .first()
            
        val sortedProviders = lyricsProviders.sortedByDescending { 
            it.name.equals(preferredProviderEnum, ignoreCase = true) 
        }
        
        sortedProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
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
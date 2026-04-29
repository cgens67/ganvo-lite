private val lyricsProviders = listOf(
        BetterLyricsProvider, // Word-by-word priority
        MusixmatchLyricsProvider,
        KugouLyricsProvider,
        LrclibLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider
    )
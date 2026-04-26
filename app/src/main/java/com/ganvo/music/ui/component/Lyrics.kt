package com.ganvo.music.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import coil.compose.AsyncImage
import com.ganvo.music.LocalDatabase
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.DarkModeKey
import com.ganvo.music.constants.LyricsClickKey
import com.ganvo.music.constants.LyricsTextPositionKey
import com.ganvo.music.constants.PlayerBackgroundStyle
import com.ganvo.music.constants.PlayerBackgroundStyleKey
import com.ganvo.music.constants.ShowLyricsKey
import com.ganvo.music.constants.SliderStyle
import com.ganvo.music.constants.SliderStyleKey
import com.ganvo.music.db.entities.LyricsEntity
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.lyrics.LyricsEntry
import com.ganvo.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.ganvo.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.ganvo.music.lyrics.LyricsUtils.parseLyrics
import com.ganvo.music.ui.component.shimmer.ShimmerHost
import com.ganvo.music.ui.component.shimmer.TextPlaceholder
import com.ganvo.music.ui.menu.LyricsMenu
import com.ganvo.music.ui.screens.settings.DarkMode
import com.ganvo.music.ui.screens.settings.LyricsPosition
import com.ganvo.music.ui.utils.fadingEdge
import com.ganvo.music.utils.ComposeToImage
import com.ganvo.music.utils.TransliterationUtils
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberEnumPreference
import com.ganvo.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider
import kotlin.time.Duration.Companion.seconds

const val ANIMATE_SCROLL_DURATION = 300L
val LyricsPreviewTime = 2.seconds

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope", "StringFormatInvalid")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val isFullscreen = onNavigateBack != null
    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val landscapeOffset = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id
    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    var lyricsCache by remember { mutableStateOf<Map<String, LyricsEntity>>(emptyMap()) }
    var currentLyricsEntity by remember(currentSongId) { mutableStateOf(lyricsCache[currentSongId]) }
    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }

    val rawLyrics = remember(currentLyricsEntity) { currentLyricsEntity?.lyrics?.trim() }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val playerVolume = playerConnection.service.playerVolume.collectAsState()

    val playerBackground by rememberEnumPreference(key = PlayerBackgroundStyleKey, defaultValue = PlayerBackgroundStyle.DEFAULT)
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    var position by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var duration by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.duration) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var isMuted by remember { mutableStateOf(playerVolume.value == 0f) }
    var previousVolume by remember { mutableFloatStateOf(if (playerVolume.value > 0f) playerVolume.value else 0.5f) }

    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }

    LaunchedEffect(currentSongId) {
        currentSongId?.let { songId ->
            if (lyricsCache.containsKey(songId)) {
                currentLyricsEntity = lyricsCache[songId]
                return@LaunchedEffect
            }
            isLoadingLyrics = true
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetchedLyrics = mediaMetadata?.let { entryPoint.lyricsHelper().getLyrics(it) }
                    val entity = LyricsEntity(songId, fetchedLyrics ?: LYRICS_NOT_FOUND)
                    database.query { upsert(entity) }
                    lyricsCache = lyricsCache.toMutableMap().apply { put(songId, entity) }
                    currentLyricsEntity = entity
                } catch (e: Exception) {
                    val entity = LyricsEntity(songId, LYRICS_NOT_FOUND)
                    lyricsCache = lyricsCache.toMutableMap().apply { put(songId, entity) }
                    currentLyricsEntity = entity
                } finally {
                    isLoadingLyrics = false
                }
            }
        }
    }

    LaunchedEffect(rawLyrics, currentSongId) {
        if (rawLyrics.isNullOrEmpty() || rawLyrics == LYRICS_NOT_FOUND) {
            lines = emptyList()
            return@LaunchedEffect
        }
        val parsed = if (rawLyrics.startsWith("[")) listOf(HEAD_LYRICS_ENTRY) + parseLyrics(rawLyrics)
        else rawLyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
        lines = parsed

        withContext(Dispatchers.IO) {
            val plainText = parsed.joinToString("\n") { it.text }
            val romanizedFull = TransliterationUtils.transliterate(plainText)
            if (romanizedFull != null) {
                val romLines = romanizedFull.lines()
                if (romLines.size == parsed.size || romLines.size > parsed.size - 5) {
                    lines = parsed.mapIndexed { i, entry -> entry.copy(romanizedText = romLines.getOrNull(i)) }
                }
            }
        }
    }

    val isSynced = remember(rawLyrics) { !rawLyrics.isNullOrEmpty() && rawLyrics.startsWith("G
@file:Suppress("DEPRECATION")

package com.arturo254.opentune.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.arturo254.opentune.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale


/**
 * Modelo de datos para representar un idioma
 */
data class LanguageItem(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val completionStatus: CompletionStatus = CompletionStatus.COMPLETE,
    val isSystemDefault: Boolean = false,
    val flag: String = "" // Emoji de bandera opcional
)

/**
 * Estado de completitud de las traducciones
 */
enum class CompletionStatus(val label: String, val color: @Composable () -> Color) {
    COMPLETE("", { Color.Transparent }),
    INCOMPLETE("Traducción incompleta", { MaterialTheme.colorScheme.tertiary }),
    BETA("BETA", { MaterialTheme.colorScheme.primary }),
    EXPERIMENTAL("Experimental", { MaterialTheme.colorScheme.secondary })
}

/**
 * Estados de la operación de cambio de idioma
 */
sealed class LanguageChangeState {
    object Idle : LanguageChangeState()
    object Changing : LanguageChangeState()
    object Success : LanguageChangeState()
    data class Error(val message: String) : LanguageChangeState()
}

/**
 * LocaleManager mejorado con mejor arquitectura, performance y UX
 */
class LocaleManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocaleManager"
        private const val PREF_NAME = "locale_preferences"
        private const val PREF_LANGUAGE_KEY = "selected_language"
        private const val SYSTEM_DEFAULT = "system_default"
        private const val RESTART_DELAY = 1200L
        private const val ANIMATION_DELAY = 300L

        @Volatile
        private var instance: LocaleManager? = null

        fun getInstance(context: Context): LocaleManager {
            return instance ?: synchronized(this) {
                instance ?: LocaleManager(context.applicationContext).also { instance = it }
            }
        }

        // Configuración de idiomas con banderas y mejor organización
        private val LANGUAGE_CONFIG = mapOf(
            "system_default" to LanguageConfig("Sistema", "", CompletionStatus.COMPLETE, "🌐"),
            "en" to LanguageConfig("English", "English", CompletionStatus.COMPLETE, "🇺🇸"),
            "es" to LanguageConfig("Spanish", "Español", CompletionStatus.COMPLETE, "🇪🇸"),
            "fr" to LanguageConfig("French", "Français", CompletionStatus.COMPLETE, "🇫🇷"),
            "de" to LanguageConfig("German", "Deutsch", CompletionStatus.COMPLETE, "🇩🇪"),
            "it" to LanguageConfig("Italian", "Italiano", CompletionStatus.COMPLETE, "🇮🇹"),
            "pt-BR" to LanguageConfig("Portuguese (Brazil)", "Português (Brasil)", CompletionStatus.COMPLETE, "🇧🇷"),
            "ru" to LanguageConfig("Russian", "Русский", CompletionStatus.COMPLETE, "🇷🇺"),
            "zh-CN" to LanguageConfig("Chinese (Simplified)", "简体中文", CompletionStatus.COMPLETE, "🇨🇳"),
            "zh-TW" to LanguageConfig("Chinese (Traditional)", "繁體中文", CompletionStatus.COMPLETE, "🇹🇼"),
            "ja" to LanguageConfig("Japanese", "日本語", CompletionStatus.COMPLETE, "🇯🇵"),
            "ko" to LanguageConfig("Korean", "한국어", CompletionStatus.COMPLETE, "🇰🇷"),
            "ar" to LanguageConfig("Arabic", "العربية", CompletionStatus.BETA, "🇸🇦"),
            "hi" to LanguageConfig("Hindi", "हिन्दी", CompletionStatus.BETA, "🇮🇳"),
            "th" to LanguageConfig("Thai", "ไทย", CompletionStatus.INCOMPLETE, "🇹🇭"),
            "vi" to LanguageConfig("Vietnamese", "Tiếng Việt", CompletionStatus.INCOMPLETE, "🇻🇳"),
            "tr" to LanguageConfig("Turkish", "Türkçe", CompletionStatus.BETA, "🇹🇷"),
            "pl" to LanguageConfig("Polish", "Polski", CompletionStatus.INCOMPLETE, "🇵🇱"),
            "nl" to LanguageConfig("Dutch", "Nederlands", CompletionStatus.INCOMPLETE, "🇳🇱"),
            "sv" to LanguageConfig("Swedish", "Svenska", CompletionStatus.INCOMPLETE, "🇸🇪"),
            "da" to LanguageConfig("Danish", "Dansk", CompletionStatus.INCOMPLETE, "🇩🇰"),
            "no" to LanguageConfig("Norwegian", "Norsk", CompletionStatus.INCOMPLETE, "🇳🇴"),
            "fi" to LanguageConfig("Finnish", "Suomi", CompletionStatus.EXPERIMENTAL, "🇫🇮"),
            "hu" to LanguageConfig("Hungarian", "Magyar", CompletionStatus.EXPERIMENTAL, "🇭🇺"),
            "cs" to LanguageConfig("Czech", "Čeština", CompletionStatus.EXPERIMENTAL, "🇨🇿"),
            "sk" to LanguageConfig("Slovak", "Slovenčina", CompletionStatus.EXPERIMENTAL, "🇸🇰"),
            "uk" to LanguageConfig("Ukrainian", "Українська", CompletionStatus.BETA, "🇺🇦"),
            "he" to LanguageConfig("Hebrew", "עברית", CompletionStatus.BETA, "🇮🇱"),
            "fa" to LanguageConfig("Persian", "فارسی", CompletionStatus.EXPERIMENTAL, "🇮🇷"),
            "bn" to LanguageConfig("Bengali", "বাংলা", CompletionStatus.EXPERIMENTAL, "🇧🇩"),
            "ta" to LanguageConfig("Tamil", "தமிழ்", CompletionStatus.EXPERIMENTAL, "🇱🇰"),
            "te" to LanguageConfig("Telugu", "తెలుగు", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "ml" to LanguageConfig("Malayalam", "മലയാളം", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "kn" to LanguageConfig("Kannada", "ಕನ್ನಡ", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "gu" to LanguageConfig("Gujarati", "ગુજરાતી", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "pa" to LanguageConfig("Punjabi", "ਪੰਜਾਬੀ", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "mr" to LanguageConfig("Marathi", "मराठी", CompletionStatus.EXPERIMENTAL, "🇮🇳"),
            "ne" to LanguageConfig("Nepali", "नेपाली", CompletionStatus.EXPERIMENTAL, "🇳🇵"),
            "si" to LanguageConfig("Sinhala", "සිංහල", CompletionStatus.EXPERIMENTAL, "🇱🇰"),
            "my" to LanguageConfig("Myanmar", "မြန်မာ", CompletionStatus.EXPERIMENTAL, "🇲🇲"),
            "km" to LanguageConfig("Khmer", "ខ្មែរ", CompletionStatus.EXPERIMENTAL, "🇰🇭"),
            "lo" to LanguageConfig("Lao", "ລາວ", CompletionStatus.EXPERIMENTAL, "🇱🇦"),
            "ka" to LanguageConfig("Georgian", "ქართული", CompletionStatus.EXPERIMENTAL, "🇬🇪"),
            "am" to LanguageConfig("Amharic", "አማርኛ", CompletionStatus.EXPERIMENTAL, "🇪🇹"),
            "id" to LanguageConfig("Indonesian", "Bahasa Indonesia", CompletionStatus.BETA, "🇮🇩"),
            "ms" to LanguageConfig("Malay", "Bahasa Melayu", CompletionStatus.EXPERIMENTAL, "🇲🇾"),
            "tl" to LanguageConfig("Filipino", "Filipino", CompletionStatus.EXPERIMENTAL, "🇵🇭"),
            "sw" to LanguageConfig("Swahili", "Kiswahili", CompletionStatus.EXPERIMENTAL, "🇰🇪"),
            "zu" to LanguageConfig("Zulu", "isiZulu", CompletionStatus.EXPERIMENTAL, "🇿🇦"),
            "af" to LanguageConfig("Afrikaans", "Afrikaans", CompletionStatus.EXPERIMENTAL, "🇿🇦")
        )

        private data class LanguageConfig(
            val displayName: String,
            val nativeName: String,
            val completionStatus: CompletionStatus,
            val flag: String
        )
    }

    private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _currentLanguage = MutableStateFlow(getSelectedLanguageCode())
    private val _changeState = MutableStateFlow<LanguageChangeState>(LanguageChangeState.Idle)

    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    val changeState: StateFlow<LanguageChangeState> = _changeState.asStateFlow()

    // Cache para mejorar performance
    private var _cachedLanguages: List<LanguageItem>? = null
    private var _cachedSystemLanguage: String? = null

    /** Obtiene el código de idioma seleccionado por el usuario */
    fun getSelectedLanguageCode(): String {
        return sharedPreferences.getString(PREF_LANGUAGE_KEY, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT
    }

    /** Obtiene el código de idioma efectivo (resuelve system_default) */
    fun getEffectiveLanguageCode(): String {
        val saved = getSelectedLanguageCode()
        return if (saved == SYSTEM_DEFAULT) getSystemLanguageCode() else saved
    }

    /** Obtiene el código de idioma del sistema con cache */
    private fun getSystemLanguageCode(): String {
        return _cachedSystemLanguage ?: run {
            val systemCode = try {
                val localeList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ConfigurationCompat.getLocales(Resources.getSystem().configuration)
                } else {
                    LocaleListCompat.create(Locale.getDefault())
                }

                val systemLocale = if (localeList.isEmpty) Locale.getDefault() else localeList[0]
                    ?: Locale.getDefault()
                val language = systemLocale.language
                val country = systemLocale.country

                when {
                    language == "zh" && country.isNotEmpty() -> {
                        when (country) {
                            "CN" -> "zh-CN"
                            "TW", "HK" -> "zh-TW"
                            else -> "zh-CN"
                        }
                    }
                    language == "pt" && country == "BR" -> "pt-BR"
                    else -> language
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo idioma del sistema", e)
                "en"
            }
            _cachedSystemLanguage = systemCode
            systemCode
        }
    }

    /** Obtiene la lista de idiomas disponibles con cache */
    fun getAvailableLanguages(): List<LanguageItem> {
        return _cachedLanguages ?: run {
            val systemLanguageCode = getSystemLanguageCode()
            val systemDisplayName = LANGUAGE_CONFIG[systemLanguageCode]?.displayName ?: systemLanguageCode

            val languages = LANGUAGE_CONFIG.map { (code, config) ->
                LanguageItem(
                    code = code,
                    displayName = if (code == SYSTEM_DEFAULT) {
                        "Sistema ($systemDisplayName)"
                    } else {
                        config.displayName
                    },
                    nativeName = if (code == SYSTEM_DEFAULT) {
                        systemDisplayName
                    } else {
                        config.nativeName
                    },
                    completionStatus = config.completionStatus,
                    isSystemDefault = code == SYSTEM_DEFAULT,
                    flag = config.flag
                )
            }.sortedWith(
                compareBy<LanguageItem> { !it.isSystemDefault }
                    .thenBy { it.completionStatus.ordinal }
                    .thenBy { it.displayName }
            )

            _cachedLanguages = languages
            languages
        }
    }

    /** Actualiza el idioma con mejor manejo de estados */
    suspend fun updateLanguage(languageCode: String): Boolean {
        if (_changeState.value is LanguageChangeState.Changing) {
            return false // Prevenir múltiples cambios simultáneos
        }

        return try {
            _changeState.value = LanguageChangeState.Changing
            Log.d(TAG, "Cambiando idioma a: $languageCode")

            delay(ANIMATION_DELAY) // Tiempo para animaciones

            // Guardar preferencia
            val editor = sharedPreferences.edit()
            editor.putString(PREF_LANGUAGE_KEY, languageCode)
            val saved = editor.commit() // Usar commit para operación síncrona

            if (!saved) {
                throw Exception("No se pudo guardar la preferencia")
            }

            // Actualizar estados
            _currentLanguage.value = languageCode

            // Aplicar configuración de idioma
            val effectiveLanguageCode = if (languageCode == SYSTEM_DEFAULT) {
                getSystemLanguageCode()
            } else {
                languageCode
            }

            val locale = createLocaleFromCode(effectiveLanguageCode)
            applyLocaleToApp(locale)

            _changeState.value = LanguageChangeState.Success

            Log.d(TAG, "Idioma actualizado exitosamente a: $languageCode (efectivo: $effectiveLanguageCode)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando idioma a $languageCode", e)
            _changeState.value = LanguageChangeState.Error(e.message ?: "Error desconocido")
            false
        }
    }

    /** Limpia el cache cuando sea necesario */
    fun clearCache() {
        _cachedLanguages = null
        _cachedSystemLanguage = null
    }

    /** Aplica la configuración de idioma a la aplicación */
    private fun applyLocaleToApp(locale: Locale) {
        try {
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
                config.setLocale(locale)
            } else {
                config.locale = locale
            }

            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando configuración de idioma", e)
        }
    }

    /** Aplica el idioma a un contexto específico */
    fun applyLocaleToContext(baseContext: Context): Context {
        return try {
            val languageCode = getEffectiveLanguageCode()
            val locale = createLocaleFromCode(languageCode)

            Locale.setDefault(locale)
            val config = Configuration(baseContext.resources.configuration)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale)
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
                baseContext.createConfigurationContext(config)
            } else {
                config.locale = locale
                @Suppress("DEPRECATION")
                baseContext.resources.updateConfiguration(
                    config,
                    baseContext.resources.displayMetrics
                )
                baseContext
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando idioma al contexto", e)
            baseContext
        }
    }

    /** Crea un objeto Locale desde un código de idioma */
    private fun createLocaleFromCode(languageCode: String): Locale {
        return try {
            when {
                languageCode == "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                languageCode == "zh-TW" -> Locale.TRADITIONAL_CHINESE
                languageCode.contains("-") -> {
                    val parts = languageCode.split("-", limit = 2)
                    if (parts.size >= 2) {
                        Locale(parts[0], parts[1])
                    } else {
                        Locale(parts[0])
                    }
                }
                else -> Locale(languageCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creando Locale para: $languageCode", e)
            Locale(languageCode)
        }
    }

    /** Reinicia la aplicación con mejor UX */
    fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                Handler(Looper.getMainLooper()).postDelayed({
                    context.startActivity(it)
                    if (context is Activity) {
                        context.finish()
                        context.overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                    }
                }, RESTART_DELAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reiniciando aplicación", e)
        }
    }

    /** Resetea el estado después del reinicio */
    fun resetChangeState() {
        _changeState.value = LanguageChangeState.Idle
    }
}

/** Composable principal mejorado siguiendo Material Design 3 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val localeManager = remember { LocaleManager.getInstance(context) }
    val hapticFeedback = LocalHapticFeedback.current

    val currentLanguage by localeManager.currentLanguage.collectAsState()
    val changeState by localeManager.changeState.collectAsState()
    val availableLanguages by remember { derivedStateOf { localeManager.getAvailableLanguages() } }

    var selectedLanguageCode by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Manejo mejorado del cambio de idioma
    LaunchedEffect(selectedLanguageCode) {
        selectedLanguageCode?.let { languageCode ->
            if (localeManager.updateLanguage(languageCode)) {
                localeManager.restartApp(context)
            }
            selectedLanguageCode = null
        }
    }

    // Auto-scroll mejorado
    LaunchedEffect(availableLanguages, currentLanguage) {
        val selectedIndex = availableLanguages.indexOfFirst { it.code == currentLanguage }
        if (selectedIndex != -1) {
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -100 // Mejor posicionamiento
            )
        }
    }

    // Reset del estado al cerrar
    DisposableEffect(Unit) {
        onDispose {
            localeManager.resetChangeState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header mejorado con icono
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.language),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // Estado de cambio mejorado
            when (changeState) {
                is LanguageChangeState.Changing -> {
                    LanguageChangeIndicator(
                        text = "Aplicando cambios...",
                        showProgress = true
                    )
                }
                is LanguageChangeState.Success -> {
                    LanguageChangeIndicator(
                        text = "¡Listo! Reiniciando aplicación...",
                        showProgress = false,
                        icon = Icons.Default.Check
                    )
                }
                is LanguageChangeState.Error -> {
                    LanguageChangeIndicator(
                        text = "Error: ${(changeState as LanguageChangeState.Error).message}",
                        showProgress = false,
                        isError = true
                    )
                }
                else -> Unit
            }

            // Lista mejorada
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = availableLanguages,
                    key = { it.code }
                ) { language ->
                    val isSelected = language.code == currentLanguage
                    val isEnabled = changeState !is LanguageChangeState.Changing

                    LanguageItemCard(
                        language = language,
                        isSelected = isSelected,
                        isEnabled = isEnabled,
                        onClick = {
                            if (isEnabled && !isSelected) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedLanguageCode = language.code
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Indicador de cambio de idioma mejorado */
@Composable
private fun LanguageChangeIndicator(
    text: String,
    showProgress: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isError: Boolean = false
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { -it / 2 }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it / 2 }
        ) + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            colors = if (isError) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    showProgress -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    icon != null -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Card mejorada para elementos de idioma */
@Composable
private fun LanguageItemCard(
    language: LanguageItem,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .animateContentSize()
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bandera/Emoji
            if (language.flag.isNotEmpty()) {
                Text(
                    text = language.flag,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.size(32.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.width(16.dp))
            }

            // Información del idioma
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (language.nativeName.isNotEmpty() &&
                    language.nativeName != language.displayName &&
                    !language.isSystemDefault
                ) {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Badge de estado
            if (language.completionStatus != CompletionStatus.COMPLETE) {
                val statusColor = language.completionStatus.color() // Invocar solo una vez

                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = language.completionStatus.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor.copy(alpha = 0.12f),
                        labelColor = statusColor
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = statusColor.copy(alpha = 0.2f)
                    )
                )
            }


            Spacer(modifier = Modifier.width(12.dp))

            // Radio button mejorado
            RadioButton(
                selected = isSelected,
                onClick = null, // Manejado por el Card
                enabled = isEnabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            )
        }
    }
}

/** Composable para integrar con las preferencias - Mejorado */
@Composable
fun LanguagePreference(
    modifier: Modifier = Modifier
) {
    var showLanguageSelector by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val localeManager = remember { LocaleManager.getInstance(context) }
    val currentLanguage by localeManager.currentLanguage.collectAsState()
    val changeState by localeManager.changeState.collectAsState()

    val currentLanguageDisplay = remember(currentLanguage) {
        val selectedCode = localeManager.getSelectedLanguageCode()
        localeManager.getAvailableLanguages()
            .find { it.code == selectedCode }
            ?.let { language ->
                if (language.isSystemDefault) {
                    language.displayName
                } else {
                    "${language.displayName} ${language.flag}".trim()
                }
            } ?: selectedCode
    }

    val isChanging = changeState is LanguageChangeState.Changing

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .clickable(enabled = !isChanging) {
                showLanguageSelector = true
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isChanging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.translate),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isChanging) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (isChanging) {
                        "Cambiando idioma..."
                    } else {
                        currentLanguageDisplay
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isChanging) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (isChanging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Cambiar idioma",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showLanguageSelector) {
        LanguageSelector(
            onDismiss = { showLanguageSelector = false }
        )
    }
}

/** Application class mejorada con mejor manejo de errores */
abstract class LocaleAwareApplication : android.app.Application() {

    private val localeManager by lazy { LocaleManager.getInstance(this) }

    override fun attachBaseContext(base: Context) {
        try {
            val updatedContext = LocaleManager.getInstance(base).applyLocaleToContext(base)
            super.attachBaseContext(updatedContext)
        } catch (e: Exception) {
            Log.e("LocaleAwareApplication", "Error aplicando idioma en attachBaseContext", e)
            super.attachBaseContext(base)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Inicializar LocaleManager
            localeManager
            Log.d("LocaleAwareApplication", "LocaleManager inicializado correctamente")
        } catch (e: Exception) {
            Log.e("LocaleAwareApplication", "Error inicializando LocaleManager", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Limpiar cache cuando cambie la configuración del sistema
        localeManager.clearCache()
    }
}

/** Composable adicional para mostrar información del idioma actual */
@Composable
fun CurrentLanguageInfo(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localeManager = remember { LocaleManager.getInstance(context) }
    val currentLanguage by localeManager.currentLanguage.collectAsState()

    val languageInfo = remember(currentLanguage) {
        localeManager.getAvailableLanguages()
            .find { it.code == currentLanguage }
    }

    languageInfo?.let { language ->
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (language.flag.isNotEmpty()) {
                    Text(
                        text = language.flag,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.size(32.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Text(
                        text = "Idioma actual",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (language.nativeName.isNotEmpty() &&
                        language.nativeName != language.displayName &&
                        !language.isSystemDefault) {
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
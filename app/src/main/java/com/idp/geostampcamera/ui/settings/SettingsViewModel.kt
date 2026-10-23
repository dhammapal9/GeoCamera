package com.idp.geostampcamera.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.BuildConfig
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _rows = MutableStateFlow<List<Pair<String, List<SettingsRow>>>>(emptyList())
    val rows: StateFlow<List<Pair<String, List<SettingsRow>>>> = _rows

    private val refreshTrigger = MutableStateFlow(0)

    init {
        val general = combine(
            settings.themeFlow(),
            settings.photoQualityFlow(),
            settings.coordFormatFlow(),
            settings.mapTypeFlow()
        ) { theme, quality, coord, mapType -> arrayOf(theme, quality, coord, mapType) }
        val camera = combine(
            settings.keepOriginalFlow(),
            settings.shutterSoundFlow(),
            settings.volumeActionFlow()
        ) { keep, shutter, volume -> arrayOf(keep, shutter, volume) }
        val stamp = combine(
            settings.stampOrientationFlow(),
            settings.showAppLogoOnStampFlow(),
            settings.showCameraIndicatorFlow()
        ) { orient, logo, indicator -> arrayOf(orient, logo, indicator) }
        val report = combine(
            settings.emailAfterCaptureFlow(),
            settings.shareAfterCaptureFlow()
        ) { email, share -> arrayOf(email, share) }
        val storage = combine(
            settings.storageLocationFlow(),
            settings.isProFlow(),
            settings.driveAccountEmailFlow()
        ) { loc, pro, driveEmail -> arrayOf(loc, pro, driveEmail ?: "") }

        val allSettings = combine(general, camera, stamp, report, storage) { g, c, s, r, st ->
            arrayOf(g, c, s, r, st)
        }

        combine(allSettings, refreshTrigger) { all, _ ->
            val g = all[0]
            val c = all[1]
            val s = all[2]
            val r = all[3]
            val st = all[4]
            buildRows(
                theme = g[0] as String,
                quality = g[1] as String,
                coord = g[2] as String,
                mapType = g[3] as String,
                keepOriginal = c[0] as Boolean,
                shutter = c[1] as Boolean,
                volume = c[2] as String,
                stampOrientation = s[0] as String,
                showAppLogo = s[1] as Boolean,
                showCameraIndicator = s[2] as Boolean,
                email = r[0] as Boolean,
                share = r[1] as Boolean,
                storageLocation = st[0] as String,
                isPro = st[1] as Boolean,
                driveAccountEmail = (st[2] as String).takeIf { it.isNotBlank() }
            )
        }.onEach { _rows.value = it }.launchIn(viewModelScope)
    }

    fun refresh() {
        refreshTrigger.value += 1
    }

    fun toggle(row: SettingsRow, value: Boolean) {
        viewModelScope.launch {
            when (row.id) {
                SettingsAction.KEEP_ORIGINAL -> settings.setKeepOriginal(value)
                SettingsAction.SHUTTER_SOUND -> settings.setShutterSound(value)
                SettingsAction.SHOW_APP_LOGO -> settings.setShowAppLogoOnStamp(value)
                SettingsAction.SHOW_CAMERA_INDICATOR -> settings.setShowCameraIndicator(value)
                SettingsAction.EMAIL_AFTER_CAPTURE -> settings.setEmailAfterCapture(value)
                SettingsAction.SHARE_AFTER_CAPTURE -> settings.setShareAfterCapture(value)
                else -> {}
            }
        }
    }

    fun setChoice(id: SettingsAction, value: String) {
        viewModelScope.launch {
            when (id) {
                SettingsAction.THEME -> {
                    settings.setTheme(value)
                    val mode = when (value) {
                        SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
                SettingsAction.PHOTO_QUALITY -> settings.setPhotoQuality(value)
                SettingsAction.COORD_FORMAT -> settings.setCoordFormat(value)
                SettingsAction.MAP_TYPE -> settings.setMapType(value)
                SettingsAction.VOLUME_ACTION -> settings.setVolumeAction(value)
                SettingsAction.STAMP_ORIENTATION -> settings.setStampOrientation(value)
                SettingsAction.STORAGE_LOCATION -> {
                    settings.setStorageLocation(value)
                    if (value == SettingsRepository.STORAGE_PHONE) {
                        settings.clearDriveAccount()
                    }
                }
                else -> {}
            }
        }
    }

    fun setDriveAccount(email: String) {
        viewModelScope.launch {
            settings.setDriveAccountEmail(email)
            settings.setStorageLocation(SettingsRepository.STORAGE_DRIVE)
        }
    }

    private fun getLocalizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val config = android.content.res.Configuration(context.resources.configuration)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locales.get(0)
        }
        return context.createConfigurationContext(config)
    }

    private fun getLanguageLabel(tag: String, defaultLabel: String): String {
        if (tag.isBlank()) return defaultLabel
        val primaryCode = tag.split("-")[0].lowercase()
        return when (primaryCode) {
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "pt" -> "Português"
            "it" -> "Italiano"
            "ru" -> "Русский"
            "ja" -> "日本語"
            "zh" -> if (tag.contains("TW", ignoreCase = true)) "中文 (繁體)" else "中文 (简体)"
            "ko" -> "한국어"
            "ar" -> "العربية"
            "in", "id" -> "Bahasa Indonesia"
            "tr" -> "Türkçe"
            "vi" -> "Tiếng Việt"
            "th" -> "ไทย"
            "pl" -> "Polski"
            "nl" -> "Nederlands"
            "hi" -> "हिन्दी"
            "bn" -> "বাংলা"
            "gu" -> "ગુજરાતી"
            "kn" -> "ಕನ್ನಡ"
            "mr" -> "मराठी"
            "ta" -> "தமிழ்"
            "te" -> "తెలుగు"
            "ur" -> "اردو"
            "uk" -> "Українська"
            "ro" -> "Română"
            "el" -> "Ελληνικά"
            "cs" -> "Čeština"
            "hu" -> "Magyar"
            "sv" -> "Svenska"
            "da" -> "Dansk"
            "fi" -> "Suomi"
            "nb" -> "Norsk Bokmål"
            "sk" -> "Slovenčina"
            "bg" -> "Български"
            "hr" -> "Hrvatski"
            "sr" -> "Српски"
            "sl" -> "Slovenščina"
            "lt" -> "Lietuvių"
            "lv" -> "Latviešu"
            "et" -> "Eesti"
            "iw", "he" -> "עברית"
            "fa" -> "فارسی"
            "ms" -> "Bahasa Melayu"
            "tl", "fil" -> "Tagalog"
            "sw" -> "Kiswahili"
            "af" -> "Afrikaans"
            "am" -> "አማርኛ"
            "ne" -> "नेपाली"
            "si" -> "සිංහල"
            "km" -> "ភាសាខ្មែរ"
            "lo" -> "ພາສາລາວ"
            "my" -> "မြန်မာ"
            "ka" -> "ქართული"
            "hy" -> "Հայերեն"
            "az" -> "Azərbaycan"
            "kk" -> "Қазақ тілі"
            "uz" -> "Oʻzbekcha"
            "mn" -> "Монгол"
            "pa" -> "ਪੰਜਾਬੀ"
            "ml" -> "മലയാളം"
            "or" -> "ଓଡ଼ିଆ"
            "as" -> "অসমীয়া"
            "ca" -> "Català"
            "gl" -> "Galego"
            "eu" -> "Euskara"
            "is" -> "Íslenska"
            "sq" -> "Shqip"
            "mk" -> "Македонски"
            "be" -> "Беларуская"
            "bs" -> "Bosanski"
            "cy" -> "Cymraeg"
            "ga" -> "Gaeilge"
            "jv" -> "Basa Jawa"
            "su" -> "Basa Sunda"
            "eo" -> "Esperanto"
            "mt" -> "Malti"
            "lb" -> "Lëtzebuergesch"
            "gd" -> "Gàidhlig"
            "fy" -> "Frysk"
            "ht" -> "Kreyòl Ayisyen"
            "mg" -> "Malagasy"
            "so" -> "Soomaali"
            "sn" -> "chiShona"
            "st" -> "Sesotho"
            "xh" -> "isiXhosa"
            "ig" -> "Asụsụ Igbo"
            "rw" -> "Ikinyarwanda"
            "ny" -> "Chichewa"
            "om" -> "Afaan Oromoo"
            "ti" -> "ትግርኛ"
            "tg" -> "Тоҷикӣ"
            "ky" -> "Кыргызча"
            "tk" -> "Türkmen dili"
            "ps" -> "پښتو"
            "sd" -> "سنڌي"
            "ug" -> "ئۇيغۇرچە"
            "bo" -> "བོད་སྐད།"
            "ceb" -> "Cebuano"
            "haw" -> "ʻŌlelo Hawaiʻi"
            "mi" -> "Te Reo Māori"
            "sm" -> "Gagana Samoa"
            "la" -> "Latina"
            else -> tag
        }
    }

    private fun buildRows(
        theme: String, quality: String, coord: String, mapType: String,
        keepOriginal: Boolean, shutter: Boolean, volume: String,
        stampOrientation: String, showAppLogo: Boolean, showCameraIndicator: Boolean,
        email: Boolean, share: Boolean,
        storageLocation: String, isPro: Boolean,
        driveAccountEmail: String?
    ): List<Pair<String, List<SettingsRow>>> {
        val localizedCtx = getLocalizedContext()
        fun s(@androidx.annotation.StringRes id: Int) = localizedCtx.getString(id)

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) currentLocales.get(0)?.toLanguageTag().orEmpty() else ""

        val general = listOf(
            SettingsRow(
                id = SettingsAction.LANGUAGE,
                title = s(R.string.settings_language),
                iconRes = R.drawable.ic_text,
                valueLabel = getLanguageLabel(currentTag, s(R.string.settings_theme_system))
            ),
            SettingsRow(
                id = SettingsAction.THEME,
                title = s(R.string.settings_theme),
                iconRes = R.drawable.ic_settings,
                valueLabel = when (theme) {
                    SettingsRepository.THEME_LIGHT -> s(R.string.settings_theme_light)
                    SettingsRepository.THEME_DARK -> s(R.string.settings_theme_dark)
                    else -> s(R.string.settings_theme_system)
                },
                choices = listOf(
                    SettingsRepository.THEME_SYSTEM to s(R.string.settings_theme_system),
                    SettingsRepository.THEME_LIGHT to s(R.string.settings_theme_light),
                    SettingsRepository.THEME_DARK to s(R.string.settings_theme_dark)
                ),
                currentValue = theme
            )
        )
        val cameraRows = listOf(
            SettingsRow(
                id = SettingsAction.PHOTO_QUALITY, title = s(R.string.settings_photo_quality),
                iconRes = R.drawable.ic_camera,
                valueLabel = when (quality) {
                    SettingsRepository.QUALITY_HIGH -> s(R.string.settings_quality_high)
                    SettingsRepository.QUALITY_MEDIUM -> s(R.string.settings_quality_medium)
                    else -> s(R.string.settings_quality_low)
                },
                choices = listOf(
                    SettingsRepository.QUALITY_HIGH to s(R.string.settings_quality_high),
                    SettingsRepository.QUALITY_MEDIUM to s(R.string.settings_quality_medium),
                    SettingsRepository.QUALITY_LOW to s(R.string.settings_quality_low)
                ),
                currentValue = quality
            ),
            SettingsRow(
                id = SettingsAction.VOLUME_ACTION, title = s(R.string.settings_volume_button),
                iconRes = R.drawable.ic_more,
                valueLabel = when (volume) {
                    SettingsRepository.VOLUME_CAPTURE -> s(R.string.settings_volume_capture)
                    SettingsRepository.VOLUME_ZOOM -> s(R.string.settings_volume_zoom)
                    else -> s(R.string.settings_volume_none)
                },
                choices = listOf(
                    SettingsRepository.VOLUME_CAPTURE to s(R.string.settings_volume_capture),
                    SettingsRepository.VOLUME_ZOOM to s(R.string.settings_volume_zoom),
                    SettingsRepository.VOLUME_NONE to s(R.string.settings_volume_none)
                ),
                currentValue = volume
            ),
            SettingsRow(id = SettingsAction.SHUTTER_SOUND, title = s(R.string.settings_shutter_sound),
                iconRes = R.drawable.ic_sound, toggle = shutter),
            SettingsRow(id = SettingsAction.KEEP_ORIGINAL, title = s(R.string.settings_save_original),
                iconRes = R.drawable.ic_gallery, toggle = keepOriginal)
        )
        val stampRows = listOf(
            SettingsRow(id = SettingsAction.STAMP_ORIENTATION, title = s(R.string.settings_stamp_orientation),
                iconRes = R.drawable.ic_level,
                valueLabel = when (stampOrientation) {
                    SettingsRepository.STAMP_PORTRAIT -> s(R.string.settings_stamp_orientation_portrait)
                    SettingsRepository.STAMP_LANDSCAPE -> s(R.string.settings_stamp_orientation_landscape)
                    else -> s(R.string.settings_stamp_orientation_auto)
                },
                choices = listOf(
                    SettingsRepository.STAMP_AUTO to s(R.string.settings_stamp_orientation_auto),
                    SettingsRepository.STAMP_PORTRAIT to s(R.string.settings_stamp_orientation_portrait),
                    SettingsRepository.STAMP_LANDSCAPE to s(R.string.settings_stamp_orientation_landscape)
                ),
                currentValue = stampOrientation),
            SettingsRow(id = SettingsAction.SHOW_APP_LOGO, title = s(R.string.settings_show_app_logo),
                iconRes = R.drawable.ic_gallery, toggle = showAppLogo),
            SettingsRow(id = SettingsAction.SHOW_CAMERA_INDICATOR, title = s(R.string.settings_show_camera_indicator),
                iconRes = R.drawable.ic_camera, toggle = showCameraIndicator),
            SettingsRow(id = SettingsAction.COORD_FORMAT, title = s(R.string.settings_coord_format),
                iconRes = R.drawable.ic_location,
                valueLabel = if (coord == SettingsRepository.COORD_DMS) s(R.string.editor_coord_dms) else s(R.string.editor_coord_decimal),
                choices = listOf(
                    SettingsRepository.COORD_DECIMAL to s(R.string.editor_coord_decimal),
                    SettingsRepository.COORD_DMS to s(R.string.editor_coord_dms)
                ),
                currentValue = coord),
            SettingsRow(id = SettingsAction.MAP_TYPE, title = s(R.string.settings_map_type_default),
                iconRes = R.drawable.ic_map,
                valueLabel = when (mapType) {
                    SettingsRepository.MAP_SATELLITE -> s(R.string.map_type_satellite)
                    SettingsRepository.MAP_TERRAIN -> s(R.string.map_type_terrain)
                    SettingsRepository.MAP_HYBRID -> s(R.string.map_type_hybrid)
                    else -> s(R.string.map_type_normal)
                },
                choices = listOf(
                    SettingsRepository.MAP_NORMAL to s(R.string.map_type_normal),
                    SettingsRepository.MAP_SATELLITE to s(R.string.map_type_satellite),
                    SettingsRepository.MAP_TERRAIN to s(R.string.map_type_terrain),
                    SettingsRepository.MAP_HYBRID to s(R.string.map_type_hybrid)
                ),
                currentValue = mapType),
            SettingsRow(id = SettingsAction.FILENAME_BUILDER, title = s(R.string.camera_file_name),
                iconRes = R.drawable.ic_file_name),
            SettingsRow(id = SettingsAction.SAVED_LOCATIONS, title = s(R.string.saved_locations_title),
                iconRes = R.drawable.ic_location)
        )
        val reportRows = listOf(
            SettingsRow(id = SettingsAction.EMAIL_AFTER_CAPTURE, title = s(R.string.settings_email_photo),
                iconRes = R.drawable.ic_share, toggle = email),
            SettingsRow(id = SettingsAction.SHARE_AFTER_CAPTURE, title = s(R.string.settings_share_after_capture),
                iconRes = R.drawable.ic_share, toggle = share),
            SettingsRow(id = SettingsAction.PHOTO_REPORT, title = s(R.string.settings_photo_report),
                iconRes = R.drawable.ic_report)
        )
        val driveConnected = storageLocation == SettingsRepository.STORAGE_DRIVE && !driveAccountEmail.isNullOrBlank()
        val storageValueLabel = when {
            storageLocation != SettingsRepository.STORAGE_DRIVE -> s(R.string.settings_storage_phone)
            driveConnected -> localizedCtx.getString(R.string.settings_storage_drive_connected_as, driveAccountEmail)
            else -> s(R.string.settings_storage_drive_status)
        }
        val storageRows = listOf(
            SettingsRow(id = SettingsAction.STORAGE_LOCATION, title = s(R.string.settings_storage_location),
                iconRes = R.drawable.ic_gallery,
                valueLabel = storageValueLabel,
                choices = listOf(
                    SettingsRepository.STORAGE_PHONE to s(R.string.settings_storage_phone),
                    SettingsRepository.STORAGE_DRIVE to s(R.string.settings_storage_drive)
                ),
                currentValue = storageLocation)
        )
        val help = listOf(
            SettingsRow(id = SettingsAction.HOW_TO_USE, title = s(R.string.settings_how_to_use), iconRes = R.drawable.ic_info),
            SettingsRow(id = SettingsAction.FAQ, title = s(R.string.settings_faq), iconRes = R.drawable.ic_info),
            SettingsRow(id = SettingsAction.VIDEO_TUTORIALS, title = s(R.string.settings_video_tutorials), iconRes = R.drawable.ic_info)
        )
        val about = listOf(
            /*SettingsRow(id = SettingsAction.GO_PREMIUM,
                title = if (isPro) s(R.string.settings_youre_premium) else s(R.string.settings_go_premium),
                iconRes = R.drawable.ic_check),*/
            SettingsRow(id = SettingsAction.RATE_APP, title = s(R.string.settings_rate_app), iconRes = R.drawable.ic_check),
            SettingsRow(id = SettingsAction.SHARE_APP, title = s(R.string.settings_share_app), iconRes = R.drawable.ic_share),
            SettingsRow(id = SettingsAction.MORE_APPS, title = s(R.string.settings_more_apps), iconRes = R.drawable.ic_more),
            SettingsRow(id = SettingsAction.PRIVACY_POLICY, title = s(R.string.settings_privacy_policy), iconRes = R.drawable.ic_info),
            SettingsRow(id = SettingsAction.APP_VERSION, title = s(R.string.settings_app_version),
                iconRes = R.drawable.ic_info, valueLabel = BuildConfig.VERSION_NAME)
        )
        return listOf(
            s(R.string.settings_section_general) to general,
            s(R.string.settings_section_camera) to cameraRows,
            s(R.string.settings_section_stamp) to stampRows,
            s(R.string.settings_section_report) to reportRows,
            s(R.string.settings_section_storage) to storageRows,
            s(R.string.settings_section_help) to help,
            s(R.string.settings_section_about) to about
        )
    }
}

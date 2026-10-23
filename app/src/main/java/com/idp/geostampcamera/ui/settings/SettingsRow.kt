package com.idp.geostampcamera.ui.settings

enum class SettingsAction {
    THEME, PHOTO_QUALITY, COORD_FORMAT, MAP_TYPE, KEEP_ORIGINAL, SHUTTER_SOUND, VOLUME_ACTION,
    PRIVACY_POLICY, HELP, APP_VERSION, DEFAULT_TEMPLATE,
    LANGUAGE, STAMP_ORIENTATION, SHOW_APP_LOGO, SHOW_CAMERA_INDICATOR,
    EMAIL_AFTER_CAPTURE, SHARE_AFTER_CAPTURE, PHOTO_REPORT, STORAGE_LOCATION,
    FILENAME_BUILDER, SAVED_LOCATIONS, HOW_TO_USE, FAQ, VIDEO_TUTORIALS,
    RATE_APP, SHARE_APP, MORE_APPS, GO_PREMIUM
}

data class SettingsRow(
    val id: SettingsAction,
    val title: String,
    val summary: String? = null,
    val iconRes: Int,
    val valueLabel: String? = null,
    val toggle: Boolean? = null,
    val choices: List<Pair<String, String>>? = null,
    val currentValue: String? = null,
    val isSectionHeader: Boolean = false
)

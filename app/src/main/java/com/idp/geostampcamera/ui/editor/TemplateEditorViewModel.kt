package com.idp.geostampcamera.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.TemplateRepository
import com.idp.geostampcamera.domain.model.StampTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    private val repository: TemplateRepository
) : ViewModel() {

    private val _editing = MutableStateFlow<StampTemplate?>(null)
    val editing: StateFlow<StampTemplate?> = _editing

    fun load(id: String?) {
        viewModelScope.launch {
            val template = id?.let { repository.get(it) }
            _editing.value = template?.copy(
                id = if (template.isBuiltIn) UUID.randomUUID().toString() else template.id,
                isBuiltIn = false,
                name = if (template.isBuiltIn) template.name + " custom" else template.name
            ) ?: blank()
        }
    }

    fun update(mutator: (StampTemplate) -> StampTemplate) {
        _editing.value?.let { current -> _editing.value = mutator(current) }
    }

    suspend fun save(): StampTemplate? {
        val current = _editing.value ?: return null
        if (current.name.isBlank()) return null
        return repository.upsert(current)
    }

    private fun blank(): StampTemplate = StampTemplate(
        id = UUID.randomUUID().toString(),
        name = "My template",
        type = StampTemplate.TemplateType.CUSTOM,
        layout = StampTemplate.LayoutStyle.HORIZONTAL,
        backgroundArgb = android.graphics.Color.parseColor("#B2000000"),
        backgroundAlpha = 178,
        textArgb = android.graphics.Color.WHITE,
        accentArgb = android.graphics.Color.parseColor("#F2B134"),
        fontScale = 1f,
        cornerRadiusDp = 8,
        borderDp = 0,
        borderArgb = android.graphics.Color.TRANSPARENT,
        mapSize = StampTemplate.MapSize.MEDIUM,
        coordinateFormat = StampTemplate.CoordFormat.DECIMAL,
        dateFormat = com.idp.geostampcamera.util.DateFormatter.FORMAT_FULL,
        timeFormat = "hh:mm a",
        timezoneId = null,
        tempUnit = StampTemplate.TempUnit.CELSIUS,
        altitudeUnit = StampTemplate.AltitudeUnit.METERS,
        showMap = true, showShortAddress = true, showFullAddress = true, showCoordinates = true,
        showPlusCode = false, showDateTime = true, showAltitude = false, showAccuracy = false,
        showSpeed = false, showBearing = false, showCompass = false, showMagnetic = false,
        showWeather = false, showLogo = false, showNote = true,
        note = "Captured by GeoStamp Camera",
        hashtag = "", projectName = "", businessName = "", reference = "",
        logoUri = null, isBuiltIn = false
    )
}

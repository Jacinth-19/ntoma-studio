package com.ntoma.studio.ui.screens.tryon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.DesignCustomization
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.repository.GenderHint
import com.ntoma.studio.domain.repository.TryOnRequest
import com.ntoma.studio.domain.repository.TryOnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TryOnUiState(
    val personPath: String? = null,
    val gender: GenderHint = GenderHint.AUTO,
    val fabricId: Long? = null,
    val styleId: String? = null,
    val fabrics: List<Fabric> = emptyList(),
    val styles: List<DressStyle> = emptyList(),
    val tryOn: TryOnState = TryOnState.Idle,
    val running: Boolean = false,
    val quotaLeft: Int = 2,
    val premium: Boolean = false,
    val paywallOpen: Boolean = false,
    val rewardedOpen: Boolean = false,
    val customization: DesignCustomization = DesignCustomization(),
)

class TryOnViewModel(
    private val container: AppContainer,
    initialFabricId: Long?,
    initialStyleId: String?,
    initialPersonPath: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TryOnUiState(
            fabricId = initialFabricId,
            styleId = initialStyleId,
            personPath = initialPersonPath,
        ),
    )
    val state: StateFlow<TryOnUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.fabricAnalysis.observeAll().collect { list ->
                _state.value = _state.value.copy(
                    fabrics = list,
                    fabricId = _state.value.fabricId ?: list.firstOrNull()?.id,
                )
            }
        }
        viewModelScope.launch {
            container.dressStyles.observeAll().collect { list ->
                _state.value = _state.value.copy(
                    styles = list,
                    styleId = _state.value.styleId ?: list.firstOrNull()?.id,
                )
            }
        }
        viewModelScope.launch {
            container.entitlements.remainingTryOnsThisMonth().collect { left ->
                _state.value = _state.value.copy(quotaLeft = left)
            }
        }
        viewModelScope.launch {
            container.settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(premium = prefs.premiumEnabled)
            }
        }
    }

    fun setPerson(path: String) {
        _state.value = _state.value.copy(personPath = path, tryOn = TryOnState.Idle)
    }

    fun setGender(g: GenderHint) {
        _state.value = _state.value.copy(gender = g)
    }

    fun setFabric(id: Long) {
        _state.value = _state.value.copy(fabricId = id, tryOn = TryOnState.Idle)
    }

    fun setStyle(id: String) {
        _state.value = _state.value.copy(styleId = id, tryOn = TryOnState.Idle)
    }

    fun setCustomization(customization: DesignCustomization) {
        _state.value = _state.value.copy(customization = customization, tryOn = TryOnState.Idle)
    }

    fun dismissPaywall() {
        _state.value = _state.value.copy(paywallOpen = false)
    }

    fun openRewarded() {
        _state.value = _state.value.copy(paywallOpen = false, rewardedOpen = true)
    }

    fun dismissRewarded() {
        _state.value = _state.value.copy(rewardedOpen = false)
    }

    fun rewardedFinished() {
        viewModelScope.launch {
            container.entitlements.grantBonusTryOn()
            _state.value = _state.value.copy(rewardedOpen = false)
        }
    }

    fun start() {
        val s = _state.value
        val fabricId = s.fabricId ?: return
        val styleId = s.styleId ?: return
        val person = s.personPath ?: return
        viewModelScope.launch {
            if (!container.entitlements.canTryOn()) {
                _state.value = _state.value.copy(paywallOpen = true)
                return@launch
            }
            container.entitlements.consumeTryOn()
            _state.value = _state.value.copy(running = true, tryOn = TryOnState.Idle)
            container.tryOn.generate(
                TryOnRequest(
                    fabricId = fabricId,
                    dressStyleId = styleId,
                    personImageUri = person,
                    gender = s.gender,
                    customization = s.customization,
                ),
            ).collect { st ->
                _state.value = _state.value.copy(
                    tryOn = st,
                    running = st !is TryOnState.Success && st !is TryOnState.Error,
                )
            }
        }
    }
}

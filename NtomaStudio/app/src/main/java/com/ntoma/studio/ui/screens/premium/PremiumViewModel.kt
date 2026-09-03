package com.ntoma.studio.ui.screens.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.R
import com.ntoma.studio.billing.PurchaseResult
import com.ntoma.studio.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PremiumUiState(
    val premium: Boolean = false,
    /** Dialog to show after a purchase/restore attempt; null when nothing pending. */
    val noticeRes: Int? = null,
)

class PremiumViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PremiumUiState())
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(premium = prefs.premiumEnabled)
            }
        }
    }

    fun purchase() = viewModelScope.launch {
        val notice = when (container.billing.purchasePremium()) {
            PurchaseResult.Success -> R.string.premium_demo_activated
            PurchaseResult.AlreadyOwned -> R.string.premium_already_active
            PurchaseResult.Unavailable -> R.string.premium_billing_notice
            is PurchaseResult.Failed -> R.string.premium_billing_notice
        }
        _state.value = _state.value.copy(noticeRes = notice)
    }

    fun restore() = viewModelScope.launch {
        val notice = when (container.billing.restorePurchases()) {
            PurchaseResult.Success -> R.string.premium_demo_activated
            PurchaseResult.AlreadyOwned -> R.string.premium_already_active
            PurchaseResult.Unavailable -> R.string.premium_billing_notice
            is PurchaseResult.Failed -> R.string.premium_billing_notice
        }
        _state.value = _state.value.copy(noticeRes = notice)
    }

    /** Testers' escape hatch: flips the local demo flag back off. */
    fun disableDemoPremium() = viewModelScope.launch {
        container.settings.update { it.copy(premiumEnabled = false) }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(noticeRes = null)
    }
}

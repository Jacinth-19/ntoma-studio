package com.ntoma.studio.billing

import com.ntoma.studio.domain.repository.SettingsRepository

/**
 * Purchasing seam.
 *
 * This build ships [DemoBillingManager] — no Play Billing Library, no payments, and every screen
 * labels the premium flag as a demo. To go live:
 *
 * 1. Add `com.android.billingclient:billing-ktx` and implement a `PlayBillingManager` here
 *    (BillingClient + queryProductDetails + launchBillingFlow + acknowledge/queryPurchasesAsync).
 * 2. Map successful purchases onto `SettingsRepository.update { it.copy(premiumEnabled = true) }`
 *    (or better, a server-verified entitlement).
 * 3. Swap the single binding in `di/AppContainer.kt`. Nothing else in the app changes.
 */
interface BillingManager {
    suspend fun purchasePremium(): PurchaseResult
    suspend fun restorePurchases(): PurchaseResult
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data object Unavailable : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/** Local, clearly-labelled demo implementation: flips the premium flag, never touches money. */
class DemoBillingManager(private val settings: SettingsRepository) : BillingManager {

    override suspend fun purchasePremium(): PurchaseResult {
        if (settings.current().premiumEnabled) return PurchaseResult.AlreadyOwned
        settings.update { it.copy(premiumEnabled = true) }
        return PurchaseResult.Success
    }

    override suspend fun restorePurchases(): PurchaseResult =
        if (settings.current().premiumEnabled) PurchaseResult.AlreadyOwned
        else PurchaseResult.Unavailable
}

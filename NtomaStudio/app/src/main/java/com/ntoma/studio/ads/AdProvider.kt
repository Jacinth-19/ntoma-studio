package com.ntoma.studio.ads

/**
 * Advertising abstraction. The bundled provider serves clearly-labelled placeholder slots only;
 * wire AdMob (or any network) behind this interface later. Premium users never see a slot.
 */
interface AdProvider {
    /** True when the provider is connected and allowed to fill slots. */
    val isConnected: Boolean

    /** Duration of the demo rewarded ad, seconds. */
    val rewardedDurationSeconds: Int get() = 5
}

class DemoAdProvider : AdProvider {
    override val isConnected: Boolean = false
}

package com.ntoma.studio

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Carries a pending deep link (e.g. from a notification tap) from [MainActivity] to the
 * navigation host. Needed because an intent arriving while the app is alive lands in
 * onNewIntent and would otherwise be dropped.
 */
object DeepLinks {
    val pending = MutableStateFlow<String?>(null)

    fun post(uri: String?) {
        pending.value = uri
    }

    fun consume() {
        pending.value = null
    }
}

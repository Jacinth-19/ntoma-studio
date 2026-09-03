package com.ntoma.studio.analytics

import android.util.Log

/**
 * Analytics abstraction. This build ships a debug logger only — no SDK, nothing leaves the
 * device (see Privacy → Analytics).
 */
interface AnalyticsLogger {
    fun log(event: String, params: Map<String, String> = emptyMap())
}

class DebugAnalyticsLogger(private val enabled: Boolean = true) : AnalyticsLogger {
    override fun log(event: String, params: Map<String, String>) {
        if (enabled) Log.d("NtomaAnalytics", "$event $params")
    }
}

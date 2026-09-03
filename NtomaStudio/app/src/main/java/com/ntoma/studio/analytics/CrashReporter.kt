package com.ntoma.studio.analytics

import android.content.Context
import java.io.File

/**
 * Minimal on-device crash capture: writes the last 10 stack traces into filesDir/crashlogs and
 * forwards an anonymous event to [AnalyticsLogger]. Nothing leaves the device.
 *
 * For Play launch, replace the body of [install] with Firebase Crashlytics
 * (`FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)`) — see
 * docs/PLAY_DATA_SAFETY.md for the wiring checklist and the consent implications.
 */
class CrashReporter(
    private val context: Context,
    private val analytics: AnalyticsLogger,
) {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(context.filesDir, "crashlogs").apply { mkdirs() }
                File(dir, "crash_${System.currentTimeMillis()}.txt")
                    .writeText(throwable.stackTraceToString())
                dir.listFiles()
                    ?.sortedBy { it.lastModified() }
                    ?.dropLast(MAX_LOGS)
                    ?.forEach { it.delete() }
                analytics.log("crash", mapOf("type" to throwable.javaClass.simpleName))
            } catch (ignored: Exception) {
                // Never let the reporter make a crash worse.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val MAX_LOGS = 10
    }
}

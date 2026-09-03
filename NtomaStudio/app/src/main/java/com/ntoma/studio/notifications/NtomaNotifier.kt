package com.ntoma.studio.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ntoma.studio.MainActivity
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.UserPreferences
import android.app.PendingIntent
import android.content.Intent

/**
 * Channel-based notifications. Every send goes through the user's per-channel preference and
 * the system permission; nothing is ever pushed for engagement alone.
 */
class NtomaNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_PROCESSING = "ntoma_processing"
        const val CHANNEL_RECOMMENDATIONS = "ntoma_recommendations"
        const val CHANNEL_UPDATES = "ntoma_updates"
        private const val ID_LOOK = 4001
        private const val ID_RECOMMENDATION = 4002
        private const val ID_UPDATE = 4003
        private const val ID_BRIEF = 4004
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PROCESSING,
                    context.getString(R.string.notif_channel_processing),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.notif_channel_processing_desc) },
                NotificationChannel(
                    CHANNEL_RECOMMENDATIONS,
                    context.getString(R.string.notif_channel_recommendations),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_recommendations_desc) },
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notif_channel_updates_desc) },
            ),
        )
    }

    fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun lookReady(styleTitleKey: String, lookId: Long? = null) = post(
        enabledCheck = { it.notifications.processing },
        channel = CHANNEL_PROCESSING,
        id = ID_LOOK,
        title = context.getString(R.string.notif_look_ready_title),
        body = context.getString(R.string.notif_look_ready_body, resolveKey(styleTitleKey)),
        deepLink = lookId?.let { android.net.Uri.parse("app://look/$it") },
    )

    fun recommendation(styleTitleKey: String, styleId: String) = post(
        enabledCheck = { it.notifications.recommendations },
        channel = CHANNEL_RECOMMENDATIONS,
        id = ID_RECOMMENDATION,
        title = context.getString(R.string.notif_recommendation_title),
        body = context.getString(R.string.notif_recommendation_body, resolveKey(styleTitleKey)),
        deepLink = android.net.Uri.parse("app://design/$styleId"),
    )

    fun productUpdate() = post(
        enabledCheck = { it.notifications.updates },
        channel = CHANNEL_UPDATES,
        id = ID_UPDATE,
        title = context.getString(R.string.notif_update_title),
        body = context.getString(R.string.notif_update_body),
    )

    fun briefReady(tailorName: String) = post(
        enabledCheck = { it.notifications.processing },
        channel = CHANNEL_PROCESSING,
        id = ID_BRIEF,
        title = context.getString(R.string.notif_brief_ready_title),
        body = context.getString(R.string.notif_brief_ready_body, tailorName),
    )

    private var prefsProvider: (suspend () -> UserPreferences)? = null
    fun attachPreferences(provider: suspend () -> UserPreferences) {
        prefsProvider = provider
    }

    private fun post(
        enabledCheck: (UserPreferences) -> Boolean,
        channel: String,
        id: Int,
        title: String,
        body: String,
        deepLink: android.net.Uri? = null,
    ) {
        if (!permissionGranted()) return
        val prefs = runBlockingPrefs() ?: return
        if (!enabledCheck(prefs)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink != null) {
                action = Intent.ACTION_VIEW
                data = deepLink
            }
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_logo_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and post; stay quiet.
        }
    }

    private fun runBlockingPrefs(): UserPreferences? = try {
        kotlinx.coroutines.runBlocking { prefsProvider?.invoke() }
    } catch (e: Exception) {
        null
    }

    private fun resolveKey(key: String): String {
        val res = context.resources.getIdentifier(key, "string", context.packageName)
        return if (res != 0) context.getString(res) else context.getString(R.string.app_name)
    }
}

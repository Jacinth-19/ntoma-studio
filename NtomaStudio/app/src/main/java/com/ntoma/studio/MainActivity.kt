package com.ntoma.studio

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ntoma.studio.ui.AppShell

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { false } // fast, minimal splash per brand guidance
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeepLinks.post(intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.dataString)
        setContent { AppShell() }
    }

    /** Notification taps while the app is alive arrive here; without forwarding them the
     *  navigation host never sees the intent and the user stays on whatever screen they were on. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            DeepLinks.post(intent.dataString)
        }
    }
}

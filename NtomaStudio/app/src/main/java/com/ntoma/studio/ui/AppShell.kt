package com.ntoma.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.UserPreferences
import com.ntoma.studio.ui.navigation.MainScaffold
import com.ntoma.studio.ui.onboarding.OnboardingScreen
import com.ntoma.studio.ui.theme.NtomaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root of the UI tree: applies theme, gates onboarding, then hosts the main navigation.
 * A short branded splash with woven-strip artwork opens every session.
 */
@Composable
fun AppShell() {
    NtomaTheme {
        val context = LocalContext.current
        val container = AppContainer.get(context)
        val prefs by container.settings.preferences
            .collectAsStateWithLifecycle(initialValue = UserPreferences())
        val scope = rememberCoroutineScope()
        var pendingScan by remember { mutableStateOf(false) }
        var splashVisible by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(1100)
            splashVisible = false
        }

        Box(Modifier.fillMaxSize()) {
            if (!prefs.onboardingCompleted) {
                OnboardingScreen(
                    onFinished = { scanNow ->
                        pendingScan = scanNow
                        scope.launch {
                            container.settings.update { it.copy(onboardingCompleted = true) }
                        }
                    },
                )
            } else {
                MainScaffold(
                    startWithScan = pendingScan,
                    onScanConsumed = { pendingScan = false },
                )
            }

            AnimatedVisibility(visible = splashVisible, exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(400))) {
                SplashArt()
            }
        }
    }
}

@Composable
private fun SplashArt() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Image(
            painter = painterResource(R.drawable.splash_art),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The artwork is always light, so the wordmark uses a fixed ink colour
            // regardless of the system theme (dark mode included).
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = androidx.compose.ui.graphics.Color(0xFF14110F),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFF14110F),
            )
        }
    }
}

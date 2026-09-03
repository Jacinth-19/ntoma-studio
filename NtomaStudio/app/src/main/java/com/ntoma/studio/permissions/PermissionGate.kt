package com.ntoma.studio.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.ntoma.studio.R

private enum class DialogState { None, Rationale, Denied }

/**
 * Explanation-before-dialog camera permission flow. On denial the user can continue without the
 * camera or jump to system settings — the app never dead-ends.
 *
 * Usage:
 *   val camera = rememberCameraPermission(onGranted = { nav(Camera) })
 *   Button(onClick = camera.request) { … }
 */
@Composable
fun rememberCameraPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(DialogState.None) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else dialog = DialogState.Denied
    }

    if (dialog == DialogState.Rationale) {
        AlertDialog(
            onDismissRequest = { dialog = DialogState.None },
            title = { Text(stringResource(R.string.permission_camera_title)) },
            text = { Text(stringResource(R.string.permission_camera_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = DialogState.None
                    launcher.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DialogState.None }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (dialog == DialogState.Denied) {
        AlertDialog(
            onDismissRequest = { dialog = DialogState.None },
            title = { Text(stringResource(R.string.permission_camera_denied_title)) },
            text = { Text(stringResource(R.string.permission_camera_denied_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = DialogState.None
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text(stringResource(R.string.permission_open_app_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DialogState.None }) {
                    Text(stringResource(R.string.permission_denied_continue))
                }
            },
        )
    }

    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else dialog = DialogState.Rationale
    }
}

/** Returns a lambda that asks for POST_NOTIFICATIONS where required (Android 13+). */
@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onResult(true)
    }
}

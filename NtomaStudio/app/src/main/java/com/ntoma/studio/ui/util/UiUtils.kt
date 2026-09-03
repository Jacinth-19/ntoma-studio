package com.ntoma.studio.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.AppError
import java.io.File

/** Android Photo Picker — no broad storage permission needed. */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> =
    rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPicked(uri) }

/** Validate then route into the edit/confirm screen. */
fun Context.openFabricFlow(uri: Uri, navigate: (String) -> Unit, onError: (AppError) -> Unit) {
    val outcome = AppContainer.get(this).imageProcessor.validate(uri)
    when (outcome) {
        is com.ntoma.studio.domain.model.Outcome.Success ->
            navigate(com.ntoma.studio.ui.navigation.Routes.edit(uri.toString(), "fabric"))
        is com.ntoma.studio.domain.model.Outcome.Failure -> onError(outcome.error)
    }
}

fun Context.openPersonFlow(uri: Uri, navigate: (String) -> Unit, onError: (AppError) -> Unit) {
    val outcome = AppContainer.get(this).imageProcessor.validate(uri)
    when (outcome) {
        is com.ntoma.studio.domain.model.Outcome.Success ->
            navigate(com.ntoma.studio.ui.navigation.Routes.edit(uri.toString(), "person"))
        is com.ntoma.studio.domain.model.Outcome.Failure -> onError(outcome.error)
    }
}

fun Context.resolveKey(key: String?): String? {
    if (key.isNullOrBlank()) return null
    val res = resources.getIdentifier(key, "string", packageName)
    return if (res != 0) getString(res) else null
}

fun Context.timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> getString(R.string.time_just_now)
        diff < 3_600_000 -> getString(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> getString(R.string.time_hours_ago, diff / 3_600_000)
        else -> getString(R.string.time_days_ago, diff / 86_400_000)
    }
}

/** Native Android share sheet. Nothing is ever posted automatically. */
fun Context.shareLook(resultPath: String, styleTitle: String, fabricName: String) {
    val uri = AppContainer.get(this).imageProcessor.shareUriFor(File(resultPath))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            getString(R.string.share_look_text, styleTitle, fabricName),
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
}

fun Context.shareTextOnly(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
}

fun Context.shareImagePath(path: String, text: String) {
    val uri = AppContainer.get(this).imageProcessor.shareUriFor(File(path))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
}

fun Context.sharePdfFile(file: java.io.File, text: String) {
    val uri = AppContainer.get(this).imageProcessor.shareUriFor(file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
}

fun Context.shareAnyFile(file: java.io.File, mime: String, text: String) {
    val uri = AppContainer.get(this).imageProcessor.shareUriFor(file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
}

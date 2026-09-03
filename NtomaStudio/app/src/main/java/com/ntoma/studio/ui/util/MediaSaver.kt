package com.ntoma.studio.ui.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a generated look into the shared Pictures/Ntoma collection.
 * On Android 10+ this uses MediaStore (no permission needed); on older versions it falls back to
 * the app's external files dir so we never request storage permission.
 */
fun Context.saveToGallery(file: File): Boolean = try {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Ntoma")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().copyTo(out) } ?: false
        true
    } else {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        File(dir, file.name).also { file.copyTo(it, overwrite = true) }
        true
    }
} catch (e: Exception) {
    false
}

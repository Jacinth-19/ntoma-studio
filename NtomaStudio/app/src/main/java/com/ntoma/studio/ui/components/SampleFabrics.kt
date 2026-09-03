package com.ntoma.studio.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ntoma.studio.R
import java.io.File

/**
 * Bundled reference fabrics so a first-run user without cloth in hand can still experience the
 * whole pipeline. The samples are the same honest catalog photos, labelled as samples.
 */
object SampleFabrics {

    data class Sample(val asset: String, val labelRes: Int, val fileKey: String)

    val all = listOf(
        Sample("catalog/fabrics/kente.jpg", R.string.fabric_kente, "sample_kente.jpg"),
        Sample("catalog/fabrics/wax.jpg", R.string.fabric_wax, "sample_wax.jpg"),
        Sample("catalog/fabrics/lace.jpg", R.string.fabric_lace, "sample_lace.jpg"),
    )

    /** Copies the asset into the shared media dir and returns a file:// uri string. */
    fun materialize(context: Context, sample: Sample): String? = try {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val out = File(dir, sample.fileKey)
        if (!out.exists()) {
            context.assets.open(sample.asset).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        android.net.Uri.fromFile(out).toString()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SamplePickerDialog(onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sample_picker_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.sample_picker_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SampleFabrics.all.forEach { sample ->
                        Column(Modifier.weight(1f)) {
                            AssetImage(
                                asset = sample.asset,
                                contentDescription = stringResource(sample.labelRes),
                                modifier = Modifier
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        SampleFabrics.materialize(context, sample)?.let(onPicked)
                                    },
                            )
                            Text(
                                stringResource(sample.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

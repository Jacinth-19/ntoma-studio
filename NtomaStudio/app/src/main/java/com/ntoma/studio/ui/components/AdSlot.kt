package com.ntoma.studio.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext

/**
 * Clearly-labelled ad placeholder. Hidden for Premium. The demo provider never fills the slot,
 * so users see an honest, quiet placeholder instead of fake content.
 */
@Composable
fun AdSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val premium = AppContainer.get(context).settings.preferences
        .collectAsState(initial = com.ntoma.studio.domain.model.UserPreferences()).value.premiumEnabled
    val connected = AppContainer.get(context).adProvider.isConnected
    if (premium) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.ads_placeholder_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (connected) "" else stringResource(R.string.ads_placeholder_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

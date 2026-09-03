package com.ntoma.studio.ui.screens.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.ui.navigation.appViewModel
import kotlinx.coroutines.launch

@Composable
fun PremiumScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: PremiumViewModel = appViewModel { c, ctx -> PremiumViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var yearly by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.titleLarge)
        }

        Text(
            stringResource(R.string.premium_pitch),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        listOf(
            R.string.premium_benefit_analyses,
            R.string.premium_benefit_tryons,
            R.string.premium_benefit_resolution,
            R.string.premium_benefit_speed,
            R.string.premium_benefit_styles,
            R.string.premium_benefit_ads,
            R.string.premium_benefit_history,
        ).forEach { res ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !yearly, onClick = { yearly = false }, label = { Text(stringResource(R.string.premium_tab_monthly)) })
            FilterChip(selected = yearly, onClick = { yearly = true }, label = { Text(stringResource(R.string.premium_tab_yearly)) })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (yearly) R.string.premium_price_yearly else R.string.premium_price_monthly,
                if (yearly) "GHS 220" else "GHS 25",
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))

        if (state.premium) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(
                    stringResource(R.string.premium_current_plan, stringResource(R.string.label_premium)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = vm::disableDemoPremium, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_demo_locked))
            }
        } else {
            Button(onClick = vm::purchase, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.premium_subscribe))
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = vm::restore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_restore))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.premium_billing_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }

    state.noticeRes?.let { res ->
        AlertDialog(
            onDismissRequest = vm::dismissNotice,
            title = { Text(stringResource(R.string.premium_title)) },
            text = { Text(stringResource(res)) },
            confirmButton = {
                TextButton(onClick = vm::dismissNotice) { Text(stringResource(R.string.action_got_it)) }
            },
        )
    }
}

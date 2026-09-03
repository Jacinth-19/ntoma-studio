package com.ntoma.studio.ui.screens.profile

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: ProfileViewModel = appViewModel { c, ctx -> ProfileViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var renameOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.width(52.dp).height(52.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.prefs.displayName.ifBlank { stringResource(R.string.profile_guest) },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.profile_guest_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { renameOpen = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_name_hint))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCard(R.string.profile_stats_fabrics, state.fabricCount, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StatCard(R.string.profile_stats_designs, state.designCount, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StatCard(R.string.profile_stats_looks, state.lookCount, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.profile_section_activity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ProfileRow(R.string.profile_row_history, onClick = { nav.navigate(Routes.HISTORY) })
        ProfileRow(R.string.profile_row_measurements, onClick = { nav.navigate(Routes.MEASUREMENTS) })
        ProfileRow(R.string.profile_row_wardrobe, onClick = { nav.navigate(Routes.WARDROBE) })
        ProfileRow(R.string.profile_row_premium, onClick = { nav.navigate(Routes.PREMIUM) })

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profile_section_preferences), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ProfileRow(R.string.profile_row_settings, onClick = { nav.navigate(Routes.SETTINGS) })
        ProfileRow(R.string.profile_row_notifications, onClick = { nav.navigate(Routes.SETTINGS) })

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profile_section_support), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ProfileRow(R.string.profile_row_help, onClick = { nav.navigate(Routes.HELP) })
        ProfileRow(R.string.profile_row_privacy, onClick = { nav.navigate(Routes.PRIVACY) })
        ProfileRow(R.string.profile_row_about, onClick = { nav.navigate(Routes.ABOUT) })
        Spacer(Modifier.height(32.dp))
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(state.prefs.displayName) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.profile_name_hint)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.setName(text) }
                    renameOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatCard(label: Int, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ProfileRow(label: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

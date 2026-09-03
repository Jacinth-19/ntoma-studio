package com.ntoma.studio.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.ui.components.BrandMark
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import kotlinx.coroutines.launch

@Composable
private fun InfoHeader(title: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun InfoBlock(title: Int, body: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavHostController, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        InfoHeader(R.string.privacy_title) { nav.popBackStack() }
        Text(
            stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        InfoBlock(R.string.privacy_images_title, R.string.privacy_images_body)
        InfoBlock(R.string.privacy_device_title, R.string.privacy_device_body)
        InfoBlock(R.string.privacy_retention_title, R.string.privacy_retention_body)
        InfoBlock(R.string.privacy_delete_title, R.string.privacy_delete_body)
        InfoBlock(R.string.privacy_analytics_title, R.string.privacy_analytics_body)
        InfoBlock(R.string.privacy_ads_title, R.string.privacy_ads_body)
        InfoBlock(R.string.privacy_permissions_title, R.string.privacy_permissions_body)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { nav.navigate(Routes.legal("privacy")) }) {
            Text(stringResource(R.string.settings_privacy_policy))
        }
        TextButton(onClick = { nav.navigate(Routes.legal("terms")) }) {
            Text(stringResource(R.string.settings_terms))
        }
        TextButton(onClick = { nav.navigate(Routes.DATA_CONTROLS) }) {
            Text(stringResource(R.string.settings_data_controls))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun DataControlsScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: DataControlsViewModel = appViewModel { c, ctx -> DataControlsViewModel(c, ctx) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        InfoHeader(R.string.settings_data_controls) { nav.popBackStack() }

        Text(
            stringResource(R.string.privacy_keep_history_days),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(0, 30, 90).forEach { days ->
                FilterChip(
                    selected = state.prefs.keepHistoryDays == days,
                    onClick = { vm.setRetention(days) },
                    label = {
                        Text(
                            if (days == 0) stringResource(R.string.privacy_keep_forever)
                            else stringResource(R.string.privacy_keep_days, days),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_clear_history))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.settings_delete_data))
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.privacy_delete_data_title)) },
            text = { Text(stringResource(R.string.privacy_delete_data_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll()
                    nav.popBackStack()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun HelpScreen(nav: NavHostController, padding: PaddingValues) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        InfoHeader(R.string.help_title) { nav.popBackStack() }
        Text(
            stringResource(R.string.help_how_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        listOf(
            R.string.help_how_1, R.string.help_how_2, R.string.help_how_3, R.string.help_how_4, R.string.help_how_5,
        ).forEach { res ->
            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.help_tips_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.help_tips_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.help_faq_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FaqItem(R.string.help_faq_q1, R.string.help_faq_a1)
        FaqItem(R.string.help_faq_q2, R.string.help_faq_a2)
        FaqItem(R.string.help_faq_q3, R.string.help_faq_a3)
        FaqItem(R.string.help_faq_q4, R.string.help_faq_a4)
        FaqItem(R.string.help_faq_q5, R.string.help_faq_a5)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "1.0"
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.help_contact_email)))
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.help_contact_subject))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.help_contact_body, version))
            }
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.settings_help_contact))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FaqItem(q: Int, a: Int) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(q), style = MaterialTheme.typography.titleSmall)
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(a),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController, padding: PaddingValues) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.profile_row_about), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        BrandMark(modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "${stringResource(R.string.settings_about_version)} $version",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(stringResource(R.string.settings_about_made), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { nav.navigate(Routes.legal("terms")) }) { Text(stringResource(R.string.settings_terms)) }
        TextButton(onClick = { nav.navigate(Routes.legal("privacy")) }) { Text(stringResource(R.string.settings_privacy_policy)) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun LegalScreen(nav: NavHostController, padding: PaddingValues, doc: String) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        InfoHeader(
            when (doc) {
                "terms" -> R.string.terms_title
                "credits" -> R.string.credits_title
                else -> R.string.privacy_title
            },
        ) { nav.popBackStack() }
        if (doc == "terms") {
            listOf(
                R.string.terms_body_1, R.string.terms_body_2, R.string.terms_body_3, R.string.terms_body_4, R.string.terms_body_5,
            ).forEach { res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        } else {
            listOf(
                R.string.privacy_intro, R.string.privacy_images_body, R.string.privacy_device_body,
                R.string.privacy_retention_body, R.string.privacy_delete_body, R.string.privacy_analytics_body,
                R.string.privacy_ads_body, R.string.privacy_permissions_body,
            ).forEach { res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

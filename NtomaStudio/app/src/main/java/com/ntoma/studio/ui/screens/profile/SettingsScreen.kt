package com.ntoma.studio.ui.screens.profile

import android.app.Activity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import com.ntoma.studio.AppLocale
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.LanguageOption
import com.ntoma.studio.domain.model.ThemeMode
import com.ntoma.studio.permissions.rememberNotificationPermissionRequest
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.shareAnyFile
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController, padding: PaddingValues) {
    var showContribute by remember { mutableStateOf(false) }
    val vm: SettingsViewModel = appViewModel { c, ctx -> SettingsViewModel(c, ctx) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var engineDialog by remember { mutableStateOf(false) }
    var cloudUrl by remember { mutableStateOf("") }
    var engineSaved by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(engineDialog) {
        if (engineDialog) {
            cloudUrl = com.ntoma.studio.di.AppContainer.get(context).settings.current().cloudBaseUrl
            engineSaved = false
        }
    }
    var resetTipsDone by remember { mutableStateOf(false) }
    var resetPersonalizationDone by remember { mutableStateOf(false) }

    val requestNotification = rememberNotificationPermissionRequest { granted ->
        // If denied we keep the toggle off and explain; nothing breaks.
        if (!granted) vm.setAllNotifications(false)
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Section(R.string.settings_section_appearance)
        SettingRow(stringResource(R.string.settings_theme), themeLabel(state.prefs.themeMode)) { themeDialog = true }
        SettingRow(stringResource(R.string.settings_language), stringResource(state.prefs.language.labelRes)) { languageDialog = true }

        Section(R.string.settings_section_notifications)
        NotificationRow(
            stringResource(R.string.settings_notif_processing),
            stringResource(R.string.settings_notif_processing_body),
            state.prefs.notifications.processing,
        ) { on ->
            if (on) requestNotification()
            vm.setNotifications { it.copy(processing = on) }
        }
        NotificationRow(
            stringResource(R.string.settings_notif_recommendations),
            stringResource(R.string.settings_notif_recommendations_body),
            state.prefs.notifications.recommendations,
        ) { on ->
            if (on) requestNotification()
            vm.setNotifications { it.copy(recommendations = on) }
        }
        NotificationRow(
            stringResource(R.string.settings_notif_updates),
            stringResource(R.string.settings_notif_updates_body),
            state.prefs.notifications.updates,
        ) { on ->
            if (on) requestNotification()
            vm.setNotifications { it.copy(updates = on) }
        }
        NotificationRow(
            stringResource(R.string.settings_notif_inspiration),
            stringResource(R.string.settings_notif_inspiration_body),
            state.prefs.notifications.inspiration,
        ) { on ->
            if (on) requestNotification()
            vm.setNotifications { it.copy(inspiration = on) }
        }

        Section(R.string.settings_section_analysis)
        SettingRow(stringResource(R.string.settings_engine), stringResource(R.string.settings_engine_demo)) { engineDialog = true }
        NotificationRow(
            stringResource(R.string.settings_data_saver),
            stringResource(R.string.settings_data_saver_body),
            state.prefs.dataSaver,
        ) { on -> vm.setDataSaver(on) }

        Section(R.string.settings_section_privacy)
        SettingRow(stringResource(R.string.settings_privacy_policy)) { nav.navigate(Routes.PRIVACY) }
        SettingRow(stringResource(R.string.settings_terms)) { nav.navigate(Routes.legal("terms")) }
        SettingRow(stringResource(R.string.settings_data_controls)) { nav.navigate(Routes.DATA_CONTROLS) }
        SettingRow(stringResource(R.string.contrib_title)) { showContribute = true }
        if (showContribute) {
            ContributeDialog(onDismiss = { showContribute = false })
        }

        Section(R.string.settings_section_help)
        SettingRow(stringResource(R.string.settings_help_how)) { nav.navigate(Routes.HELP) }
        SettingRow(stringResource(R.string.settings_replay_onboarding)) { vm.replayOnboarding() }
        SettingRow(
            stringResource(R.string.settings_reset_tips),
            if (resetTipsDone) stringResource(R.string.settings_tips_reset_done) else "",
        ) {
            scope.launch {
                com.ntoma.studio.data.local.prefs.TooltipStore.reset(context)
                resetTipsDone = true
            }
        }
        SettingRow(
            stringResource(R.string.settings_reset_personalization),
            if (resetPersonalizationDone) stringResource(R.string.settings_personalization_reset_done) else "",
        ) {
            vm.resetPersonalization()
            resetPersonalizationDone = true
        }

        Section(R.string.settings_section_about)
        SettingRow(stringResource(R.string.settings_about_version), state.version) { nav.navigate(Routes.ABOUT) }
        Spacer(Modifier.height(32.dp))
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.prefs.themeMode == mode, onClick = {
                                vm.setTheme(mode)
                                themeDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(themeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption.values().forEach { lang ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.prefs.language == lang, onClick = {
                                languageDialog = false
                                scope.launch { vm.setLanguage(lang) }
                                AppLocale.apply(context, lang.tag)
                                (context as? Activity)?.recreate()
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(lang.labelRes))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_language_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (engineDialog) {
        AlertDialog(
            onDismissRequest = { engineDialog = false },
            title = { Text(stringResource(R.string.settings_engine)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_engine_demo), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_engine_demo_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_engine_cloud), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_engine_cloud_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = cloudUrl,
                        onValueChange = { cloudUrl = it },
                        label = { Text(stringResource(R.string.settings_cloud_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (engineSaved) {
                        Text(
                            stringResource(R.string.settings_engine_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            stringResource(R.string.settings_engine_cloud_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        engineSaved = true
                        scope.launch {
                            com.ntoma.studio.di.AppContainer.get(context).settings.update {
                                it.copy(
                                    cloudBaseUrl = cloudUrl.trim(),
                                    analysisEngine = if (cloudUrl.trim().isNotBlank()) {
                                        com.ntoma.studio.domain.model.AnalysisEngine.CLOUD
                                    } else {
                                        com.ntoma.studio.domain.model.AnalysisEngine.ON_DEVICE_DEMO
                                    },
                                )
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { engineDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(label: String, value: String = "", onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) {
                Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotificationRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
}

@androidx.compose.runtime.Composable
private fun ContributeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(com.ntoma.studio.domain.model.FabricCategory.KENTE) }
    var menuOpen by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listContributions(context)) }
    var editing by remember { mutableStateOf<ContributionEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<ContributionEntry?>(null) }
    val savedCount = items.size
    var message by remember { mutableStateOf<Int?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? -> uri?.let { photoUri = it.toString() } }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.contrib_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Text(
                    stringResource(R.string.contrib_body),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = menuOpen, onExpandedChange = { menuOpen = it },
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = stringResource(category.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        label = { androidx.compose.material3.Text(stringResource(R.string.contrib_category)) },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = menuOpen, onDismissRequest = { menuOpen = false },
                    ) {
                        com.ntoma.studio.domain.model.FabricCategory.values()
                            .filter { it != com.ntoma.studio.domain.model.FabricCategory.UNKNOWN }
                            .forEach { c ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(stringResource(c.labelRes)) },
                                    onClick = { category = c; menuOpen = false },
                                )
                            }
                    }
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.wardrobe_add_photo))
                }
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = consent, onCheckedChange = { consent = it })
                    androidx.compose.material3.Text(
                        stringResource(R.string.contrib_consent),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                }
                androidx.compose.material3.Text(
                    stringResource(R.string.contrib_saved_count, savedCount),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                if (items.isNotEmpty()) {
                    androidx.compose.material3.Text(
                        stringResource(R.string.contrib_gallery),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .height(196.dp),
                    ) {
                        items(items, key = { it.file.path }) { entry ->
                            androidx.compose.foundation.layout.Box {
                                com.ntoma.studio.ui.components.LocalImage(
                                    uri = entry.file.absolutePath,
                                    contentDescription = entry.category,
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxWidth()
                                        .height(88.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clickable { editing = entry },
                                )
                                androidx.compose.material3.IconButton(
                                    onClick = { pendingDelete = entry },
                                    modifier = androidx.compose.ui.Modifier
                                        .align(androidx.compose.ui.Alignment.TopEnd)
                                        .size(28.dp),
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.contrib_delete_cd),
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = androidx.compose.ui.Modifier
                                            .size(18.dp)
                                            .background(
                                                androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                                                androidx.compose.foundation.shape.CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                message?.let {
                    androidx.compose.material3.Text(
                        stringResource(it),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
                if (savedCount > 0) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    exportContributions(context)
                                }?.let { file ->
                                    context.shareAnyFile(
                                        file, "application/zip",
                                        context.getString(R.string.contrib_title),
                                    )
                                }
                            }
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text(stringResource(R.string.contrib_export)) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                when {
                    photoUri.isBlank() -> message = R.string.contrib_photo_needed
                    !consent -> message = R.string.contrib_consent_needed
                    else -> {
                        message = null
                        scope.launch {
                            val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                saveContribution(context, photoUri, category)
                            }
                            when (outcome) {
                                SaveOutcome.SAVED -> {
                                    items = listContributions(context)
                                    photoUri = ""
                                }
                                SaveOutcome.DUPLICATE -> message = R.string.contrib_duplicate
                                SaveOutcome.FAILED -> {}
                            }
                        }
                    }
                }
            }) { androidx.compose.material3.Text(stringResource(R.string.contrib_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(stringResource(R.string.action_cancel))
            }
        },
    )

    pendingDelete?.let { victim ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { androidx.compose.material3.Text(stringResource(R.string.contrib_delete_confirm)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.contrib_delete_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            deleteContribution(victim.file)
                        }
                        items = listContributions(context)
                        pendingDelete = null
                    }
                }) { androidx.compose.material3.Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                    androidx.compose.material3.Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    editing?.let { target ->
        CategoryPickerDialog(
            titleRes = R.string.contrib_edit_category,
            onDismiss = { editing = null },
            onPicked = { cat ->
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        recategorizeContribution(context, target.file, cat)
                    }
                    items = listContributions(context)
                    editing = null
                }
            },
        )
    }
}

@androidx.compose.runtime.Composable
private fun CategoryPickerDialog(
    titleRes: Int,
    onDismiss: () -> Unit,
    onPicked: (com.ntoma.studio.domain.model.FabricCategory) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(titleRes)) },
        text = {
            LazyColumn {
                items(
                    com.ntoma.studio.domain.model.FabricCategory.values()
                        .filter { it != com.ntoma.studio.domain.model.FabricCategory.UNKNOWN }
                        .toList()
                ) { c ->
                    androidx.compose.material3.TextButton(
                        onClick = { onPicked(c) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text(stringResource(c.labelRes)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun contributionsDir(context: android.content.Context) =
    java.io.File(context.filesDir, "contributions")

data class ContributionEntry(val category: String, val file: java.io.File)

private fun countContributions(context: android.content.Context): Int =
    contributionsDir(context).walkTopDown().count { it.isFile }

/** Newest first so the gallery shows the latest donation on top. */
private fun listContributions(context: android.content.Context): List<ContributionEntry> =
    contributionsDir(context).listFiles()
        ?.flatMap { cat ->
            (cat.listFiles() ?: emptyArray()).map { ContributionEntry(cat.name, it) }
        }
        ?.sortedByDescending { it.file.lastModified() }
        ?: emptyList()

private fun deleteContribution(file: java.io.File): Boolean = try {
    file.delete()
} catch (e: Exception) {
    false
}

/** Move a contribution to a different category folder (dataset-quality fix-up); renames the
 *  category prefix so filenames stay meaningful for ingestion. */
internal fun recategorizeContribution(
    context: android.content.Context,
    file: java.io.File,
    category: com.ntoma.studio.domain.model.FabricCategory,
): Boolean = try {
    val dir = java.io.File(contributionsDir(context), category.name).apply { mkdirs() }
    val stem = file.nameWithoutExtension.substringAfter("_", file.nameWithoutExtension)
    val suffix = if (file.name.contains(".")) "." + file.extension else ""
    var target = java.io.File(dir, "${category.name}_$stem$suffix")
    var n = 2
    while (target.exists()) {
        target = java.io.File(dir, "${category.name}_$stem${'_'}$n$suffix")
        n++
    }
    file.renameTo(target)
} catch (e: Exception) {
    false
}

/** 64-bit difference hash of a downsampled grayscale rendition; near-duplicate detection. */
internal fun dHash(bitmap: android.graphics.Bitmap): Long {
    val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 9, 8, true)
    var hash = 0L
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val l = small.getPixel(x, y)
            val r = small.getPixel(x + 1, y)
            val lumL = (l shr 16 and 0xFF) + (l shr 8 and 0xFF) + (l and 0xFF)
            val lumR = (r shr 16 and 0xFF) + (r shr 8 and 0xFF) + (r and 0xFF)
            hash = (hash shl 1) or (if (lumL > lumR) 1L else 0L)
        }
    }
    return hash
}

internal fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

internal enum class SaveOutcome { SAVED, DUPLICATE, FAILED }

private fun saveContribution(
    context: android.content.Context,
    uriString: String,
    category: com.ntoma.studio.domain.model.FabricCategory,
): SaveOutcome {
    return try {
    val bytes = context.contentResolver.openInputStream(android.net.Uri.parse(uriString))
        ?.use { it.readBytes() } ?: return SaveOutcome.FAILED
    val incoming = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return SaveOutcome.FAILED
    val inHash = dHash(incoming)
    val root = contributionsDir(context)
    val duplicate = root.exists() && root.walkTopDown().filter { it.isFile }.any { f ->
        val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
        bmp != null && hammingDistance(inHash, dHash(bmp)) <= 6
    }
    if (duplicate) return SaveOutcome.DUPLICATE
    val dir = java.io.File(root, category.name).apply { mkdirs() }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    var out = java.io.File(dir, "${category.name}_$stamp.jpg")
    var n = 2
    while (out.exists()) {
        out = java.io.File(dir, "${category.name}_${stamp}_$n.jpg")
        n++
    }
    out.writeBytes(bytes)
        SaveOutcome.SAVED
    } catch (e: Exception) {
        SaveOutcome.FAILED
    }
}

internal fun exportContributions(context: android.content.Context): java.io.File? {
    return try {
    val root = contributionsDir(context)
    if (!root.exists()) return null
    val shared = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val zipFile = java.io.File(shared, "ntoma_contributions.zip")
    java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
        val files = root.walkTopDown().filter { it.isFile }.toList()
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .format(java.util.Date())
        val manifest = buildString {
            append("{\"exportedAt\":\"").append(stamp).append("\",\"files\":[")
            files.forEachIndexed { i, f ->
                if (i > 0) append(",")
                append("{\"category\":\"").append(f.parentFile?.name ?: "")
                append("\",\"file\":\"").append(f.relativeTo(root).path).append("\"}")
            }
            append("]}")
        }
        zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
        zip.write(manifest.toByteArray())
        zip.closeEntry()
        files.forEach { f ->
            zip.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(root).path))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
        zipFile
    } catch (e: Exception) {
        null
    }
}

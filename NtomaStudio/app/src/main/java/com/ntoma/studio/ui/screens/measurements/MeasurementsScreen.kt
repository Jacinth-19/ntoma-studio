package com.ntoma.studio.ui.screens.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.Measurements
import kotlinx.coroutines.launch

/**
 * User-entered measurements only. Ntoma never estimates body measurements from photos —
 * the disclaimer says so plainly.
 */
@Composable
fun MeasurementsScreen(nav: NavHostController, padding: PaddingValues) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val scope = rememberCoroutineScope()

    var height by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var shoulder by remember { mutableStateOf("") }
    var sleeve by remember { mutableStateOf("") }
    var inseam by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val m = container.measurements.get()
        height = m.heightCm?.toString() ?: ""
        chest = m.chestCm?.toString() ?: ""
        waist = m.waistCm?.toString() ?: ""
        hip = m.hipCm?.toString() ?: ""
        shoulder = m.shoulderCm?.toString() ?: ""
        sleeve = m.sleeveCm?.toString() ?: ""
        inseam = m.inseamCm?.toString() ?: ""
        neck = m.neckCm?.toString() ?: ""
        notes = m.notes ?: ""
        loaded = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    Icons.Outlined.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                stringResource(R.string.measurements_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.measurements_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }

            MeasurementField(label = stringResource(R.string.measurement_height), value = height, onValueChange = { height = it })
            MeasurementField(label = stringResource(R.string.measurement_chest), value = chest, onValueChange = { chest = it })
            MeasurementField(label = stringResource(R.string.measurement_waist), value = waist, onValueChange = { waist = it })
            MeasurementField(label = stringResource(R.string.measurement_hip), value = hip, onValueChange = { hip = it })
            MeasurementField(label = stringResource(R.string.measurement_shoulder), value = shoulder, onValueChange = { shoulder = it })
            MeasurementField(label = stringResource(R.string.measurement_sleeve), value = sleeve, onValueChange = { sleeve = it })
            MeasurementField(label = stringResource(R.string.measurement_inseam), value = inseam, onValueChange = { inseam = it })
            MeasurementField(label = stringResource(R.string.measurement_neck), value = neck, onValueChange = { neck = it })

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.measurement_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    scope.launch {
                        container.measurements.save(
                            Measurements(
                                heightCm = height.toIntOrNull(),
                                chestCm = chest.toIntOrNull(),
                                waistCm = waist.toIntOrNull(),
                                hipCm = hip.toIntOrNull(),
                                shoulderCm = shoulder.toIntOrNull(),
                                sleeveCm = sleeve.toIntOrNull(),
                                inseamCm = inseam.toIntOrNull(),
                                neckCm = neck.toIntOrNull(),
                                notes = notes.trim().ifBlank { null },
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    nav.popBackStack()
                },
                enabled = loaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

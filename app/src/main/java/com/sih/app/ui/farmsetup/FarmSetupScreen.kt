package com.sih.app.ui.farmsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sih.app.R
import com.sih.app.ui.theme.SIHTheme

import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmSetupScreen(
    viewModel: FarmSetupViewModel,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        Log.d("AgriX_Debug", "8. [FarmSetupScreen] LaunchedEffect observed uiState: $uiState")
        if (uiState is FarmSetupUiState.Success) {
            Log.d("AgriX_Debug", "8.1. [FarmSetupScreen] uiState is Success -> triggering onSetupComplete()")
            onSetupComplete()
        } else if (uiState is FarmSetupUiState.Error) {
            Log.e("AgriX_Debug", "8.ERROR. [FarmSetupScreen] uiState is Error: ${(uiState as FarmSetupUiState.Error).message}")
        }
    }

    FarmSetupContent(
        isSaving = uiState is FarmSetupUiState.Saving,
        onSaveFarm = { farmName, state, district, village, farmArea, farmAreaUnit, soilType, currentCrop ->
            Log.d("AgriX_Debug", "3. [FarmSetupScreen] onSaveFarm received data: name=$farmName, state=$state, district=$district, village=$village, area=$farmArea, unit=$farmAreaUnit, soil=$soilType, crop=$currentCrop")
            viewModel.saveFarm(
                farmName = farmName,
                state = state,
                district = district,
                village = village,
                farmArea = farmArea,
                farmAreaUnit = farmAreaUnit,
                soilType = soilType,
                currentCrop = currentCrop,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmSetupContent(
    isSaving: Boolean,
    onSaveFarm: (
        farmName: String?,
        state: String,
        district: String,
        village: String,
        farmArea: Double,
        farmAreaUnit: String,
        soilType: String,
        currentCrop: String,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var farmName by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var village by rememberSaveable { mutableStateOf("") }
    var farmSize by rememberSaveable { mutableStateOf("") }
    var selectedUnit by rememberSaveable { mutableStateOf(FarmAreaUnit.Acres) }
    var selectedSoilType by rememberSaveable { mutableStateOf<SoilType?>(null) }
    var selectedCrop by rememberSaveable { mutableStateOf<CropType?>(null) }

    var soilDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var cropDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    var hasAttemptedSubmit by rememberSaveable { mutableStateOf(false) }

    val isStateValid = state.isNotBlank()
    val isDistrictValid = district.isNotBlank()
    val isVillageValid = village.isNotBlank()
    val isFarmSizeValid = farmSize.toDoubleOrNull()?.let { it > 0.0 } == true
    val isSoilTypeValid = selectedSoilType != null
    val isCropValid = selectedCrop != null

    val isFormValid = isStateValid && isDistrictValid && isVillageValid &&
        isFarmSizeValid && isSoilTypeValid && isCropValid

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Text(
                text = stringResource(R.string.farm_setup_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.farm_setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Basic Info
            Text(
                text = stringResource(R.string.farm_setup_section_basic),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = farmName,
                onValueChange = { farmName = it },
                label = { Text(stringResource(R.string.farm_setup_field_farm_name)) },
                placeholder = { Text(stringResource(R.string.farm_setup_field_farm_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Location
            Text(
                text = stringResource(R.string.farm_setup_section_location),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state,
                onValueChange = { state = it },
                label = { Text(stringResource(R.string.farm_setup_field_state)) },
                placeholder = { Text(stringResource(R.string.farm_setup_field_state_hint)) },
                isError = hasAttemptedSubmit && !isStateValid,
                supportingText = {
                    if (hasAttemptedSubmit && !isStateValid) {
                        Text(stringResource(R.string.err_state_required))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = district,
                onValueChange = { district = it },
                label = { Text(stringResource(R.string.farm_setup_field_district)) },
                placeholder = { Text(stringResource(R.string.farm_setup_field_district_hint)) },
                isError = hasAttemptedSubmit && !isDistrictValid,
                supportingText = {
                    if (hasAttemptedSubmit && !isDistrictValid) {
                        Text(stringResource(R.string.err_district_required))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = village,
                onValueChange = { village = it },
                label = { Text(stringResource(R.string.farm_setup_field_village)) },
                placeholder = { Text(stringResource(R.string.farm_setup_field_village_hint)) },
                isError = hasAttemptedSubmit && !isVillageValid,
                supportingText = {
                    if (hasAttemptedSubmit && !isVillageValid) {
                        Text(stringResource(R.string.err_village_required))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Farm Size
            Text(
                text = stringResource(R.string.farm_setup_section_size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = farmSize,
                    onValueChange = { farmSize = it },
                    label = { Text(stringResource(R.string.farm_setup_field_size)) },
                    placeholder = { Text(stringResource(R.string.farm_setup_field_size_hint)) },
                    isError = hasAttemptedSubmit && !isFarmSizeValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FarmAreaUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text(stringResource(unit.displayNameRes)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            if (hasAttemptedSubmit && !isFarmSizeValid) {
                Text(
                    text = stringResource(R.string.err_farm_size_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Soil Type
            Text(
                text = stringResource(R.string.farm_setup_section_soil),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = soilDropdownExpanded,
                onExpandedChange = { soilDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedSoilType?.let { stringResource(it.displayNameRes) }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.farm_setup_section_soil)) },
                    placeholder = { Text(stringResource(R.string.farm_setup_select_soil_hint)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = soilDropdownExpanded) },
                    isError = hasAttemptedSubmit && !isSoilTypeValid,
                    supportingText = {
                        if (hasAttemptedSubmit && !isSoilTypeValid) {
                            Text(stringResource(R.string.err_soil_required))
                        }
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = soilDropdownExpanded,
                    onDismissRequest = { soilDropdownExpanded = false },
                ) {
                    SoilType.entries.forEach { soil ->
                        DropdownMenuItem(
                            text = { Text(stringResource(soil.displayNameRes)) },
                            onClick = {
                                selectedSoilType = soil
                                soilDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Current Crop
            Text(
                text = stringResource(R.string.farm_setup_section_crop),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = cropDropdownExpanded,
                onExpandedChange = { cropDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedCrop?.let { stringResource(it.displayNameRes) }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.farm_setup_section_crop)) },
                    placeholder = { Text(stringResource(R.string.farm_setup_select_crop_hint)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cropDropdownExpanded) },
                    isError = hasAttemptedSubmit && !isCropValid,
                    supportingText = {
                        if (hasAttemptedSubmit && !isCropValid) {
                            Text(stringResource(R.string.err_crop_required))
                        }
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = cropDropdownExpanded,
                    onDismissRequest = { cropDropdownExpanded = false },
                ) {
                    CropType.entries.forEach { crop ->
                        DropdownMenuItem(
                            text = { Text(stringResource(crop.displayNameRes)) },
                            onClick = {
                                selectedCrop = crop
                                cropDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = {
                Log.d("AgriX_Debug", "1. [Button Click] Save & Continue clicked. isSaving=$isSaving, hasAttemptedSubmit=$hasAttemptedSubmit")
                hasAttemptedSubmit = true
                Log.d("AgriX_Debug", "2. [Validation] isFormValid=$isFormValid (state='$state' valid=$isStateValid, district='$district' valid=$isDistrictValid, village='$village' valid=$isVillageValid, farmSize='$farmSize' valid=$isFarmSizeValid, soilType='$selectedSoilType' valid=$isSoilTypeValid, crop='$selectedCrop' valid=$isCropValid)")
                if (isFormValid && !isSaving) {
                    focusManager.clearFocus()
                    val selectedSoil = selectedSoilType
                    val selectedPrimaryCrop = selectedCrop
                    if (selectedSoil != null && selectedPrimaryCrop != null) {
                        Log.d("AgriX_Debug", "2.1. [Validation Passed] Dispatching onSaveFarm(...)")
                        onSaveFarm(
                            farmName.ifBlank { null },
                            state,
                            district,
                            village,
                            farmSize.toDoubleOrNull() ?: 0.0,
                            selectedUnit.name,
                            selectedSoil.name,
                            selectedPrimaryCrop.name,
                        )
                    } else {
                        Log.w("AgriX_Debug", "2.2. [Validation Warning] selectedSoil or selectedPrimaryCrop was null unexpectedly.")
                    }
                } else {
                    Log.w("AgriX_Debug", "2.3. [Validation Failed] isFormValid=$isFormValid, isSaving=$isSaving. Submission blocked.")
                }
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_save_continue),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FarmSetupScreenPreview() {
    SIHTheme {
        FarmSetupContent(
            isSaving = false,
            onSaveFarm = { _, _, _, _, _, _, _, _ -> },
        )
    }
}

package com.sih.app.ui.farmsetup

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sih.app.R
import com.sih.app.ui.theme.SIHTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmSetupScreen(
    viewModel: FarmSetupViewModel,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val existingFarm by viewModel.existingFarm.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d("AgriX_Location", "Location permission callback: fine=$fineGranted, coarse=$coarseGranted")
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }

    LaunchedEffect(locationState) {
        if (locationState is LocationUiState.PermissionRequired) {
            Log.d("AgriX_Location", "Launching location permission prompt...")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

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
        locationState = locationState,
        existingFarm = existingFarm,
        onUseMyLocation = {
            viewModel.onUseMyLocationClicked()
        },
        onRetryLocation = {
            viewModel.onUseMyLocationClicked()
        },
        onCheckLocationServices = {
            viewModel.onLocationServicesCheck()
        },
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
    locationState: LocationUiState,
    existingFarm: com.sih.app.core.database.FarmEntity? = null,
    onUseMyLocation: () -> Unit,
    onRetryLocation: () -> Unit,
    onCheckLocationServices: () -> Unit,
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

    var farmName by rememberSaveable { mutableStateOf(existingFarm?.farmName ?: "") }
    var state by rememberSaveable { mutableStateOf(existingFarm?.state ?: "") }
    var district by rememberSaveable { mutableStateOf(existingFarm?.district ?: "") }
    var village by rememberSaveable { mutableStateOf(existingFarm?.village ?: "") }
    var farmSize by rememberSaveable {
        mutableStateOf(
            existingFarm?.let { if (it.farmArea % 1.0 == 0.0) it.farmArea.toInt().toString() else it.farmArea.toString() } ?: ""
        )
    }
    var selectedUnit by rememberSaveable {
        mutableStateOf(
            existingFarm?.let { runCatching { FarmAreaUnit.valueOf(it.farmAreaUnit) }.getOrNull() } ?: FarmAreaUnit.Acres
        )
    }
    var selectedSoilType by rememberSaveable {
        mutableStateOf(
            existingFarm?.let { runCatching { SoilType.valueOf(it.soilType) }.getOrNull() }
        )
    }
    var selectedCrop by rememberSaveable {
        mutableStateOf(
            existingFarm?.let { runCatching { CropType.valueOf(it.currentCrop) }.getOrNull() }
        )
    }

    LaunchedEffect(existingFarm) {
        val farm = existingFarm
        if (farm != null && farmName.isBlank() && state.isBlank()) {
            farmName = farm.farmName ?: ""
            state = farm.state
            district = farm.district
            village = farm.village
            farmSize = if (farm.farmArea % 1.0 == 0.0) farm.farmArea.toInt().toString() else farm.farmArea.toString()
            selectedUnit = runCatching { FarmAreaUnit.valueOf(farm.farmAreaUnit) }.getOrDefault(FarmAreaUnit.Acres)
            selectedSoilType = runCatching { SoilType.valueOf(farm.soilType) }.getOrNull()
            selectedCrop = runCatching { CropType.valueOf(farm.currentCrop) }.getOrNull()
        }
    }

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

            // Section: Farm GPS Location
            FarmGpsLocationSection(
                locationState = locationState,
                onUseMyLocation = onUseMyLocation,
                onRetryLocation = onRetryLocation,
                onCheckLocationServices = onCheckLocationServices,
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

@Composable
private fun FarmGpsLocationSection(
    locationState: LocationUiState,
    onUseMyLocation: () -> Unit,
    onRetryLocation: () -> Unit,
    onCheckLocationServices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "📍",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.farm_setup_section_gps),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (locationState) {
                is LocationUiState.Idle, is LocationUiState.PermissionRequired -> {
                    Text(
                        text = stringResource(R.string.farm_setup_gps_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onUseMyLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.farm_setup_btn_use_location),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                is LocationUiState.FetchingLocation -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.farm_setup_location_fetching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                is LocationUiState.LocationCaptured -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.farm_setup_location_captured),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.farm_setup_location_accuracy,
                            locationState.accuracyMeters,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.farm_setup_location_coords,
                            locationState.latitude,
                            locationState.longitude,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRetryLocation,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.farm_setup_location_recapture),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                is LocationUiState.PermissionDenied -> {
                    Text(
                        text = stringResource(R.string.farm_setup_location_perm_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onRetryLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.farm_setup_location_retry),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                is LocationUiState.LocationServicesDisabled -> {
                    Text(
                        text = stringResource(R.string.farm_setup_location_services_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("AgriX_Location", "Failed to open location settings: ${e.message}")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.farm_setup_location_action_turn_on),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        OutlinedButton(
                            onClick = onCheckLocationServices,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.farm_setup_location_retry),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                is LocationUiState.LocationUnavailable, is LocationUiState.LocationError -> {
                    val errorMessage = if (locationState is LocationUiState.LocationError) {
                        locationState.message
                    } else {
                        stringResource(R.string.farm_setup_location_unavailable)
                    }
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onRetryLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.farm_setup_location_retry),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FarmSetupScreenPreview() {
    SIHTheme {
        FarmSetupContent(
            isSaving = false,
            locationState = LocationUiState.Idle,
            onUseMyLocation = {},
            onRetryLocation = {},
            onCheckLocationServices = {},
            onSaveFarm = { _, _, _, _, _, _, _, _ -> },
        )
    }
}

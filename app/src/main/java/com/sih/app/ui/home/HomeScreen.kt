package com.sih.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sih.app.R
import com.sih.app.core.database.FarmEntity
import com.sih.app.ui.farmsetup.CropType
import com.sih.app.ui.farmsetup.FarmAreaUnit
import com.sih.app.ui.farmsetup.SoilType
import com.sih.app.ui.theme.SIHTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onConnectSensor: () -> Unit,
    onNavigateToAi: () -> Unit,
    onEditFarm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onConnectSensor = onConnectSensor,
        onNavigateToAi = onNavigateToAi,
        onEditFarm = onEditFarm,
        modifier = modifier,
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onConnectSensor: () -> Unit,
    onNavigateToAi: () -> Unit,
    onEditFarm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AgriXBottomNavBar(
                onNavigateToAi = onNavigateToAi,
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is HomeUiState.Success -> {
                HomeDashboardBody(
                    farm = uiState.farm,
                    isSensorConnected = uiState.isSensorConnected,
                    connectedDeviceName = uiState.connectedDeviceName,
                    onConnectSensor = onConnectSensor,
                    onNavigateToAi = onNavigateToAi,
                    onEditFarm = onEditFarm,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is HomeUiState.NoFarm -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_sensor_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboardBody(
    farm: FarmEntity,
    isSensorConnected: Boolean,
    connectedDeviceName: String?,
    onConnectSensor: () -> Unit,
    onNavigateToAi: () -> Unit,
    onEditFarm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    val cropDisplayName = runCatching {
        CropType.valueOf(farm.currentCrop).displayNameRes
    }.getOrNull()?.let { stringResource(it) } ?: farm.currentCrop

    val soilDisplayName = runCatching {
        SoilType.valueOf(farm.soilType).displayNameRes
    }.getOrNull()?.let { stringResource(it) } ?: farm.soilType

    val unitDisplayName = runCatching {
        FarmAreaUnit.valueOf(farm.farmAreaUnit).displayNameRes
    }.getOrNull()?.let { stringResource(it) } ?: farm.farmAreaUnit

    val areaFormatted = if (farm.farmArea % 1.0 == 0.0) {
        farm.farmArea.toInt().toString()
    } else {
        farm.farmArea.toString()
    }

    val locationFormatted = buildString {
        if (farm.village.isNotBlank()) append(farm.village)
        if (farm.district.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append(farm.district)
        }
        if (farm.state.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append(farm.state)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // App Header & Greeting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Your Farm Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditFarm() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = farm.farmName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_your_farm),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = soilDisplayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onEditFarm() },
                        ) {
                            Text(
                                text = stringResource(R.string.edit_profile_btn),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = cropDisplayName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Text(
                    text = "$areaFormatted $unitDisplayName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (locationFormatted.isNotBlank()) {
                    Text(
                        text = locationFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Soil Status Section
        Text(
            text = stringResource(R.string.home_soil_status_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSensorConnected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isSensorConnected) {
                            connectedDeviceName?.let { "$it (Connected)" }
                                ?: stringResource(R.string.home_sensor_connected)
                        } else {
                            stringResource(R.string.home_sensor_not_connected)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onConnectSensor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = if (isSensorConnected) {
                            stringResource(R.string.sensor_action_manage)
                        } else {
                            stringResource(R.string.home_action_connect_sensor)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current Conditions Section
        Text(
            text = stringResource(R.string.home_current_conditions_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConditionParameterCard(
                title = stringResource(R.string.home_param_moisture),
                value = stringResource(R.string.home_no_data),
                modifier = Modifier.weight(1f),
            )
            ConditionParameterCard(
                title = stringResource(R.string.home_param_temperature),
                value = stringResource(R.string.home_no_data),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConditionParameterCard(
                title = stringResource(R.string.home_param_humidity),
                value = stringResource(R.string.home_no_data),
                modifier = Modifier.weight(1f),
            )
            ConditionParameterCard(
                title = stringResource(R.string.home_param_ph),
                value = stringResource(R.string.home_no_data),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AgriX AI Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAi() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_onboarding_recommendations),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_ai_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.home_ai_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ConditionParameterCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgriXBottomNavBar(
    onNavigateToAi: () -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { /* Already on Home */ },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_home),
                    contentDescription = stringResource(R.string.nav_home),
                    modifier = Modifier.size(24.dp),
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_home),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Visual placeholder for Soil slice */ },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_soil_sprout),
                    contentDescription = stringResource(R.string.nav_soil),
                    modifier = Modifier.size(24.dp),
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_soil),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToAi,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_onboarding_recommendations),
                    contentDescription = stringResource(R.string.nav_ai),
                    modifier = Modifier.size(24.dp),
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_ai),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Visual placeholder for Farm slice */ },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_onboarding_soil),
                    contentDescription = stringResource(R.string.nav_farm),
                    modifier = Modifier.size(24.dp),
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_farm),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SIHTheme {
        HomeScreenContent(
            uiState = HomeUiState.Success(
                FarmEntity(
                    id = 1L,
                    farmName = "Sunrise Farm",
                    state = "Tamil Nadu",
                    district = "Trichy",
                    village = "Trichy",
                    farmArea = 3.5,
                    farmAreaUnit = "Acres",
                    soilType = "Sandy",
                    currentCrop = "Tomato",
                ),
                isSensorConnected = false,
            ),
            onConnectSensor = {},
            onNavigateToAi = {},
        )
    }
}

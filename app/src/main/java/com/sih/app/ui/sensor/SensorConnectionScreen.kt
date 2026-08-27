package com.sih.app.ui.sensor

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sih.app.R
import com.sih.app.core.sensor.BleDevice
import com.sih.app.core.sensor.CombinedSensorReport
import com.sih.app.core.sensor.RecommendationPriority
import com.sih.app.core.sensor.SensorState
import com.sih.app.core.sensor.UnifiedAgriXRecommendation
import com.sih.app.ui.ai.PriorityBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorConnectionScreen(
    viewModel: SensorConnectionViewModel,
    onBack: () -> Unit,
    onNavigateToCalibration: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sensorState by viewModel.sensorState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sensor_screen_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_home),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    // Soil Calibration shortcut
                    Text(
                        text = "Calibration",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToCalibration() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Quick Demo Reset Button in App Bar
                    Text(
                        text = "Reset Demo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.resetDemo() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (val state = sensorState) {
                // 1. Initial State before scanning
                is SensorState.DisconnectedInitial -> {
                    DisconnectedInitialView(
                        onStartScan = { viewModel.startScan() },
                    )
                }

                // 2. Scan 1 -> Realistic Empty State (No sensor detected)
                is SensorState.Scan1NoSensor -> {
                    if (state.isScanning) {
                        SearchingSpinnerView()
                    } else {
                        Scan1NoSensorView(
                            onScanAgain = { viewModel.startScan() },
                        )
                    }
                }

                // 3. Scan 2 -> Discovered "AgriX Sensor"
                is SensorState.Scan2SensorFound -> {
                    if (state.isScanning) {
                        SearchingSpinnerView()
                    } else {
                        Scan2SensorFoundView(
                            device = state.device,
                            onConnect = { viewModel.connect(state.device) },
                            onScanAgain = { viewModel.startScan() },
                        )
                    }
                }

                // 4. Connecting Animation
                is SensorState.Connecting -> {
                    ConnectingCard(device = state.device)
                }

                // 5. Connected (Demo BLE mode, prompt probe insertion)
                is SensorState.ConnectedDemo -> {
                    ConnectedDemoView(
                        device = state.device,
                        onScanSoil = { viewModel.performSoilScan() },
                        onDisconnect = { viewModel.disconnect() },
                        onResetDemo = { viewModel.resetDemo() },
                    )
                }

                // 6. Active Soil Scanning Animation (2-4 seconds across 6 steps)
                is SensorState.ScanningSoil -> {
                    ScanningSoilProgressView(
                        stepName = state.stepName,
                        progress = state.progress,
                    )
                }

                // 7. Analyzing Stages (Local & Cloud)
                is SensorState.DataReady,
                is SensorState.AnalyzingLocal,
                is SensorState.AnalyzingCloud -> {
                    AnalyzingProgressView()
                }

                // 8. Final Comprehensive Sensor Report (Persistent Scan Soil button)
                is SensorState.ResultReady -> {
                    SensorReportView(
                        report = state.report,
                        onScanSoilAgain = { viewModel.performSoilScan() },
                        onNavigateToCalibration = onNavigateToCalibration,
                        onDone = onBack,
                        onResetDemo = { viewModel.resetDemo() },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STAGE 1: INITIAL DISCONNECTED VIEW
// -------------------------------------------------------------

@Composable
private fun DisconnectedInitialView(
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_soil_sprout),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Ready to discover soil sensor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Press the button below to scan for nearby AgriX sensor probes.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.sensor_action_find_sensor),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// -------------------------------------------------------------
// STAGE 2: FIRST SCAN -> NO SENSOR FOUND (EMPTY STATE)
// -------------------------------------------------------------

@Composable
private fun Scan1NoSensorView(
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.sensor_no_sensor_detected_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.sensor_no_sensor_detected_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onScanAgain,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.sensor_action_find_sensor),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// -------------------------------------------------------------
// STAGE 3: SECOND SCAN -> SENSOR FOUND
// -------------------------------------------------------------

@Composable
private fun Scan2SensorFoundView(
    device: BleDevice,
    onConnect: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sensor_section_nearby),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onConnect),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "DEMO BLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = "Available • Demo BLE Sensor",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Text(
                        text = "${device.address} • RSSI: ${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.sensor_action_connect),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onScanAgain,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.sensor_action_scan_again),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// -------------------------------------------------------------
// STAGE 4 & 5: CONNECTED DEMO VIEW (READY FOR SOIL INSERTION)
// -------------------------------------------------------------

@Composable
private fun ConnectedDemoView(
    device: BleDevice,
    onScanSoil: () -> Unit,
    onDisconnect: () -> Unit,
    onResetDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_soil_sprout),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "✓ " + device.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        text = "Connection: Demo BLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "🌱 " + stringResource(R.string.sensor_probe_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large CTA Button: SCAN SOIL (remains persistently available)
        Button(
            onClick = onScanSoil,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_soil_sprout),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sensor_action_scan_soil).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sensor_action_disconnect),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = onResetDemo,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sensor_reset_demo),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STAGE 6: ACTIVE SOIL SCAN ANIMATION (2-4 SECONDS)
// -------------------------------------------------------------

@Composable
private fun ScanningSoilProgressView(
    stepName: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(72.dp),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Icon(
                painter = painterResource(R.drawable.ic_soil_sprout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Acquiring Soil Telemetry...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stepName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepItemRow("Preparing sensor...", progress >= 0.15f)
            StepItemRow("Reading raw soil ADC...", progress >= 0.35f)
            StepItemRow("Reading temperature & humidity...", progress >= 0.55f)
            StepItemRow("Estimating soil pH...", progress >= 0.75f)
            StepItemRow("Applying soil context calibration...", progress >= 0.90f)
            StepItemRow("Synthesizing plant-available water...", progress >= 1.00f)
        }
    }
}

@Composable
private fun StepItemRow(label: String, isDone: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (isDone) "✓" else "○",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDone) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = if (isDone) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// -------------------------------------------------------------
// STAGE 7: ANALYZING PROGRESS
// -------------------------------------------------------------

@Composable
private fun AnalyzingProgressView(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Synthesizing AgriX Recommendation...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Analyzing sensor readings, crop profile & soil context...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// -------------------------------------------------------------
// STAGE 8: UNIFIED SMART AGRIX RECOMMENDATION REPORT VIEW
// -------------------------------------------------------------

@Composable
private fun SensorReportView(
    report: CombinedSensorReport,
    onScanSoilAgain: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onDone: () -> Unit,
    onResetDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = report.reading
    val rec = report.recommendation
    val scrollState = rememberScrollState()
    val formattedTime = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(reading.timestamp))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // 1. Telemetry Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AGRI X SENSOR ANALYSIS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = "✓ CONNECTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Sensor: AgriX Probe • Soil: ${reading.soilType} • $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Telemetry Parameter Tiles (2x2 Grid)
        Text(
            text = "CALIBRATED SENSOR TELEMETRY",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TelemetryTile(
                title = "Estimated VWC",
                value = "${reading.estimatedVwc} %",
                badge = when {
                    reading.availableWaterFraction < 0.25 -> "Very Dry"
                    reading.availableWaterFraction <= 0.50 -> "Mod. Dry"
                    reading.availableWaterFraction <= 0.75 -> "Optimal"
                    else -> "High"
                },
                badgeColor = when {
                    reading.availableWaterFraction < 0.25 -> MaterialTheme.colorScheme.error
                    reading.availableWaterFraction <= 0.50 -> Color(0xFFE65100)
                    reading.availableWaterFraction <= 0.75 -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
            )
            TelemetryTile(
                title = "Raw ADC",
                value = "${reading.rawAdc}",
                badge = "Raw Sensor",
                badgeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TelemetryTile(
                title = "Temperature",
                value = "${reading.temperature} °C",
                badge = if (reading.temperature > 32.0) "High" else "Optimal",
                badgeColor = if (reading.temperature > 32.0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                modifier = Modifier.weight(1f),
            )
            TelemetryTile(
                title = "Humidity",
                value = "${reading.humidity} %",
                badge = if (reading.humidity > 80.0) "High" else "Normal",
                badgeColor = if (reading.humidity > 80.0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TelemetryTile(
                title = "Plant-Available Water",
                value = "${(reading.availableWaterFraction * 100).toInt()} %",
                badge = "FC ${reading.fieldCapacity}%",
                badgeColor = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f),
            )
            TelemetryTile(
                title = "Soil pH",
                value = "${reading.soilPH}",
                badge = when {
                    reading.soilPH < 5.8 -> "Acidic"
                    reading.soilPH <= 7.5 -> "Optimal"
                    else -> "Alkaline"
                },
                badgeColor = when {
                    reading.soilPH in 5.8..7.5 -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calibration Shortcut Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToCalibration() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚙ Soil Context ML Calibration",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "View Model Accuracy →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Single Unified SMART AGRIX RECOMMENDATION Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header with Priority
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AGRIX RECOMMENDATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    PriorityBadge(priority = rec.priority)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Crop: ${rec.cropName} • Soil: ${rec.soilType} • Condition: ${rec.overallCondition}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: 🌱 Soil Condition & Water Status
                RecommendationSection(
                    icon = "🌱",
                    title = "Soil & Water Status",
                    content = "${rec.waterStatus} (Available Water: ${(rec.availableWaterFraction * 100).toInt()}%, VWC: ${rec.estimatedVwc}% in ${rec.soilType} soil). ${rec.soilCondition}",
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Section 2: 💧 Watering
                RecommendationSection(
                    icon = "💧",
                    title = "Irrigation Guidance",
                    content = buildString {
                        append(rec.wateringDecision)
                        append("\n• Why: ")
                        append(rec.wateringExplanation)
                        append("\n• When: ")
                        append(rec.wateringTiming)
                        append("\n• Action: ")
                        append(rec.wateringAction)
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Section 3: 🌡 Environment
                RecommendationSection(
                    icon = "🌡",
                    title = "Environment",
                    content = rec.environmentAssessment,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Section 4: 🦠 Disease Prevention
                RecommendationSection(
                    icon = "🦠",
                    title = "Disease Prevention",
                    content = rec.diseasePrevention,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Section 5: 📈 Crop Growth & Yield Guidance
                RecommendationSection(
                    icon = "📈",
                    title = "Yield Support",
                    content = rec.cropGrowthGuidance,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 6: 🎯 ACTION NOW (Highlighted Action Banner)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🎯 ACTION NOW",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = rec.immediateActionSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "✓ AgriX Intelligence Analysis Complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Action: SCAN SOIL AGAIN (Always available!)
        Button(
            onClick = onScanSoilAgain,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_soil_sprout),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sensor_scan_soil_again).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onResetDemo,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sensor_reset_demo),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sensor_done_home),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------
// HELPER SUB-COMPONENTS
// -------------------------------------------------------------

@Composable
private fun RecommendationSection(
    icon: String,
    title: String,
    content: String,
) {
    Column {
        Text(
            text = "$icon $title",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SearchingSpinnerView(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Scanning for nearby sensors...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Searching on simulated BLE channels...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ConnectingCard(
    device: BleDevice,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.sensor_action_connecting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    badge: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

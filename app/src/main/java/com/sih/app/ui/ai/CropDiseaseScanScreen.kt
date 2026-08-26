package com.sih.app.ui.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sih.app.R
import com.sih.app.core.ai.AdvisoryConfidenceLevel
import com.sih.app.core.ai.AdvisoryPresentation
import com.sih.app.core.ai.AdvisoryResult
import com.sih.app.core.ai.AiAnalysisState
import com.sih.app.core.ai.AiResult
import com.sih.app.core.ai.AiRouterMode
import com.sih.app.core.ai.ConfidenceBand
import com.sih.app.core.ai.DiagnosticResult
import com.sih.app.core.ai.DiagnosticStatus
import com.sih.app.core.ai.DiagnosisSource
import com.sih.app.core.ai.DiseasePrediction
import com.sih.app.core.ai.ImageAssessment
import com.sih.app.ui.theme.SIHTheme
import java.io.File

@Composable
fun CropDiseaseScanScreen(
    viewModel: CropDiseaseScanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val selectedCrop by viewModel.selectedCrop.collectAsState()
    val selectedAiMode by viewModel.selectedAiMode.collectAsState()
    val aiAnalysisState by viewModel.aiAnalysisState.collectAsState()
    val advisoryResult by viewModel.advisoryResult.collectAsState()

    CropDiseaseScanContent(
        selectedImageUri = selectedImageUri,
        selectedCrop = selectedCrop,
        supportedCrops = viewModel.supportedCrops,
        selectedAiMode = selectedAiMode,
        aiAnalysisState = aiAnalysisState,
        advisoryResult = advisoryResult,
        onImageSelected = { viewModel.onImageSelected(it) },
        onClearImage = { viewModel.onClearImage() },
        onCropSelected = { viewModel.onCropSelected(it) },
        onAiModeSelected = { viewModel.onAiModeSelected(it) },
        onAnalyze = { viewModel.analyzeCropPhoto() },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun CropDiseaseScanContent(
    selectedImageUri: Uri?,
    selectedCrop: String?,
    supportedCrops: List<String>,
    selectedAiMode: AiRouterMode,
    aiAnalysisState: AiAnalysisState,
    advisoryResult: AdvisoryResult? = null,
    onImageSelected: (Uri?) -> Unit,
    onClearImage: () -> Unit,
    onCropSelected: (String?) -> Unit,
    onAiModeSelected: (AiRouterMode) -> Unit,
    onAnalyze: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    var previewBitmap by remember(selectedImageUri) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(selectedImageUri) {
        previewBitmap = selectedImageUri?.let { uri ->
            ImageUtils.loadDownsampledBitmap(context, uri)
        }
    }

    // Photo Picker Launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    // Camera Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        if (success && currentCameraUri != null) {
            onImageSelected(currentCameraUri)
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            val uri = createTempCameraUri(context)
            currentCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.camera_permission_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun launchCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val uri = createTempCameraUri(context)
            currentCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Header Bar with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.disease_scan_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // STEP 1: Crop Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCropDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.disease_select_crop_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = selectedCrop ?: stringResource(R.string.disease_all_crops_option),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (selectedCrop != null) {
                            Text(
                                text = stringResource(R.string.disease_crop_filter_applied, selectedCrop),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_soil_sprout),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STEP 2: Photo Capture / Upload Area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (previewBitmap != null) {
                        // Image Preview with remove button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.photo_selected),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Clear button
                            IconButton(
                                onClick = onClearImage,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        // Empty photo picker placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_onboarding_recommendations),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.select_leaf_photo),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Camera and Gallery buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { launchCamera() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(stringResource(R.string.camera))
                            }

                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(stringResource(R.string.gallery))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // STEP 3: Analyze Action Button or Analyzing Spinner
            if (aiAnalysisState is AiAnalysisState.Analyzing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.disease_analyzing_progress),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (aiAnalysisState !is AiAnalysisState.Success) {
                Button(
                    onClick = onAnalyze,
                    enabled = selectedImageUri != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.analyze_crop_photo),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Error / Unavailable message
            if (aiAnalysisState is AiAnalysisState.Unavailable) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.ai_model_not_connected),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = aiAnalysisState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            } else if (aiAnalysisState is AiAnalysisState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.ai_analysis_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = aiAnalysisState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            // STEP 4: Diagnosis Results Display
            if (aiAnalysisState is AiAnalysisState.Success) {
                Spacer(modifier = Modifier.height(20.dp))
                DiagnosisResultCard(
                    result = aiAnalysisState.result,
                    advisoryResult = advisoryResult,
                    onRetakePhoto = {
                        onClearImage()
                        launchCamera()
                    },
                    onScanAnother = onClearImage,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Crop Selection Dialog
    if (showCropDialog) {
        AlertDialog(
            onDismissRequest = { showCropDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.disease_select_crop_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCropSelected(null)
                                    showCropDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedCrop == null,
                                onClick = {
                                    onCropSelected(null)
                                    showCropDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.disease_all_crops_option),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedCrop == null) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        HorizontalDivider()
                    }

                    items(supportedCrops) { cropName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCropSelected(cropName)
                                    showCropDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedCrop == cropName,
                                onClick = {
                                    onCropSelected(cropName)
                                    showCropDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cropName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedCrop == cropName) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCropDialog = false }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
        )
    }
}

@Composable
fun DiagnosisResultCard(
    result: AiResult,
    advisoryResult: AdvisoryResult? = null,
    onRetakePhoto: () -> Unit,
    onScanAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diag = result.diagnosticResult
    val isIrrelevant = result.isIrrelevant || result.assessment == ImageAssessment.IRRELEVANT_IMAGE
    val isHealthy = result.isHealthy || result.assessment == ImageAssessment.HEALTHY_CROP || advisoryResult is AdvisoryResult.Healthy

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isIrrelevant -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                isHealthy -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (isIrrelevant) {
                // 1. IRRELEVANT IMAGE STATE
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_onboarding_recommendations),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.image_gate_irrelevant_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.image_gate_irrelevant_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onRetakePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.disease_action_retake),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (isHealthy) {
                // 2. HEALTHY CROP STATE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_soil_sprout),
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.image_gate_healthy_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32),
                    ) {
                        Text(
                            text = stringResource(R.string.image_gate_healthy_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.image_gate_healthy_desc),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.image_gate_healthy_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.advisory_section_monitoring),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))

                val guidanceList = if (advisoryResult is AdvisoryResult.Healthy) {
                    advisoryResult.monitoringGuidance
                } else {
                    listOf(
                        "Continue regular crop monitoring and good agronomic management.",
                        "Maintain balanced fertilization and appropriate irrigation schedules.",
                        "Scout lower canopy leaves weekly for early signs of pest or disease emergence.",
                    )
                }

                guidanceList.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.disease_action_scan_another),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                // 3. CONFIDENT / RECOGNIZED / PROTOTYPE FALLBACK RESULT STATE
                val isFallback = diag?.isPrototypeFallback == true || result.source == DiagnosisSource.DEMO_PROTOTYPE

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isFallback) {
                            stringResource(R.string.disease_possible_diagnosis_title)
                        } else {
                            stringResource(R.string.disease_result_heading)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Confidence Band / AI Source Badge
                    val (badgeText, badgeColor, textColor) = when {
                        result.source == DiagnosisSource.CLOUD_AI -> Triple(
                            "Cloud-Enhanced AI",
                            Color(0xFF1565C0),
                            Color.White,
                        )
                        isFallback -> Triple(
                            stringResource(R.string.disease_prototype_guidance_badge),
                            Color(0xFFE65100),
                            Color.White,
                        )
                        diag?.confidenceBand == ConfidenceBand.HIGH -> Triple(
                            stringResource(R.string.disease_status_confident),
                            Color(0xFF2E7D32),
                            Color.White,
                        )
                        diag?.confidenceBand == ConfidenceBand.MEDIUM -> Triple(
                            stringResource(R.string.disease_status_moderate),
                            Color(0xFFF57F17),
                            Color.White,
                        )
                        diag?.confidenceBand == ConfidenceBand.LOW -> Triple(
                            stringResource(R.string.disease_status_low),
                            Color(0xFFE65100),
                            Color.White,
                        )
                        else -> Triple(
                            stringResource(R.string.disease_status_uncertain),
                            Color.Gray,
                            Color.White,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor,
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Diagnosis
                Text(
                    text = result.disease,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (diag?.selectedCrop != null) {
                    Text(
                        text = "${stringResource(R.string.disease_label_crop)}: ${diag.selectedCrop}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (!isFallback) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.disease_label_confidence)}: ${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Top Possibilities Section (shown when multiple candidates exist or for moderate/low confidence on real model)
                if (!isFallback && diag?.status != DiagnosticStatus.CONFIDENT) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.disease_label_top_possibilities),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val candidates = if (diag != null && diag.cropCompatiblePredictions.isNotEmpty()) {
                        diag.cropCompatiblePredictions
                    } else if (diag != null && diag.topPredictions.isNotEmpty()) {
                        diag.topPredictions
                    } else {
                        emptyList()
                    }

                    if (candidates.isNotEmpty()) {
                        candidates.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${item.rank}. ${item.diseaseName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.rank == 1) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(item.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                // ADVISORY & GUIDANCE SECTION
                if (advisoryResult != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.advisory_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AdvisorySectionView(advisoryResult = advisoryResult)
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.disease_action_scan_another),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun AdvisorySectionView(
    advisoryResult: AdvisoryResult?,
    modifier: Modifier = Modifier,
) {
    when (advisoryResult) {
        is AdvisoryResult.Available -> {
            val pres = advisoryResult.presentation
            Column(modifier = modifier.fillMaxWidth()) {
                // Confidence Notice (only for real model confidence states, never prototype demo)
                if (!pres.noticeMessage.isNullOrBlank() && !pres.isPrototypeFallback) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (pres.confidenceLevel == AdvisoryConfidenceLevel.LOW) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = pres.noticeMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (pres.confidenceLevel == AdvisoryConfidenceLevel.LOW) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Section: What is it?
                if (!pres.overview.isNullOrBlank()) {
                    AdvisoryBlock(
                        title = stringResource(R.string.advisory_section_overview),
                        content = pres.overview,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: Symptoms to check
                if (pres.symptoms.isNotEmpty()) {
                    AdvisoryListBlock(
                        title = stringResource(R.string.advisory_section_symptoms),
                        items = pres.symptoms,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: What to do now (Immediate actions)
                if (pres.immediateActions.isNotEmpty()) {
                    AdvisoryListBlock(
                        title = stringResource(R.string.advisory_section_actions),
                        items = pres.immediateActions,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: Prevention
                if (pres.prevention.isNotEmpty()) {
                    AdvisoryListBlock(
                        title = stringResource(R.string.advisory_section_prevention),
                        items = pres.prevention,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: Monitor
                if (pres.monitoring.isNotEmpty()) {
                    AdvisoryListBlock(
                        title = stringResource(R.string.advisory_section_monitoring),
                        items = pres.monitoring,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: When to seek expert help
                if (!pres.expertEscalation.isNullOrBlank()) {
                    AdvisoryBlock(
                        title = stringResource(R.string.advisory_section_expert),
                        content = pres.expertEscalation,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: Safety note
                if (!pres.safetyNote.isNullOrBlank()) {
                    AdvisoryBlock(
                        title = stringResource(R.string.advisory_section_safety),
                        content = pres.safetyNote,
                    )
                }
            }
        }
        is AdvisoryResult.Unavailable -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.advisory_unavailable_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        is AdvisoryResult.Uncertain -> {
            // Rendered in Uncertain branch of card
        }
        is AdvisoryResult.Healthy -> {
            // Rendered in Healthy branch of card
        }
        null -> {}
    }
}

@Composable
fun AdvisoryBlock(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AdvisoryListBlock(
    title: String,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun createTempCameraUri(context: Context): Uri {
    val tempDir = File(context.cacheDir, "crop_photos")
    if (!tempDir.exists()) {
        tempDir.mkdirs()
    }
    val tempFile = File(tempDir, "temp_crop_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile,
    )
}

@Preview(showBackground = true)
@Composable
private fun CropDiseaseScanScreenPreview() {
    SIHTheme {
        CropDiseaseScanContent(
            selectedImageUri = null,
            selectedCrop = "Tomato",
            supportedCrops = listOf("Corn", "Cucumber", "Potato", "Rice", "Tomato", "Wheat"),
            selectedAiMode = AiRouterMode.LOCAL,
            aiAnalysisState = AiAnalysisState.Idle,
            advisoryResult = null,
            onImageSelected = {},
            onClearImage = {},
            onCropSelected = {},
            onAiModeSelected = {},
            onAnalyze = {},
            onBack = {},
        )
    }
}

# AgriX AI: Android On-Device Integration Plan

## 1. Overview

This document specifies the exact technical blueprint for integrating the trained **AgriX MobileNetV3 TFLite Plant Disease Classification Model** into the Android application in subsequent slices.

---

## 2. End-to-End Inference Pipeline

```
  Farmer captures/picks image
              │
              ↓
   [ Bitmap Preprocessing ]
   • Decode Uri from Gallery/Camera
   • Center-crop / aspect-ratio scale to 224×224 px
   • Extract RGB pixels into DirectByteBuffer (Float32 or Uint8)
   • Normalize: (pixel / 255.0 - mean) / std
              │
              ↓
  [ LocalAiEngine.kt / TFLite Interpreter ]
   • Load disease_model.tflite from assets/
   • Execute native inference via TensorFlow Lite / LiteRT (CPU NEON)
   • Obtain 20-class raw logits / softmax probabilities
              │
              ↓
  [ Output Interpretation & Confidence Engine ]
   • Find argmax class index & max probability
   • Apply Confidence Tiering (High / Medium / Low)
   • Incorporate Farm Crop Context Hint (if available from FarmEntity)
              │
              ↓
  [ Multilingual Diagnosis & Recommendation Engine ]
   • Map class_id to localized strings (English, Hindi, Tamil, Telugu, Kannada, Malayalam)
   • Provide immediate, actionable organic & chemical recommendations
              │
              ↓
  [ UI Display (CropDiseaseScanScreen.kt) ]
   • Diagnostic Card + Confidence Badge + Severity + Action Steps
```

---

## 3. Image Preprocessing Specifications

```kotlin
// Target Input Tensor Shape: [1, 224, 224, 3] (Batch, Height, Width, RGB Channels)
const val INPUT_IMAGE_SIZE = 224
const val CHANNELS_COUNT = 3
const val BYTES_PER_CHANNEL = 4 // Float32

// Normalization Parameters (ImageNet Standard)
val NORM_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
val NORM_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
```

### Preprocessing Flow:
1. **Aspect-Ratio Preserved Scaling**:
   - Avoid distorting lesion aspect ratios. The photo is centrally cropped and resized to $224 \times 224$ pixels.
2. **Buffer Allocation**:
   - Allocate `ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())`.
3. **RGB Channel Extraction**:
   - Iterate through the 224×224 pixels, extract Red, Green, and Blue channels, normalize each channel:
     $$\text{normalized} = \frac{\frac{\text{pixelValue}}{255.0} - \text{mean}_c}{\text{std}_c}$$

---

## 4. Confidence Tiering & Diagnostic Logic

To protect farmers from misdiagnosis caused by blurry photos, poor lighting, or background clutter, AgriX applies a **3-tier confidence decision matrix**:

```
               Softmax Confidence Score
        0%                     50%             80%            100%
        ├───────────────────────┼───────────────┼───────────────┤
        │      LOW CONFIDENCE   │  MEDIUM CONF  │ HIGH CONFID.  │
        │  "Please Retake Photo"│ "Likely X,    │ "Confirmed X" │
        │                       │  Also check Y"│               │
        └───────────────────────┴───────────────┴───────────────┘
```

### Confidence Tiers:

#### 1. High Confidence ($\ge 80\%$)
- **Action**: Confirmed diagnosis displayed with high-confidence green indicator.
- **UI Presentation**: Shows precise disease name, pathogen, severity rating, and detailed actionable steps (organic bio-control & conventional management).

#### 2. Medium Confidence ($50\% - 79\%$)
- **Action**: Cautious diagnosis displayed with amber indicator.
- **UI Presentation**: Shows the most probable diagnosis, differential secondary possibility (top-2 class), and prompts the farmer to verify visible symptoms against the listed visual signs.

#### 3. Low Confidence ($< 50\%$)
- **Action**: Inconclusive diagnosis.
- **UI Presentation**: Gracefully informs the farmer that the leaf photo is unclear or out-of-distribution (e.g. hand in frame, severe blur, low contrast) and provides practical photo tips with a one-tap "Retake Photo" button.

---

## 5. Model Output Contract (`DiseasePrediction.kt`)

```kotlin
data class DiseasePrediction(
    val classId: String,          // e.g. "tomato_early_blight"
    val crop: String,             // e.g. "Tomato"
    val diseaseName: String,      // e.g. "Early Blight"
    val confidence: Float,        // e.g. 0.93f
    val confidenceTier: ConfidenceTier, // HIGH, MEDIUM, LOW
    val secondaryPrediction: SecondaryPrediction? = null,
    val modelVersion: String = "1.0.0",
)

enum class ConfidenceTier {
    HIGH,
    MEDIUM,
    LOW,
}

data class SecondaryPrediction(
    val classId: String,
    val diseaseName: String,
    val confidence: Float,
)
```

---

## 6. Offline Multilingual Knowledge Repository

To ensure 100% offline functionality, all disease definitions, symptom checklists, organic solutions, chemical controls, and preventive measures will be indexed by `classId` in a static local repository or JSON asset mapped to string resources:

```
classId: "tomato_early_blight"
├── Symptom Key: R.string.disease_tomato_early_blight_symptoms
├── Recommendation Key: R.string.disease_tomato_early_blight_treatment
└── Prevention Key: R.string.disease_tomato_early_blight_prevention
```

Translations for all supported languages (English, Hindi, Tamil, Telugu, Kannada, Malayalam) will reside in the app's `res/values-*/strings.xml`, guaranteeing seamless offline access in every farmer's native language.

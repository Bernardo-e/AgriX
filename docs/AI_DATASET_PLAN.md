# AgriX AI: Plant Disease Dataset & Scope Specification

## 1. Executive Summary

This document specifies the dataset strategy, crop/disease scope, and data quality guidelines for the **AgriX On-Device Crop Disease Diagnostic System**. 

The goal is to build an accurate, low-latency, mobile-first image classification model that operates entirely offline on a 4 GB RAM Android smartphone in rural India.

---

## 2. Dataset Landscape & Candidate Evaluation

We investigated the primary open-access plant pathology datasets available in agricultural computer vision research:

| Dataset Name | Source / Authors | Images Count | Crops Covered | Classes Count | Environment | License |
|---|---|---|---|---|---|---|
| **PlantVillage** | Hughes & Salathé (Penn State / EPFL) | 54,305 | 14 species | 38 classes | Controlled lab (single leaf on neutral grey/black sheet) | CC0 / CC BY 4.0 |
| **PlantDoc** | Singh et al. (IIT Delhi) | 2,598 | 13 species | 27 classes | Natural field conditions (uncontrolled lighting, backgrounds, soil, multiple leaves) | CC BY 4.0 |
| **New Plant Diseases Dataset (Augmented)** | Geetharamani & Pandian (Kaggle) | 87,039 | 14 species | 38 classes | Augmented lab & controlled | Open / CC BY-SA |
| **Rice Leaf Disease Dataset** | UCI / Mendeley (Prajapati et al.) | ~3,355 | Rice | 4 classes (Bacterial blight, Brown spot, Leaf blast, Tungro) | Field & semi-controlled | CC BY 4.0 |
| **Cotton Leaf Disease Dataset** | Mendeley / Kaggle | ~2,300 | Cotton | 4 classes (Bacterial blight, Curl virus, Fusarium wilt, Healthy) | Real field | CC BY 4.0 |
| **DigiPathos** | Embrapa (Brazil) | ~4,000 | Multiple tropical crops | Variable | Real field & lab | Research Only |

### Critical Comparison: Controlled Lab vs. Real-World Field Images

```
[ PlantVillage (Lab) ]              [ PlantDoc / Real Field ]
┌─────────────────────────┐          ┌─────────────────────────┐
│ • Single detached leaf  │          │ • Attached leaf on stem │
│ • Solid grey background │          │ • Soil / sun / shadows  │
│ • Uniform lighting      │          │ • Variable focus/blur   │
│ • High class volume     │          │ • Lower sample volume   │
│ ⚠️ Risk: Shortcut       │          │ ✓ Realistic farmer view │
│    learning on background│         │                         │
└─────────────────────────┘          └─────────────────────────┘
```

#### Key Findings & Risks:
1. **Shortcut Learning in Lab Datasets**: Models trained exclusively on PlantVillage achieve 99%+ lab test accuracy but often drop to <50% in real agricultural fields because the neural network learns solid background textures and lighting artifacts rather than genuine pathological lesion morphology.
2. **Field Dataset Scarcity**: Real-world field datasets (like PlantDoc) have fewer images per class and high visual variance, making cold-start training from scratch prone to overfitting.
3. **Class Imbalance**: Common diseases (e.g., Tomato Early Blight) have thousands of images, while emerging diseases may only have dozens.

---

## 3. Recommended Dataset Strategy: Hybrid Curriculum

To achieve high real-world accuracy without requiring millions of custom field photos upfront, AgriX adopts a **two-phase hybrid data pipeline**:

```
[ Phase 1: Base Pretraining ]
  Broad feature extraction on curated subset of PlantVillage / New Plant Diseases
            ↓
[ Phase 2: Domain Adaptation & Fine-Tuning ]
  Fine-tuning with heavy field augmentations + PlantDoc & real-world Indian crop datasets
            ↓
[ Phase 3: Field Validation ]
  Evaluation on an independent field test set never seen during training
```

---

## 4. Initial Crop & Disease Scope for AgriX (SIH Prototype)

To maintain high diagnostic reliability for Indian smallholder farmers, we deliberately bound our Phase 1 prototype to **6 high-priority Indian crops** spanning **20 clinically and visually distinct classes** (including Healthy controls for every crop):

### Target Scope (20 Classes)

| Crop | Class Identifier (`class_id`) | Common Disease Name | Pathogen Type | Visual Signature |
|---|---|---|---|---|
| **Tomato** | `tomato_healthy` | Healthy Leaf | N/A | Smooth green margin, no spots |
| | `tomato_early_blight` | Early Blight | Fungus (*Alternaria solani*) | Concentric rings ("target-board" spots) |
| | `tomato_late_blight` | Late Blight | Oomycete (*Phytophthora infestans*) | Water-soaked dark brown lesions, white mold |
| | `tomato_leaf_mold` | Leaf Mold | Fungus (*Passalora fulva*) | Pale yellow spots on upper, olive mold under |
| | `tomato_yellow_leaf_curl` | Yellow Leaf Curl Virus | Virus (TYLCV, Whitefly vector) | Upward curling, yellowing margins, stunted |
| **Potato** | `potato_healthy` | Healthy Leaf | N/A | Vibrant green, compound leaf intact |
| | `potato_early_blight` | Early Blight | Fungus (*Alternaria solani*) | Brown concentric ring spots on older leaves |
| | `potato_late_blight` | Late Blight | Oomycete (*Phytophthora infestans*) | Purplish-black lesions with pale halos |
| **Rice** | `rice_healthy` | Healthy Leaf | N/A | Long slender uniform green blade |
| | `rice_bacterial_blight` | Bacterial Leaf Blight | Bacteria (*Xanthomonas oryzae*) | Wavy yellow-white lesions along leaf margins |
| | `rice_brown_spot` | Brown Spot | Fungus (*Bipolaris oryzae*) | Oval reddish-brown spots with grey centers |
| | `rice_leaf_blast` | Leaf Blast | Fungus (*Magnaporthe oryzae*) | Diamond/spindle-shaped lesions with dark margins |
| **Cotton** | `cotton_healthy` | Healthy Leaf | N/A | Broad palmate green leaf |
| | `cotton_bacterial_blight` | Bacterial Blight (Angular Spot)| Bacteria (*Xanthomonas citri*) | Angular water-soaked lesions bounded by veins |
| | `cotton_curl_virus` | Cotton Leaf Curl Virus | Virus (CLCuD) | Upward/downward leaf curling, thick veins |
| **Maize (Corn)**| `maize_healthy` | Healthy Leaf | N/A | Clean linear leaf blade |
| | `maize_common_rust` | Common Rust | Fungus (*Puccinia sorghi*) | Cinnamon-brown powdery pustules |
| | `maize_northern_blight`| Northern Corn Leaf Blight | Fungus (*Exserohilum turcicum*) | Long elliptical tan/grey cigar-shaped lesions |
| **Chili (Pepper)**| `chili_healthy` | Healthy Leaf | N/A | Smooth oval dark green blade |
| | `chili_bacterial_spot` | Bacterial Spot | Bacteria (*Xanthomonas campestris*) | Small circular dark lesions with yellow halo |

---

## 5. Dataset Statistics & Partitioning

For this 20-class scope, the target curated dataset contains approximately **22,000 images**:

- **Training Split (70%)**: ~15,400 images
- **Validation Split (15%)**: ~3,300 images (used for hyperparameter tuning & early stopping)
- **Test Split (15%)**: ~3,300 images (stratified evaluation set including real-world field imagery)

```
Total Curated Dataset: ~22,000 Images
├── Training   (70%) : 15,400 images
├── Validation (15%) :  3,300 images
└── Test Set   (15%) :  3,300 images (Field-focused)
```

---

## 6. Licensing & Legal Compliance

1. **PlantVillage Dataset**: Open access under **Creative Commons Attribution 4.0 International (CC BY 4.0)** and public domain (CC0).
2. **PlantDoc Dataset**: Released under **CC BY 4.0** by researchers from IIT Delhi.
3. **Attribution**: Proper academic and legal attribution will be maintained in the `README.md` and licensing disclosures of the ML codebase.
4. **Commercial / Academic Freedom**: All selected sources permit modification, redistribution, and on-device deployment in open agricultural tooling.

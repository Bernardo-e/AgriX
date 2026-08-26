# AgriX ML: Crop Disease Classification Pipeline

This repository contains the standalone machine learning training, evaluation, and edge model export pipeline for the **AgriX On-Device Plant Disease Diagnostic System**.

---

## 1. Project Directory Structure

```
ml/
├── README.md                  # Project overview & operational instructions
├── requirements.txt           # Python ML dependencies
├── configs/
│   └── training_config.yaml  # Hyperparameters, architecture, and dataset paths
├── data/
│   ├── raw/                  # Raw dataset downloads (ignored in git)
│   └── processed/            # Processed & stratified train/val/test splits
├── notebooks/
│   ├── 01_exploratory_data_analysis.ipynb  # Dataset visualization & class balance analysis
│   └── 02_training_pipeline.ipynb          # Cloud GPU (Colab / Kaggle) training notebook
├── src/
│   ├── dataset/
│   │   └── data_loader.py    # Dataset loading & PyTorch DataLoader builders
│   ├── preprocessing/
│   │   └── augmentations.py  # Albumentations field-simulation pipelines
│   ├── training/
│   │   └── train.py          # Reproducible training loop with mixed precision & early stopping
│   ├── evaluation/
│   │   ├── evaluate.py       # Comprehensive test evaluation & metrics generation
│   │   └── metrics.py        # Top-1/Top-3, Precision, Recall, F1, Confusion Matrix
│   └── export/
│       └── export_tflite.py  # INT8 / FP16 TFLite quantization & metadata export
├── models/                   # Saved checkpoints & exported .tflite binaries (ignored in git)
└── reports/                  # Evaluation plots, confusion matrices, and metrics JSONs
```

---

## 2. Environment Setup

### Prerequisites
- Python 3.10+
- NVIDIA GPU with CUDA (recommended for fast training) or modern multi-core CPU

### Installation
```bash
# Navigate to the ml directory
cd ml

# Create and activate a Python virtual environment
python -m venv .venv
source .venv/bin/activate   # On Linux/macOS
# or: .venv\Scripts\activate # On Windows

# Install required dependencies
pip install --upgrade pip
pip install -r requirements.txt
```

---

## 3. Dataset Preparation

1. Download the curated plant pathology datasets (PlantVillage, PlantDoc, specialized Indian crop datasets).
2. Place the image folders into `ml/data/raw/` organized by class label:
   ```
   ml/data/raw/
   ├── tomato_healthy/
   ├── tomato_early_blight/
   ├── potato_late_blight/
   └── ...
   ```
3. Run the data loader to verify stratification and class balancing.

---

## 4. Model Training

Run the training script using the centralized configuration:
```bash
python src/training/train.py --config configs/training_config.yaml
```

The script will:
- Load MobileNetV3-Large with pretrained ImageNet weights.
- Apply domain-specific augmentations (flips, random crops, color jitter, blur).
- Train with Cosine Annealing learning rate schedule and AdamW optimizer.
- Save best validation checkpoints to `models/best_model.pth`.

---

## 5. Evaluation

Evaluate the trained checkpoint on the independent test set:
```bash
python src/evaluation/evaluate.py --config configs/training_config.yaml --checkpoint models/best_model.pth
```
This generates:
- Classification report (Precision, Recall, F1 per disease class)
- Normalized Confusion Matrix plot (`reports/confusion_matrix.png`)
- Confidence calibration curves

---

## 6. Exporting to Mobile TFLite

Export the best checkpoint to an optimized INT8 quantized `.tflite` model:
```bash
python src/export/export_tflite.py --config configs/training_config.yaml --checkpoint models/best_model.pth --output models/disease_model.tflite --quantize int8
```

The resulting `disease_model.tflite` can be placed into the Android app's `app/src/main/assets/` folder for offline on-device inference.

"""
AgriX Independent Test Set Evaluation Script
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import timm
import torch
import torch.nn as nn
import torch.nn.functional as F
import yaml
from tqdm import tqdm

sys.path.append(str(Path(__file__).resolve().parents[2]))

from src.dataset.data_loader import create_stratified_data_loaders
from src.evaluation.metrics import compute_classification_metrics, plot_and_save_confusion_matrix
from src.preprocessing.augmentations import get_eval_transforms


def load_config(config_path: str) -> Dict:
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def main():
    parser = argparse.ArgumentParser(description="AgriX Test Evaluation")
    parser.add_argument("--config", type=str, default="configs/training_config.yaml", help="Path to config YAML")
    parser.add_argument("--checkpoint", type=str, default="models/best_model.pth", help="Path to saved model checkpoint")
    args = parser.parse_args()

    cfg = load_config(args.config)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"🔍 Running AgriX Evaluation on Device: {device}")

    reports_dir = Path(cfg["export"].get("reports_dir", "reports"))
    reports_dir.mkdir(parents=True, exist_ok=True)
    raw_data_dir = cfg["dataset"]["raw_data_dir"]
    class_names = cfg["dataset"]["classes"]
    image_size = cfg["dataset"]["image_size"]

    eval_transform = get_eval_transforms(image_size)

    # 1. Load Test DataLoader
    try:
        _, _, test_loader, class_to_idx, _ = create_stratified_data_loaders(
            data_dir=raw_data_dir,
            class_names=class_names,
            train_transform=eval_transform,
            eval_transform=eval_transform,
            train_split=cfg["dataset"]["train_split"],
            val_split=cfg["dataset"]["val_split"],
            test_split=cfg["dataset"]["test_split"],
            batch_size=cfg["training"]["batch_size"],
            num_workers=cfg["dataset"]["num_workers"],
            random_seed=cfg["dataset"]["random_seed"],
        )
    except ValueError as e:
        print(f"⚠️ Dataset Warning: {e}")
        return

    # 2. Rebuild Architecture & Load Weights
    checkpoint_path = Path(args.checkpoint)
    if not checkpoint_path.exists():
        print(f"❌ Checkpoint not found at: {checkpoint_path}")
        print("   Train the model first using: python src/training/train.py")
        return

    model_name = cfg["model"]["architecture"]
    model = timm.create_model(
        model_name,
        pretrained=False,
        num_classes=len(class_names),
    ).to(device)

    print(f"📦 Loading weights from {checkpoint_path}...")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    state_dict = checkpoint.get("model_state_dict", checkpoint)
    model.load_state_dict(state_dict)
    model.eval()

    # 3. Test Inference Loop
    all_preds: List[int] = []
    all_targets: List[int] = []
    all_probs_list: List[np.ndarray] = []

    print("🚀 Evaluating on independent test set...")
    with torch.no_grad():
        for images, targets in tqdm(test_loader, desc="Testing"):
            images = images.to(device, non_blocking=True)
            outputs = model(images)
            probs = F.softmax(outputs, dim=1).cpu().numpy()
            preds = np.argmax(probs, axis=1)

            all_preds.extend(preds.tolist())
            all_targets.extend(targets.tolist())
            all_probs_list.append(probs)

    all_probs = np.vstack(all_probs_list)

    # 4. Metrics & Reports
    metrics = compute_classification_metrics(all_targets, all_preds, all_probs, class_names)
    print("\n" + "=" * 50)
    print("📊 AGRIX MODEL EVALUATION REPORT")
    print("=" * 50)
    print(f"Test Samples    : {metrics['total_samples']}")
    print(f"Top-1 Accuracy  : {metrics['top1_accuracy']}%")
    print(f"Top-3 Accuracy  : {metrics['top3_accuracy']}%")
    print(f"Macro F1-Score  : {metrics['macro_avg']['f1_score']}")
    print(f"Weighted F1     : {metrics['weighted_avg']['f1_score']}")
    print("=" * 50)

    # Save JSON metrics report
    metrics_path = reports_dir / "evaluation_metrics.json"
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    print(f"📁 Metrics saved to: {metrics_path}")

    # Plot confusion matrix
    cm_path = reports_dir / "confusion_matrix.png"
    plot_and_save_confusion_matrix(all_targets, all_preds, class_names, str(cm_path))
    print(f"🖼️ Confusion matrix plot saved to: {cm_path}")


if __name__ == "__main__":
    main()

"""
AgriX Machine Learning Training Script
Trains MobileNetV3 with PyTorch, mixed-precision, and cosine annealing schedule.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, Tuple

import timm
import torch
import torch.nn as nn
from torch.optim import AdamW, SGD
from torch.optim.lr_scheduler import CosineAnnealingLR
import yaml
from tqdm import tqdm

# Add project root to sys.path
sys.path.append(str(Path(__file__).resolve().parents[2]))

from src.dataset.data_loader import create_stratified_data_loaders
from src.preprocessing.augmentations import get_eval_transforms, get_train_transforms


def load_config(config_path: str) -> Dict:
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def build_model(
    architecture: str,
    num_classes: int,
    pretrained: bool = True,
    drop_rate: float = 0.2,
) -> nn.Module:
    """Instantiates a lightweight vision model using timm."""
    model = timm.create_model(
        architecture,
        pretrained=pretrained,
        num_classes=num_classes,
        drop_rate=drop_rate,
    )
    return model


def train_one_epoch(
    model: nn.Module,
    loader: torch.utils.data.DataLoader,
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    scaler: torch.amp.GradScaler,
    device: torch.device,
    use_amp: bool,
) -> Tuple[float, float]:
    model.train()
    running_loss = 0.0
    correct = 0
    total = 0

    for images, targets in tqdm(loader, desc="Training", leave=False):
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)

        optimizer.zero_grad(set_to_none=True)

        with torch.amp.autocast("cuda", enabled=use_amp):
            outputs = model(images)
            loss = criterion(outputs, targets)

        if use_amp:
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
        else:
            loss.backward()
            optimizer.step()

        running_loss += loss.item() * images.size(0)
        _, preds = outputs.max(dim=1)
        correct += preds.eq(targets).sum().item()
        total += targets.size(0)

    epoch_loss = running_loss / total
    epoch_acc = (correct / total) * 100.0
    return epoch_loss, epoch_acc


@torch.no_grad()
def evaluate_validation(
    model: nn.Module,
    loader: torch.utils.data.DataLoader,
    criterion: nn.Module,
    device: torch.device,
) -> Tuple[float, float]:
    model.eval()
    running_loss = 0.0
    correct = 0
    total = 0

    for images, targets in tqdm(loader, desc="Validation", leave=False):
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)

        outputs = model(images)
        loss = criterion(outputs, targets)

        running_loss += loss.item() * images.size(0)
        _, preds = outputs.max(dim=1)
        correct += preds.eq(targets).sum().item()
        total += targets.size(0)

    epoch_loss = running_loss / total
    epoch_acc = (correct / total) * 100.0
    return epoch_loss, epoch_acc


def main():
    parser = argparse.ArgumentParser(description="AgriX Plant Disease Training Loop")
    parser.add_argument("--config", type=str, default="configs/training_config.yaml", help="Path to config YAML")
    args = parser.parse_args()

    cfg = load_config(args.config)

    # 1. Device Setup
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"🚀 AgriX Training Initialized on Device: {device}")
    if torch.cuda.is_available():
        print(f"   GPU Model: {torch.cuda.get_device_name(0)}")

    # 2. Paths and Directories
    models_dir = Path(cfg["export"]["checkpoint_dir"])
    models_dir.mkdir(parents=True, exist_ok=True)
    raw_data_dir = cfg["dataset"]["raw_data_dir"]
    class_names = cfg["dataset"]["classes"]
    image_size = cfg["dataset"]["image_size"]

    # 3. Data Transformations
    train_transform = get_train_transforms(image_size, cfg.get("augmentation", {}))
    eval_transform = get_eval_transforms(image_size)

    # 4. DataLoaders
    print(f"📦 Loading dataset from '{raw_data_dir}' with {len(class_names)} classes...")
    try:
        train_loader, val_loader, test_loader, class_to_idx, class_weights = create_stratified_data_loaders(
            data_dir=raw_data_dir,
            class_names=class_names,
            train_transform=train_transform,
            eval_transform=eval_transform,
            train_split=cfg["dataset"]["train_split"],
            val_split=cfg["dataset"]["val_split"],
            test_split=cfg["dataset"]["test_split"],
            batch_size=cfg["training"]["batch_size"],
            num_workers=cfg["dataset"]["num_workers"],
            random_seed=cfg["dataset"]["random_seed"],
        )
        print(f"   Train Batches: {len(train_loader)} | Val Batches: {len(val_loader)} | Test Batches: {len(test_loader)}")
    except ValueError as e:
        print(f"⚠️ Dataset Warning: {e}")
        print("   Scaffolding verified. Place raw dataset images in 'ml/data/raw/' to execute real training.")
        return

    # Save class mapping
    class_map_path = models_dir / "class_labels.json"
    with open(class_map_path, "w", encoding="utf-8") as f:
        json.dump(class_to_idx, f, indent=2)
    print(f"   Saved class mapping to: {class_map_path}")

    # 5. Model Architecture
    model_name = cfg["model"]["architecture"]
    num_classes = len(class_names)
    print(f"🧠 Building Model: {model_name} (Classes: {num_classes})")
    model = build_model(
        architecture=model_name,
        num_classes=num_classes,
        pretrained=cfg["model"]["pretrained"],
        drop_rate=cfg["model"]["drop_rate"],
    ).to(device)

    # 6. Loss & Optimization
    class_weights = class_weights.to(device)
    criterion = nn.CrossEntropyLoss(
        weight=class_weights,
        label_smoothing=cfg["training"].get("label_smoothing", 0.0),
    )

    opt_choice = cfg["training"]["optimizer"].lower()
    lr = float(cfg["training"]["learning_rate"])
    weight_decay = float(cfg["training"]["weight_decay"])

    if opt_choice == "adamw":
        optimizer = AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)
    else:
        optimizer = SGD(model.parameters(), lr=lr, momentum=0.9, weight_decay=weight_decay)

    epochs = cfg["training"]["epochs"]
    scheduler = CosineAnnealingLR(
        optimizer,
        T_max=epochs,
        eta_min=float(cfg["training"].get("min_learning_rate", 1e-6)),
    )

    use_amp = cfg["training"].get("mixed_precision", True) and torch.cuda.is_available()
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)

    # 7. Training Loop with Early Stopping
    best_val_acc = 0.0
    patience = cfg["training"].get("early_stopping_patience", 7)
    patience_counter = 0
    best_model_path = models_dir / "best_model.pth"

    print("\n--- Training Loop Started ---")
    for epoch in range(1, epochs + 1):
        train_loss, train_acc = train_one_epoch(
            model=model,
            loader=train_loader,
            criterion=criterion,
            optimizer=optimizer,
            scaler=scaler,
            device=device,
            use_amp=use_amp,
        )

        val_loss, val_acc = evaluate_validation(
            model=model,
            loader=val_loader,
            criterion=criterion,
            device=device,
        )

        scheduler.step()
        current_lr = scheduler.get_last_lr()[0]

        print(
            f"Epoch [{epoch:02d}/{epochs:02d}] "
            f"LR: {current_lr:.6f} | "
            f"Train Loss: {train_loss:.4f}, Acc: {train_acc:.2f}% | "
            f"Val Loss: {val_loss:.4f}, Acc: {val_acc:.2f}%"
        )

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            patience_counter = 0
            torch.save({
                "epoch": epoch,
                "model_state_dict": model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "val_acc": val_acc,
                "class_names": class_names,
                "config": cfg,
            }, best_model_path)
            print(f"   ★ Best model saved ({val_acc:.2f}%) -> {best_model_path}")
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print(f"\n⏹ Early stopping triggered after {patience} epochs without improvement.")
                break

    print(f"\n✅ Training Complete. Best Validation Accuracy: {best_val_acc:.2f}%")


if __name__ == "__main__":
    main()

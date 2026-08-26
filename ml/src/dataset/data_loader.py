"""
AgriX Dataset Loader & Preprocessing Pipeline
"""

import os
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
from PIL import Image
import torch
from torch.utils.data import DataLoader, Dataset
from sklearn.model_selection import train_test_split


class PlantDiseaseDataset(Dataset):
    """PyTorch Dataset for plant leaf disease classification."""

    def __init__(
        self,
        image_paths: List[str],
        labels: List[int],
        transform=None,
    ):
        self.image_paths = image_paths
        self.labels = labels
        self.transform = transform

    def __len__(self) -> int:
        return len(self.image_paths)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, int]:
        image_path = self.image_paths[idx]
        image = Image.open(image_path).convert("RGB")
        image_np = np.array(image)

        if self.transform is not None:
            augmented = self.transform(image=image_np)
            image_tensor = augmented["image"]
        else:
            # Fallback basic tensor conversion
            image_tensor = torch.from_numpy(image_np).permute(2, 0, 1).float() / 255.0

        label = self.labels[idx]
        return image_tensor, label


def scan_dataset_directory(
    data_dir: str,
    class_names: List[str],
) -> Tuple[List[str], List[int], Dict[str, int]]:
    """
    Scans a directory where subdirectories represent class names.
    Returns list of valid image filepaths, corresponding integer labels, and class_to_idx map.
    """
    class_to_idx = {name: idx for idx, name in enumerate(class_names)}
    image_paths: List[str] = []
    labels: List[int] = []

    valid_extensions = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
    data_path = Path(data_dir)

    for class_name in class_names:
        class_folder = data_path / class_name
        if not class_folder.exists() or not class_folder.is_dir():
            continue

        for entry in class_folder.iterdir():
            if entry.is_file() and entry.suffix.lower() in valid_extensions:
                image_paths.append(str(entry.resolve()))
                labels.append(class_to_idx[class_name])

    return image_paths, labels, class_to_idx


def create_stratified_data_loaders(
    data_dir: str,
    class_names: List[str],
    train_transform,
    eval_transform,
    train_split: float = 0.70,
    val_split: float = 0.15,
    test_split: float = 0.15,
    batch_size: int = 32,
    num_workers: int = 4,
    random_seed: int = 42,
) -> Tuple[DataLoader, DataLoader, DataLoader, Dict[str, int], torch.Tensor]:
    """
    Creates stratified Train, Validation, and Test DataLoaders.
    Also returns class_to_idx and computed inverse-class-frequency weights for balanced loss.
    """
    image_paths, labels, class_to_idx = scan_dataset_directory(data_dir, class_names)

    if not image_paths:
        raise ValueError(
            f"No images found in dataset directory: '{data_dir}'. "
            f"Ensure subdirectories match specified classes: {class_names}"
        )

    # 1. Stratified split: Train vs Temp (Val + Test)
    train_paths, temp_paths, train_labels, temp_labels = train_test_split(
        image_paths,
        labels,
        test_size=(val_split + test_split),
        stratify=labels,
        random_state=random_seed,
    )

    # 2. Stratified split: Val vs Test
    val_ratio = val_split / (val_split + test_split)
    val_paths, test_paths, val_labels, test_labels = train_test_split(
        temp_paths,
        temp_labels,
        test_size=(1.0 - val_ratio),
        stratify=temp_labels,
        random_state=random_seed,
    )

    # 3. Instantiate datasets
    train_dataset = PlantDiseaseDataset(train_paths, train_labels, transform=train_transform)
    val_dataset = PlantDiseaseDataset(val_paths, val_labels, transform=eval_transform)
    test_dataset = PlantDiseaseDataset(test_paths, test_labels, transform=eval_transform)

    # 4. Compute class weights for loss weighting
    class_counts = np.bincount(train_labels, minlength=len(class_names))
    total_samples = len(train_labels)
    class_weights = total_samples / (len(class_names) * np.maximum(class_counts, 1).astype(np.float32))
    class_weights_tensor = torch.tensor(class_weights, dtype=torch.float32)

    # 5. Build DataLoaders
    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=True,
        num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
        drop_last=True,
    )

    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
    )

    test_loader = DataLoader(
        test_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
    )

    return train_loader, val_loader, test_loader, class_to_idx, class_weights_tensor

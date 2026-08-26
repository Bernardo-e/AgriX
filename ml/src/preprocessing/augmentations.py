"""
AgriX Image Augmentation & Preprocessing Pipelines
Uses Albumentations for fast, mobile-relevant field transformations.
"""

from typing import Any, Dict
import albumentations as A
from albumentations.pytorch import ToTensorV2

# Standard ImageNet normalization parameters (matched in Android app)
IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)


def get_train_transforms(
    image_size: int = 224,
    aug_config: Dict[str, Any] = None,
) -> A.Compose:
    """
    Returns aggressive training augmentations designed to simulate real-world
    field conditions (sunlight, shadows, leaf orientation, minor camera blur).
    """
    cfg = aug_config or {}

    return A.Compose([
        A.RandomResizedCrop(
            size=(image_size, image_size),
            scale=(0.8, 1.0),
            ratio=(0.9, 1.1),
            p=1.0,
        ),
        A.HorizontalFlip(p=cfg.get("horizontal_flip_prob", 0.5)),
        A.VerticalFlip(p=cfg.get("vertical_flip_prob", 0.3)),
        A.Rotate(
            limit=cfg.get("random_rotate_degrees", 30),
            border_mode=0,
            p=0.7,
        ),
        A.ColorJitter(
            brightness=cfg.get("color_jitter_brightness", 0.2),
            contrast=cfg.get("color_jitter_contrast", 0.2),
            saturation=cfg.get("color_jitter_saturation", 0.2),
            hue=0.05,
            p=0.6,
        ),
        A.OneOf([
            A.GaussianBlur(blur_limit=(3, 5), p=0.5),
            A.MotionBlur(blur_limit=(3, 5), p=0.5),
        ], p=cfg.get("gaussian_blur_prob", 0.2)),
        A.CoarseDropout(
            num_holes_range=(1, 4),
            hole_height_range=(16, 32),
            hole_width_range=(16, 32),
            fill=0,
            p=cfg.get("cutout_prob", 0.3),
        ),
        A.Normalize(
            mean=IMAGENET_MEAN,
            std=IMAGENET_STD,
            max_pixel_value=255.0,
        ),
        ToTensorV2(),
    ])


def get_eval_transforms(image_size: int = 224) -> A.Compose:
    """
    Returns deterministic validation and test evaluation transformations.
    Matches the Android on-device preprocessing pipeline.
    """
    return A.Compose([
        A.Resize(image_size, image_size),
        A.Normalize(
            mean=IMAGENET_MEAN,
            std=IMAGENET_STD,
            max_pixel_value=255.0,
        ),
        ToTensorV2(),
    ])

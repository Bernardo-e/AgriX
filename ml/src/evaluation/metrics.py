"""
AgriX Model Evaluation & Diagnostic Metrics Computation
"""

import json
from pathlib import Path
from typing import Dict, List, Tuple

import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support


def compute_classification_metrics(
    y_true: List[int],
    y_pred: List[int],
    y_probs: np.ndarray,
    class_names: List[str],
) -> Dict:
    """Computes Top-1, Top-3, Precision, Recall, F1, and per-class diagnostics."""
    total_samples = len(y_true)
    top1_correct = sum(t == p for t, p in zip(y_true, y_pred))
    top1_acc = (top1_correct / total_samples) * 100.0

    # Top-3 Accuracy
    top3_preds = np.argsort(y_probs, axis=1)[:, -3:]
    top3_correct = sum(t in top3 for t, top3 in zip(y_true, top3_preds))
    top3_acc = (top3_correct / total_samples) * 100.0

    precision_macro, recall_macro, f1_macro, _ = precision_recall_fscore_support(
        y_true, y_pred, average="macro", zero_division=0
    )
    precision_weighted, recall_weighted, f1_weighted, _ = precision_recall_fscore_support(
        y_true, y_pred, average="weighted", zero_division=0
    )

    clf_report_dict = classification_report(
        y_true,
        y_pred,
        target_names=class_names,
        output_dict=True,
        zero_division=0,
    )

    metrics = {
        "total_samples": total_samples,
        "top1_accuracy": round(top1_acc, 2),
        "top3_accuracy": round(top3_acc, 2),
        "macro_avg": {
            "precision": round(float(precision_macro), 4),
            "recall": round(float(recall_macro), 4),
            "f1_score": round(float(f1_macro), 4),
        },
        "weighted_avg": {
            "precision": round(float(precision_weighted), 4),
            "recall": round(float(recall_weighted), 4),
            "f1_score": round(float(f1_weighted), 4),
        },
        "per_class_report": clf_report_dict,
    }

    return metrics


def plot_and_save_confusion_matrix(
    y_true: List[int],
    y_pred: List[int],
    class_names: List[str],
    output_image_path: str,
    normalize: bool = True,
):
    """Generates and saves a normalized confusion matrix heatmap."""
    cm = confusion_matrix(y_true, y_pred)
    if normalize:
        cm = cm.astype("float") / np.maximum(cm.sum(axis=1)[:, np.newaxis], 1e-9)

    plt.figure(figsize=(14, 12))
    sns.heatmap(
        cm,
        annot=True,
        fmt=".2f" if normalize else "d",
        cmap="Blues",
        xticklabels=class_names,
        yticklabels=class_names,
        cbar=True,
    )
    plt.title("AgriX Disease Classification Confusion Matrix", fontsize=14, pad=12)
    plt.xlabel("Predicted Disease Class", fontsize=12)
    plt.ylabel("Actual True Disease Class", fontsize=12)
    plt.xticks(rotation=45, ha="right", fontsize=9)
    plt.yticks(fontsize=9)
    plt.tight_layout()

    out_path = Path(output_image_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(str(out_path), dpi=300)
    plt.close()

"""
AgriX Mobile Model Export & Quantization Pipeline
Exports trained PyTorch checkpoints to optimized TensorFlow Lite (TFLite) models for Android on-device inference.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List

import numpy as np
import timm
import torch
import yaml

sys.path.append(str(Path(__file__).resolve().parents[2]))


def load_config(config_path: str) -> Dict:
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def export_pytorch_to_onnx(
    model: torch.nn.Module,
    onnx_path: str,
    image_size: int = 224,
) -> str:
    """Exports PyTorch model to ONNX format."""
    model.eval()
    dummy_input = torch.randn(1, 3, image_size, image_size, requires_grad=False)
    input_names = ["input_tensor"]
    output_names = ["output_probabilities"]

    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=13,
        do_constant_folding=True,
        input_names=input_names,
        output_names=output_names,
        dynamic_axes={"input_tensor": {0: "batch_size"}, "output_probabilities": {0: "batch_size"}},
    )
    return onnx_path


def convert_onnx_to_tflite(
    onnx_path: str,
    tflite_path: str,
    quantization: str = "int8",
    calibration_data_dir: str = None,
    image_size: int = 224,
    num_calibration_samples: int = 100,
):
    """
    Converts ONNX graph to TensorFlow Lite with optional INT8 / FP16 Post-Training Quantization.
    Requires tensorflow and onnx-tf / tf-nightly.
    """
    import tensorflow as tf

    print(f"🔄 Converting ONNX ({onnx_path}) to TFLite ({quantization.upper()} mode)...")

    # When onnx_tf is available or direct SavedModel conversion
    # For PyTorch direct conversion via TFLite converter:
    try:
        import onnx
        from onnx_tf.backend import prepare

        onnx_model = onnx.load(onnx_path)
        tf_rep = prepare(onnx_model)
        saved_model_dir = str(Path(onnx_path).parent / "tf_saved_model")
        tf_rep.export_graph(saved_model_dir)

        converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)

        if quantization.lower() == "int8":
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            # Set representative dataset generator
            def representative_data_gen():
                for _ in range(num_calibration_samples):
                    data = np.random.randn(1, image_size, image_size, 3).astype(np.float32)
                    yield [data]

            converter.representative_dataset = representative_data_gen
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
            converter.inference_input_type = tf.float32  # Keep input float32 for easy Android Bitmap processing
            converter.inference_output_type = tf.float32
        elif quantization.lower() == "fp16":
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.float16]

        tflite_model = converter.convert()
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)

        size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
        print(f"✅ Successfully exported TFLite model -> {tflite_path} ({size_mb:.2f} MB)")

    except ImportError:
        print("⚠️ 'onnx-tf' or 'tensorflow' not installed in current environment.")
        print(f"   Exported ONNX file at {onnx_path} is ready for TFLite conversion in Colab / Cloud environment.")


def export_label_map(class_names: List[str], output_label_path: str):
    """Exports labels.txt formatted for Android mobile asset packaging."""
    with open(output_label_path, "w", encoding="utf-8") as f:
        for name in class_names:
            f.write(f"{name}\n")
    print(f"📄 Exported label file -> {output_label_path}")


def main():
    parser = argparse.ArgumentParser(description="AgriX TFLite Model Export Pipeline")
    parser.add_argument("--config", type=str, default="configs/training_config.yaml", help="Path to config YAML")
    parser.add_argument("--checkpoint", type=str, default="models/best_model.pth", help="Path to PyTorch checkpoint")
    parser.add_argument("--output", type=str, default="models/disease_model.tflite", help="Path to output TFLite model")
    parser.add_argument("--quantize", type=str, default="int8", choices=["none", "fp16", "int8"], help="Quantization mode")
    args = parser.parse_args()

    cfg = load_config(args.config)
    class_names = cfg["dataset"]["classes"]
    image_size = cfg["dataset"]["image_size"]

    models_dir = Path(args.output).parent
    models_dir.mkdir(parents=True, exist_ok=True)

    # 1. Export label file
    labels_file = models_dir / "labels.txt"
    export_label_map(class_names, str(labels_file))

    # 2. Load PyTorch model
    checkpoint_path = Path(args.checkpoint)
    if not checkpoint_path.exists():
        print(f"⚠️ Checkpoint not found at {checkpoint_path}. Exporting architecture definition directly.")
        model = timm.create_model(
            cfg["model"]["architecture"],
            pretrained=True,
            num_classes=len(class_names),
        )
    else:
        print(f"📦 Loading checkpoint from {checkpoint_path}...")
        model = timm.create_model(
            cfg["model"]["architecture"],
            pretrained=False,
            num_classes=len(class_names),
        )
        checkpoint = torch.load(checkpoint_path, map_location="cpu")
        state_dict = checkpoint.get("model_state_dict", checkpoint)
        model.load_state_dict(state_dict)

    # 3. Export to ONNX
    onnx_path = str(models_dir / "disease_model.onnx")
    print(f"📦 Exporting PyTorch model to ONNX -> {onnx_path}...")
    export_pytorch_to_onnx(model, onnx_path, image_size=image_size)
    print(f"✅ ONNX model created successfully ({os.path.getsize(onnx_path) / (1024*1024):.2f} MB)")

    # 4. Convert ONNX to TFLite
    convert_onnx_to_tflite(
        onnx_path=onnx_path,
        tflite_path=args.output,
        quantization=args.quantize,
        image_size=image_size,
    )


if __name__ == "__main__":
    main()

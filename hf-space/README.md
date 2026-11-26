---
title: Qwen-Image-2.1 Omni Demo
emoji: 🎨
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 6.32.0
python_version: "3.12"
app_file: app.py
short_description: Text-to-image and editing with Qwen-Image-2.1 Omni + Turbo
startup_duration_timeout: 1h
license: other
license_name: qwen-research
models:
  - chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized
  - chatrlabs/Qwen-Image-2.1-Uncensored-Omni-LoRA-v0.1
  - Qwen/Qwen-Image-2.1
tags:
  - text-to-image
  - image-editing
  - not-for-all-audiences
---

# Qwen-Image-2.1 Uncensored Omni: demo

A ZeroGPU Gradio demo for
[chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized](https://huggingface.co/chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized).

- **Transformer:** the `qwen-image-2.1-step90000-BF16/` folder from the model repo (14.23 GB, plain `from_pretrained`).
- **Text encoder, processor, VAE:** from [Qwen/Qwen-Image-2.1](https://huggingface.co/Qwen/Qwen-Image-2.1), as the model card says.
- **Turbo mode (default):** the 8-step
  [Turbo LoRA](https://huggingface.co/chatrlabs/Qwen-Image-2.1-Uncensored-Omni-LoRA-v0.1) loaded as an adapter,
  8 steps, CFG 1.0, scheduler with `shift_terminal=None`.
- **Quality mode:** LoRA disabled, 35 steps, true CFG 5.0, negative prompt `" "`, which is the model card's recommendation.
- Two tabs: text-to-image and image editing. VAE tiling is left off, because the model card reports colour streaks with it.
- A minimal prompt guard refuses requests that combine sexual terms with terms describing minors.

Adults only. The model has no output filter. Use is limited to non-commercial research and evaluation
under the Qwen Research License.

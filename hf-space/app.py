"""Gradio demo for chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized.

The model repo ships the transformer only. Text encoder, processor and VAE come
from the Qwen/Qwen-Image-2.1 base repo (as stated on the model card).

Two modes:
  * Turbo   - 8-step Turbo LoRA as an adapter, CFG 1.0, shift_terminal=None.
  * Quality - adapter disabled, 35 steps, true CFG 5.0, negative prompt " ".
"""

import os

os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

import spaces  # noqa: E402  must be imported before torch / diffusers

import random  # noqa: E402
import re  # noqa: E402
import time  # noqa: E402

import gradio as gr  # noqa: E402
import torch  # noqa: E402
from diffusers import (  # noqa: E402
    FlowMatchEulerDiscreteScheduler,
    QwenImage21Pipeline,
    QwenImage21Transformer2DModel,
)

BASE_ID = "Qwen/Qwen-Image-2.1"
MODEL_ID = "chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized"
SUBFOLDER = "qwen-image-2.1-step90000-BF16"
LORA_ID = "chatrlabs/Qwen-Image-2.1-Uncensored-Omni-LoRA-v0.1"
LORA_FILE = "chatrlabs-omni-turbo-8step-fp32.safetensors"

MAX_SEED = 2**31 - 1
MODES = {
    # name: (steps, true_cfg_scale)
    "Turbo (8 steps, CFG 1.0)": (8, 1.0),
    "Quality (35 steps, CFG 5.0)": (35, 5.0),
}
DEFAULT_MODE = "Turbo (8 steps, CFG 1.0)"
SIZES = {
    "Square 1:1 (1024x1024)": (1024, 1024),
    "Portrait 2:3 (768x1152)": (768, 1152),
    "Landscape 3:2 (1152x768)": (1152, 768),
    "Portrait 9:16 (768x1344)": (768, 1344),
    "Landscape 16:9 (1344x768)": (1344, 768),
}
DEFAULT_SIZE = "Square 1:1 (1024x1024)"

# ---------------------------------------------------------------------------
# Minimal guard: the checkpoint has no safety checker, so at least refuse
# prompts that combine sexual terms with terms describing minors.
_SEXUAL = re.compile(
    r"\b(nsfw|nude|nudes|naked|topless|explicit|sexual\w*|sexy|erotic\w*|fetish\w*|"
    r"lingerie|underwear|porn\w*|breasts?|genitals?|nipples?|undress\w*)\b",
    re.IGNORECASE,
)
_MINOR = re.compile(
    r"\b(child|children|kid|kids|toddler|baby|infant|teen|teens|teenager|teenage|"
    r"minor|minors|preteen|underage|schoolgirl|schoolboy|loli|shota|"
    r"young\s+(?:girl|boy)|(?:[1-9]|1[0-7])\s*(?:yo|y/o|years?\s*old))\b",
    re.IGNORECASE,
)


def check_prompt(prompt: str) -> str:
    prompt = (prompt or "").strip()
    if not prompt:
        raise gr.Error("Please enter a prompt.")
    if _SEXUAL.search(prompt) and _MINOR.search(prompt):
        raise gr.Error("Blocked: sexual content involving minors is not allowed.")
    return prompt


# ---------------------------------------------------------------------------
# Model loading (once, at startup)
transformer = QwenImage21Transformer2DModel.from_pretrained(
    MODEL_ID, subfolder=SUBFOLDER, torch_dtype=torch.bfloat16
)
pipe = QwenImage21Pipeline.from_pretrained(
    BASE_ID, transformer=transformer, torch_dtype=torch.bfloat16
).to("cuda")
pipe.load_lora_weights(LORA_ID, weight_name=LORA_FILE, adapter_name="turbo")

QUALITY_SCHEDULER = pipe.scheduler
# The Turbo LoRA was distilled for this schedule: no terminal stretch of the sigmas.
TURBO_SCHEDULER = FlowMatchEulerDiscreteScheduler.from_config(
    pipe.scheduler.config, shift_terminal=None
)


def _configure(mode: str) -> tuple[int, float, bool]:
    steps, cfg = MODES.get(mode, MODES[DEFAULT_MODE])
    turbo = mode.startswith("Turbo")
    if turbo:
        pipe.enable_lora()
        pipe.set_adapters(["turbo"], adapter_weights=[1.0])
        pipe.scheduler = TURBO_SCHEDULER
    else:
        pipe.disable_lora()
        pipe.scheduler = QUALITY_SCHEDULER
    return steps, cfg, turbo


def _seed(seed, randomize) -> int:
    return random.randint(0, MAX_SEED) if randomize else int(seed) % (MAX_SEED + 1)


def _gpu_seconds(mode) -> int:
    # Quality mode runs two transformer passes per step (true CFG).
    return 45 if str(mode).startswith("Turbo") else 150


def _dur_t2i(prompt="", negative_prompt=" ", size=DEFAULT_SIZE, mode=DEFAULT_MODE, *a, **k):
    return _gpu_seconds(mode)


def _dur_edit(image=None, prompt="", mode=DEFAULT_MODE, *a, **k):
    return _gpu_seconds(mode)


@spaces.GPU(duration=_dur_t2i)
def generate(
    prompt: str,
    negative_prompt: str = " ",
    size: str = DEFAULT_SIZE,
    mode: str = DEFAULT_MODE,
    seed: int = 42,
    randomize_seed: bool = True,
    progress: gr.Progress = gr.Progress(track_tqdm=True),
):
    """Generate an image from a text prompt.

    Args:
        prompt: Description of the image to generate.
        negative_prompt: Used in Quality mode only. A single space means none.
        size: One of the size presets shown in the UI.
        mode: "Turbo (8 steps, CFG 1.0)" or "Quality (35 steps, CFG 5.0)".
        seed: RNG seed, used when randomize_seed is False.
        randomize_seed: Pick a random seed.

    Returns:
        The generated image, the seed used, and a short info line.
    """
    prompt = check_prompt(prompt)
    if size not in SIZES:
        raise gr.Error(f"Unknown size: {size}")
    width, height = SIZES[size]
    seed = _seed(seed, randomize_seed)
    steps, cfg, turbo = _configure(mode)

    kwargs = {}
    if not turbo:
        # An empty negative prompt silently disables true CFG; a single space keeps it on.
        kwargs["negative_prompt"] = negative_prompt if (negative_prompt or "").strip() else " "

    t0 = time.perf_counter()
    with torch.inference_mode():
        image = pipe(
            prompt=prompt,
            width=width,
            height=height,
            num_inference_steps=steps,
            true_cfg_scale=cfg,
            use_kv_cache=True,
            generator=torch.Generator("cuda").manual_seed(seed),
            **kwargs,
        ).images[0]
    dt = time.perf_counter() - t0
    return image, seed, f"{width}x{height} · {steps} steps · CFG {cfg} · seed {seed} · {dt:.1f}s"


@spaces.GPU(duration=_dur_edit)
def edit(
    image,
    prompt: str,
    mode: str = DEFAULT_MODE,
    seed: int = 42,
    randomize_seed: bool = True,
    progress: gr.Progress = gr.Progress(track_tqdm=True),
):
    """Edit an uploaded image with a text instruction.

    Args:
        image: Reference image to edit.
        prompt: Edit instruction, e.g. "make it a watercolor painting".
        mode: "Turbo (8 steps, CFG 1.0)" or "Quality (35 steps, CFG 5.0)".
        seed: RNG seed, used when randomize_seed is False.
        randomize_seed: Pick a random seed.

    Returns:
        The edited image, the seed used, and a short info line.
    """
    if image is None:
        raise gr.Error("Please upload an image to edit.")
    prompt = check_prompt(prompt)
    seed = _seed(seed, randomize_seed)
    steps, cfg, turbo = _configure(mode)
    kwargs = {} if turbo else {"negative_prompt": " "}

    t0 = time.perf_counter()
    with torch.inference_mode():
        # Width/height omitted: the pipeline derives them from the reference
        # aspect ratio at output_resolution.
        out = pipe(
            image=[image.convert("RGBA")],
            prompt=prompt,
            output_resolution=1024,
            num_inference_steps=steps,
            true_cfg_scale=cfg,
            use_kv_cache=True,
            generator=torch.Generator("cuda").manual_seed(seed),
            **kwargs,
        ).images[0]
    dt = time.perf_counter() - t0
    return out, seed, f"{out.size[0]}x{out.size[1]} · {steps} steps · CFG {cfg} · seed {seed} · {dt:.1f}s"


# ---------------------------------------------------------------------------
INTRO = """
# Qwen-Image-2.1 Uncensored Omni
Unofficial demo of [chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized](https://huggingface.co/chatrlabs/Qwen-Image-2.1-Uncensored-Omni-Quantized)
(BF16 build), a community fine-tune of [Qwen/Qwen-Image-2.1](https://huggingface.co/Qwen/Qwen-Image-2.1).
**Turbo** uses the 8-step [Turbo LoRA](https://huggingface.co/chatrlabs/Qwen-Image-2.1-Uncensored-Omni-LoRA-v0.1);
**Quality** uses the model card's 35 steps / true CFG 5.0.

> **Adults only (18+).** No output filter. Requests sexualising minors are blocked. Do not depict real people without consent.
> Non-commercial research and evaluation only (Qwen Research License).
"""

with gr.Blocks(title="Qwen-Image-2.1 Omni") as demo:
    with gr.Column(elem_id="col"):
        gr.Markdown(INTRO)
        with gr.Tabs():
            with gr.Tab("Text to image"):
                with gr.Row():
                    with gr.Column(scale=5):
                        prompt = gr.Textbox(
                            label="Prompt",
                            lines=3,
                            placeholder="A blue ceramic mug on a wooden table by a window, soft morning light",
                        )
                        t_mode = gr.Radio(list(MODES), value=DEFAULT_MODE, label="Mode")
                        run_btn = gr.Button("Generate", variant="primary")
                        with gr.Accordion("Advanced", open=False):
                            size = gr.Dropdown(list(SIZES), value=DEFAULT_SIZE, label="Size")
                            negative = gr.Textbox(
                                label="Negative prompt (Quality mode only)",
                                value=" ",
                                info="Keep at least a single space; empty turns true CFG off.",
                            )
                            with gr.Row():
                                t_seed = gr.Number(label="Seed", value=42, precision=0)
                                t_rand = gr.Checkbox(label="Randomize seed", value=True)
                    with gr.Column(scale=5):
                        t_out = gr.Image(label="Result", type="pil", format="png", height=560)
                        t_info = gr.Markdown()
                gr.Examples(
                    examples=[
                        ["A blue ceramic mug on a wooden table by a window, soft morning light, product photography"],
                        ["Portrait of an elderly fisherman mending a net at dawn, weathered hands, 85mm film photograph"],
                        ['A neon-lit ramen stall in a rainy alley, a handwritten sign that reads "OPEN LATE", cinematic'],
                        ["Watercolor painting of a red fox curled up asleep in autumn leaves"],
                    ],
                    inputs=[prompt],
                )

            with gr.Tab("Image editing"):
                with gr.Row():
                    with gr.Column(scale=5):
                        e_img = gr.Image(label="Reference image", type="pil", height=380)
                        e_prompt = gr.Textbox(
                            label="Edit instruction",
                            lines=2,
                            placeholder="Change the mug to green, keep everything else unchanged",
                        )
                        e_mode = gr.Radio(list(MODES), value=DEFAULT_MODE, label="Mode")
                        e_btn = gr.Button("Apply edit", variant="primary")
                        with gr.Accordion("Advanced", open=False):
                            with gr.Row():
                                e_seed = gr.Number(label="Seed", value=45, precision=0)
                                e_rand = gr.Checkbox(label="Randomize seed", value=True)
                    with gr.Column(scale=5):
                        e_out = gr.Image(label="Edited image", type="pil", format="png", height=560)
                        e_info = gr.Markdown()

    t_inputs = [prompt, negative, size, t_mode, t_seed, t_rand]
    run_btn.click(generate, t_inputs, [t_out, t_seed, t_info], api_name="generate")
    prompt.submit(generate, t_inputs, [t_out, t_seed, t_info], api_name=False)

    e_inputs = [e_img, e_prompt, e_mode, e_seed, e_rand]
    e_btn.click(edit, e_inputs, [e_out, e_seed, e_info], api_name="edit")
    e_prompt.submit(edit, e_inputs, [e_out, e_seed, e_info], api_name=False)

if __name__ == "__main__":
    demo.launch(
        mcp_server=True,
        theme=gr.themes.Citrus(),
        css="#col { max-width: 1150px; margin: 0 auto; }",
    )

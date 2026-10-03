# WordWise Build Pamphlet

## Overview
- Purpose: System-wide grammar assistant for Android — type `?fix` / `?ask` in any app; an Accessibility Service rewrites the sentence in place via OpenRouter.
- Current Version: 1.0.0
- Status: In Active Development

## Development Timeline

### 2026-10-02 - OpenRouter model picker
Session: [session-2026-10-02-openrouter-model-picker.md](sessions/session-2026-10-02-openrouter-model-picker.md)
Replaced the hardcoded `openrouter/free` router with a user-chosen model. Settings
now offers a searchable catalog dropdown plus a paste field for any `vendor/model`
path; nothing is set until one SAVE MODEL submit. The model is stored in plain
`SharedPreferences` (it is not a secret), validated by a new pure `ModelId` object,
and resolved per request so a change applies without restarting the service. Also
extended the `?ask` prompt with the no-em-dash and concision rules `?fix` already
had. Findings: [feature-research/openrouter-model-picker.md](feature-research/openrouter-model-picker.md).

### 2026-09-25 - Small on-device grammar alternatives research
Session: [session-2026-09-25-1010-on-device-grammar-alternatives.md](sessions/session-2026-09-25-1010-on-device-grammar-alternatives.md)
Compared Harper, small T5 grammar models, Microsoft EdgeFormer/EdgeLM, GECToR, LaserTagger, rule engines, and small general LLMs. Harper's 2.10.0 WASM API was selected for the first feasibility pilot because it returns spans/suggestions at roughly 16 MB; EdgeFormer was identified as the strongest fine-tunable neural base. No production integration was started. Findings: [feature-research/on-device-grammar-alternatives.md](feature-research/on-device-grammar-alternatives.md).

### 2026-09-25 - Harper Android pilot and EdgeFormer deferred plan
Session: [session-2026-09-25-1147-harper-android-pilot.md](sessions/session-2026-09-25-1147-harper-android-pilot.md)
Built a throwaway Android WebView/WASM harness outside production, ran it on an ARM64 API 31 device, and measured local latency, memory, APK size, Unicode spans, and suggestion application. Added a durable future EdgeFormer training plan. Findings: [feature-research/on-device-grammar-alternatives.md](feature-research/on-device-grammar-alternatives.md) and [feature-research/edgeformer-wordwise-training-plan.md](feature-research/edgeformer-wordwise-training-plan.md).

### 2026-09-25 - Needle 3 / Cactus feasibility research
Session: [session-2026-09-25-0934-needle3-cactus-feasibility-spike.md](sessions/session-2026-09-25-0934-needle3-cactus-feasibility-spike.md)
Official-source research separated legacy Needle from standalone Needle 3, assessed Android artifacts and Cactus Kotlin integration, and identified the documented span-grounding mismatch with the proposed edit schema. Findings: [feature-research/needle3-cactus-android.md](feature-research/needle3-cactus-android.md).

### 2026-09-24 - Launch video (brag + HyperFrames)
Session: [session-2026-09-24-2051-brag-launch-video.md](sessions/session-2026-09-24-2051-brag-launch-video.md)
22s pastel launch video: hook typing, `?fix` payoff, `?ask` demo, real Home-screen recreation, outro lockup — beat-locked to music, audio-reactive glow, WCAG AA clean. Outputs in `brag-output/` (`brag.mp4`, `brag.jpg`, `share-copy.txt`).

## Architecture Overview
See [ARCHITECTURE.md](ARCHITECTURE.md).

## Bugs Discovered & Fixed
### Critical
- Broken system ffmpeg + missing numpy → static ffmpeg 7.0.2 in `~/.local/bin` + `python-numpy` (Session: 2026-09-24)
- `chrome-headless-shell` corrupt/undownloadable → manual CfT install + `HYPERFRAMES_BROWSER_PATH` (Session: 2026-09-24)

### Medium
- `<audio>` elements without `id` render silent → ids added to all 38 clips (Session: 2026-09-24)
- WCAG 2.7:1 failures on white-on-light-accent glyphs → accent gradient light stop darkened to `#9179d6` (Session: 2026-09-24)
- Standalone Needle 3 `.cact` cannot currently be loaded by the Cactus Engine Android JNI path; upstream issue #813 remains open (Session: 2026-09-25)
- Cactus Kotlin is archived and its repository license restricts commercial use; Android 16 KB alignment/AAR issues remain release gates (Session: 2026-09-25)

## Testing Methodology
- Unit testing: (project tests as applicable)
- Video QA: `hyperframes check` gate (lint/runtime/layout/motion/contrast), key-frame snapshots, ffprobe integrity checks

## AI Models & Their Contributions
### Session 2026-09-24 (launch video)
- **MiMo-V2.6-Flash (OpenCode)**: research, storyboard, composition code, environment debugging, render/delivery — see session file.

### Session 2026-09-25 (Needle 3 research)
- **Space Bunny Free (current agent)**: official-source investigation, legacy/Needle 3 separation, Android integration analysis, schema/grounding risk assessment, and research documentation.
- **Research subagent**: independent first-party source review and release/licensing risk cross-check.

### Session 2026-09-25 (on-device grammar alternatives)
- **Space Bunny Free (OpenCode)**: compared Harper, T5 grammar models, edit-native neural architectures, rule engines, and small general LLMs; ran the Harper 2.10.0 host-side corpus probe; documented the Android feasibility plan.
- **Research subagent**: independent candidate matrix covering size, license, output contract, mobile runtime fit, and grammar-quality evidence.
- **Context7**: current LanguageTool, nlprule, ONNX Runtime, and llama.cpp documentation.

### Session 2026-09-25 (Harper Android pilot)
- **Space Bunny Free (OpenCode)**: built and validated a throwaway Android WebView/WASM harness, measured device behavior, diagnosed packaging/API issues, and documented the deferred EdgeFormer training plan.
- **Context7**: current AndroidX WebViewAssetLoader and WebViewClientCompat API guidance.

## Build Outputs
- 2026-09-24: `brag-output/brag.mp4` (5.0 MB, 22s, 1080p), `brag.jpg`, `share-copy.txt`

## Development Resources
- Version Control: git
- Video toolchain: HyperFrames CLI 0.8.73, static ffmpeg 7.0.2 (`~/.local/bin`), chrome-headless-shell 152 (`~/.cache/hyperframes/chrome/`)

## Future Roadmap
- [x] Run the Harper 2.10.0 Android feasibility probe on the connected device.
- [ ] Expand to a 50–100 sentence expected-edit corpus and measure span/suggestion precision, false positives, and UTF-16 behavior.
- [ ] Decide between a WebView bridge and native Rust/JNI wrapper for a future Harper provider.
- [ ] Keep EdgeFormer training deferred until suitable hardware, a versioned dataset, and a licensing review exist.
- [ ] Export and benchmark a self-contained T5-Efficient-Tiny ONNX model if Harper coverage is insufficient.
- [ ] User review of launch video; re-roll scenes/tone if requested
- [ ] Commit `brag-output/` when ready to ship

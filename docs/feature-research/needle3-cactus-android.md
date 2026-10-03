# Research: Needle 3 / Cactus Android Integration

**Research date:** 2026-09-25  
**Scope:** Official Cactus Compute sources only, focused on whether Needle 3 can support WordWise's offline grammar/edit-list path and how it could be integrated into the Android APK.

**Source snapshot:** `needle@42bf1f2` (2026-09-23), `cactus@fa094ce` / v2.2.1 (2026-09-25), `cactus-kotlin@e55ff49` (2026-02-10; repository archived 2026-07-24), and Hugging Face model revision `b274efcb` (2026-09-19). Dynamic web pages and issue status can change after this date.

## Search Terms Used

- `Cactus Compute Needle 3 official model card`
- `cactus-compute/needle README Android`
- `cactus-compute/cactus-kotlin Needle 3`
- `cactus_init bundle directory needle3.cact`
- `Needle structured extraction grounding spans`
- `Needle Android libneedle.a needle.h`
- `Cactus-Compute/needle3 Android-arm64`
- `Cactus-Compute/needle legacy Cactus Engine integration`

## Executive Finding

The official sources reveal an important **two-generation split**:

1. **Legacy Needle** (`Cactus-Compute/needle` on Hugging Face, 26M parameters, encoder-decoder, MIT) is the version integrated into the Cactus Engine and exposed by the Cactus CLI.
2. **Needle 3** (`Cactus-Compute/needle3`, 29–121M parameters, Laddered Simple Attention Network, Apache-2.0) is a newer standalone runtime with its own `.cact` archive, native C API, Android platform folders, and Python package.

The current WordWise idea targets **Needle 3**, but the current **Cactus Kotlin SDK is not a documented Needle 3 integration** and its repository is archived. The official sources do show a viable *custom native integration route* through Needle 3's Android `libneedle.a` and `needle.h`, but that route is not a drop-in Kotlin/AAR dependency. Commercial distribution also needs a separate license review because the Cactus core/Kotlin repository licenses are restricted rather than plainly Apache-2.0.

The proposed edit schema is also not fully aligned with Needle 3's documented extraction contract: Needle 3 is designed to extract values grounded in spans of the input, not to freely generate arbitrary replacement prose. This makes the `replace` field in the original edit schema a significant feasibility risk.

## Existing Code Found

- `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`: AccessibilityService orchestrator; currently calls `AiClient.fixGrammar()` and replaces the entire field text.
- `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`: OpenRouter-only cloud client; returns `Success(text)` and has no structured edit result type.
- `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt`: existing service/command tests.
- `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt`: existing client tests.
- `app/src/androidTest/`: instrumentation coverage for sensitive fields and cancellation.
- `README.md`: current product contract is full-text correction plus `?ask`; no offline model tier exists yet.
- `/home/musa/Cactus-Compute`: attached directory is currently empty, so no local Cactus source or weights are available in the workspace.

The current WordWise response contract therefore does not yet have a seam for edit lists, confidence, suppressed calls, or native-model lifecycle management. That is expected for a feasibility spike; no production integration was attempted.

## Similar Patterns

- **WordWise cloud contract:** `AiClient.Result.Success(text)` is a full-text replacement contract; a local edit-list adapter would need a separate result type rather than silently changing the cloud response semantics.
- **Cactus Kotlin native bridge:** the official SDK already demonstrates an Android JNI boundary, but it wraps Cactus Engine's FFI (`cactus_*`) rather than standalone Needle's `needle_*` API.
- **Needle structured extraction:** the official `extract(text, schema)` pattern is a one-tool, one-call design with a record-shaped schema; it is the closest documented analogue to a grammar edit extractor, but its grounding rules differ from free replacement generation.
- **Cactus bundle loading:** Cactus Engine model directories (`config.txt` plus `components/manifest.json`) are structurally different from Needle 3's single `.cact` archive, so the existing model-download path cannot be reused unchanged.
- **WordWise service lifecycle:** `GrammarFixService` already uses coroutine cancellation and node recycling; a native model would need an explicit worker/lifecycle boundary rather than blocking the AccessibilityService's main-thread path.

## Official Source Findings

### 1. Needle 3 is a structured extraction/tool model, not a rewrite model

The official Needle 3 README describes three jobs:

- tool calls;
- structured extraction;
- text embeddings.

It explicitly says the model is a foundation model for tool calling, device control, and structured extraction, and that an off-topic request returns an empty call rather than a guess.

Sources:

- [Needle 3 README, official GitHub](https://github.com/cactus-compute/needle/blob/42bf1f2d0a7784b0d4d1ec94bb5ade425cf9a67c/README.md)
- [Needle 3 model card, official Hugging Face](https://huggingface.co/Cactus-Compute/needle3/blob/b274efcb211a9eef48c9a88da4b43bd569696a39/README.md)
- [How to Design Tools for Needle 3](https://cactuscompute.com/blog/designing-tools-for-needle)
- [Structured JSON Extraction with Needle](https://cactuscompute.com/blog/structured-extraction-with-needle)
- [Needle Python reference](https://cactuscompute.com/blog/needle-python-docs)

The official Python reference states:

- unsupported input returns an empty call;
- there is no free-text fallback;
- the final answer is tool results, not generated prose;
- arguments should contain values evidenced in the input;
- optional fields with no evidence are omitted rather than invented.

This supports the original diagnosis that Needle 3 is not a general grammar rewriter. The official sources go further than the original assumption: their documented grounding contract is specifically span-based.

### 2. The original edit-list schema conflicts with the documented grounding contract

The proposed schema is:

```json
{
  "edits": [
    {"start": 4, "end": 8, "replace": "their"}
  ]
}
```

Needle 3's official extraction guide says:

- extraction is tool calling with one tool;
- every filled field is a span of the passage;
- a required field with no span is withheld;
- optional fields with no span are omitted;
- values are grounded in the passage rather than freely invented.

The official design guide repeats that each argument is derived from a span of the request and that a field with no span is not guessed.

Sources:

- [Structured JSON Extraction with Needle](https://cactuscompute.com/blog/structured-extraction-with-needle)
- [How to Design Tools for Needle 3](https://cactuscompute.com/blog/designing-tools-for-needle)
- [Needle Python reference — contract and response shape](https://cactuscompute.com/blog/needle-python-docs)

Implications for WordWise:

- `replace: "their"` is normally **not a span of the flawed input** when the input contains `there`; the official grounding contract may suppress or reject it.
- An insertion such as `replace: ","` also asks the model to produce a token that is not present at the requested location.
- Numeric character offsets are structurally legal, but the official grounding rules treat numeric values as evidence-sensitive. The model is not documented as a character-offset locator, and no official benchmark covers prose span offsets.
- The byte-level grammar guarantees valid shape/types/constraints, not correct spans, offsets, or semantic edits. This confirms the caveat in the original design.
- The documented Needle 3 response contract has no `start`, `end`, character-offset, token-offset, or edit-operation field; `extract()` returns only the tool arguments. Offsets therefore are not a supported Needle 3 output contract, even if a schema can make an integer field syntactically legal.

A more contract-compatible experiment would extract evidence plus a constrained error class, for example:

```json
{
  "errors": [
    {
      "exact_text": "there",
      "error_class": "there_to_their"
    }
  ]
}
```

The app would map `error_class` to a replacement locally. That may work for a finite taxonomy, but it is not a general grammar corrector. A second experiment can still try the original edit schema, but should treat suppression, empty calls, and ungrounded replacements as expected outcomes rather than exceptional parser failures.

### 3. Schema constraints are supported, but not necessarily the semantics WordWise needs

Needle 3's official APIs support:

- typed fields;
- enums and literals;
- numeric ranges;
- string patterns and lengths;
- arrays and item constraints;
- classification through enum fields.

Sources:

- [Needle Python reference](https://cactuscompute.com/blog/needle-python-docs)
- [Structured JSON Extraction with Needle](https://cactuscompute.com/blog/structured-extraction-with-needle)
- [Needle 3 README](https://github.com/cactus-compute/needle/blob/42bf1f2d0a7784b0d4d1ec94bb5ade425cf9a67c/README.md)

This means an edit-list *JSON shape* can be made syntactically legal. It does not establish that the model can identify grammar errors, calculate character offsets, or generate correct replacement strings. Those are separate quality questions requiring the planned corpus test.

### 4. Android support exists for standalone Needle 3, but not as an official Kotlin SDK

The official supported-devices guide lists these Android targets:

- `android-arm64`;
- `android-armv7`;
- `android-riscv64`.

Each folder contains:

- the `needle` runner;
- `libneedle.a`;
- `needle.h`.

Source:

- [What Devices Are Supported on Needle](https://cactuscompute.com/blog/needle-supported-devices)
- [Needle 3 Hugging Face repository](https://huggingface.co/Cactus-Compute/needle3/tree/main)

The official C header exposes a small C API:

```c
int needle_init(const char* system_prompt,
                const char* tools_json,
                const char* tool_index_path);
int needle_complete(const char* input,
                    int max_new_tokens,
                    char* out,
                    int out_capacity);
int needle_embed(const char* input, float* out, int out_capacity);
void needle_reset(void);
int needle_load(const unsigned char* cact, unsigned long long n);
```

Source:

- [Official Android `needle.h`](https://huggingface.co/Cactus-Compute/needle3/raw/main/android-arm64/needle.h)

Important details:

- `needle_init()` does not accept a model path; the model is loaded with `needle_load()` from the `.cact` bytes.
- The C API is process-global and non-thread-safe according to the header.
- The header exposes no cancellation/stop function.
- The official repository provides native artifacts, not a Kotlin wrapper or AAR for Needle 3.

Therefore the direct Android route would require a small project-owned JNI/NDK bridge around `libneedle.a`, plus model loading, JSON response handling, lifecycle management, and cancellation/error policy. This is feasible in principle, but it is not the low-friction “add Cactus dependency” integration path.

### 5. The Cactus Kotlin SDK is Android-ready, but it targets Cactus Engine bundles, not standalone Needle 3

The official Kotlin SDK states:

- Android API 24+;
- ARM64 support;
- native libraries included;
- `CactusContextInitializer` initialization;
- `CactusLM.downloadModel()` and `initializeModel()`;
- tool calling through `CactusCompletionParams`.

Sources:

- [Cactus Kotlin README](https://github.com/cactus-compute/cactus-kotlin/blob/main/README.md)
- [Cactus Kotlin Android build configuration](https://github.com/cactus-compute/cactus-kotlin/blob/main/library/build.gradle.kts)
- [Cactus Kotlin JNI bridge](https://github.com/cactus-compute/cactus-kotlin/blob/main/library/src/androidMain/kotlin/CactusLibraryJNI.kt)

The current build configuration sets `minSdk = 24` and filters Android ABIs to `arm64-v8a`. Its Android native layer links prebuilt Cactus libraries and exposes the Cactus Engine FFI, not the standalone `needle_*` API.

The official `cactus-kotlin` repository was archived by its owner on 2026-07-24 and is now read-only. This is a material signal for a production dependency: the SDK should not be treated as an actively maintained Needle 3 integration path without an upstream replacement or commercial agreement.

Sources:

- [Archived Cactus Kotlin repository](https://github.com/cactus-compute/cactus-kotlin)
- [Cactus Kotlin 16 KB page-size issue #18](https://github.com/cactus-compute/cactus-kotlin/issues/18)

The Kotlin wrapper also has limitations for the proposed edit schema:

- `ToolCall.arguments` is typed as `Map<String, String>`, not a faithful typed representation of nested edit objects, arrays, or integer fields.
- `CactusCompletionResult` does not expose a `confidence` field, and the parser ignores the engine's confidence value.
- The wrapper's `CactusTool`/`ToolParameter` API exposes only a small subset of JSON Schema constraints; it does not expose the full Needle 3 `Field` constraint surface.
- A repository-wide inspection found no `needle3` reference in the official Kotlin SDK source or README.

The SDK obtains model metadata from a remote model registry and stores extracted model folders under the app's files directory. That is suitable for Cactus model bundles, but it is not evidence that `Cactus-Compute/needle3` is a registered model for this SDK.

### 6. Cactus Engine's built-in Needle support is for the legacy model, not automatically Needle 3

The current Cactus Engine source contains `model_type=needle` handling and a Needle tool-call constrainer. However, the built-in conversion adapter is shaped around the older encoder-decoder Needle model:

- default `d_model = 512`;
- 12 encoder layers;
- 8 decoder layers;
- `NeedleForCausalLM`;
- encoder-decoder execution path.

Sources:

- [Cactus Engine README — legacy Needle section](https://github.com/cactus-compute/cactus/blob/main/README.md)
- [Cactus Needle conversion configuration](https://github.com/cactus-compute/cactus/blob/main/python/cactus/models/needle/configuration_needle.py)
- [Cactus Needle conversion model](https://github.com/cactus-compute/cactus/blob/main/python/cactus/models/needle/modeling_needle.py)
- [Cactus Engine model loader](https://github.com/cactus-compute/cactus/blob/main/cactus-engine/src/model.cpp)

By contrast, the current Needle 3 model config is:

- `NeedleForToolCalling`;
- 20 layers;
- hidden size 768;
- Hadamard MLP;
- engram memory;
- 8,192-token context;
- CQ2 deployment format.

Source:

- [Official Needle 3 config.json](https://huggingface.co/Cactus-Compute/needle3/raw/main/config.json)

The Cactus Engine `cactus_init()` API expects a **bundle directory** containing files such as `config.txt` and `components/manifest.json`. It does not load a standalone `.cact` file. This distinction is visible in the official C API and model loader:

- [Cactus Engine FFI reference](https://github.com/cactus-compute/cactus/blob/main/docs/cactus_engine.md)
- [Cactus Android build README](https://github.com/cactus-compute/cactus/blob/main/android/README.md)

The open official issue [#813](https://github.com/cactus-compute/cactus/issues/813) reproduces exactly this Android/JNI mismatch for `needle3.cact`. The issue is open, has no maintainer resolution in the inspected page, and should be treated as an unresolved integration blocker rather than proof that no workaround exists.

There are additional open Android release risks in the Cactus repositories:

- [Cactus Kotlin issue #18](https://github.com/cactus-compute/cactus-kotlin/issues/18) reports Android 15 / 16 KB page-size rejection caused by the bundled JNA native library.
- [Cactus issue #795](https://github.com/cactus-compute/cactus/issues/795) reports an Android AAR/weight-format mismatch that prevents current published Cactus weights from loading.
- [Cactus issue #798](https://github.com/cactus-compute/cactus/issues/798) reports a 16 KB page-size failure in `libcactus_engine.so`.

These are user-reported open issues, not proof that every build is broken, but they are release gates that must be tested in the final APK/AAB.

### 7. Current release synchronization has a separate packaging problem

The official Needle 3 repository's current fetch code points generation 3 at engine version `3.0.2`, while the official Hugging Face repository metadata inspected on 2026-09-25 listed Python wheels for `3.0.0` and `3.0.1`, not `3.0.2`.

Official open issues:

- [Needle issue #142 — missing 3.0.2 wheels](https://github.com/cactus-compute/needle/issues/142)
- [Needle issue #146 — fresh initialization 404 for 3.0.2 wheel](https://github.com/cactus-compute/needle/issues/146)

This affects fresh Python installs and test setup. A direct Android artifact download can bypass the Python wheel path, but any WordWise prototype should pin an explicit model revision and engine version rather than relying on “latest”.

There is also an open user report that the shipped Linux ARM engine accepts `--depth` but produces byte-identical output across ladder depths: [Needle issue #145](https://github.com/cactus-compute/needle/issues/145). Treat the ladder/depth selector as unverified until reproduced on the target Android artifact rather than assuming smaller APK/model sizes will work as documented.

The official engine implementation is not source-auditable through the public Needle repository. In [issue #118](https://github.com/cactus-compute/needle/issues/118), a maintainer states that the engine source is not public and points to the prebuilt binary/static-library distribution. A custom JNI integration can still use the published artifact, but deep runtime debugging or independent rebuilding is not available from the official public source.

## Model, Size, and License Facts

### Current Needle 3

Sources:

- [Needle 3 model card](https://huggingface.co/Cactus-Compute/needle3/blob/b274efcb211a9eef48c9a88da4b43bd569696a39/README.md)
- [Hugging Face model metadata](https://huggingface.co/api/models/Cactus-Compute/needle3)
- [Official `.cact` format guide](https://cactuscompute.com/blog/cact-format)

Observed/documented facts:

- Apache-2.0 license on the current GitHub/Hugging Face model.
- 29–121M parameter range across the 2–20 layer ladder.
- 20-layer current model config reports 121,021,910 parameters.
- CQ2/CQ4 mixed quantization; the `.cact` container embeds the tokenizer.
- Official model-card prose advertises an 8–29 MB model, while the inspected `needle3.cact` artifact reports 35,335,380 bytes at revision `b274efcb`. Treat the exact APK payload as revision-dependent and measure the pinned artifact rather than relying on the headline range.
- The Android arm64 static library is published alongside the archive, not as a Kotlin dependency.

### Cactus Core and Kotlin SDK licensing

The current Cactus core and `cactus-kotlin` repository `LICENSE` files are **not Apache-2.0**. They grant free use only to personal/educational/research/non-commercial users, qualifying organizations below both stated funding and revenue thresholds, educational institutions, and qualifying nonprofits; other users must obtain a separate commercial license.

This matters for WordWise if the app is distributed commercially. The Kotlin build metadata separately labels the published artifact as Apache-2.0, so the repository license and package metadata should be treated as a licensing discrepancy requiring legal review, not silently assumed permissive.

Sources:

- [Cactus core LICENSE](https://github.com/cactus-compute/cactus/blob/fa094ce86c99117f351b100a6ebe51021c11499b/LICENSE)
- [Cactus Kotlin LICENSE](https://github.com/cactus-compute/cactus-kotlin/blob/e55ff49a1f8f2b18757c307d6cd056e6cd6d4afa/LICENSE)
- [Cactus Kotlin publishing metadata](https://github.com/cactus-compute/cactus-kotlin/blob/e55ff49a1f8f2b18757c307d6cd056e6cd6d4afa/library/build.gradle.kts)

### Legacy Needle

Sources:

- [Legacy `Cactus-Compute/needle` model card](https://huggingface.co/Cactus-Compute/needle)
- [Cactus Engine README](https://github.com/cactus-compute/cactus/blob/main/README.md)

Observed/documented facts:

- MIT license.
- 26M parameters.
- Encoder-decoder architecture with 12 encoder and 8 decoder layers.
- This is the model referenced by Cactus Engine's `cactus run Cactus-Compute/needle` example and by the Cactus conversion adapter.

Do not use legacy model/license/runtime claims as evidence about Needle 3.

## Implications for the Proposed WordWise Spike

### What is supported enough to test

- A single `fix_grammar` tool schema can be made syntactically valid.
- Exact-substring extraction is more aligned with Needle 3's documented contract than arbitrary replacements.
- Confidence and suppressed-call behavior are available in the standalone Needle response contract.
- Offline inference is supported when the engine and `.cact` archive are placed on-device; no inference network call is required.
- Android native artifacts are officially published for ARM64, ARMv7, and RISC-V.

### What remains unproven or poorly aligned

- Whether Needle 3 can identify grammar errors rather than merely route/extract obvious spans.
- Whether it can produce multiple edits in one sentence.
- Whether it can calculate UTF-16 character offsets, especially around emoji/non-ASCII text.
- Whether it can generate a replacement token not present in the source.
- Whether its confidence score is calibrated for grammar-error categories rather than tool-routing tasks.
- Whether the Cactus Kotlin SDK can ever load the standalone Needle 3 archive; current official sources do not document that path, and the SDK repository is archived.
- Whether the chosen native artifacts pass Android 15 / 16 KB page-size validation in the final APK/AAB.
- Whether commercial distribution of the selected Cactus core/Kotlin components is permitted under the repository license; this requires legal review.

## Recommended Next Experiment

Run two separate probes with the **standalone Needle 3 C API**, not the Cactus Kotlin SDK:

1. **Contract-compatible probe**
   - Ask for exact erroneous substrings plus a constrained error class.
   - Test agreement, tense, homophones, punctuation, and typos.
   - Score exact substring recall, class accuracy, false positives, and suppression rate.

2. **Original edit-schema probe**
   - Ask for `start`, `end`, and `replace` edits.
   - Treat the result as a diagnostic, not a production contract.
   - Record whether the model emits structurally valid JSON, whether the engine suppresses calls as ungrounded, and whether offsets are correct.

Pin:

- model repository `Cactus-Compute/needle3`;
- model revision `b274efcb211a9eef48c9a88da4b43bd569696a39` (or a later explicitly chosen revision);
- a known engine artifact/version;
- telemetry opt-out behavior;
- Android ABI under test;
- the exact license path for every binary redistributed in the APK.

## New Implementation Required

Not implemented in this research session. A production integration would require:

- a project-owned JNI/NDK wrapper for Needle 3's `needle_*` C API, or an upstream official AAR/Kotlin binding;
- model asset packaging or a first-run download strategy;
- model/engine revision pinning and integrity checks;
- a structured edit/result type distinct from WordWise's current full-text `AiClient.Result.Success(text)`;
- UTF-16-safe span validation and right-to-left edit application;
- confidence/suppressed-call routing to cloud fallback;
- native lifecycle, timeout, cancellation, and memory policy;
- explicit telemetry/privacy configuration;
- Android 15 / 16 KB native-library alignment validation;
- a documented commercial-license decision for any Cactus core/Kotlin components.

## Implementation Plan

1. Acquire or populate the actual Needle 3 Android/C artifacts and confirm the pinned revision.
2. Build a small host/native probe using the official C API and a 30–50 sentence grammar corpus.
3. Run the contract-compatible and original edit-schema variants.
4. Compare exact-span/class quality against the original full-text cloud behavior.
5. Decide whether to proceed with a custom JNI prototype, request an upstream AAR/bundle, or keep cloud/AICore as the grammar path.
6. Only after that decision, design WordWise's provider interface and edit-application tests.

## Source Index

- [Cactus Compute — Needle 3 landing page](https://cactuscompute.com/needle)
- [Cactus Compute — supported devices](https://cactuscompute.com/blog/needle-supported-devices)
- [Cactus Compute — `.cact` format](https://cactuscompute.com/blog/cact-format)
- [Cactus Compute — structured extraction](https://cactuscompute.com/blog/structured-extraction-with-needle)
- [Cactus Compute — tool design](https://cactuscompute.com/blog/designing-tools-for-needle)
- [Cactus Compute — confidence](https://cactuscompute.com/blog/needle-confidence)
- [Cactus Compute — Python reference](https://cactuscompute.com/blog/needle-python-docs)
- [Cactus Compute — porting Needle 3](https://cactuscompute.com/blog/porting-needle)
- [Needle 3 GitHub README](https://github.com/cactus-compute/needle/blob/42bf1f2d0a7784b0d4d1ec94bb5ade425cf9a67c/README.md)
- [Needle 3 GitHub repository](https://github.com/cactus-compute/needle)
- [Needle 3 Hugging Face model](https://huggingface.co/Cactus-Compute/needle3)
- [Needle 3 Android header](https://huggingface.co/Cactus-Compute/needle3/raw/main/android-arm64/needle.h)
- [Cactus Engine repository](https://github.com/cactus-compute/cactus)
- [Cactus Android build](https://github.com/cactus-compute/cactus/blob/main/android/README.md)
- [Cactus Engine FFI reference](https://github.com/cactus-compute/cactus/blob/main/docs/cactus_engine.md)
- [Cactus Kotlin SDK](https://github.com/cactus-compute/cactus-kotlin)
- [Cactus Kotlin Android build](https://github.com/cactus-compute/cactus-kotlin/blob/main/library/build.gradle.kts)
- [Cactus Android/JNI Needle 3 issue #813](https://github.com/cactus-compute/cactus/issues/813)
- [Cactus Kotlin 16 KB page-size issue #18](https://github.com/cactus-compute/cactus-kotlin/issues/18)
- [Cactus Android AAR/weight mismatch issue #795](https://github.com/cactus-compute/cactus/issues/795)
- [Cactus 16 KB page-size issue #798](https://github.com/cactus-compute/cactus/issues/798)
- [Needle 3 engine-wheel issue #142](https://github.com/cactus-compute/needle/issues/142)
- [Needle 3 initialization issue #146](https://github.com/cactus-compute/needle/issues/146)
- [Needle depth issue #145](https://github.com/cactus-compute/needle/issues/145)
- [Needle engine-source issue #118](https://github.com/cactus-compute/needle/issues/118)
- [Legacy Cactus-Compute/needle model](https://huggingface.co/Cactus-Compute/needle)

## Research Status

- Official-source research: **complete**
- Local Needle inference corpus test: **not run**
- WordWise production integration: **not started**
- Main unresolved question: whether the standalone Needle 3 model produces useful grammar error spans, and whether an upstream-maintained Android/Kotlin integration will emerge.

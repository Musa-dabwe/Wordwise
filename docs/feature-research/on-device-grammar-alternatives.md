# Research: Small On-Device Grammar and Text-Editing Alternatives

**Research date:** 2026-09-25  
**Scope:** Offline, bundleable alternatives to Cactus Needle 3 for WordWise, with emphasis on small models and tools that return text edits rather than arbitrary generated prose.  
**Method:** Official project repositories, project documentation, model cards, and a host-side Harper 2.10.0 WebAssembly probe. No WordWise production source files were changed.

## Search Terms Used

- `small offline grammar correction model Android`
- `grammar correction ONNX T5 efficient tiny mini`
- `text editing model grammatical error correction sequence tagging`
- `GECToR LaserTagger Android ONNX`
- `Harper grammar checker WebAssembly spans suggestions`
- `nlprule Android grammar correction`
- `LanguageTool Android offline Java API`
- `SmolLM2 135M ONNX grammar correction`
- `LiteRT-LM Android small language model`
- `ONNX Runtime Mobile Android`

## Executive Finding

There are viable alternatives, but the strongest one is **not a generative LLM**:

1. **Best immediate pilot: Harper.** Harper is an English-only, Rust-powered grammar checker with an Apache-2.0 license and an official `harper.js` WebAssembly package. Its API returns lint spans, messages, priorities, and replacement/removal suggestions. The published full WebAssembly binary is about 16.2 MB; the slim binary is about 15.9 MB. It is not an LLM, but it maps most closely to WordWise's desired `start`/`end`/`replace` contract and is fast enough to be worth testing immediately.
2. **Best small neural grammar candidate: T5-Efficient-Tiny grammar correction.** The `visheratin` checkpoint is a 15.58M-parameter, English, grammar-specific model with an MIT license and ONNX export work. It is a full-text generator, not an edit-span model, so WordWise would need a deterministic diff adapter and careful no-change/overcorrection guards. The original repository's quantized graph set is incomplete for a turnkey generation path, so a reproducible export/quantization step is required.
3. **Best neural base if we are willing to fine-tune: Microsoft EdgeFormer/EdgeLM.** Microsoft released an approximately 11M-parameter on-device seq2seq checkpoint and reports competitive CoNLL-14 GEC results after task fine-tuning. It is not a ready grammar checkpoint, Android model, or ONNX artifact, but it is a much more credible long-term neural starting point than a general 135M chat model.
4. **Higher-quality but larger neural candidate: Unbabel `gec-t5_small`.** This is a 60M-parameter Apache-2.0 grammar-correction model with a reported F0.5 of 60.70 in its model card. A community ONNX conversion is available, but it is roughly 96 MB for the INT8 encoder/decoder plus tokenizer and should not be treated as a production artifact without pinning, licensing review, and independent validation.
5. **Best architecture for a future custom neural editor: LaserTagger or GECToR.** Both predict grounded edit operations rather than rewriting an entire sentence. GECToR has strong published grammar-quality evidence but its official BERT/RoBERTa checkpoints are 449–514 MB. LaserTagger has no released grammar checkpoint and would require training on WordWise correction pairs.
6. **Do not select a general-purpose LLM by parameter count alone.** SmolLM2-135M is the smallest permissive option with official ONNX artifacts, but it has no grammar-specific benchmark and its Q4F16 model is about 118 MB. Qwen2.5-0.5B, FLAN-T5-small, and Gemma 3 270M are larger, less editing-specific, or carry more licensing/integration trade-offs.

**Recommendation:** run a Harper-first feasibility spike. Keep the current cloud path as fallback for ambiguous or unsupported cases. Treat a small T5 grammar model as a separate quality experiment, not as the first edit-list implementation.

## Existing Code Found

- `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`: AccessibilityService orchestrator; currently replaces the complete focused-field text with the result from `AiClient`.
- `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`: OpenRouter-backed full-text correction contract; no local edit-list provider or confidence model exists.
- `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt`: service and command tests.
- `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt`: cloud client tests.
- `app/src/androidTest/`: instrumentation coverage for sensitive fields and cancellation.
- `app/build.gradle.kts`: `minSdk = 26`, Java/Kotlin 17, no native-model or ONNX dependency yet.

The current application can host a local provider behind a new interface, but it does not yet have a provider-neutral result type for spans, suggestions, suppression, or confidence.

## Similar Patterns

- **Harper `Lint` → WordWise edit:** Harper exposes a source span, problem text, message, lint kind, priority, and zero or more suggestions. A `Remove` suggestion maps naturally to an empty replacement; a `ReplaceWith` suggestion maps to the replacement string.
- **T5 output → deterministic edits:** A T5 grammar model returns a corrected sentence. WordWise should not ask it for character offsets. The app should validate the output, compute a Unicode-safe diff, reject unsupported rewrites, and then expose edits to the service.
- **GECToR/LaserTagger → edit operations:** These models naturally produce token-level keep/delete/add/replace-style transformations, but the available checkpoints or training pipelines are not Android-ready.
- **Cloud fallback:** A local provider can return high-confidence, narrowly scoped edits while leaving ambiguous suggestions to the existing cloud path. This is safer than auto-applying every local rewrite.

## Candidate Matrix

Sizes below are artifact sizes observed on 2026-09-25 unless stated otherwise. Runtime libraries, tokenizer code, ABI copies, and APK/AAB packaging overhead are not included.

| Option | Type and output | Size / parameters | License | Android path | Evidence and verdict |
|---|---|---:|---|---|---|
| **Harper 2.10.0** | Rule-based English linter; spans, messages, priorities, replace/remove suggestions | Full WASM 16,164,077 bytes; slim WASM 15,935,196 bytes | Apache-2.0 | Official `harper.js` WASM; no native Android SDK or documented Android support. A WebView/worker pilot is possible; production would likely use a project-owned Rust/JNI wrapper around `harper-core`. | **Pilot first.** Small, fast, private, and closest to the edit contract. English-only; incomplete coverage and ambiguous suggestions require policy. |
| **visheratin T5-Efficient-Tiny grammar** | 15.58M encoder-decoder grammar generator; full corrected text | 15.58M parameters; original ONNX quantized encoder 11,477,401 bytes and quantized initial decoder 20,977,764 bytes; complete generation graphs need validation/re-export | MIT on the original model card | ONNX Runtime Mobile; export/tokenizer/generation loop must be owned by the app | **Best neural pilot.** Grammar-specific and small, but not edit-native. The original graph set does not obviously provide every quantized decoder graph needed for a complete autoregressive loop. |
| **visheratin T5-Efficient-Mini grammar** | 31.23M grammar generator | 31.23M parameters; quantized encoder 20,343,833 bytes and quantized initial decoder 36,073,701 bytes | MIT | ONNX Runtime Mobile | **Second neural tier.** More capacity, but larger and still full-text generation. |
| **Microsoft EdgeFormer / EdgeLM** | Parameter-efficient encoder-decoder designed for on-device seq2seq; full corrected text | Official Adapter-LA checkpoint is approximately 11M parameters; downstream table reports 9.4M for the pretrained EdgeFormer variant | MIT repository/license; task checkpoint terms must be checked | Fine-tune and export to ONNX/LiteRT; no Android artifact | **Best neural base.** Explicitly evaluated for GEC and designed for memory/latency constraints, but no ready grammar checkpoint or mobile runtime package. |
| **Unbabel `gec-t5_small`** | 60M T5 grammar generator; full corrected text | 60.5M parameters; original PyTorch checkpoint about 242 MB; JonaWhisper INT8 encoder 35,518,119 bytes + decoder 58,449,240 bytes plus tokenizer | Apache-2.0 original; conversion repository has separate obligations | ONNX Runtime Mobile after a controlled export | **Higher-quality experiment.** More credible published GEC evidence than the tiny model, but roughly 96 MB of INT8 graphs and no official Android package. |
| **GECToR** | Transformer token tagger; grounded edit transformations | BERT checkpoint 448,749,083 bytes; RoBERTa 514,093,595 bytes | Repository Apache-2.0; checkpoint provenance/license should be checked separately | Custom export to ONNX/LiteRT; no official Android artifact | **Quality benchmark, not small shipping model.** Strong F0.5 evidence and up to 10x seq2seq speedup, but too large without distillation/quantization. |
| **LaserTagger FF/AR** | Static/autoregressive text tagger; keep/delete/add and optional swap | BERT-base class, approximately 110M parameters; no released grammar checkpoint | Apache-2.0 repository | Fine-tune, export to LiteRT/ONNX, then mobile runtime | **Best future custom editor.** Architecture matches edit lists and avoids free-form hallucination; requires a WordWise training/evaluation project. |
| **nlprule** | Rust rule/lookup linter; `start`, `end`, replacements, source, message | Compiled English tokenizer observed at 11,590,973 bytes; rules binary size not measured; 3,725 English grammar rules reported | MIT OR Apache-2.0 library; LanguageTool-derived binaries LGPL-2.1 | Rust NDK/JNI or WASM; no official Android target | **Viable fallback.** Better explicit span API than a generator, but older project, LGPL resource obligations, and no Android support. |
| **LanguageTool** | Mature rule-based Java linter; offsets, issue types, replacements | Current Java 17 core; current all-language distribution is large; no small Android artifact | LGPL-2.1-or-later | In-process Java is documented, Android compatibility is not; current dependency graph is heavy | **Not the small-APK choice.** Mature rules but poor Android fit and licensing/resource overhead. |
| **SmolLM2-135M Instruct** | General decoder-only LLM; arbitrary rewritten text | 134.5M; official ONNX Q4F16 117,691,126 bytes, INT8 137,147,867 bytes | Apache-2.0 | ONNX Runtime or a WebView/JS runtime; generation loop required | **Baseline only.** Model card mentions text rewriting but publishes no grammar benchmark; likely too weak/unpredictable for auto-apply. |
| **Qwen2.5-0.5B Instruct** | General multilingual instruction LLM | 0.49B; community Q4 GGUF approximately 429 MB | Apache-2.0 base model; verify every GGUF derivative | llama.cpp/LiteRT-LM; JNI/NDK work | **Reject for this tier.** General model, much larger, and no grammar/editing evidence. |
| **FLAN-T5-small** | General instruction seq2seq model | 76.96M; approximately 308 MB FP32 checkpoint | Apache-2.0 | Export and implement encoder/decoder generation | **Not a ready grammar model.** It has broad task fine-tuning but no grammar-specific checkpoint or mobile artifact. |
| **Gemma 3 270M IT** | General instruction LLM | 268M; generic LiteRT-LM Q8 artifact approximately 304 MB | Gemma Terms, not Apache-2.0 | LiteRT-LM has a stable Kotlin Android API | **Not shortlisted.** Larger than Harper/Tiny T5, non-Apache terms, and no grammar benchmark. |

## Harper Evidence

### What the official API provides

The official Harper documentation describes `harper.js` as an ECMAScript module backed by Harper core compiled to WebAssembly. `LocalLinter` and `WorkerLinter` both expose:

- `lint(text)`;
- `organizedLints(text)`;
- `applySuggestion(...)`;
- configurable rules and dialect;
- linter setup/disposal.

The official span documentation says a lint span identifies both the problematic text and the text edited by a suggestion. The Node example prints `span().start`, `span().end`, and replacement text. The `Lint` type also exposes a message, lint kind, and JSON representation; the observed JSON includes a numeric priority.

This is a much closer fit than a general LLM that must be prompted to emit JSON offsets.

### Host-side probe

I ran the published `harper.js` 2.10.0 package under Node 26 on a 41-sentence corpus:

- 10 correct control sentences;
- 31 sentences covering articles, spelling, agreement, tense, homophones, word choice, punctuation, contractions, and non-ASCII text;
- 22 sentences produced at least one lint;
- all 10 correct controls produced no lint in this small sample;
- warm lint median across two host runs: **5.6–6.4 ms**;
- warm lint p95 across two host runs: **10.5–18.6 ms**;
- maximum observed: **111–140 ms**, caused by early dictionary/spelling warm-up;
- initial `setup()`: approximately **1.37–1.72 s** on the host.

The probe caught examples such as:

- `This is a example` → `an`;
- `She dont like pizza` → `don't`;
- `I has completed` → `have`;
- `They was going` → `were`;
- `I goed` → `gone`/`went`;
- `This will effect` → `affect`;
- `I could of helped` → `could have`;
- `Despite of` → `Despite`/`In spite of`;
- `Its a beautiful day` → `It's`;
- spacing before a comma and trailing whitespace.

The probe also exposed limitations that matter for production:

- it missed `He can to swim`, `The books is`, `There are less people`, `Your going`, `Me and him went`, and `I did good on the test`;
- one lint (`We was tired`) had no replacement suggestion;
- some spelling suggestions were ambiguous or included formatting variants;
- a rule-based linter will not match a broad generative model's ability to rewrite awkward sentences.

These results are a feasibility signal, not a benchmark. The corpus is small and author-written.

### Unicode observation

For test strings containing supplementary-plane characters, the JavaScript binding returned span offsets that matched UTF-16 code-unit positions in the tested examples, including an emoji and a ZWJ emoji sequence. The public documentation describes spans as Unicode scalar-value windows, so this discrepancy must be treated as an implementation detail to verify against Android `Editable` rather than assumed. WordWise should add explicit UTF-16, surrogate-pair, combining-mark, and emoji tests before applying edits.

### Size and packaging caveat

The npm tarball is about 34 MB compressed and contains full, slim, external, and inlined variants. That is not the required APK payload. A production package should copy only the JavaScript glue and one external WASM binary; the full and slim WASM files themselves are about 16 MB. The slim binary missed at least one homophone rule in the host probe, so the full binary is the safer initial artifact despite the negligible size difference.

## Android Harper Pilot Results

A throwaway Android WebView/WASM harness was built outside the production app and run on the connected ARM64 device (`PCLM50`, Android 12/API 31, WebView/Chrome 151). It loaded the pinned Harper 2.10.0 JavaScript and both WASM assets through `WebViewAssetLoader`; the APK declared no network permission.

Observed results:

- debug APK: **18,420,881 bytes** compressed;
- cold `setup()`: **2.26–2.66 seconds** across runs;
- warm lint median: **1.5–1.7 ms**;
- warm p95: **2.0–2.6 ms**;
- warm maximum: **2.2–3.9 ms**;
- 22 of 41 corpus sentences produced lints;
- 0 of 10 correct controls produced lints;
- six representative suggestions were applied successfully, including agreement, homophone, accented text, emoji, and a ZWJ emoji sequence;
- returned spans matched the tested JavaScript/Android UTF-16 string positions, including `😊` and `👩🏽‍💻` cases;
- post-setup process PSS was approximately **104–113 MB**, including the WebView and app process; the before/after corpus delta was small, but this is not a peak-memory measurement.

The full Harper loader probes the slim WASM asset before loading the full asset. A production package therefore cannot blindly include only the 16.2 MB full file; the pilot included both the 16.2 MB full and 15.9 MB slim assets, producing an 18.4 MB compressed APK. A production packaging pass should compare the external two-asset layout with an inlined or Rust/JNI layout.

The pilot proves that Harper can run offline in an Android WebView and can produce/apply WordWise-shaped edits on the target device. It does not prove production grammar coverage, peak memory, battery impact, or that a WebView is the preferred production integration. The next quality gate is a larger expected-edit corpus and a decision between retaining the WebView bridge and implementing a native Rust/JNI wrapper.


### T5-Efficient-Tiny/Mini

The original model cards state:

- Tiny: 15.58M parameters;
- Mini: 31.23M parameters;
- both are English T5 encoder-decoder models fine-tuned for grammar correction on a C4_200M subset with synthetic typo augmentation;
- the author's demo reports validation loss of 0.08 for Tiny and 0.06 for Mini;
- ONNX variants are available in the model repositories.

These are useful, small, task-specific models. They are not edit-list models: the output is a generated sentence. The original Tiny repository contains quantized encoder and initial-decoder graphs, but the visible file set does not include an obviously complete quantized autoregressive decoder graph. A reproducible export/quantization pass is safer than assuming that a partial graph set is sufficient.

A third-party `TonyRaju` repository provides a complete INT8 Tiny graph set and reports a 50-sentence benchmark, but it is a derivative artifact. Its benchmark and license metadata should not be treated as authoritative until the original model, conversion procedure, and redistribution terms are independently checked. WordWise should generate its own pinned artifact if this route proceeds.

### Microsoft EdgeFormer / EdgeLM

Microsoft's official EdgeFormer repository is explicitly designed for parameter-efficient on-device seq2seq generation. It publishes an approximately 11M-parameter Adapter-LA pretrained checkpoint, and its downstream table reports a 9.4M-parameter pretrained EdgeFormer result with F0.5 52.7 on CoNLL-14 after task fine-tuning. The associated Microsoft Research grammar-checker article describes EdgeLM as the foundation of a client grammar model and discusses aggressive decoding and ONNX Runtime optimizations.

This is a strong long-term candidate because it is smaller and more purpose-built than a general chat model. It is not an immediate drop-in: the public checkpoint is a generic pretrained model, the repository uses Fairseq-era training code, and no Android/ONNX grammar artifact is published. WordWise would need to fine-tune it on correction pairs, export/quantize it, and validate the generated-text-to-edit adapter.

### Unbabel `gec-t5_small`

The original Unbabel model is Apache-2.0, T5-small-sized, and explicitly trained for grammatical error correction. Its model card reports F0.5 60.70 from the cited recipe. It is a stronger quality candidate than the 15M model, but the original checkpoint is about 242 MB. The JonaWhisper ONNX conversion provides INT8 encoder/decoder files totaling about 94 MB before tokenizer and runtime overhead; its conversion scripts have a separate GPL-3.0 notice. This is a reasonable optional-download experiment, not a small bundled default without a license review.

### General LLMs

SmolLM2's official card says the 135M instruction model can perform text rewriting and publishes ONNX artifacts. It does not publish a grammar-correction score. Qwen2.5-0.5B, FLAN-T5-small, and Gemma 3 270M likewise lack a task-specific WordWise evaluation. Their extra capacity may improve fluency, but it also increases hallucination, over-correction, battery, and APK-size risk. They should be controls in an experiment, not the default offline provider.

## Edit-Native Neural Options

### GECToR

The official Grammarly repository implements token-level transformations and publishes BERT, RoBERTa, and XLNet checkpoints. Its README reports F0.5 61.0/68.0 for BERT and 64.0/71.8 for RoBERTa on CoNLL-2014/BEA-2019, and the paper reports up to 10x faster inference than a comparable seq2seq system. This is strong evidence that the architecture is suitable for grammar editing.

It is not a small Android model: the official BERT checkpoint is about 449 MB and the RoBERTa checkpoint about 514 MB. It is best treated as a teacher, benchmark, or starting point for distillation.

### LaserTagger

Google's official LaserTagger predicts `KEEP`, `DELETE`, and phrase additions, with an optional sentence swap. The paper reports that the approach is less prone to hallucination and can be more than two orders of magnitude faster than comparable seq2seq models. The low-resource GEC experiment used only 4,384 training sentences and reported F0.5 37.82 for FF and 40.52 for AR.

There is no released grammar checkpoint in the repository. A WordWise-specific model would require:

1. a curated set of input/corrected-text pairs;
2. a constrained phrase vocabulary for common insertions/replacements;
3. fine-tuning and calibration against false positives;
4. export to a mobile-friendly static graph runtime;
5. a realizer that emits UTF-16-safe spans.

This is the most attractive long-term architecture if WordWise needs a proprietary neural editor, but it is a training project rather than a dependency to add now.

## WordWise Fit

### Harper adapter

A local Harper result can be normalized into a provider-neutral result such as:

```kotlin
data class LocalGrammarEdit(
    val start: Int,
    val end: Int,
    val replacement: String,
    val kind: String,
    val message: String,
    val priority: Int,
    val autoApply: Boolean
)
```

The adapter should:

- convert `SuggestionKind.Remove` to an empty replacement;
- retain all suggestions rather than blindly choosing the first;
- reject overlapping edits or resolve them deterministically;
- treat a lint with no replacement as informational;
- map dialect and user dictionary settings explicitly;
- run off the AccessibilityService's main thread;
- use the cloud path for ambiguous, low-priority, or unsupported requests.

### T5 adapter

For a T5 model, the adapter should treat the result as a candidate full-text rewrite:

1. reject empty/overlong output;
2. preserve the user's punctuation, casing, names, and formatting unless the model has evidence to change them;
3. compute a deterministic diff rather than trusting model-generated offsets;
4. reject changes outside a validated allowlist of grammar/error patterns;
5. use sentence or paragraph windows rather than unlimited context;
6. measure unchanged-text suppression and false-positive rate.

## Recommended Experiment

### Phase 1: Harper feasibility

1. Pin `harper.js` 2.10.0 and record the npm tarball checksum (`sha256:a0799595fd6312b3e862b8ab06efe18c69dc093e2257ebcc7d1fa7beb3dabdab` for the artifact inspected here).
2. Package only the full external WASM binary and required JS glue in a temporary test surface.
3. Run the 41-sentence host corpus, then repeat it on the connected Android device/API 31 and on an API 26 emulator or device if available.
4. Record cold setup, warm p50/p95 latency, memory, APK/AAB delta, and offline behavior.
5. Add exact expected spans and suggestions for every corpus item; measure:
   - span recall;
   - suggestion precision;
   - false-positive rate on correct controls;
   - no-suggestion rate;
   - UTF-16 correctness around emoji and combining marks;
   - multi-edit overlap behavior;
   - behavior with names, technical terms, slang, and dialect variants.
6. Only auto-apply a small allowlist of high-precision rule kinds. Keep ambiguous suggestions visible but not automatic.

### Phase 2: neural comparison

Run the same corpus against:

- a self-exported T5-Efficient-Tiny ONNX model;
- optionally, a self-exported `gec-t5_small` ONNX model;
- a small general model such as SmolLM2-135M only as a control.

Use the same input window, no-change guard, Unicode diff adapter, and metrics. Do not compare raw BLEU alone; record edit precision, false positives, suppression, and human-rated acceptability.

### Phase 3: production decision

Choose among:

- **Harper-only local tier:** simplest, smallest, deterministic, English-only, incomplete coverage;
- **Harper plus cloud fallback:** recommended first product architecture;
- **Harper plus a validated T5 rewrite tier:** more coverage but higher size and over-correction risk;
- **custom LaserTagger/GECToR model:** best long-term edit-native control, highest ML effort;
- **retain cloud/AICore:** if local quality or device coverage misses the product threshold.

## New Implementation Required

No production implementation was started. A later implementation would require:

- a provider-neutral local grammar result/edit type;
- a Harper Rust/JNI or WebView bridge with lifecycle and cancellation handling;
- a pinned model/artifact manifest and license notice bundle;
- UTF-16-safe span validation and reverse-order edit application;
- a policy for ambiguous suggestions, dialect, dictionary, and custom vocabulary;
- optional model download/asset-delivery strategy if the APK budget is too small;
- Android 15/16 KB native/WebView packaging validation;
- corpus-based regression tests and a cloud-fallback threshold.

## Implementation Plan

1. Complete the Harper Android pilot without changing the current cloud contract.
2. Add a small provider interface only after the pilot identifies the required result semantics.
3. Implement the Harper adapter and deterministic edit-application tests if the quality gate passes.
4. Run the controlled T5 comparison separately; do not combine both providers until their false-positive behavior is measured.
5. Revisit a custom LaserTagger/GECToR model only if Harper's coverage is insufficient and WordWise can fund training/evaluation.

## Source Index

### Harper

- [Harper repository](https://github.com/Automattic/harper)
- [Harper `harper.js` introduction](https://writewithharper.com/docs/harperjs/introduction)
- [Harper linting API](https://writewithharper.com/docs/harperjs/linting)
- [Harper span semantics](https://writewithharper.com/docs/harperjs/spans)
- [Harper Node example](https://writewithharper.com/docs/harperjs/node)
- [Harper project comparison](https://github.com/Automattic/harper/blob/master/COMPARISON.md)
- [Harper Android support request](https://github.com/Automattic/harper/issues/2316)
- [Harper npm package](https://www.npmjs.com/package/harper.js)

### EdgeFormer

- [Deferred WordWise EdgeFormer training plan](edgeformer-wordwise-training-plan.md)
- [Microsoft EdgeFormer repository](https://github.com/microsoft/unilm/tree/master/edgelm)
- [Microsoft Research EdgeFormer publication](https://www.microsoft.com/en-us/research/publication/edgeformer-a-parameter-efficient-transformer-for-on-device-seq2seq-generation/)
- [Microsoft Editor neural grammar checker article](https://www.microsoft.com/en-us/research/blog/achieving-zero-cogs-with-microsoft-editor-neural-grammar-checker/)
- [EdgeFormer paper](https://aclanthology.org/2022.emnlp-main.741/)

### T5 grammar models

- [T5-Efficient-Tiny grammar model](https://huggingface.co/visheratin/t5-efficient-tiny-grammar-correction)
- [T5-Efficient-Mini grammar model](https://huggingface.co/visheratin/t5-efficient-mini-grammar-correction)
- [Author's standalone grammar demo](https://edge-ai.vercel.app/demos/grammar-check)
- [Google T5-Efficient-Tiny base model](https://huggingface.co/google/t5-efficient-tiny)
- [Unbabel `gec-t5_small`](https://huggingface.co/Unbabel/gec-t5_small)
- [JonaWhisper ONNX conversion](https://huggingface.co/JonaWhisper/jonawhisper-gec-t5-small-onnx)
- [Third-party Tiny INT8 conversion](https://huggingface.co/TonyRaju/visheratin-t5-tiny-grammar-correction-onnx-int8)

### Edit-native models

- [Official GECToR repository](https://github.com/grammarly/gector)
- [GECToR paper](https://aclanthology.org/2020.bea-1.16/)
- [Official LaserTagger repository](https://github.com/google-research/lasertagger)
- [LaserTagger paper](https://arxiv.org/abs/1909.01187)

### Rule-based alternatives

- [LanguageTool repository](https://github.com/languagetool-org/languagetool)
- [LanguageTool Java API](https://dev.languagetool.org/java-api)
- [nlprule repository](https://github.com/bminixhofer/nlprule)
- [nlprule README and licensing](https://github.com/bminixhofer/nlprule#readme)

### Runtimes and general models

- [ONNX Runtime mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/)
- [ONNX Runtime Android build/integration](https://onnxruntime.ai/docs/build/android.html)
- [LiteRT-LM Android Kotlin API](https://ai.google.dev/edge/litert-lm/android)
- [SmolLM2-135M Instruct](https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct)
- [Qwen2.5-0.5B Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct)
- [Qwen2.5-0.5B Instruct GGUF derivatives](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF)
- [FLAN-T5-small](https://huggingface.co/google/flan-t5-small)
- [llama.cpp Android guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)

## Research Status

- Official-source comparison: **complete for the shortlisted options**.
- Harper host-side corpus probe: **run** (`harper.js` 2.10.0, Node 26, 41 sentences).
- Harper Android/WebView pilot: **run** on ARM64 API 31; native Rust/JNI probe: **not run**.
- T5 model inference/quality comparison: **not run**.
- WordWise production integration: **not started**.
- Main unresolved questions: peak memory, battery impact, packaging trade-offs between WebView and Rust/JNI, larger-corpus grammar quality, and whether Harper's coverage is sufficient for WordWise's target writing.

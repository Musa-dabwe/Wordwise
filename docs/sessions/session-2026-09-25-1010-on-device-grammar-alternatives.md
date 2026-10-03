# Session: 2026-09-25 10:10
**Duration**: 10:10 - 11:10 (approximate)  
**Project**: WordWise

## Objective

Determine whether a small, offline, bundleable grammar/text-editing model or engine can replace the blocked Cactus Needle 3 path, with emphasis on output contracts that fit WordWise's edit-list/application model.

## Research Phase

The existing WordWise flow was re-checked: `GrammarFixService` delegates to the OpenRouter-backed `AiClient` and currently replaces full field text. The app has `minSdk = 26` and Java/Kotlin 17, but no local provider abstraction or native-model dependency.

The full research report is in [feature-research/on-device-grammar-alternatives.md](../feature-research/on-device-grammar-alternatives.md). It compares Harper, small T5 grammar models, GECToR, LaserTagger, nlprule, LanguageTool, general small LLMs, and mobile runtimes.

Key findings:

- Harper is the strongest immediate pilot: Apache-2.0, about 16 MB for the full WASM binary, span/suggestion API, no neural model required, but English-only and without official Android support.
- T5-Efficient-Tiny grammar correction is the strongest small neural candidate: 15.58M parameters, MIT, grammar-specific, ONNX work available, but it returns generated text rather than grounded edits and needs a reproducible export path.
- Microsoft EdgeFormer/EdgeLM is the strongest neural base if WordWise is willing to fine-tune: an approximately 11M on-device seq2seq checkpoint with published GEC-oriented research, but no ready grammar checkpoint or Android artifact.
- Unbabel `gec-t5_small` is a higher-quality but larger neural experiment; the original is Apache-2.0, while the available ONNX conversion is third-party.
- GECToR and LaserTagger are the best edit-native neural architectures, but their available GECToR checkpoints are hundreds of MB and LaserTagger has no released grammar checkpoint.
- General models such as SmolLM2-135M are too large relative to Harper/Tiny T5 and have no grammar-specific benchmark.

## Implementation Steps

1. Researched official repositories, model cards, runtime documentation, and licensing for the shortlisted alternatives.
2. Ran a host-side Harper 2.10.0 WebAssembly probe under Node 26 using a 41-sentence corpus covering correct controls, agreement, spelling, tense, homophones, punctuation, contractions, emoji, and accented text.
3. Recorded the observed Harper behavior, latency, output shape, Unicode observations, and limitations in the feature-research report.
4. Defined a staged recommendation: Harper Android feasibility first; controlled T5 comparison second; custom LaserTagger/GECToR only if local coverage is insufficient.
5. No WordWise production source files were changed.

## Bugs Discovered & Fixed

- **Bug #[ID]**: No production bug was investigated or fixed in this session.
  - Root cause: N/A
  - Fix applied: N/A
  - File: N/A
  - Status: NOT APPLICABLE

## Testing Performed

- **Unit Tests**: No WordWise unit tests run; no source implementation was changed.
- **Integration Tests**: No Android integration test run.
- **Manual Testing**: Harper 2.10.0 host-side Node probe completed twice. 41 sentences produced 22 linted sentences; all 10 correct controls were clean in this small sample. Across the runs, warm median was 5.6–6.4 ms, p95 was 10.5–18.6 ms, maximum observed latency was 111–140 ms, and setup was approximately 1.37–1.72 s. The probe exposed both useful corrections and missed/ambiguous cases.
- **Regression Testing**: `git diff --check` passed after documentation changes. No application behavior was changed.

## AI Models Used & Their Role

- **Space Bunny Free (OpenCode)**: primary research, source comparison, Harper host probe, feasibility synthesis, and documentation.
  - Tasks: official-source review, model/runtime/license comparison, corpus probe, WordWise-specific recommendation.
  - Effectiveness: high for the scoped research; Android runtime behavior and grammar quality remain unverified.
- **Research subagent (general)**: independent first-party-source comparison of grammar models, mobile runtimes, size, license, and output contracts.
  - Tasks: cross-check candidate matrix and identify Harper/LaserTagger/GECToR alternatives.
  - Effectiveness: high; findings were independently checked against official project pages where used in the report.
- **Context7**: current LanguageTool, nlprule, ONNX Runtime, and llama.cpp documentation queries.

## Key Decisions Made

- Treat Harper as the first feasibility pilot even though it is not an LLM, because its span/suggestion contract is much closer to WordWise's needs and its size is close to Needle 3.
- Do not ask a small generative model to produce arbitrary character offsets. For T5, derive edits from a validated full-text diff instead.
- Keep cloud correction as a fallback for ambiguous, unsupported, or low-confidence local cases.
- Pin all candidate artifacts before testing; do not rely on mutable `main`, `latest`, or community conversion repositories.
- Treat Android WebView/WASM as a pilot path, not a documented Harper Android integration; a Rust/JNI wrapper is the likely production path.

## Build Outputs Generated

Path: `~/storage/shared/Docs/Build/`  
Files created: none

## Issues & Blockers

- Harper has no official Android SDK or native Android artifact; Android WebView/JNI behavior is untested.
- The T5 repositories' quantized graph sets need a reproducible export/quantization check before they can be bundled.
- General small LLMs lack grammar-specific evidence.
- The connected test device is ARM64 API 31 with a 4 KB page size; Android 15/16 KB packaging validation is still pending for any native path.

## Performance Metrics

- Harper host probe: 41 sentences; across two runs, warm p50 5.6–6.4 ms, p95 10.5–18.6 ms, max 111–140 ms; setup approximately 1.37–1.72 s.
- Harper WASM artifact: 16,164,077 bytes full; 15,935,196 bytes slim.
- No APK/AAB build was produced.

## Next Session Priorities

- [ ] Package a temporary Harper test surface and run it on the connected Android device.
- [ ] Add a 50-sentence expected-edit corpus with UTF-16 and ambiguity cases.
- [ ] Export and benchmark a self-contained T5-Efficient-Tiny ONNX artifact if Harper's coverage is insufficient.
- [ ] Decide whether Harper-only, Harper plus cloud, or a neural editor is the product direction.

## Related Sessions

- See also: [session-2026-09-25-0934-needle3-cactus-feasibility-spike.md](session-2026-09-25-0934-needle3-cactus-feasibility-spike.md)
- Continuation of: the Needle 3/Cactus feasibility spike

# Session: 2026-09-25 11:47
**Duration**: 11:47 - 12:22 (approximate)  
**Project**: WordWise

## Objective

Pilot Harper as the first offline grammar/edit-list candidate on Android, while preserving a durable deferred plan for training a WordWise-specific EdgeFormer model when suitable hardware becomes available.

## Research Phase

The existing WordWise production path was left unchanged. The pilot used the official Harper 2.10.0 `harper.js` package, the full external WASM binary, and AndroidX WebKit's `WebViewAssetLoader` to serve local HTML/modules/WASM over an HTTPS-like asset origin.

The future neural-training plan is documented in [feature-research/edgeformer-wordwise-training-plan.md](../feature-research/edgeformer-wordwise-training-plan.md). The broader candidate comparison and pilot results are in [feature-research/on-device-grammar-alternatives.md](../feature-research/on-device-grammar-alternatives.md).

## Implementation Steps

1. Created the deferred EdgeFormer training plan with hardware triggers, dataset policy, training phases, export strategy, evaluation gates, and licensing safeguards.
2. Built a throwaway Android project at `/tmp/opencode/harper-android-pilot`; it is outside the WordWise repository and is not production code.
3. Added Harper 2.10.0 JS glue, full WASM, slim fallback WASM, a local WebView asset loader, a JavaScript bridge, memory snapshots, and a 41-sentence corpus runner.
4. Built and installed the debug APK on the connected ARM64 API 31 device.
5. Ran the pilot repeatedly, collected JSON results through the app sandbox, verified suggestion application and Unicode spans, and captured a screenshot/UI layout check.
6. Left `app/` and all WordWise production source files unchanged.

## Bugs Discovered & Fixed

- **Bug #[PILOT-001]**: Standalone Gradle project could not locate the Android SDK.
  - Root cause: Gradle's `-p` invocation uses the temporary project root and does not inherit WordWise's untracked `local.properties`.
  - Fix applied: added `/tmp/opencode/harper-android-pilot/local.properties` with `sdk.dir=/home/musa/Android/Sdk`.
  - File: temporary pilot configuration only.
  - Status: **FIXED**.

- **Bug #[PILOT-002]**: `WebViewClientCompat` rejected the modern `onReceivedError` override.
  - Root cause: AndroidX WebKit 1.17.1 makes the framework `WebResourceError` overload final and exposes a separate `WebResourceErrorCompat` callback.
  - Fix applied: changed the pilot callback to `onReceivedError(..., WebResourceErrorCompat)`.
  - File: `/tmp/opencode/harper-android-pilot/app/src/main/java/com/musa/harperpilot/MainActivity.java`.
  - Status: **FIXED**.

- **Bug #[PILOT-003]**: Full Harper binary initially logged a missing slim WASM network error.
  - Root cause: Harper's full loader probes the slim WASM asset before loading the full asset; only the full file had initially been copied.
  - Fix applied: included the official slim WASM asset in the temporary APK.
  - File: temporary pilot assets only.
  - Status: **FIXED**.

- **Bug #[PILOT-004]**: Pilot emitted a benign favicon cache error.
  - Root cause: the test page had no favicon and the asset origin returned a cache miss.
  - Fix applied: added an empty data-URI favicon to the temporary page.
  - File: `/tmp/opencode/harper-android-pilot/app/src/main/assets/index.html`.
  - Status: **FIXED**.

## Testing Performed

- **Unit Tests**: No WordWise unit tests run; production source was not changed.
- **Integration Tests**: Built the temporary Android app successfully with Gradle 8.11.1/AGP 8.9.1; installed and launched it on `PCLM50` (ARM64, Android 12/API 31).
- **Manual Testing**:
  - 41-sentence corpus executed twice per run;
  - final clean run: setup **2,595.1 ms**, warm p50 **1.6 ms**, p95 **2.0 ms**, max **2.3 ms**;
  - verification rerun: setup **2,425.5 ms**, warm p50 **1.7 ms**, p95 **2.6 ms**, max **3.9 ms**;
  - 22 sentences produced lints; 0/10 correct controls produced lints;
  - six sampled suggestions applied successfully;
  - UTF-16-looking spans were correct around accented text, `😊`, and a ZWJ emoji sequence;
  - debug APK size: **18,420,881 bytes** compressed;
  - post-setup PSS: approximately **104–113 MB**, including WebView/process overhead; peak memory was not measured;
  - APK declared no network permissions;
  - final Logcat run contained only page-finished/result-saved messages after the favicon fix.
- **Regression Testing**: WordWise production build/tests were not run because no production source or dependency was changed. `git diff --check` and documentation checks were run separately.

## AI Models Used & Their Role

- **Space Bunny Free (OpenCode)**: designed the isolated pilot, researched AndroidX WebView asset loading, built the temporary harness, diagnosed build/runtime issues, ran device measurements, and documented results.
  - Tasks: implementation, debugging, Android validation, measurement, and research documentation.
  - Effectiveness: high for the feasibility question; production grammar quality remains intentionally unproven.
- **Context7**: queried current AndroidX WebViewAssetLoader and WebViewClientCompat API guidance.
- **Research sources**: official Harper, AndroidX, and EdgeFormer documentation from the prior research session.

## Key Decisions Made

- Keep the Harper harness outside the repository and production app until a larger quality corpus and packaging decision are complete.
- Use Harper's structured suggestions as a direct edit-list source; do not derive offsets from arbitrary generated prose.
- Preserve cloud fallback for ambiguous or unsupported suggestions.
- Defer EdgeFormer training on the current laptop; use it for corpus preparation, inference, and export experiments only.
- Require pinned checkpoints, dataset manifests, licensing review, and measured hardware capacity before future training.

## Build Outputs Generated

Path: temporary pilot directory `/tmp/opencode/harper-android-pilot`  
Files created:
- `app/build/outputs/apk/debug/app-debug.apk` — throwaway 18.4 MB debug APK.
- `/tmp/opencode/harper-android-result-clean.json` — final device result.
- `/tmp/opencode/harper-pilot-screen.png` — device screenshot.

No files were exported to `~/storage/shared/Docs/Build/` because this was a feasibility artifact, not a distributable build.

## Issues & Blockers

- WebView/WASM works on the tested device, but a native Rust/JNI path has not been built or benchmarked.
- The full Harper loader requires the slim fallback asset in the tested external-WASM layout; a production package should compare this with an inlined/native layout.
- The corpus is small and author-written; it is not a grammar-quality benchmark.
- Peak memory, battery impact, startup UX, and behavior on other Android/WebView versions remain unknown.
- EdgeFormer training remains intentionally deferred until suitable hardware and a versioned dataset are available.

## Performance Metrics

- Temporary debug build: successful; final build completed in approximately 20 seconds with warm Gradle tasks.
- Device: ARM64 API 31, WebView/Chrome 151.
- Cold setup: 2.26–2.66 seconds across runs.
- Warm p50/p95: 1.5–1.7 ms / 2.0–2.6 ms.
- Compressed APK: 18,420,881 bytes with full and slim WASM assets.
- Post-setup PSS: approximately 104–113 MB including WebView overhead.

## Next Session Priorities

- [ ] Expand the corpus to 50–100 expected-edit cases with ambiguous, technical, and user-specific examples.
- [ ] Decide whether to keep the WebView bridge for the next prototype or build a native Rust/JNI wrapper.
- [ ] Measure peak memory, cold-start UX, battery impact, and behavior on additional Android/WebView versions.
- [ ] Define a Harper-to-WordWise provider interface only after the quality gate is agreed.
- [ ] Keep EdgeFormer training deferred until the documented hardware and dataset gates are met.

## Related Sessions

- See also: [session-2026-09-25-1010-on-device-grammar-alternatives.md](session-2026-09-25-1010-on-device-grammar-alternatives.md)
- See also: [session-2026-09-25-0934-needle3-cactus-feasibility-spike.md](session-2026-09-25-0934-needle3-cactus-feasibility-spike.md)
- Continuation of: the on-device grammar alternatives research

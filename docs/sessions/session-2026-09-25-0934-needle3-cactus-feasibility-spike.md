# Session: 2026-09-25 09:34–10:05 CAT
**Duration**: 09:34–10:05 CAT  
**Project**: WordWise

## Objective

Research official Cactus Compute sources to determine whether Needle 3 can support WordWise's offline grammar correction idea, whether edit-list extraction is compatible with its documented contract, and what Android integration path is actually available.

## Research Phase

- Inspected the current WordWise architecture and tests:
  - `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`
  - `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`
  - `app/src/test/`
  - `app/src/androidTest/`
- Confirmed the attached `/home/musa/Cactus-Compute` directory is empty; no local Cactus source or weights are available.
- Cloned shallow read-only snapshots of the official repositories into `/tmp/opencode/cactus-src/` for source inspection:
  - `cactus-compute/needle`
  - `cactus-compute/cactus`
  - `cactus-compute/cactus-kotlin`
- Inspected official model cards, repository READMEs, native headers, Android build files, Kotlin bindings, C API implementations, and open upstream issues.
- Recorded the full research report in [docs/feature-research/needle3-cactus-android.md](../feature-research/needle3-cactus-android.md).

## Implementation Steps

1. Created the required feature research document at `docs/feature-research/needle3-cactus-android.md` with source links, findings, risks, and a follow-up experiment plan.
2. Kept production code unchanged; this session was a read-only feasibility investigation.
3. Recorded the main architectural finding: Needle 3's documented grounding contract requires extracted values to come from input spans, which creates a direct risk for arbitrary `replace` strings and numeric character offsets in the proposed edit schema.
4. Recorded the integration finding: standalone Needle 3 publishes Android `libneedle.a`/`needle.h` artifacts, while the official Cactus Kotlin SDK targets Cactus Engine bundles and is not documented as a Needle 3 loader.

## Bugs Discovered & Fixed

- **Bug #[upstream-1]**: `cactus_init` cannot load the standalone `needle3.cact` file as a Cactus Engine model; it expects a bundle directory with `config.txt` and `components/manifest.json`.
  - Root cause: Cactus Engine's bundle format and standalone Needle 3 `.cact` format are different.
  - Fix applied: none in WordWise; documented the mismatch and the open upstream issue `cactus-compute/cactus#813`.
  - File: `docs/feature-research/needle3-cactus-android.md`
  - Status: **PENDING / upstream integration blocker**

- **Bug #[upstream-2]**: Current Needle 3 fetch code requests engine wheel version `3.0.2`, while the official Hugging Face repository inspected on 2026-09-25 listed `3.0.0` and `3.0.1` wheels only.
  - Root cause: release synchronization between the GitHub package and Hugging Face artifacts.
  - Fix applied: none; documented the need to pin revisions and reported the open issues `cactus-compute/needle#142` and `#146`.
  - File: `docs/feature-research/needle3-cactus-android.md`
  - Status: **PENDING / packaging blocker**

- **Bug #[release-1]**: The official `cactus-kotlin` repository is archived, and its Cactus core/Kotlin licenses restrict commercial use outside stated funding/revenue thresholds despite Apache-style package metadata.
  - Root cause: upstream project status and license metadata are not aligned with a straightforward commercial SDK dependency.
  - Fix applied: none; documented the issue and added legal review plus 16 KB Android release validation as production gates.
  - File: `docs/feature-research/needle3-cactus-android.md`
  - Status: **PENDING / legal and release review**

## Testing Performed

- **Unit Tests**: Not run; no production code changed.
- **Integration Tests**: Not run; no Needle runtime or WordWise integration was added.
- **Manual Testing**: Inspected official web pages, GitHub source snapshots, Hugging Face metadata, native headers, and Android/Kotlin build configuration.
- **Regression Testing**: Not applicable; no WordWise source was modified.

## AI Models Used & Their Role

- **Space Bunny Free (current agent)**:
  - Tasks: repository inspection, official-source synthesis, Android/runtime comparison, risk analysis, research documentation.
  - Effectiveness: high; separated legacy Needle from Needle 3 and verified the proposed schema against the official grounding contract.
- **Research subagent (general)**:
  - Tasks: independent official-source review of Needle 3, Cactus Engine, Cactus Kotlin, Android artifacts, licensing, and upstream issues.
  - Effectiveness: high; independently confirmed the legacy/Needle 3 split, the lack of a direct Kotlin loader, the span/offset limitation, and additional release/licensing risks.

## Key Decisions Made

- Treat this as a feasibility spike rather than a production feature implementation.
- Do not use the Cactus Kotlin SDK as the assumed Needle 3 integration path; use the standalone Needle 3 C API for the next probe unless upstream provides an official AAR/bundle path.
- Treat the archived SDK, restricted Cactus core/Kotlin licenses, and open 16 KB alignment reports as production blockers requiring explicit review.
- Test a contract-compatible exact-substring-plus-error-class schema before treating arbitrary replacement generation as viable.
- Pin model and engine revisions for any future corpus test; do not depend on mutable `main`/latest artifacts.

## Build Outputs Generated

Path: `~/storage/shared/Docs/Build/`

- No build artifacts generated; research-only session.

## Issues & Blockers

- The attached Cactus-Compute directory is empty.
- No Android device or runnable Needle 3 artifact has been tested yet.
- Cactus Engine and standalone Needle 3 use incompatible model-container formats for the attempted JNI path.
- The original `replace` field conflicts with Needle 3's documented span-grounding behavior and needs empirical validation.
- The Cactus Kotlin repository is archived; Cactus core/Kotlin commercial licensing is unresolved; Android 15/16 KB alignment reports remain open.

## Performance Metrics

- Build time: not applicable.
- File size: not applicable.
- Code complexity change: unchanged; no production code changed.

## Next Session Priorities

- [ ] Acquire the pinned Needle 3 Android/native artifacts or run the host-side C API probe.
- [ ] Build a 30–50 sentence corpus and compare exact-substring/error-class extraction with the original offset/replacement schema.
- [ ] Record span recall, false positives, suppression rate, offset correctness, and non-ASCII behavior.
- [ ] Decide whether to pursue a custom JNI integration, request upstream support, or keep the cloud/AICore path.

## Related Sessions

- See also: `session-2026-09-24-2051-brag-launch-video.md`
- Continuation of: the Needle 3 feasibility spike started in this session.

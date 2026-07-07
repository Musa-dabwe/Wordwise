# WordWise — Dead Code Audit

**Audit date:** 20-06-2026
**Auditor:** OpenCode + Context7
**Scope:** Static analysis only — no runtime profiling.
**Codebase:** 4 Kotlin source files, 4 resource/config files, 3 build files, themes
**All findings resolved:** Yes — see Removal Execution Log below.

---

## Component Inventory

| Component | File | Status |
|---|---|---|
| `MainActivity` | MainActivity.kt | Used — launcher activity |
| `GrammarFixService` | GrammarFixService.kt | Used — accessibility service |
| `ApiKeyRepository` | ApiKeyRepository.kt | Used — secure key storage |
| `AiClient` | AiClient.kt | Used — Gemini API client (singleton) |
| `safeRecycle()` | GrammarFixService.kt | Used — 6 call sites |
| `shortcut` / `shortcutRegex` | GrammarFixService.kt | Used — `?fix` detection |
| `serviceScope` | GrammarFixService.kt | Used — coroutine scope |
| `pendingJob` | GrammarFixService.kt | Used — cancel/reassign |
| `mainHandler` | GrammarFixService.kt | Used — toast posting |
| `replaceText()` | GrammarFixService.kt | Used — 2 call sites |
| `showToast()` | GrammarFixService.kt | Used — 5 call sites |
| All view IDs in `activity_main.xml` | activity_main.xml | Used — via ViewBinding |
| `network_security_config` | network_security_config.xml | Used — manifest reference |
| `INTERNET` permission | AndroidManifest.xml | Used — OkHttp |
| `BIND_ACCESSIBILITY_SERVICE` | AndroidManifest.xml | Used — service declaration |

---

## Findings

All dead code findings (DC-01 through DC-09) identified during the audit were reviewed, confirmed, and removed. No remaining dead code, unreachable logic, or unused resources exist in the current codebase.

| ID | File | Issue | Disposition |
|---|---|---|---|
| DC-01 | GrammarFixService.kt | Unused `withContext` import | Removed |
| DC-02 | build.gradle.kts | Unused `preference-ktx` dependency | Removed |
| DC-03 | build.gradle.kts | Unused `constraintlayout` dependency | Removed |
| DC-04 | proguard-rules.pro | Orphaned `**$$serializer` keep rule | Removed |
| DC-05 | accessibility_service_config.xml | Redundant `flagDefault` attribute | Removed |
| DC-06 | themes.xml | Unused `tools` namespace | Deferred — low risk |
| DC-07 | AndroidManifest.xml | Redundant `roundIcon` | Deferred — convention |
| DC-08 | ApiKeyRepository.kt | Deprecated `MasterKeys` API | Deferred — needs refactor |
| DC-09 | GrammarFixService.kt | Redundant `withContext(Dispatchers.IO)` wrapper | Removed |

### Legacy removals (pre-audit)

- `FixMode` enum — retired in v2
- `shortcuts` map — replaced with single `shortcut` string and `regex`
- `getAllTextFromField()` — fully removed
- `isProcessing` flag — fully removed
- Ollama backend, provider enum, spinner UI, Ollama string resources — all removed when project became Gemini-only

---

## Items Deferred to Future Work

| ID | File | Issue | Status |
|---|---|---|---|
| DC-06 | themes.xml | `tools` namespace declared but unused | Resolved 2026-07 — themes.xml rewritten for the six-theme system |
| DC-07 | AndroidManifest.xml | `roundIcon` duplicates `icon` | Kept — convention for round icon support |
| DC-08 | ApiKeyRepository.kt | `MasterKeys.getOrCreate()` deprecated | Resolved 2026-07 — migrated to `MasterKey.Builder` on security-crypto 1.1.0 |

### 2026-07 follow-up sweep

Additional dead code removed in the July 2026 audit/redesign pass:

| File | Issue | Disposition |
|---|---|---|
| values/styles.xml | Unused `ShapeAppearanceOverlay.WordWise.Input` | Removed (file deleted) |
| values-night/* | Empty colors.xml, duplicate themes.xml | Removed — theming is now explicit per user choice |
| drawable/edit_text_background.xml | Unreferenced | Removed |
| drawable*/ic_launcher.png, ic_launcher_background.xml | Unreferenced (manifest uses @mipmap) | Removed |
| activity_main.xml | Used ConstraintLayout although DC-03 removed the dependency (worked only transitively) | Rewritten as LinearLayout |
| strings.xml | ~25 orphaned strings, two with broken `%1` format placeholders | Pruned/fixed |

---

## Removal Execution Log

**Executed by:** Jules
**Execution date:** 2026-06-07
**Build result:** BUILD SUCCESSFUL

| ID | Removal | Status |
|---|---|---|
| DC-05 | `flagDefault` attribute | Removed — confirmed redundant as implicit default |
| DC-09 | Redundant `withContext` wrapper | Removed — AiClient handles its own dispatcher switch |
| DC-01 | `withContext` import | Removed — cleaned up after DC-09 |
| DC-02 | `preference-ktx` dependency | Removed — confirmed unused |
| DC-03 | `constraintlayout` dependency | Removed — no ConstraintLayout in layouts |
| DC-04 | `$$serializer` ProGuard rule | Removed — tree-based JSON parsing needs no keep rule |
| Ollama removal | Provider enum, Ollama backend, spinner UI, Ollama strings | Removed — single-provider Gemini hardcode |

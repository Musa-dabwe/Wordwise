# WordWise — Dead Code Audit

**Audit date:** 2026-06-07
**Auditor:** OpenCode + Context7
**Scope:** Static analysis only — no runtime profiling. Android Lint could not be executed (no Java runtime available in environment).
**Codebase:** 12 files inspected, ~973 lines total (4 Kotlin source, 4 resource/config, 3 build, 1 theme)

---

## Research Notes

### 1. Kotlin Dead Code Detection Patterns
Context7 source: /websites/kotlinlang
Kotlin 2.1.0+ provides extra compiler checks (`-Wextra`) for: `UNUSED_VARIABLE`, `UNREACHABLE_CODE`, `REDUNDANT_NULLABLE`, `USELESS_CALL_ON_NOT_NULL`, `UNUSED_ANONYMOUS_PARAMETER`, `CAN_BE_VAL`, `ASSIGNED_VALUE_IS_NEVER_READ`, and `REDUNDANT_SINGLE_EXPRESSION_STRING_TEMPLATE`. Kotlin 2.3.0 introduces an unused return value checker for non-Unit/non-Nothing expressions. Key takeaway: manual inspection catches what compiler checks would flag — redundant dispatcher switching, unnecessary null-safe calls on non-null receivers, and unused imports.

### 2. Android AccessibilityService Lifecycle
Context7 source: /websites/developer_android
`AccessibilityService` requires exactly two method overrides: `onAccessibilityEvent` and `onInterrupt`. `onServiceConnected` is optional for initialization. `onDestroy` is inherited from `Service` and is called by the framework. XML config flags like `android:accessibilityFlags` and `android:notificationTimeout` are relevant: `flagDefault` is the implicit default, making its explicit declaration redundant. `notificationTimeout` controls event coalescing — still meaningful for `typeViewTextChanged` at 100ms.

### 3. OkHttp Best Practices
Context7 source: /square/okhttp
`OkHttpClient` should be a shared singleton — already done via `AiClient.httpClient`. The `newBuilder()` pattern allows per-call variants sharing the same connection pool. Default connection pool holds 5 idle connections (5 min TTL). Timeouts (connect/read/write) are all configured at 30s, which is reasonable. No unused builder options detected.

### 4. kotlinx.serialization
Context7 source: /kotlin/kotlinx.serialization
The `Json { ... }` DSL allows configuration (lenient parsing, ignoreUnknownKeys, etc.). The codebase uses `Json.parseToJsonElement()` with default configuration — no custom `Json` instance, so no redundant config. No `@Serializable` annotations exist anywhere; the library is used only for its JSON builder DSL and tree-based parsing. This means ProGuard `**$$serializer` rules are orphaned.

### 5. Android ProGuard/R8
Context7 source: /websites/developer_android
R8 handles dead code removal when `isMinifyEnabled = true`. ProGuard rules in `proguard-rules.pro` should match only what's needed. Overly broad `-keep` rules (like `-keep class okhttp3.** { *; }`) prevent R8 from shrinking those classes. Orphaned rules (like `**$$serializer` with no `@Serializable` classes) are harmless but add noise. `-dontwarn` directives suppress legitimate warnings and should be scoped.

### 6. EncryptedSharedPreferences Patterns
Context7 source: /git_android_googlesource_com/platform_frameworks_support
The `MasterKeys` class (used via `MasterKeys.getOrCreate()`) is **deprecated** in favor of `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`. The current code compiles and works but uses a deprecated API path. The `EncryptedSharedPreferences.create()` signature with `prefFileName` as first parameter is also deprecated; the modern overload takes `context` first. No unused preference keys — only `KEY_API_KEY` is used.

### 7. Kotlin Coroutines
Context7 source: /kotlin/kotlinx.coroutines
`SupervisorJob` prevents child failure from cancelling siblings — correctly used in `GrammarFixService.serviceScope`. `Job.cancel()` is used to cancel pending operations. `withContext(Dispatchers.IO)` switches dispatcher for blocking work. Key finding: `AiClient.fixGrammar()` already wraps its body in `withContext(Dispatchers.IO)`, so the outer `withContext(Dispatchers.IO)` in `GrammarFixService` is **redundant** — the inner call is a no-op (already on IO dispatcher).

---

## Component Inventory

| Component | File | Usage Status |
|---|---|---|
| `MainActivity` | MainActivity.kt | Used — launcher activity |
| `GrammarFixService` | GrammarFixService.kt | Used — accessibility service |
| `ApiKeyRepository` | ApiKeyRepository.kt | Used — key storage |
| `AiClient` | AiClient.kt | Used — API client |
| `FixMode` enum | AiClient.kt | Used — 3 entries all have call sites |
| `safeRecycle()` extension | GrammarFixService.kt | Used — 6 call sites |
| `shortcuts` map | GrammarFixService.kt | Used — checked in event handler |
| `serviceScope` | GrammarFixService.kt | Used — launches coroutines |
| `pendingJob` | GrammarFixService.kt | Used — cancel/reassign |
| `mainHandler` | GrammarFixService.kt | Used — `showToast` |
| `replaceText()` | GrammarFixService.kt | Used — 2 call sites |
| `showToast()` | GrammarFixService.kt | Used — 5 call sites |
| `apiKeyEditText` (view) | activity_main.xml | Used — MainActivity.kt |
| `saveApiKeyButton` (view) | activity_main.xml | Used — MainActivity.kt |
| `openAccessibilitySettingsButton` (view) | activity_main.xml | Used — MainActivity.kt |
| `serviceStatusTextView` (view) | activity_main.xml | Used — MainActivity.kt |
| `app_name` (string) | strings.xml | Used — AndroidManifest |
| `accessibility_service_description` (string) | strings.xml | Used — service config XML |
| `network_security_config` | network_security_config.xml | Used — manifest reference |
| `INTERNET` permission | AndroidManifest.xml | Used — OkHttp in AiClient |
| `BIND_ACCESSIBILITY_SERVICE` | AndroidManifest.xml | Used — service declaration |
| `edit_text_background` (drawable) | drawable/edit_text_background.xml | Used — activity_main.xml |
| `ic_launcher` (mipmap) | mipmap-*/ | Used — manifest |
| `AppTheme` style | themes.xml | Used — manifest |

---

## Findings

### Category A — Unused Kotlin Declarations

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| DC-01 | GrammarFixService.kt | `kotlinx.coroutines.withContext` (line 19) | High | Yes (after DC-09) | Import is only used on line 133; if the redundant `withContext` call is removed (DC-09), this import becomes unused. |

### Category B — Unreachable / Redundant Logic

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| _No findings_ | | | | | `getAllTextFromField()` confirmed fully removed. `isProcessing` confirmed fully removed. No OpenAI remnants found. All conditional branches are reachable. |

### Category C — Unused Resources

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| _No findings_ | | | | | All 2 string resources are referenced. All 4 layout view IDs are used via ViewBinding. Drawables are referenced. |

### Category D — Unused Dependencies and Build Config

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| DC-02 | build.gradle.kts | `androidx.preference:preference-ktx` (line 53) | High | Yes | No import or reference to any `androidx.preference.*` class exists in any source file. |
| DC-03 | build.gradle.kts | `androidx.constraintlayout:constraintlayout` (line 58) | High | Yes | No `ConstraintLayout` usage in any layout XML or Kotlin file. Layouts use `ScrollView` + `LinearLayout`. |
| DC-04 | proguard-rules.pro | `**$$serializer` keep rule (lines 18-20) | High | Yes | No `@Serializable` classes exist in the codebase. The kotlinx.serialization usage is limited to `buildJsonObject` DSL and `Json.parseToJsonElement()`, which do not generate serializer companions. |

### Category E — Manifest and Service Config

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| DC-05 | accessibility_service_config.xml | `android:accessibilityFlags="flagDefault"` (line 6) | High | Yes | `flagDefault` is the implicit default value. Removing it has zero behavioral change. |
| DC-06 | themes.xml | `xmlns:tools="http://schemas.android.com/tools"` (line 1) | Medium | No | The `tools` namespace is declared but no `tools:` attribute is used in the file. However, this is a common template artifact; removing it may cause IDE warnings in some tooling versions. |
| DC-07 | AndroidManifest.xml | `android:roundIcon="@mipmap/ic_launcher"` (line 11) | Low | No | `ic_launcher_round` resources exist in all mipmap densities. While functionally correct, the icon is duplicated with `android:icon`. Harmless convention. |

### Category F — Obsolete Compatibility Code

| ID | File | Symbol/Line | Confidence | Safe to Remove | Reason |
|---|---|---|---|---|---|
| DC-08 | ApiKeyRepository.kt | `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` (line 9) | High | No — needs replacement | `MasterKeys` class is deprecated. Modern equivalent: `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`. Not dead code — just uses a deprecated API path. |
| DC-09 | GrammarFixService.kt | `withContext(Dispatchers.IO)` wrapper (line 133) | High | Yes | `AiClient.fixGrammar()` already wraps its body in `withContext(Dispatchers.IO)`. The outer dispatcher switch in GrammarFixService is redundant — the inner call becomes a no-op. Removing it eliminates an unnecessary thread hop. |

---

## Final Check Answers

1. **Was `getAllTextFromField()` fully removed?** Yes — no references found anywhere in the codebase.
2. **Was `isProcessing` fully removed?** Yes — no references found anywhere in the codebase.
3. **Any remaining references to OpenAI, `?fixg`, or `gemini-1.5-flash`?** No — none found. Current model: `gemini-2.5-flash-lite`. Shortcuts: `?fixs`, `?fixp`, `?fixo`.
4. **Does `FixMode.ALL` have an active call site in `GrammarFixService.kt`?** Yes — in the `shortcuts` map (line 32: `"?fixo" to FixMode.ALL`), the `when` label (line 115), and passed to `AiClient.fixGrammar()` (line 134).
5. **Are all four view IDs in `activity_main.xml` referenced in `MainActivity.kt`?** Yes — all 4 IDs (`apiKeyEditText`, `saveApiKeyButton`, `openAccessibilitySettingsButton`, `serviceStatusTextView`) are accessed via ViewBinding.

---

## Summary

| Category | Total Findings | High Confidence | Safe to Remove |
|---|---|---|---|
| A — Unused Kotlin Declarations | 1 | 1 | 1 (conditional) |
| B — Unreachable / Redundant Logic | 0 | 0 | 0 |
| C — Unused Resources | 0 | 0 | 0 |
| D — Unused Dependencies & Build Config | 3 | 3 | 3 |
| E — Manifest & Service Config | 3 | 1 | 1 |
| F — Obsolete Compatibility Code | 2 | 1 | 1 |
| **Total** | **9** | **6** | **6 (5 unconditional + 1 conditional)** |

---

## Recommended Removal Order

1. **DC-05** — Remove `android:accessibilityFlags="flagDefault"` from accessibility_service_config.xml (zero risk)
2. **DC-09** — Remove redundant `withContext(Dispatchers.IO)` wrapper in GrammarFixService.kt:133
3. **DC-01** — Remove now-unused `import kotlinx.coroutines.withContext` from GrammarFixService.kt (after DC-09)
4. **DC-02** — Remove `androidx.preference:preference-ktx` dependency from build.gradle.kts
5. **DC-03** — Remove `androidx.constraintlayout:constraintlayout` dependency from build.gradle.kts
6. **DC-04** — Remove orphaned `**$$serializer` ProGuard rule from proguard-rules.pro:18-20

---

## Items Requiring Further Investigation

| ID | Reason for Caution |
|---|---|
| DC-06 | `tools` namespace in themes.xml is unused in content but may serve tooling purposes. Safe to remove for cleanup but may cause IDE lint noise. |
| DC-07 | `android:roundIcon` is a convention — not dead, but redundant with `android:icon`. Keep as future-proofing for round icon support. |
| DC-08 | Deprecated `MasterKeys` API needs replacement (not removal). Recommend migrating to `MasterKey.Builder` but this is a refactor, not a dead code removal. |

---

## Notes for Jules

### Safe removals (execute in this order):

1. **DC-05** — `accessibility_service_config.xml` line 6: delete `android:accessibilityFlags="flagDefault"` from the `<accessibility-service>` element.

2. **DC-09** — `GrammarFixService.kt` line 133: change:
   ```kotlin
   val correctedText = withContext(Dispatchers.IO) {
       AiClient.fixGrammar(textToFix, apiKeyRepository.getApiKey(), mode)
   }
   ```
   to:
   ```kotlin
   val correctedText = AiClient.fixGrammar(textToFix, apiKeyRepository.getApiKey(), mode)
   ```

3. **DC-01** — `GrammarFixService.kt` line 19: delete `import kotlinx.coroutines.withContext` (only after DC-09 is applied, as it removes the last usage site).

4. **DC-02** — `build.gradle.kts` line 53: delete the line `implementation("androidx.preference:preference-ktx:1.2.1")`.

5. **DC-03** — `build.gradle.kts` line 58: delete the line `implementation("androidx.constraintlayout:constraintlayout:2.2.1")`.

6. **DC-04** — `proguard-rules.pro` lines 18-20: delete the block:
   ```
   -keepclasseswithmembers class **$$serializer {
       kotlinx.serialization.descriptors.SerialDescriptor descriptor;
   }
   ```

### Cross-file dependency warnings:
- DC-01 is **dependent** on DC-09 — remove the redundant `withContext` call first, then remove the import. If done in wrong order, the file won't compile.
- No other cross-file dependencies among the removals.

### Items explicitly **not** removed:
- `MasterKeys.getOrCreate()` (DC-08) — deprecated but functional. Needs replacement with `MasterKey.Builder`, which is a refactor beyond dead code scope.
- `tools` namespace in themes.xml (DC-06) — low risk but may trigger IDE warnings.
- Override functions in `GrammarFixService` — all required by AccessibilityService lifecycle.
- All string resources — both are referenced.
- All view IDs — all used via ViewBinding.

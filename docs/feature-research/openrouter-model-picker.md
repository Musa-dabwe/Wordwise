# Research: OpenRouter Model Picker

## Search Terms Used

- `openrouter|OpenRouter|OPENROUTER`
- `AiClient.MODEL`
- `Prefs.getTheme` / SharedPreferences keys
- `/api/` Ktor routes, `hx-post`, `hx-get`

## Existing Code Found

- `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`: hardcodes
  `const val MODEL = "openrouter/free"`. Both `fixGrammar()` and `ask()` write
  `put("model", MODEL)` into the payload. The model is **not** a parameter, so
  there is currently no way to change it without recompiling.
- `app/src/main/kotlin/com/musa/wordwise/data/Prefs.kt`: plain `SharedPreferences`
  (`wordwise_prefs`) holding `selected_theme` only. Documented as the home for
  non-secret settings.
- `app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt`:
  `EncryptedSharedPreferences` (`secret_keys`). Holds `api_key_openrouter`.
- `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`: calls
  `AiClient.fixGrammar(textForAi, apiKey)` / `AiClient.ask(textForAi, apiKey)`.
  The only place that would need to inject a user-chosen model.
- `app/src/main/kotlin/com/musa/wordwise/server/Views.kt`: settings screen renders
  a static badge — `AI MODEL` / `Openrouter - Free Models Router`.
- `app/src/main/kotlin/com/musa/wordwise/server/WwServer.kt`: Ktor routes.
  Existing precedents: `POST /api/key` (form params + `HX-Trigger` toast),
  `POST /api/settings/theme` (validated against `Themes.KEYS`).
- `app/src/main/kotlin/com/musa/wordwise/server/Shell.kt`: design-system CSS
  (`.ww-drop` / `.ww-pop` / `.ww-row` dropdown, `.key-wrap` input,
  `.ww-save` button) and client JS (`wwToggleDrop`, `wwCloseDrops`, `wwSetTheme`,
  `wwToast`). Line 211 already special-cases `model-card` in
  `htmx:afterSwap`, so a model card is an anticipated swap target.
- `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt`: asserts
  `AiClient.MODEL == "openrouter/free"` and unit-tests the `internal fun
  parseContent` helper. The `internal` + plain-JUnit pattern for testing pure
  parsing without Android is established.

## Similar Patterns

- **Dropdown picker** — `Views.themePicker(context)` + `Shell.wwToggleDrop` /
  `wwSetTheme`. Directly reusable for the model list: same `.ww-drop` markup,
  same `data-k`-equivalent `data-id` attribute, same checkmark-visibility logic.
- **Save-with-toast form** — the API KEY form posts to `/api/key` and the server
  answers with `HX-Trigger: {"ww-toast":...,"ww-saved":true}`; `Shell` listens
  for `ww-saved` and animates the button. The model form should mirror this.
- **Server-side validation before persisting** — `post("/api/settings/theme")`
  checks `if (name in Themes.KEYS)` and silently no-ops otherwise. Model IDs get
  an explicit reject-with-toast instead, because the user needs to know why.
- **Pure, unit-testable parsing helper** — `AiClient.parseContent` is `internal`
  so JVM tests can call it with a JSON string. The catalog parser follows the
  same shape.

## New Implementation Required

Functionality that does not exist:

1. **A user-configurable model.** `AiClient.MODEL` is a compile-time constant.
   Both call sites must accept a `model` parameter, defaulting to the free
   router.
2. **Non-secret persistence for the model.** `Prefs` already exists for exactly
   this class of setting and is deliberately *not* encrypted — a model ID is
   public information and must never sit next to the key inside the encrypted
   store.
3. **Format validation.** Needs to be a pure Kotlin object with no Android
   imports so it is unit-testable on the JVM.
4. **Catalog endpoint.** `GET https://openrouter.ai/api/v1/models` returns the
   full model list; nothing in the app consumes it yet.
5. **Picker UI.** `Views.homeScreen` has no interactive model control, and
   `Shell` has no search-input CSS or model JS.

Adaptation needed:

- `GrammarFixService` reads the stored model and passes it on.
- `Views` gains a model card; `Shell` gains the popup search CSS/JS.
- `WwServer` gains a save route and a catalog route.

## OpenRouter Catalog Facts (verified against the live API)

`GET https://openrouter.ai/api/v1/models` → `{"data": [...], "total_count": N, "links": ...}`

| Fact | Value | Consequence for the design |
| --- | --- | --- |
| Total models | 464 | Too many for a bare dropdown, and 762 KB of raw JSON |
| `openrouter/free` present | yes | The default stays selectable in the picker |
| Slashes per id | exactly 1, for all 464 | `vendor/model` split is a safe validation rule |
| Chars used in ids | `- . / 0-9 : a-z ~` | Charset regex must include `~` |
| `~`-prefixed ids | 18 | OpenRouter internal "latest version" aliases. Must stay valid or the user cannot paste `~openai/gpt-astra-latest` |
| Max id length | 56 | Cap at 120 for headroom |
| `text->text` modality | 156 models | Grammar correction needs text in, text out. Filter to this |
| Compact payload for those 156 (`id`/`name`/`context_length`/free flag) | ~15 KB | Small enough to serve to the WebView without paging |

Free-text models are rare (only **9** of the 156 text-to-text models cost
nothing), so the picker must not assume free models exist — the current
`openrouter/free` default is doing real work.

## Implementation Plan

1. **`network/ModelId.kt`** (new) — pure object holding `DEFAULT` plus
   `validate()`/`resolve()`. Blank input means "reset to the free router".
   Rules: trim, ≤120 chars, no whitespace, exactly one `/`, both segments
   non-empty, charset `[A-Za-z0-9._:~-]`. No Android imports → JVM-testable.
2. **`network/ModelCatalog.kt`** (new) — `ModelInfo` data class plus a
   `fetch(apiKey)` suspend call and an `internal fun parse(json)` for tests.
   Filters to text-output models and projects to the four display fields.
3. **`data/Prefs.kt`** — add `getModel`/`setModel` on `wordwise_prefs`.
4. **`network/AiClient.kt`** — replace `MODEL` with `DEFAULT_MODEL`, thread a
   `model` parameter through `fixGrammar`/`ask`.
5. **`GrammarFixService.kt`** — resolve the stored model and pass it in.
6. **`server/Views.kt`** — replace the static badge with a model card:
   dropdown + search box + paste field + save button.
7. **`server/Shell.kt`** — popup search CSS and `wwPickModel` / `wwSaveModel` JS.
8. **`server/WwServer.kt`** — `POST /api/settings/model` (validate + persist,
   toast on reject) and `GET /api/models` (cached catalog proxy).
9. **Tests** — `ModelIdTest`, `ModelCatalogTest`, update `AiClientTest`.
10. **Docs** — session log, `docs/BUILD.md`, `docs/ARCHITECTURE.md`, README.

## Decisions Confirmed With User

- Picker = paste field **plus** a catalog dropdown; the pasted value wins.
- No model set → fall back to `openrouter/free`, so existing installs are
  unaffected by the upgrade.
- Light format check only. OpenRouter's own error remains the final authority on
  whether a well-formed model ID actually exists.
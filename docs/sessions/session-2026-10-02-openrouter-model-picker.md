# Session: 2026-10-02 OpenRouter model picker

**Duration**: 2026-10-02 14:50 - 17:10
**Project**: WordWise

## Objective

Let the user choose which OpenRouter model WordWise uses, by pasting a model path
(`vendor/model`) or picking from the fetched catalog, replacing the hardcoded
`openrouter/free` router. Secondary: extend the `?ask` system prompt with the
no-em-dash and concision rules that `?fix` already had.

## Research Phase

Research doc: [feature-research/openrouter-model-picker.md](../feature-research/openrouter-model-picker.md)

Verified against the live `GET /api/v1/models` response:

| Fact | Value |
| --- | --- |
| Total models | 464 |
| `openrouter/free` present in catalog | yes |
| Slashes per ID | exactly 1, for all 464 |
| Characters used in IDs | `- . / 0-9 : a-z ~` |
| `~`-prefixed "latest version" aliases | 18 |
| Longest ID | 56 chars |
| `text->text` output models | 156 |

Findings that shaped the design:

- The `~` prefix is OpenRouter's internal alias syntax, so the validation charset
  had to include it or `~openai/gpt-astra-latest` would be rejected.
- Only 9 of the 156 text-to-text models are free, so the picker cannot assume free
  models exist. The `openrouter/free` default is doing real work.
- The raw catalog is 762 KB; projecting to four display fields cuts it to ~15 KB,
  which is small enough to ship straight to the WebView.
- `Shell.kt` already special-cased a `model-card` htmx swap target that nothing
  produced, so a model card was an anticipated shape.

## Implementation Steps

1. **`network/ModelId.kt`** (new): pure `vendor/model` format validator with
   `DEFAULT`, `validate`, `resolve`. No Android imports so it is JVM-testable.
   Blank input is Valid and means "reset to the free router".
2. **`network/ModelCatalog.kt`** (new): `ModelInfo` projection plus `fetch` and an
   `internal fun parse` for tests. Filters to text-output models, caps name
   length and response body size.
3. **`data/Prefs.kt`**: added `getModel`/`setModel` on the plain (non-encrypted)
   `wordwise_prefs`. The model is public information and never goes near the key.
4. **`network/AiClient.kt`**: `MODEL` became `DEFAULT_MODEL`; `fixGrammar`/`ask`
   take a `model` parameter and resolve it. Extracted `fixPayload`/`askPayload` so
   tests can prove the chosen model reaches the request.
5. **`GrammarFixService.kt`**: resolves `Prefs.getModel()` per request so a settings
   change takes effect without restarting the service.
6. **`server/Views.kt`**: replaced the static model badge with a searchable
   dropdown plus a paste field and one SAVE MODEL submit.
7. **`server/Shell.kt`**: popup search CSS, scroll cap on the row list, and the
   client-side model JS.
8. **`server/WwServer.kt`**: `POST /api/settings/model` (validate + persist + toast
   on reject) and `GET /api/models` behind a single-flight, key-scoped cache.

## Bugs Discovered & Fixed

- **Dropdown was unusable**: `.ww-pop` had no height cap, so a 60-row list
  overflowed the viewport while `#ww-backdrop` (`position:fixed`, z-index 40)
  swallowed every tap below the fold. Rows were visible but untappable. Fixed with
  `#model-rows { max-height:min(52vh,420px); overflow-y:auto; }`.
- **`/api/models` error path was incoherent**: the route answered 204 +
  `HX-Trigger`, but it is consumed by `fetch()`, not htmx, so the trigger header
  was discarded and `r.json()` happened to throw on the empty body. Now 503 with
  a `[]` body, so `!r.ok` is the explicit failure the client checks for.
- **Cache stuck the list on re-swap**: `wwLoadModels` returned early on a warm
  client cache, but an htmx swap of `#main-container` replaces `#model-rows` with
  fresh "Loading..." markup. Now re-renders whenever the cache is warm and only
  skips the network call.
- **Inconsistent JS comparator**: `wwSortModels` never returned 0, so
  `cmp(a,b) != -cmp(b,a)`. Ties are common because the parser falls back to the ID
  when a model has no name. Verified against the real 464-row catalog: 0
  violations after the fix.
- **Blank catalog ID became a selectable row**: `validate("")` is Valid (it means
  "reset to default" for user input) which made it useless as a filter. The parser
  now also requires the validated ID to equal the ID it was given.
- **Dead branch removed**: `htmx:afterSwap` had a `model-card` case nothing
  triggered.

## Testing Performed

- **Unit Tests**: 78 JVM tests, 0 failures (was 67 before the payload and
  hardening tests were added).
  - `ModelIdTest` 16, `ModelCatalogTest` 23, `AiClientTest` 19,
    `GrammarFixServiceTest` 20.
- **Build**: `assembleDebug` and `assembleDebugAndroidTest` both clean.
- **Static**: extracted the generated `<script>` block and ran `node --check`
  (syntax OK); ran the comparator over the real catalog to prove consistency.
- **Manual Testing**: user-tested the picker against `stealth/space-bunny-alpha`
  on device and confirmed it works before this release.

Not done: no browser automation of the WebView UI (no Chromium available on this
machine), and no instrumented run on a cloud device for this change.

## AI Models Used & Their Role

- **Space Bunny Free** (OpenCode, this session): implementation, test authoring,
  research doc. Also drove the two independent review passes below.
  - Effectiveness: high. The review passes caught a genuinely blocking UI defect
    (the untappable dropdown) that unit tests could never have surfaced.

## Key Decisions Made

- **Model is not a secret.** It goes in plain `SharedPreferences`, keeping
  `EncryptedSharedPreferences` holding exactly one thing.
- **Format check only, no existence check.** `ModelId` rejects malformed input but
  never claims a well-formed ID is real. OpenRouter already returns an explicit
  error, and rejecting unknown-but-valid IDs would be worse than passing them
  through. `docs/feature-research/openrouter-model-picker.md` records this.
- **Invalid input is rejected, not silently downgraded.** `Prefs.setModel` takes a
  `ModelId.Result.Valid`, so an invalid value is unrepresentable rather than
  quietly becoming the free router while the save looked successful.
- **One submit for the picker.** Tapping a row only fills the field; SAVE MODEL
  persists. One code path for the user's intent, so a half-finished edit is never
  applied.
- **Rows built with `createElement`/`textContent`, never `innerHTML`.** Model names
  are third-party data; textContent makes injection structurally impossible rather
  than relying on escaping being correct.

## Issues & Blockers

Pre-existing security debt surfaced by the review passes and **not** addressed in
this change, because each needs its own decision and refactor:

1. **The OpenRouter API key is served in plaintext HTML** by `GET /screens/home`
   over unauthenticated loopback. Any co-resident app can read it. Pre-existing,
   but the review notes it is the most serious issue in the repo. Fix is to stop
   putting the key in HTML and move sensitive routes behind the existing
   `WwNative` WebView bridge.
2. **No CSRF/Origin protection on any mutating route.** A web page the user visits
   can submit a no-preflight form POST to `/api/settings/model` and silently switch
   to an expensive model, causing unconfirmed spend. `POST /api/settings/model` is
   the first route where that has a financial consequence. Fix is one interceptor:
   require an `X-Requested-With` header, check `Origin`, add CSP and
   `X-Frame-Options`.
3. **WebView disk cache can retain the page holding the key.** `/assets/*` is
   intercepted but the HTML routes are not.
4. **Missing official kotlinx.serialization ProGuard rules.** Harmless today (no
   `@Serializable` class exists), but it would fail only in release, at runtime,
   the first time one is added.

The `?ask` prompt change is logically separate from the picker and was committed
separately for revertability.

## Performance Metrics

- Unit test suite: ~11s warm, ~86s cold.
- Catalog payload: 762 KB upstream -> ~15 KB to the WebView (156 rows).
- JVM test count: 67 -> 78.

## Next Session Priorities

- [ ] Decide on the loopback API key exposure (issue 1) - highest severity.
- [ ] Add the CSRF/Origin/CSP interceptor (issue 2).
- [ ] Show model pricing in the picker, since the parser already reads
      `pricing` and currently discards everything but a boolean.
- [ ] Add the official kotlinx.serialization ProGuard rules.

## Related Sessions

- [session-2026-09-18 OpenRouter migration plan](../superpowers/plans/2026-09-16-openrouter-migration.md)
- Continues the provider set-up in `docs/ARCHITECTURE.md`.
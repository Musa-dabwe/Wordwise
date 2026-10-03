# Session: 2026-10-03 Security hardening before re-release

**Duration**: 2026-10-03 18:00 - 22:30
**Project**: WordWise

## Objective

Fix the vulnerabilities flagged during the model-picker review and re-cut the
release. The previous release (`v1.0.0`) was published with all three open.

## Research Phase

No new research doc. This work was driven by the two review passes on PR #37,
recorded in [session-2026-10-02-openrouter-model-picker.md](session-2026-10-02-openrouter-model-picker.md)
under "Issues & Blockers".

The governing insight: Android's loopback is a **single shared namespace**. Any
app holding `INTERNET` can request `127.0.0.1:8977` directly, with no permission
and no prompt. That makes a per-process token useless as a defence — the token
lives in the page, and whoever fetches the page can read the token. So the fix
cannot be authentication; it has to be removing what there is to steal and what
there is to mutate.

## Implementation Steps

1. **`MainActivity.kt`**: added `WwNativeBridge` as the sole secret read and
   settings write path. `hasApiKey()` returns a boolean; `saveApiKey()` is
   write-only. Set `LOAD_NO_CACHE`, removed the bridge in `onDestroy`, and made
   `shouldInterceptRequest` refuse any non-loopback subresource.
2. **`WwServer.kt`**: removed all four mutating routes and every secret from
   responses. Added a `WwLocalGuard` plugin: foreign `Origin` gets 403, plus
   no-store, CSP, `X-Frame-Options`, `Referrer-Policy`, nosniff.
3. **`Views.kt` / `Shell.kt`**: replaced htmx POSTs with bridge calls. The key
   field is now write-only, with a note reporting that a key exists.
4. **`ModelCatalog.kt`**: catalog fetch is now unauthenticated, so the response
   is not account-specific and the cache cannot leak entitlements between users.
5. **`Html.kt`**: `jsonStr` now escapes `<`, `>`, `&`.
6. **`proguard-rules.pro`**: explicit `@JavascriptInterface` keep plus the
   official kotlinx.serialization rule set.

## Bugs Discovered & Fixed

Found by the two review passes, not by any test:

- **The key button reverted to the wrong label.** `wwSavedFeedback` restored a
  captured `SAVE API KEY` string 1.7s after a successful save, while the live
  state said `REPLACE API KEY`. The note and the button contradicted each other
  until the next screen swap. The reset label is now derived from live state.
- **`/assets` emitted two `Cache-Control` headers.** Ktor's `header()` appends,
  so the guard's `no-store` and the route's `max-age=86400` both went on the
  wire and `no-store` won. The fonts and htmx runtime were never cached, which
  the code comment claimed was the intent. The guard now skips that path.
- **`jsonStr` could not close a script block.** A preference containing
  `</script>` would terminate the inline `<script>`. Unreachable through the UI
  today, but preferences are editable via backup restore or `adb`.

## Testing Performed

- **Unit Tests**: 87 JVM tests, 0 failures (was 78; added `HtmlTest` with 9).
- **Build**: `compileDebugKotlin`, `assembleDebugAndroidTest`, `assembleRelease`
  all clean, with R8 minification and signing.
- **Minified-APK verification**: dumped the release DEX and confirmed all seven
  bridge method names, the `"WwNative"` interface name and
  `android/webkit/JavascriptInterface` survive R8. The inner class itself is
  renamed to `i0.k`, which is harmless — the bridge is resolved by interface
  name, not class name — but a stripped *annotation* would break it silently, so
  it was checked explicitly.
- **Frontend**: extracted the generated `<script>` and ran `node --check`;
  cross-checked that every `onclick`/`oninput` handler in `Views.kt` resolves to
  a function defined in `Shell.kt`, and that no dead route references remain.
- **Reviewed by**: `opencode/fledge-alpha-free`, as both a security reviewer and
  a code reviewer, in two independent passes.

Not done: no on-device smoke test. There is no emulator on this build machine,
so the release should be launched once on a real device before it is relied on.
The Ktor `-keep` was retained precisely so that this unverified path stays safe.

## AI Models Used & Their Role

- **Space Bunny Free** (this session): design, implementation, verification.
- **`opencode/fledge-alpha-free`** (two independent subagent passes):
  - Security reviewer: confirmed each claim of the fix actually held — key not
    obtainable via any of the five channels (co-resident app, web page, disk
    cache, backup, logcat), and no settings change forceable. Found the
    `jsonStr` gap and the subframe exposure.
  - Code reviewer: verified the Ktor plugin short-circuits *empirically* against
    the real ktor 2.3.13 jars rather than by inspection, and found the button
    label race and the duplicate `Cache-Control`. Called the release Block on
    the label bug.
- **`opencode/fledge-alpha-free`** (ProGuard pass): discovered from
  `app/build/outputs/mapping/release/configuration.txt` that kotlinx.serialization,
  Ktor and OkHttp all ship consumer rules that are already merged. This
  corrected two of my premises and, more usefully, identified that Ktor's
  consumer rules cover the *client* engines but not the server routing this app
  actually uses — which is why the blanket keep was retained.
- **`general`** (README pass): updated the README for the picker.

## Key Decisions Made

- **Delete the write surface rather than authenticate it.** A CSRF token on the
  local server would stop a browser but not a co-resident app, and the key would
  still be readable. Removing both makes the residual risk descriptive rather
  than load-bearing.
- **The key is write-only.** The settings screen reports that a key exists and
  cannot display it. Slight UX cost, no functional loss: the user re-pastes to
  replace.
- **Retained `-keep class io.ktor.**` against the agent's recommendation.** The
  agent was right that it is probably redundant with Ktor's consumer rules and
  wrong that removing it is safe here: those rules cover the client engines, and
  this app runs a server. With no device available to smoke-test a routing
  regression, obfuscation (a size win) is not worth an unverifiable risk in a
  security release.
- **Kept Ktor for display and the catalog only.** The catalog moved to an
  unauthenticated fetch so the shared cache cannot serve one account's
  entitlements to another.

## Issues & Blockers

- **No on-device verification.** Everything above is static analysis, unit
  tests and DEX inspection. The app should be launched once on a real device
  before it is depended on, specifically to confirm the embedded Ktor server
  still serves the UI after minification.
- **`setInterval(wwPoll, 2000)` still leaks** across activity pause. Pre-existing
  and harmless (the page is the whole activity), but it is not cleaned up.

## Performance Metrics

- JVM tests: 78 -> 87.
- Release APK: unchanged in size, as the retained keeps mean no new shrinking.
- `/assets` responses now actually cache, which they did not before.

## Next Session Priorities

- [ ] Launch the release build once on a real device and confirm the settings
      screen renders and the picker saves.
- [ ] Show model pricing in the picker. The parser reads `pricing` and discards
      everything but a boolean, so a user picking a paid model has no idea what
      it costs. This is the remaining gap behind "cannot silently spend money".
- [ ] Clear the status poller interval in `onDestroy`.

## Related Sessions

- [session-2026-10-02-openrouter-model-picker.md](session-2026-10-02-openrouter-model-picker.md)

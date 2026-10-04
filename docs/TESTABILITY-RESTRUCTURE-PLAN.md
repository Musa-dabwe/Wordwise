# Plan: Testability Restructure

**Status:** in progress
**Started:** 2026-10-04
**Motivation:** 7 of the 9 server security invariants are asserted by reading the
source text rather than by exercising behaviour. That is not a real test: it
breaks on cosmetic edits, passes on semantically broken code, and proves nothing
about what a request actually receives. The cause is structural — the Ktor routing
block and the guard plugin are inline lambdas inside `WwServer.start()`, which
also binds a real port behind a one-shot `started` flag, so nothing can drive them
in-process.

**Release state:** the published `v1.0.0` APK has been unpublished. The tag,
release page and download URL all return 404. No APK is publicly available while
this work is in progress.

---

## Acceptance criteria

1. Every security invariant is asserted against a **real HTTP request**, not by
   parsing source text.
2. `./gradlew testDebugUnitTest` runs the full suite in under 60 s with no
   network access required.
3. The release APK launches on a real device and the settings screen renders,
   the model picker saves, and the remove-key flow works.
4. A fresh security review finds **no high or critical finding**, and any
   medium finding is either fixed or explicitly accepted.
5. The app is only re-published once 1-4 hold.

---

## Tasks, in order

Each task is done and verified before the next begins.

### Task 1 — Extract a testable Ktor module (unblocks the 7 source-level invariants)

`WwServer.start()` currently contains the routing block and the `WwLocalGuard`
plugin inline, and binds a real socket. Extract them so `testApplication` can
drive the real code.

- Extract the guard plugin into a named, installable function.
- Extract the routing block into an `Application` extension that takes its
  dependencies (`Context`, `ApiKeyRepository`, catalog provider) as parameters
  rather than closing over `app`.
- `start()` becomes a thin wrapper that calls it.
- **Acceptance:** the 7 source-level assertions in `WwServerTest` are deleted and
  replaced by real requests asserting status codes, header values and header
  *multiplicity* (the `/assets` `Cache-Control` bug needs exactly-one-header
  semantics, which only a real response can prove).

### Task 2 — Extract the WebView bridge from `MainActivity`

`MainActivity.WwNativeBridge` is a 324-line activity whose bridge methods cannot
be tested without an activity and a WebView. All seven method names surviving R8
was verified by DEX inspection, but their *behaviour* is untested.

- Move the bridge's logic into a plain class with no Activity dependency
  (returning values instead of touching views).
- `MainActivity` keeps only the `runOnUiThread` hops and the `AlertDialog`.
- **Acceptance:** `hasApiKey`, `saveApiKey`, `clearApiKey`, `getModel`,
  `setModel`, `getTheme`, `setTheme` each have unit tests, including every
  validation-rejection path and the "returns empty string on success" contract.

### Task 3 — Instrumented test for the encrypted-store wipe path

Robolectric ships no AndroidKeyStore shadow, so `EncryptedSharedPreferences`
cannot be constructed on the JVM at all. The wipe-and-recover path — which runs
when the Keystore keyset is unreadable, e.g. after a backup restore — is the one
security behaviour with zero coverage on any platform.

- **Acceptance:** an instrumented test corrupts the stored ciphertext, asserts the
  repository wipes and recreates rather than throwing, and asserts the user is
  left with no key rather than a silently fabricated one.

### Task 4 — Extract the frontend JS from the Kotlin raw string

607 lines of JavaScript live inside a Kotlin raw string with `${...}`
interpolations. Consequences: the CSP needs `script-src 'unsafe-inline'`, and the
JS can only be tested by regex-extraction at runtime.

- Move the script and stylesheet into real files under `app/src/main/assets/web/`.
- Serve them from the existing `/assets` route.
- **Acceptance:** CSP drops `'unsafe-inline'` for scripts, `node --check` runs
  directly against the file with no extraction step, and the JS test suite loads
  it as a module.

### Task 5 — Close the remaining known gaps

From the session reviews, none of these is a vulnerability but all are known
incomplete behaviour:

- Model pricing is parsed and discarded, so a user cannot see what a paid model
  costs before selecting it. Show it.
- `setInterval(wwPoll, 2000)` is never cleared.
- `Prefs.getTheme` does not validate, unlike `getModel`. Either validate or
  document the asymmetry deliberately.

**Acceptance:** each is either fixed or explicitly documented as intentional, with
a test where applicable.

### Task 6 — Full verification and re-publish

- `testDebugUnitTest` green, no network needed.
- Instrumented suite green on the real device via `scripts/run-device-tests.sh`.
- `assembleRelease` clean; DEX-inspect the minified APK for the bridge methods and
  the removed routes.
- Fresh security review against the acceptance criteria.
- Re-cut the release only if all of the above hold.

---

## Sequencing notes

Tasks 1 and 2 are independent and both are production refactors, so each gets its
own commit and its own verification pass — a refactor that is only half-tested is
how the last three bugs shipped. Task 4 is the largest and riskiest (it changes how
the WebView loads its content), so it comes after the security-critical extractions
are proven.

No release is published between tasks. The APK stays unpublished until Task 6.
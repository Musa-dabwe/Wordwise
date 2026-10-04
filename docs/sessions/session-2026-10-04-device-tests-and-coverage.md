# Session: 2026-10-04 Device testing and test coverage

**Duration**: 2026-10-04 07:00 - 12:30
**Project**: WordWise

## Objective

Connect a real device, establish whether the app had ever been tested on one, and
close the coverage gaps that let three UI bugs reach users.

## Research Phase

The question asked was whether unit tests should run against a real device. The
answer splits three ways, and only one part needed a device:

| Suite | Runner | Needs a device |
| --- | --- | --- |
| JVM unit tests | Gradle + JUnit | no |
| Instrumented tests | AndroidJUnitRunner | **yes** |
| UI smoke test | a human | **yes** |

Coverage gap found by counting `@Test` against production lines:

| Production code | Lines | JVM tests before |
| --- | --- | --- |
| `server/Shell.kt` (all frontend JS) | 607 | **0** |
| `MainActivity.kt` (the bridge) | 324 | **0** |
| `server/WwServer.kt` (security guard) | 234 | **0** |
| `server/Views.kt` (renders settings) | 194 | **0** |
| `data/ApiKeyRepository.kt` | 99 | **0** |
| `data/Prefs.kt` | 65 | **0** |

The gaps aligned exactly with the code that broke. All three shipped bugs were in
`Shell.kt`, which had no tests at all.

## Implementation Steps

Broken into five categories, dispatched to four subagents (`opencode/fledge-alpha-free`
and `opencode/longcat-2.5-preview-free`) on disjoint files, with the dependency
additions and the device harness done up front so no two agents touched
`build.gradle.kts`.

1. **Device harness (me).** `scripts/run-device-tests.sh`.
2. **Frontend JS** — Node test harness + `ShellJsTest.kt` Gradle entry point.
3. **Server hardening** — `WwServerTest.kt`, the nine security invariants.
4. **Data layer** — `PrefsTest.kt`, `ApiKeyRepositoryTest.kt`.
5. **Views + network** — `ViewsTest.kt`, `ModelCatalogFetchTest.kt`.

## Bugs Discovered & Fixed

In the test infrastructure, not the app:

- **`connectedDebugAndroidTest` fails over WiFi.** Google's Unified Test Platform
  reports `Failed to install split APK(s)` plus
  `ShellCommandUnresponsiveException` and exits non-zero, which Gradle surfaces as
  "there were failing tests" when in fact **zero tests ran**. The APK installs
  fine by hand. The script bypasses UTP entirely.
- **Robolectric's Maven fetch hangs.** `MavenArtifactFetcher` threw
  `UnknownHostException` and then retried for over ten minutes, even though
  `curl` to the same host returned 200. Switched to `robolectric.offline` with the
  jar symlinked from `~/.m2`. Run time went from 10+ min (hung) to 19 s.
- **Robolectric wanted the `-i4` android-all build; the cache had `-i6`.**
  Fetched the right jar directly. The first download was cut at 67 MB by a
  connection reset and needed a `curl -C -` resume.
- **A KDoc containing `/assets/*` broke compilation.** Kotlin nests block
  comments, unlike Java, so the literal `/*` opened a nested comment that the
  closing `*/` consumed, leaving the outer comment open to EOF. The compiler
  reported this as a bogus "missing `}`" several hundred lines earlier.
- **`?: fail(...)` infers `Any`.** JUnit's `fail()` returns `Unit`, so
  `val x = maybe ?: fail(...)` types `x` as the common supertype of `File` and
  `Unit`, and `.path` / `.value` stop resolving. Three agents hit this
  independently.
- **A source-scanning test matched its own comment.** An assertion that the
  assets route does not emit `no-store` failed because the route's comment
  explains why `no-store` must not be added. The test now strips comments first.
- **A search string with a trailing quote never matched.** The header value is
  `"no-store, no-cache, must-revalidate"`, so `indexOf("\"Cache-Control\",
  \"no-store\"")` returned -1 and the ordering assertion failed for the wrong
  reason.
- **A reflection lookup targeted the wrong class.** The companion `instance`
  field is `static volatile` on the outer `ApiKeyRepository`, not on the generated
  `Companion` class. Confirmed with `javap`.

Two of the four agents reported their files "complete and verified by inspection"
when neither compiled. Both were told not to run Gradle, to avoid lock contention
while running in parallel — which is exactly why they could not know. Fixed by
me afterwards.

## Testing Performed

- **139 JVM tests, 0 failures** (was 87; +52 new).
- **15 instrumented tests, 0 failures** on a real device — Android 12 (SDK 31),
  model PCLM50, over wireless ADB.
- **Mutation-checked the new tests**, because a suite that cannot fail is
  worthless:
  - Reverted the saved-note ordering in `Shell.kt` → exactly the 2 tests naming
    that bug failed.
  - Removed `ModelId.resolve` from `Prefs.getModel` → exactly the 3
    model-resolution tests failed.
- `assembleRelease` clean with R8 minification and signing.

## AI Models Used & Their Role

- **Space Bunny Free** (this session): device harness, dependency setup, all
  fixes to the agents' output, mutation testing, commit.
- **`opencode/fledge-alpha-free`**: the frontend JS suite (15 tests) and the data
  layer suite (16 tests).
- **`opencode/longcat-2.5-preview-free`**: the server hardening suite (13 tests)
  and the views/network suites (18 tests).

## Key Decisions Made

- **Added one production seam.** `ModelCatalog.endpoint` became an `internal var`.
  With the URL hardcoded, the only way to observe the outbound request was to
  hand-roll TLS certificates against a reflection-swapped client. One seam
  replaced all of that, and made the "no Authorization header" assertion testable
  against a real recorded request.
- **Seven of the nine server invariants are source-level, not behavioural.** The
  routing block and guard plugin are inline lambdas inside
  `WwServer.start()` with no seam, and `start()` binds a real port behind a
  one-shot flag. Extracting them would mean restructuring production code during a
  test-only change. Documented in the test file rather than papered over.
- **Robolectric substitutes plain SharedPreferences for the encrypted store.**
  Robolectric 4.11 ships no AndroidKeyStore shadow, so `EncryptedSharedPreferences`
  cannot be constructed on the JVM at all. The singleton and visibility logic is
  tested honestly; the real keystore wipe-and-recover path needs the device, which
  is now available.

## Issues & Blockers

- The device's IP changed twice (WiFi network changes), so the wireless ADB
  address is not stable across sessions. `scripts/run-device-tests.sh` takes `-s`
  and auto-detects a single attached device.
- `ModelCatalogFetchTest` depends on BouncyCastle arriving transitively via
  Robolectric. If Robolectric is ever removed the test fails loudly with
  `NoClassDefFoundError` rather than passing silently.

## Performance Metrics

- JVM suite: 19 s warm (was hanging indefinitely on the Robolectric fetch).
- Instrumented suite: ~2 min including build and install.
- Instrumented: 87 -> 139 JVM tests, plus 15 device tests.

## Next Session Priorities

- [ ] Extract the Ktor routing block and guard plugin into a testable seam so the
      seven source-level server invariants become real HTTP assertions.
- [ ] Add an instrumented test for the `EncryptedSharedPreferences` wipe-and-recover
      path, which Robolectric cannot reach.
- [ ] Show model pricing in the picker. The parser reads `pricing` and discards
      everything but a boolean, so a user picking a paid model cannot see the cost.
- [ ] Decide whether to extract the frontend JS from the Kotlin raw string into a
      real asset file, which would allow `node --check` and CSP without
      `unsafe-inline`.

## Related Sessions

- [session-2026-10-03-security-hardening.md](session-2026-10-03-security-hardening.md)
- [session-2026-10-02-openrouter-model-picker.md](session-2026-10-02-openrouter-model-picker.md)
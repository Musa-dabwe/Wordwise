# Frontend Migration: Android Views → HTMX + Ktor (Poet design system)

WordWise's configuration UI has been migrated from XML layouts + Material 3
to the same architecture as PoetMusic: an **embedded Ktor server** bound to
`127.0.0.1` inside the app process, serving an **HTMX**-driven pastel web UI
into a fullscreen **WebView**. The grammar-fixing backend (accessibility
service, Gemini client, encrypted key storage) is unchanged.

## 1. Functionality map (everything the app does)

### Background service (unchanged)
| Feature | Where |
|---|---|
| Listen for `TYPE_VIEW_TEXT_CHANGED` across all apps | `GrammarFixService` |
| Detect `?fix` suffix, strip it, skip password fields | `GrammarFixService` |
| Inline Unicode spinner while the request is in flight | `GrammarFixService` |
| Call Gemini `generateContent` with strict correction prompt | `AiClient` |
| Replace field text via `ACTION_SET_TEXT`; toast results/errors | `GrammarFixService` |
| Rate-limit (429), auth, 404-model, 5xx error mapping | `AiClient` |
| Encrypted API key storage (AES-256-GCM / SIV, self-healing) | `ApiKeyRepository` |

### Frontend (migrated)
| Feature | Before | After |
|---|---|---|
| API key: prefill, save, empty-check, AI Studio link | `activity_main.xml` + ViewBinding | `/screens/home` card + `POST /api/key` |
| Accessibility status dot (live), Enable button | `onResume` check | 2s poller on `GET /api/status`; `POST /api/accessibility/open` fires a native intent |
| Model selector (4 free-tier Gemini models) | `AutoCompleteTextView` | Radio rows, `POST /api/settings/model` |
| Theme selection | 6 IDE themes (`setTheme` + `recreate()`) | PoetMusic theming: **4 accent swatches** + **3 canvas tints** (Lavender / Cream / Sage), applied live via CSS variables, `POST /api/settings/accent` / `.../theme` |
| How-to steps 1-2-3 | XML text views | `/screens/home` card |
| Toasts for save/errors | Android `Toast` | Pastel web toast via `HX-Trigger` events (service toasts stay native) |

## 2. Architecture

```
┌────────────────────────────────────────────────────────┐
│ MainActivity (WebView host)                            │
│  - waits for server port, loads http://127.0.0.1:8977  │
│  - intercepts /assets/* straight from the APK          │
│  - external links → ACTION_VIEW (AI Studio)            │
│  - WwNative JS bridge: status-bar color follows accent │
│  - back button → wwBack() in JS                        │
└──────────────┬─────────────────────────────────────────┘
               │ http (localhost only)
┌──────────────▼─────────────────────────────────────────┐
│ WwServer (Ktor CIO, 127.0.0.1:8977)                    │
│  GET  /                  → Shell.page (CSS+JS+htmx)    │
│  GET  /assets/{name}     → htmx.min.js, Outfit fonts   │
│  GET  /screens/home      → status/key/model/how-to     │
│  GET  /screens/settings  → accent, tint, about         │
│  GET  /api/status        → {enabled, hasKey} JSON      │
│  POST /api/key           → ApiKeyRepository.saveApiKey │
│  POST /api/accessibility/open → native settings intent │
│  POST /api/settings/model|accent|theme → Prefs         │
└──────────────┬─────────────────────────────────────────┘
               │ reads/writes
   ApiKeyRepository (encrypted)  ·  Prefs (wordwise_prefs)
```

- `WordWiseApp : Application` starts the server before the WebView exists.
- Port **8977** (not 8080) so WordWise and PoetMusic can run on one device.
- `Prefs` owns model/accent/tint; `GrammarFixService` reads the model from it.
- Cleartext is allowed **only** for `127.0.0.1` in `network_security_config.xml`;
  everything else remains TLS-only.

## 3. Design system (identical to PoetMusic)

- Font: **Outfit** (latin + latin-ext woff2, bundled).
- Canvas tints: Lavender `#f2effa`, Cream `#faf5ec`, Sage `#eff6f0`.
- Accents: `#b9a5ec` `#9fd8c0` `#f4b89a` `#a5c9ec`, with derived
  `--accent-faint/soft/shadow` alpha ramps.
- Ink `#3b3651`, muted `#8a84a3`; white cards, radius 18, soft shadows;
  pill nav buttons, `btn-primary`/`btn-outline`, swatches, tint pills,
  radio rows, pastel toast — all lifted from Poet's `Shell.kt` CSS.
- Status-bar color follows the accent (light icons decided by luminance).

## 4. Removed

- `activity_main.xml`, `themes.xml` (6 IDE themes), `colors.xml` tokens,
  `attrs.xml` (`wwSuccess`), `status_dot.xml`, ViewBinding, Material
  Components dependency, `NoFilterAdapter`, `AppTheme` enum.

## 5. Build changes

- `minSdk` 23 → **26** (Ktor server engine floor; matches PoetMusic).
- Added `io.ktor:ktor-server-core` + `ktor-server-cio` 2.3.13, `slf4j-nop`.
- `packaging.resources.excludes` for Ktor META-INF duplicates.
- ProGuard keep rules for Ktor.
- Assets: `web/htmx.min.js`, `web/outfit-latin.woff2`, `web/outfit-latin-ext.woff2`.

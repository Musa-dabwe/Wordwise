# Documentation & UI Copy Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update all user-facing copy across the settings screen, About page, and README to reflect the current state: OpenRouter provider, `?ask` command, and corrected branding.

**Architecture:** Copy-only changes across 3 files. No logic changes, no new dependencies.

**Tech Stack:** Kotlin (htmx server-rendered HTML), Markdown

**Spec:** `docs/superpowers/specs/2026-09-18-docs-and-ui-copy-updates.md`

## Global Constraints

- No em dashes in copy
- No code logic changes — copy and strings only
- Follow existing HTML/Markdown style in each file
- `?fix` and `?ask` must both be documented
- OpenRouter is the provider (not Gemini)

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/kotlin/com/musa/wordwise/server/Shell.kt` | Modify | Header subtitle |
| `app/src/main/kotlin/com/musa/wordwise/server/Views.kt` | Modify | Arrow icon, model display, How to Use, About screen |
| `README.md` | Modify | Full documentation rewrite |

---

### Task 1: Settings Screen Copy Updates

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/server/Shell.kt:175`
- Modify: `app/src/main/kotlin/com/musa/wordwise/server/Views.kt:37,43,52-58`

**Interfaces:**
- No dependencies on other tasks
- Produces: Updated settings screen copy

- [ ] **Step 1: Update header subtitle in Shell.kt**

In `Shell.kt` line 175, change:
```kotlin
<div class="hdr-sub">Grammar correction, system-wide</div>
```
to:
```kotlin
<div class="hdr-sub">System wide grammar assistant</div>
```

- [ ] **Step 2: Remove arrow icon from OpenRouter link in Views.kt**

In `Views.kt` line 37, change:
```kotlin
<a class="key-link" href="https://openrouter.ai/keys" target="_blank">Get a free key at OpenRouter →</a>
```
to:
```kotlin
<a class="key-link" href="https://openrouter.ai/keys" target="_blank">Get a free key at OpenRouter</a>
```

- [ ] **Step 3: Shorten model display in Views.kt**

In `Views.kt` line 43, change:
```kotlin
<div class="ww-model-badge" style="display:inline-block; padding:6px 14px; border-radius:8px; background:#f0f0f0; font-family:monospace; font-size:14px;">openrouter/free — Free Models Router (OpenRouter)</div>
```
to:
```kotlin
<div class="ww-model-badge" style="display:inline-block; padding:6px 14px; border-radius:8px; background:#f0f0f0; font-family:monospace; font-size:14px;">Openrouter - Free Models Router</div>
```

- [ ] **Step 4: Update How to Use section in Views.kt**

In `Views.kt` lines 52-58, replace the entire HOW TO USE block:
```kotlin
<div>
  <div class="ww-lab" style="margin-bottom:14px;">HOW TO USE</div>
  <div style="display:flex; flex-direction:column; gap:14px;">
    <div class="step"><div class="step-num">1</div><div class="step-txt">Type your text in any app (WhatsApp, Gmail, etc.)</div></div>
    <div class="step"><div class="step-num">2</div><div class="step-txt">Add <code>?fix</code> at the end of your text</div></div>
    <div class="step"><div class="step-num">3</div><div class="step-txt">WordWise replaces it with the corrected text</div></div>
  </div>
</div>
```
with:
```kotlin
<div>
  <div class="ww-lab" style="margin-bottom:14px;">HOW TO USE</div>
  <div style="display:flex; flex-direction:column; gap:14px;">
    <div class="step"><div class="step-num">1</div><div class="step-txt">Type your text in any app (WhatsApp, Gmail, etc.)</div></div>
    <div class="step"><div class="step-num">2</div><div class="step-txt">Add <code>?fix</code> to correct grammar, or <code>?ask</code> to ask AI anything</div></div>
    <div class="step"><div class="step-num">3</div><div class="step-txt">WordWise replaces it with the result</div></div>
  </div>
</div>
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/server/Shell.kt app/src/main/kotlin/com/musa/wordwise/server/Views.kt
git commit -m "docs: update settings screen copy for ?ask and OpenRouter"
```

---

### Task 2: About Screen Updates

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/server/Views.kt:85-128`

**Interfaces:**
- No dependencies on other tasks
- Produces: Updated About screen content

- [ ] **Step 1: Update About screen description**

In `Views.kt`, replace the entire `aboutScreen()` method body (lines 85-128):

```kotlin
fun aboutScreen(): String {
    return """
    <div class="screen" data-screen="about">
      <div class="md-body">
        <h1>WordWise</h1>
        <p><strong>System-wide grammar correction and AI assistant for Android.</strong> Type <code>?fix</code> at the end of any text to correct grammar, or <code>?ask</code> to ask AI anything — WordWise handles it using OpenRouter's free models router, all without leaving your current app.</p>

        <h2>How it works</h2>
        <p>WordWise runs as an Android <strong>Accessibility Service</strong>. When you type a trigger after your text, it:</p>
        <ol>
          <li>Reads the surrounding text from the input field.</li>
          <li>Sends it to <strong>OpenRouter</strong>'s free models with the appropriate prompt.</li>
          <li>Replaces the text in place — instantly, in any app.</li>
        </ol>
        <p>Password fields are always skipped.</p>

        <h2>Commands</h2>
        <ul>
          <li><code>?fix</code> — Corrects grammar and style. Preserves meaning and language.</li>
          <li><code>?ask</code> — Asks AI anything. Writes, translates, answers, generates — you name it.</li>
        </ul>

        <h2>Tech Stack</h2>
        <ul>
          <li><strong>Frontend</strong> — <code>htmx</code> with server-rendered HTML, running in a native Android WebView.</li>
          <li><strong>Backend</strong> — embedded <strong>Ktor</strong> (CIO) server on-device, bound to localhost.</li>
          <li><strong>Language</strong> — <strong>Kotlin</strong>, front to back: the UI screens are rendered by the same Kotlin process that runs the accessibility service.</li>
          <li><strong>AI</strong> — <strong>OpenRouter</strong> API with your own free key.</li>
        </ul>

        <h2>Security &amp; Privacy</h2>
        <ul>
          <li><strong>Key at rest</strong> — your OpenRouter key is stored with <code>EncryptedSharedPreferences</code> (AES-256-GCM / AES-256-SIV).</li>
          <li><strong>In transit</strong> — sent only to <code>openrouter.ai</code> over TLS; cleartext traffic is blocked.</li>
          <li><strong>No retention</strong> — text lives in memory only for the request. Never logged, cached, or stored.</li>
          <li><strong>Sensitive fields</strong> — password and web-password inputs are never read.</li>
          <li><strong>Backups excluded</strong> — the encrypted key store never leaves the device.</li>
        </ul>
        <blockquote><p>WordWise does <strong>not</strong> protect against a rooted device or other malicious accessibility services.</p></blockquote>

        <h2>License</h2>
        <p>Licensed under the <strong>Apache License 2.0</strong>.</p>
        <p>Copyright © 2026 Fackson Mutetesha. Distributed on an "AS IS" basis, without warranties of any kind.</p>

        <h2>Developer</h2>
        <p>Built by <strong>Fackson Mutetesha</strong>.</p>
        <p>GitHub: <a href="https://github.com/Musa-dabwe">@Musa-dabwe</a></p>
      </div>
    </div>"""
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/server/Views.kt
git commit -m "docs: update About screen for ?ask command and OpenRouter"
```

---

### Task 3: README.md Rewrite

**Files:**
- Modify: `README.md`

**Interfaces:**
- No dependencies on other tasks
- Produces: Updated README reflecting current state

- [ ] **Step 1: Rewrite README.md**

Replace the entire content of `README.md` with the updated version (see plan file for full content). Key changes:
- Header: "System-wide grammar correction and AI assistant for Android"
- Provider: OpenRouter (not Gemini)
- Usage: Both `?fix` and `?ask` commands documented
- Architecture diagram: OpenRouter endpoint
- Components: `Command` sealed class, `ask()` method
- System prompts: Both `GRAMMAR_SYSTEM_PROMPT` and `ASK_SYSTEM_PROMPT`
- Models: OpenRouter free models router
- Security: OpenRouter endpoint
- Limitations: Added `?ask` specific items

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: rewrite README for OpenRouter and ?ask command"
```

---

### Task 4: Final Verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all unit tests pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 3: Install and verify on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

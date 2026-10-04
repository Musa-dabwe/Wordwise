// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

import com.musa.wordwise.network.ModelId

/** Server-rendered htmx screens in the WordWise pastel design system. */
object Views {

    // ---------------- settings (home) ----------------

    /**
     * Settings screen.
     *
     * Takes no secret as a parameter, on purpose: whatever this returns is
     * readable by any app on the device, because the Ktor port is a shared
     * loopback socket. Account state reaches the page as booleans and non-secret
     * values only; the API key is write-only and lives behind the WebView bridge.
     */
    fun homeScreen(serviceEnabled: Boolean, currentModel: String, currentTheme: String): String {
        return """
        <div class="screen" data-screen="home" style="display:flex; flex-direction:column; gap:24px;">

          <div class="status-pill">
            <div class="status-left">
              <div id="status-dot" class="status-dot${if (serviceEnabled) " on" else ""}"></div>
              <div id="status-label" class="status-label">${if (serviceEnabled) "SERVICE ACTIVE" else "SERVICE PAUSED"}</div>
            </div>
            <button id="status-btn" class="status-btn" onclick="wwOpenAccessibility()">${if (serviceEnabled) "Enabled ✓" else "Enable"}</button>
          </div>

          <div>
            <div class="ww-lab" style="margin-bottom:10px;">API KEY</div>
            <div class="key-wrap">
              <input id="key-input" type="password" placeholder="Paste your OpenRouter API key here" autocomplete="off" autocapitalize="off" spellcheck="false">
              <button type="button" class="key-eye" onclick="wwToggleKey(this)">SHOW</button>
            </div>
            <div id="key-state" class="key-state"></div>
            <a class="key-link" href="https://openrouter.ai/keys" target="_blank">Get a free key at OpenRouter</a>
            <button id="save-btn" type="button" class="ww-save" style="margin-top:18px;" onclick="wwSaveKey()">SAVE API KEY</button>
            <button id="key-remove" type="button" class="key-remove" style="display:none;" onclick="wwRemoveKey()">Remove saved key</button>
          </div>

          <div>
            <div class="ww-lab" style="margin-bottom:10px;">AI MODEL</div>
            ${modelPicker(currentModel)}
          </div>

          <div>
            <div class="ww-lab" style="margin-bottom:10px;">THEME</div>
            ${themePicker(currentTheme)}
          </div>

          <div>
            <div class="ww-lab" style="margin-bottom:14px;">HOW TO USE</div>
            <div style="display:flex; flex-direction:column; gap:14px;">
              <div class="step"><div class="step-num">1</div><div class="step-txt">Type your text in any app (WhatsApp, Gmail, etc.)</div></div>
              <div class="step"><div class="step-num">2</div><div class="step-txt">Add <code>?fix</code> to correct grammar, or <code>?ask</code> to ask AI anything</div></div>
              <div class="step"><div class="step-num">3</div><div class="step-txt">WordWise replaces it with the result</div></div>
            </div>
          </div>
        </div>"""
    }

    /**
     * Theme dropdown. Selection is persisted through the WebView bridge by
     * `wwSetTheme()`, never by POSTing to the local server.
 */
    private fun themePicker(currentTheme: String): String {
        val current = Themes.byKey(currentTheme)
        val rows = Themes.ALL.mapIndexed { i, t ->
            val on = t.key == current.key
            """
            <button type="button" class="ww-row" data-k="${t.key}" style="animation-delay:${i * 55}ms;" onclick="wwSetTheme('${t.key}')">
              <span class="name"><span class="swatch" style="background:${t.swatch};"></span>${esc(t.name)}</span>
              <span class="check" style="visibility:${if (on) "visible" else "hidden"};">✓</span>
            </button>"""
        }.joinToString("")
        return """
        <div id="theme-drop" class="ww-drop">
          <button type="button" class="ww-sel" onclick="wwToggleDrop('theme-drop')">
            <span class="ww-selv"><span id="theme-swatch" class="swatch" style="width:16px; height:16px; border-radius:5px; background:${current.swatch};"></span><span id="theme-name" class="val">${esc(current.name)}</span></span>
            <span class="ww-chev">▾</span>
          </button>
          <div class="ww-pop">$rows</div>
        </div>"""
    }

    /**
     * Model dropdown + paste field.
     *
     * Neither control writes anything by itself: picking a row or typing a path
     * only fills the field, and the single SAVE MODEL submit persists it. That
     * keeps one code path for the user's intent, so a half-finished edit is
     * never silently applied, and makes the paste field the authoritative value.
     */
    private fun modelPicker(currentModel: String): String {
        return """
        <div id="model-card">
          <div id="model-drop" class="ww-drop">
            <button type="button" class="ww-sel" onclick="wwToggleDrop('model-drop')">
              <span class="ww-selv"><span id="model-label" class="val">${esc(labelFor(currentModel))}</span></span>
              <span class="ww-chev">▾</span>
            </button>
            <div class="ww-pop">
              <input id="model-search" class="ww-search" type="text" placeholder="Search models…" autocomplete="off"
                     autocapitalize="off" spellcheck="false" oninput="wwFilterModels()">
              <div id="model-rows">
                <div class="ww-note">Loading models…</div>
              </div>
              <div class="ww-note" id="model-empty" style="display:none;">No models match. Paste an ID below instead.</div>
            </div>
          </div>

          <div style="margin-top:12px;">
            <div class="key-wrap plain">
              <input id="model-input" type="text" placeholder="vendor/model — e.g. anthropic/claude-3.5-sonnet"
                     autocomplete="off" autocapitalize="off" spellcheck="false" oninput="wwSetModelField(this.value)">
            </div>
            <a class="key-link" href="https://openrouter.ai/models" target="_blank">Browse all models at OpenRouter</a>
            <button id="model-save" type="button" class="ww-save ww-save-sm" onclick="wwSaveModel()">SAVE MODEL</button>
          </div>
        </div>"""
    }

    /** Dropdown label for a model ID, with the free router spelled out. */
    private fun labelFor(modelId: String): String =
        if (modelId == ModelId.DEFAULT) "$modelId — Free Models Router" else modelId

    // ---------------- about ----------------

    fun aboutScreen(): String {
        return """
        <div class="screen" data-screen="about">
          <div class="md-body">
            <h1>WordWise</h1>
            <p><strong>System-wide grammar assistant and AI helper for Android.</strong> Type <code>?fix</code> to correct grammar or <code>?ask</code> to ask AI anything, right from any app, no copy-paste needed.</p>

            <h2>Commands</h2>
            <ul>
              <li><code>?fix</code> - Correct grammar, spelling, and style in-place.</li>
              <li><code>?ask</code> - Ask AI anything about your text and get a response.</li>
            </ul>

            <h2>Choosing a Model</h2>
            <p>WordWise runs on <strong>OpenRouter</strong>. By default it uses the free models router, so it costs nothing. To pick a different model, open <strong>Settings</strong> and either choose one from the searchable list or paste a model path such as <code>anthropic/claude-3.5-sonnet</code> into the <strong>AI MODEL</strong> field.</p>
            <p>Paid models use your OpenRouter account's own billing. If a model needs a subscription or is otherwise unavailable to your account, OpenRouter returns an error and WordWise leaves your text untouched.</p>

            <h2>How it works</h2>
            <p>WordWise runs as an Android <strong>Accessibility Service</strong>. When you type a command after your text, it:</p>
            <ol>
              <li>Reads the surrounding text from the input field.</li>
              <li>Sends it to your chosen <strong>OpenRouter</strong> model with the appropriate prompt.</li>
              <li>Replaces the text in place, instantly, in any app.</li>
            </ol>
            <p>Password fields are always skipped.</p>

            <h2>Tech Stack</h2>
            <ul>
              <li><strong>Frontend</strong> - <code>htmx</code> with server-rendered HTML, running in a native Android WebView.</li>
              <li><strong>Backend</strong> - embedded <strong>Ktor</strong> (CIO) server on-device, bound to localhost.</li>
              <li><strong>Language</strong> - <strong>Kotlin</strong>, front to back: the UI screens are rendered by the same Kotlin process that runs the accessibility service.</li>
              <li><strong>AI</strong> - <strong>OpenRouter</strong> API with your own key.</li>
            </ul>

            <h2>Security &amp; Privacy</h2>
            <ul>
              <li><strong>Key at rest</strong> - your OpenRouter key is stored with <code>EncryptedSharedPreferences</code> (AES-256-GCM / AES-256-SIV), using a key that never leaves the device keystore.</li>
              <li><strong>Key is write-only</strong> - the key is never rendered back into this page, and there is no way to read it out of the app. Once saved, the settings screen only tells you that a key exists.</li>
              <li><strong>No local write surface</strong> - the on-device server only serves display pages. Reading your key and saving the key, model or theme all happen inside the app, not over the local network socket.</li>
              <li><strong>In transit</strong> - text and key are sent only to <code>openrouter.ai</code> over TLS; cleartext traffic is blocked everywhere else.</li>
              <li><strong>No retention</strong> - text lives in memory only for the request. Never logged, cached, or stored.</li>
              <li><strong>Sensitive fields</strong> - password and web-password inputs are never read.</li>
              <li><strong>Backups excluded</strong> - the encrypted key store never leaves the device.</li>
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
}

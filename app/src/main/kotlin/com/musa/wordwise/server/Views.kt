// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

import android.content.Context
import com.musa.wordwise.data.Prefs

/** Server-rendered htmx screens in the WordWise pastel design system. */
object Views {

    // ---------------- settings (home) ----------------

    fun homeScreen(context: Context, serviceEnabled: Boolean, key: String): String {
        return """
        <div class="screen" data-screen="home" style="display:flex; flex-direction:column; gap:24px;">

          <div class="status-pill">
            <div class="status-left">
              <div id="status-dot" class="status-dot${if (serviceEnabled) " on" else ""}"></div>
              <div id="status-label" class="status-label">${if (serviceEnabled) "SERVICE ACTIVE" else "SERVICE PAUSED"}</div>
            </div>
            <button id="status-btn" class="status-btn" hx-post="/api/accessibility/open" hx-swap="none">${if (serviceEnabled) "Enabled ✓" else "Enable"}</button>
          </div>

          <form hx-post="/api/key" hx-swap="none">
            <div class="ww-lab" style="margin-bottom:10px;">API KEY</div>
            <div class="key-wrap">
              <input id="key-input" type="password" name="key" value="${esc(key)}" placeholder="Paste your OpenRouter API key here" autocomplete="off" autocapitalize="off" spellcheck="false">
              <button type="button" class="key-eye" onclick="wwToggleKey(this)">SHOW</button>
            </div>
            <a class="key-link" href="https://openrouter.ai/keys" target="_blank">Get a free key at OpenRouter</a>
            <button id="save-btn" type="submit" class="ww-save" style="margin-top:18px;">SAVE API KEY</button>
          </form>

          <div>
            <div class="ww-lab" style="margin-bottom:10px;">AI MODEL</div>
            <div class="ww-model-badge" style="display:inline-block; padding:6px 14px; border-radius:8px; background:#f0f0f0; font-family:monospace; font-size:14px;">Openrouter - Free Models Router</div>
          </div>

          <div>
            <div class="ww-lab" style="margin-bottom:10px;">THEME</div>
            ${themePicker(context)}
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

    /** Theme dropdown — selection is applied client-side by wwSetTheme(). */
    private fun themePicker(context: Context): String {
        val current = Themes.byKey(Prefs.getTheme(context))
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

            <h2>How it works</h2>
            <p>WordWise runs as an Android <strong>Accessibility Service</strong>. When you type a command after your text, it:</p>
            <ol>
              <li>Reads the surrounding text from the input field.</li>
              <li>Sends it to <strong>OpenRouter</strong>'s free models with the appropriate prompt.</li>
              <li>Replaces the text in place — instantly, in any app.</li>
            </ol>
            <p>Password fields are always skipped.</p>

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
}

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

/** Server-rendered htmx screens in the Poet pastel design system. */
object Views {

    private val MODEL_NOTES = mapOf(
        "gemini-3.1-flash-lite" to "Default — fast, frontier-class quality",
        "gemini-3.5-flash" to "Most capable free model",
        "gemini-2.5-flash-lite" to "Fastest and most budget-friendly",
        "gemini-2.5-flash" to "2.5 workhorse, thinking disabled for speed"
    )

    // ---------------- home ----------------

    fun homeScreen(context: Context, serviceEnabled: Boolean, key: String): String {
        return """
        <div class="screen" data-screen="home" style="display:flex; flex-direction:column; gap:16px;">
          <div>
            <div style="font-size:22px; font-weight:700; letter-spacing:-0.02em;">Grammar, fixed anywhere</div>
            <div style="font-size:13px; color:var(--muted); margin-top:2px;">Type <b>?fix</b> at the end of any text, in any app.</div>
          </div>

          <div class="status-pill">
            <div id="status-dot" class="status-dot${if (serviceEnabled) " on" else ""}"></div>
            <div id="status-label" class="status-label">${if (serviceEnabled) "SERVICE ACTIVE" else "SERVICE INACTIVE"}</div>
            <button id="status-btn" class="status-btn${if (serviceEnabled) " on" else ""}" hx-post="/api/accessibility/open" hx-swap="none">${if (serviceEnabled) "Enabled ✓" else "Enable"}</button>
          </div>

          <div class="card">
            <div class="card-title">Gemini API key</div>
            <div class="card-sub">Stored encrypted on this device — never leaves it except to call Gemini</div>
            <form hx-post="/api/key" hx-swap="none" style="display:flex; flex-direction:column; gap:12px;">
              <div class="key-row">
                <div class="field" style="flex:1; min-width:0;">
                  <input id="key-input" type="password" name="key" value="${esc(key)}" placeholder="Paste your API key here" autocomplete="off" autocapitalize="off" spellcheck="false">
                </div>
                <button type="button" class="key-eye" onclick="wwToggleKey()" aria-label="Show or hide key">👁</button>
              </div>
              <button type="submit" class="btn-primary" style="width:100%; justify-content:center;">Save key</button>
            </form>
            <a class="link" href="https://aistudio.google.com" style="margin-top:6px;">Get a free key at Google AI Studio →</a>
          </div>

          <div class="card" id="model-card">
            ${modelCard(context)}
          </div>

          <div class="card">
            <div class="card-title" style="margin-bottom:12px;">How to use</div>
            <div style="display:flex; flex-direction:column; gap:14px;">
              <div class="step"><div class="step-num">1</div><div class="step-txt">Type your text in any app (WhatsApp, Gmail, etc.)</div></div>
              <div class="step"><div class="step-num">2</div><div class="step-txt">Add <code>?fix</code> at the end of your sentence or paragraph</div></div>
              <div class="step"><div class="step-num">3</div><div class="step-txt">Wait a moment — WordWise replaces it with the corrected text</div></div>
            </div>
          </div>
        </div>"""
    }

    /** Model picker body — re-rendered in place after a selection commits. */
    fun modelCard(context: Context): String {
        val selected = Prefs.getSelectedModel(context)
        val rows = Prefs.GEMINI_MODELS.joinToString("") { m ->
            val on = m == selected
            """
            <button class="sortopt${if (on) " active" else ""}" hx-post="/api/settings/model?m=${esc(m)}" hx-target="#model-card" hx-swap="innerHTML">
              <span class="radio">${if (on) """<span class="radio-dot"></span>""" else ""}</span>
              <span style="min-width:0;">
                <span class="opt-name" style="display:block; font-family:monospace;">${esc(m)}</span>
                <span class="opt-sub" style="display:block;">${esc(MODEL_NOTES[m] ?: "")}</span>
              </span>
            </button>"""
        }
        return """
        <div class="card-title">Model</div>
        <div class="card-sub">Free-tier Gemini models only (Flash / Flash-Lite families)</div>
        <div style="display:flex; flex-direction:column; gap:6px;">$rows</div>"""
    }

    // ---------------- settings ----------------

    fun settingsScreen(context: Context): String {
        val accent = Prefs.getAccent(context)
        val tint = Prefs.getTint(context)
        val swatches = Shell.ACCENTS.joinToString("") { c ->
            """<button class="swatch${if (c == accent) " on" else ""}" data-c="$c" style="background:$c;" onclick="setAccent('$c')"></button>"""
        }
        val tints = Shell.CANVAS_TINTS.entries.joinToString("") { (name, bg) ->
            """<button class="tint-pill${if (name == tint) " on" else ""}" data-n="$name" style="background:$bg;" onclick="setTheme('$name','$bg')">$name</button>"""
        }

        return """
        <div class="screen" data-screen="settings" style="display:flex; flex-direction:column; gap:16px;">
          <div style="font-size:22px; font-weight:700; letter-spacing:-0.02em;">Settings</div>

          <div class="card">
            <div class="card-title" style="margin-bottom:12px;">Accent color</div>
            <div style="display:flex; gap:12px; margin-bottom:18px;">$swatches</div>
            <div class="card-title" style="margin-bottom:12px;">Canvas tint</div>
            <div style="display:flex; gap:8px; flex-wrap:wrap;">$tints</div>
          </div>

          <div class="card">
            <div class="card-title">Privacy</div>
            <div style="font-size:12px; color:var(--muted); line-height:1.6;">
              WordWise never logs, caches, or stores the text it corrects — it is
              held in memory only for the duration of the Gemini request.<br>
              Password fields are always skipped. Your API key is encrypted with
              AES-256-GCM and sent only in a TLS request header.
            </div>
          </div>

          <div class="card">
            <div class="card-title">About</div>
            <div style="font-size:12px; color:var(--muted); line-height:1.6;">
              WordWise 2.0 — system-wide AI grammar correction.<br>
              Powered by Google Gemini with your own free-tier key.<br>
              Type <b>?fix</b> at the end of any text to correct it in place.
            </div>
          </div>
        </div>"""
    }
}

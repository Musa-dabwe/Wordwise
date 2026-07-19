// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

/**
 * The single-page shell: Poet design-system CSS, the htmx runtime, the
 * persistent header, and the client-side glue JS (status poller, theming,
 * toasts, back handling).
 */
object Shell {

    val CANVAS_TINTS = mapOf("Lavender" to "#f2effa", "Cream" to "#faf5ec", "Sage" to "#eff6f0")
    val ACCENTS = listOf("#b9a5ec", "#9fd8c0", "#f4b89a", "#a5c9ec")

    /**
     * Generates the complete HTML shell for the WordWise application.
     *
     * @param accent The initial accent color.
     * @param tint The initial canvas tint name.
     * @return An HTML document configured with the specified theme.
     */
    fun page(accent: String, tint: String): String {
        val bg = CANVAS_TINTS[tint] ?: CANVAS_TINTS.getValue("Lavender")
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<title>WordWise</title>
<script src="/assets/htmx.min.js"></script>
<style>
@font-face { font-family:'Outfit'; font-style:normal; font-weight:400 700; font-display:swap;
  src:url('/assets/outfit-latin.woff2') format('woff2');
  unicode-range:U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+2000-206F,U+2074,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD; }
@font-face { font-family:'Outfit'; font-style:normal; font-weight:400 700; font-display:swap;
  src:url('/assets/outfit-latin-ext.woff2') format('woff2');
  unicode-range:U+0100-024F,U+0259,U+1E00-1EFF,U+2020,U+20A0-20AB,U+20AD-20CF,U+2113,U+2C60-2C7F,U+A720-A7FF; }

:root {
  --accent: $accent;
  --accent-faint: ${accent}2e;
  --accent-soft: ${accent}66;
  --accent-shadow: ${accent}80;
  --bg: $bg;
  --ink: #3b3651;
  --muted: #8a84a3;
  --ok: #7fbf9a;
  --bad: #c25f6e;
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin:0; padding:0; }
body { background: var(--bg); font-family:'Outfit', sans-serif; color: var(--ink); transition: background 0.25s;
  -webkit-user-select:none; user-select:none;
  /* blocks pinch and double-tap zoom while keeping scroll + tap responsive */
  touch-action: manipulation; }
input, textarea { -webkit-user-select:text; user-select:text; }
button { font-family: inherit; color: var(--ink); }
@keyframes poet-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes poet-fade { from { opacity:0; transform: translateY(6px);} to { opacity:1; transform: translateY(0);} }
@keyframes poet-pop { 0% { transform: scale(0.7); } 60% { transform: scale(1.15); } 100% { transform: scale(1); } }
@keyframes poet-pulse { 0%,100% { opacity:1; } 50% { opacity:0.45; } }

#app { width:100%; max-width:480px; min-height:100vh; margin:0 auto; position:relative; display:flex; flex-direction:column; }
#main-container { flex:1; padding:6px 20px calc(32px + env(safe-area-inset-bottom)) 20px; }
.screen { animation: poet-fade 0.18s ease-out; }

/* header */
.hdr { display:flex; align-items:center; justify-content:space-between; padding:18px 20px 10px 20px; }
.hdr-brand { display:flex; align-items:center; gap:10px; cursor:pointer; }
.hdr-logo { width:30px; height:30px; border-radius:9px; background:var(--accent); display:flex; align-items:center; justify-content:center; font-weight:700; font-size:15px; }
.hdr-name { font-size:20px; font-weight:700; letter-spacing:-0.02em; }
.navpill { border:none; cursor:pointer; padding:8px 14px; border-radius:99px; font-size:13px; font-weight:600; background:transparent; transition: background 0.15s, transform 0.12s; }
.navpill:active { transform: scale(0.95); }
.navpill.active { background: var(--accent-faint); }

/* generic */
.btn-primary { border:none; cursor:pointer; display:inline-flex; align-items:center; gap:8px; padding:10px 18px; border-radius:12px; background:var(--accent); font-size:14px; font-weight:700; box-shadow:0 2px 8px var(--accent-shadow); transition: transform 0.12s; }
.btn-primary:active { transform: scale(0.95); }
.btn-outline { border:1.5px solid var(--accent); cursor:pointer; display:inline-flex; align-items:center; gap:8px; padding:10px 18px; border-radius:12px; background:#ffffff; font-size:14px; font-weight:700; transition: transform 0.12s; }
.btn-outline:active { transform: scale(0.95); }
.card { background:#ffffff; border-radius:18px; padding:18px; box-shadow:0 2px 10px rgba(59,54,81,0.06); }
.card-title { font-size:15px; font-weight:700; margin-bottom:4px; }
.card-sub { font-size:12px; color:var(--muted); margin-bottom:12px; }
.link { border:none; background:transparent; cursor:pointer; padding:6px 0; font-size:13px; font-weight:600; color:var(--muted); text-decoration:none; display:inline-block; }
.link:active { opacity:0.7; }

/* status pill card */
.status-pill { display:flex; align-items:center; gap:12px; background:#ffffff; border-radius:99px; padding:10px 10px 10px 18px; box-shadow:0 2px 10px rgba(59,54,81,0.06); }
.status-dot { width:10px; height:10px; border-radius:50%; flex-shrink:0; background:var(--bad); }
.status-dot.on { background:var(--ok); animation:poet-pulse 2.2s ease-in-out infinite; }
.status-label { flex:1; min-width:0; font-size:12px; font-weight:700; letter-spacing:0.1em; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.status-btn { border:1.5px solid var(--accent); cursor:pointer; padding:8px 16px; border-radius:99px; background:#ffffff; font-size:13px; font-weight:700; flex-shrink:0; transition: transform 0.12s; }
.status-btn:active { transform: scale(0.95); }
.status-btn.on { border-color:transparent; background:var(--accent-faint); }

/* input fields */
.field { display:flex; flex-direction:column; gap:6px; }
.field span { font-size:12px; font-weight:700; color:var(--muted); letter-spacing:0.02em; }
.field input { font-family:inherit; font-size:14px; font-weight:600; color:var(--ink); padding:12px 14px; border-radius:12px;
  border:1.5px solid rgba(59,54,81,0.12); background:#ffffff; outline:none; transition:border-color 0.15s, box-shadow 0.15s; width:100%; }
.field input:focus { border-color:var(--accent); box-shadow:0 0 0 3px var(--accent-faint); }
.key-row { display:flex; gap:8px; }
.key-row input { flex:1; min-width:0; font-family:monospace; font-weight:600; }
.key-eye { border:1.5px solid rgba(59,54,81,0.12); cursor:pointer; width:46px; border-radius:12px; background:#ffffff;
  display:flex; align-items:center; justify-content:center; font-size:16px; flex-shrink:0; }
.key-eye:active { transform:scale(0.93); }

/* option rows (model picker) */
.sortopt { display:flex; align-items:center; gap:14px; width:100%; border:none; cursor:pointer; text-align:left;
  padding:13px 14px; border-radius:14px; background:rgba(59,54,81,0.03); color:var(--ink); transition:background 0.15s; }
.sortopt:active { transform: scale(0.98); }
.sortopt.active { background: var(--accent-faint); }
.radio { width:20px; height:20px; border-radius:50%; border:2px solid var(--ink); box-sizing:border-box;
  display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.radio-dot { width:10px; height:10px; border-radius:50%; background:var(--ink); animation:poet-pop 0.25s ease; }
.opt-name { font-size:14px; font-weight:700; }
.opt-sub { font-size:12px; color:var(--muted); }

/* how-to steps */
.step { display:flex; gap:12px; align-items:flex-start; }
.step-num { width:26px; height:26px; border-radius:9px; background:var(--accent-faint); color:var(--ink);
  display:flex; align-items:center; justify-content:center; font-size:13px; font-weight:700; flex-shrink:0; }
.step-txt { font-size:14px; line-height:1.5; padding-top:3px; }
.step-txt code { font-family:monospace; font-size:13px; font-weight:700; background:var(--accent-faint); padding:2px 6px; border-radius:6px; }

/* theming */
.swatch { width:40px; height:40px; border-radius:50%; cursor:pointer; border:3px solid #ffffff; transition: transform 0.12s; }
.swatch:active { transform: scale(0.9); }
.swatch.on { border-color: var(--ink); }
.tint-pill { border:1.5px solid rgba(59,54,81,0.15); cursor:pointer; padding:9px 16px; border-radius:99px; font-size:13px; font-weight:600; color:var(--ink); }
.tint-pill.on { border-color: var(--ink); }
.spinner { display:inline-block; width:15px; height:15px; border:2.5px solid var(--accent); border-top-color:transparent; border-radius:50%; animation:poet-spin 0.8s linear infinite; }

/* toast */
#toast { position:fixed; bottom:32px; left:50%; transform:translateX(-50%); z-index:90; background:var(--ink); color:#f5f3fa; font-size:13px; font-weight:600; padding:10px 18px; border-radius:99px; box-shadow:0 8px 24px rgba(59,54,81,0.35); opacity:0; pointer-events:none; transition:opacity 0.25s; max-width:85vw; text-align:center; }
#toast.show { opacity:1; }
</style>
</head>
<body>
<div id="app">
  <div class="hdr">
    <div class="hdr-brand" onclick="wwGo('/screens/home')">
      <div class="hdr-logo">W</div>
      <div class="hdr-name">WordWise</div>
    </div>
    <div style="display:flex; gap:8px;">
      <button id="nav-home" class="navpill active" hx-get="/screens/home" hx-target="#main-container">Home</button>
      <button id="nav-settings" class="navpill" hx-get="/screens/settings" hx-target="#main-container">Settings</button>
    </div>
  </div>

  <div id="main-container"></div>

  <div id="toast"></div>
</div>

<script>
window.WW = { accent: ${jsonStr(accent)}, tint: ${jsonStr(tint)} };

var wwScreenUrl = '/screens/home';

function wwGo(url) { htmx.ajax('GET', url, { target: '#main-container', swap: 'innerHTML' }); }

function wwToast(msg) {
  var t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(t._h);
  t._h = setTimeout(function () { t.classList.remove('show'); }, 2200);
}

document.body.addEventListener('ww-toast', function (e) { wwToast(e.detail.value || e.detail); });

document.body.addEventListener('htmx:afterSwap', function (e) {
  if (e.detail.target && e.detail.target.id === 'main-container') {
    var path = e.detail.pathInfo && (e.detail.pathInfo.finalRequestPath || e.detail.pathInfo.requestPath);
    if (path && path.indexOf('/screens/') === 0) wwScreenUrl = path;
    var home = document.getElementById('nav-home'), set = document.getElementById('nav-settings');
    home.classList.toggle('active', wwScreenUrl.indexOf('/screens/settings') !== 0);
    set.classList.toggle('active', wwScreenUrl.indexOf('/screens/settings') === 0);
    window.scrollTo(0, 0);
    wwPoll();
  }
});

/* ---- live accessibility-service status poller ---- */
function wwApplyStatus(s) {
  var dot = document.getElementById('status-dot');
  if (!dot) return;
  dot.classList.toggle('on', !!s.enabled);
  document.getElementById('status-label').textContent = s.enabled ? 'SERVICE ACTIVE' : 'SERVICE INACTIVE';
  var btn = document.getElementById('status-btn');
  btn.textContent = s.enabled ? 'Enabled ✓' : 'Enable';
  btn.classList.toggle('on', !!s.enabled);
}
function wwPoll() {
  fetch('/api/status').then(function (r) { return r.json(); }).then(wwApplyStatus).catch(function () {});
}
setInterval(wwPoll, 2000);

/* ---- API key show/hide toggle ---- */
function wwToggleKey() {
  var i = document.getElementById('key-input');
  i.type = i.type === 'password' ? 'text' : 'password';
}

/* ---- theming ---- */
function setAccent(c) {
  var r = document.documentElement.style;
  r.setProperty('--accent', c);
  r.setProperty('--accent-faint', c + '2e');
  r.setProperty('--accent-soft', c + '66');
  r.setProperty('--accent-shadow', c + '80');
  window.WW.accent = c;
  if (window.WwNative && WwNative.setStatusBarColor) WwNative.setStatusBarColor(c);
  fetch('/api/settings/accent', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'c=' + encodeURIComponent(c) });
  document.querySelectorAll('.swatch').forEach(function (s) { s.classList.toggle('on', s.getAttribute('data-c') === c); });
}
function setTheme(name, bg) {
  document.documentElement.style.setProperty('--bg', bg);
  window.WW.tint = name;
  fetch('/api/settings/theme', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'name=' + encodeURIComponent(name) });
  document.querySelectorAll('.tint-pill').forEach(function (p) { p.classList.toggle('on', p.getAttribute('data-n') === name); });
}

document.addEventListener('DOMContentLoaded', function () {
  wwGo('/screens/home');
  wwPoll();
});

/* back-button support: Android calls wwBack() */
function wwBack() {
  if (wwScreenUrl !== '/screens/home') { wwGo('/screens/home'); return 'handled'; }
  return 'exit';
}
</script>
</body>
</html>"""
    }
}

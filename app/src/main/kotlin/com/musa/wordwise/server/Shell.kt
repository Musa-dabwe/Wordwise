// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

/**
 * The single-page shell: pastel design-system CSS, the htmx runtime, the
 * persistent header + Settings/About tabs, and the client-side glue JS
 * (status poller, full-theme switching, dropdowns, toasts, back handling).
 */
object Shell {

    fun page(themeKey: String): String {
        return """<!DOCTYPE html>
<html lang="en" style="${Themes.styleVars(themeKey)}">
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

* { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
html, body { margin:0; padding:0; }
body { background:var(--bg); background-attachment:fixed; font-family:'Outfit',sans-serif; color:var(--ink);
  transition:background .5s;
  -webkit-user-select:none; user-select:none;
  /* blocks pinch and double-tap zoom while keeping scroll + tap responsive */
  touch-action:manipulation; }
input, textarea { -webkit-user-select:text; user-select:text; }
button { font-family:inherit; color:var(--ink); }
a { color:var(--accsolid); text-decoration:none; }

@keyframes wwFade { from { opacity:0; } to { opacity:1; } }
@keyframes wwScreen { from { opacity:0; transform:translateX(10px); } to { opacity:1; transform:none; } }
@keyframes wwSlide { from { opacity:0; transform:translateY(-8px); } to { opacity:1; transform:none; } }
@keyframes wwDrop { from { opacity:0; transform:translateY(-8px); } to { opacity:1; transform:none; } }
@keyframes wwPulse { 0%,100% { box-shadow:0 0 0 0 rgba(122,158,102,.5); } 55% { box-shadow:0 0 0 8px rgba(122,158,102,0); } }
@keyframes wwSaved { 0% { transform:scale(1); } 35% { transform:scale(.95); } 100% { transform:scale(1); } }
@keyframes wwSpin { from { transform:rotate(0deg); } to { transform:rotate(360deg); } }

#app { width:100%; max-width:480px; min-height:100vh; margin:0 auto; position:relative;
  display:flex; flex-direction:column; padding:0 24px calc(30px + env(safe-area-inset-bottom)); }
#main-container { flex:1; }
/* fill-mode must stay "backwards": a filled transform keyframe would make .screen a
   permanent stacking context, trapping the dropdown popups (z-index 50/51) below the
   z-index 40 #ww-backdrop, which would then swallow their taps. */
.screen { animation:wwScreen .3s backwards; }

/* header */
.hdr { display:flex; align-items:center; gap:13px; margin:18px 0 22px; }
.hdr-logo { width:50px; height:50px; border-radius:16px; background:var(--logo); display:flex; align-items:center;
  justify-content:center; color:#fff; font-size:23px; text-shadow:0 1px 3px rgba(0,0,0,.18);
  box-shadow:0 8px 18px -6px rgba(0,0,0,.28); flex:none; }
.hdr-name { font-weight:800; font-size:26px; color:var(--ink); line-height:1; letter-spacing:-0.01em; }
.hdr-sub { color:var(--sub); font-size:13px; margin-top:3px; }

/* tab bar */
.tabs { display:flex; gap:6px; padding:5px; background:var(--soft); border-radius:16px; margin-bottom:24px; }
.tab { flex:1; text-align:center; padding:11px; border:none; border-radius:12px; cursor:pointer;
  font-weight:800; font-size:14px; background:transparent; color:var(--sub); transition:background .25s, color .25s; }
.tab.active { background:var(--accsolid); color:#fff; }

/* section labels */
.ww-lab { font-size:12px; font-weight:800; letter-spacing:.14em; color:var(--lab); }

/* status pill */
.status-pill { display:flex; align-items:center; justify-content:space-between; background:var(--soft);
  border-radius:24px; padding:15px 18px; }
.status-left { display:flex; align-items:center; gap:12px; min-width:0; }
.status-dot { width:11px; height:11px; border-radius:50%; flex:none; background:#c0b6a8; }
.status-dot.on { background:#7a9e66; animation:wwPulse 2.4s infinite; }
.status-label { font-weight:800; letter-spacing:.08em; font-size:13.5px; color:var(--ink);
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.status-btn { border:1.5px solid var(--border); cursor:pointer; background:#fff; border-radius:20px;
  padding:8px 15px; font-weight:700; color:var(--accsolid); font-size:13.5px; flex:none; transition:transform .15s; }
.status-btn:active { transform:scale(.95); }

/* api key field */
.key-wrap { position:relative; border:2px solid var(--accsolid); border-radius:18px; background:var(--field);
  padding:0; }
.key-wrap input { width:100%; border:none; outline:none; background:transparent; font-family:monospace;
  font-size:15px; color:var(--ink); letter-spacing:1px; padding:16px 74px 16px 16px; border-radius:18px; }
.key-eye { position:absolute; right:12px; top:50%; transform:translateY(-50%); border:none; cursor:pointer;
  font-size:11px; font-weight:800; color:var(--sub); background:var(--soft); padding:6px 9px; border-radius:9px; }
.key-eye:active { transform:translateY(-50%) scale(.9); }
.key-link { display:inline-block; margin-top:11px; font-weight:700; font-size:14.5px; }

/* save button */
.ww-save { border:none; cursor:pointer; width:100%; background:linear-gradient(135deg,var(--accent),var(--accent2));
  color:#fff; border-radius:20px; padding:19px; font-weight:800; font-size:18px; letter-spacing:.05em;
  box-shadow:0 14px 26px -10px var(--accsh); transition:transform .15s, filter .2s; }
.ww-save:active { transform:scale(.97); }
.ww-save.saved { animation:wwSaved .4s; }

/* dropdown selector */
.ww-drop { position:relative; }
.ww-sel { display:flex; align-items:center; justify-content:space-between; cursor:pointer; user-select:none;
  width:100%; text-align:left; transition:transform .15s, box-shadow .25s; border:1.5px solid var(--border);
  border-radius:18px; background:var(--field); padding:18px; }
.ww-sel:active { transform:scale(.985); }
.ww-selv { font-weight:700; font-size:16px; color:var(--ink); display:flex; align-items:center; gap:10px; min-width:0; }
.ww-selv .val { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ww-chev { transition:transform .32s cubic-bezier(.4,0,.2,1); font-size:13px; color:var(--sub); flex:none; }
.ww-drop.open .ww-chev { transform:rotate(180deg); }
.ww-drop.open { z-index:50; }
.ww-pop { display:none; position:absolute; left:0; right:0; top:calc(100% + 10px); z-index:51;
  background:var(--popbg); border:1px solid var(--popbd); border-radius:20px;
  box-shadow:0 20px 42px -12px var(--popsh); padding:8px; animation:wwDrop .28s both; }
.ww-drop.open .ww-pop { display:block; }
.ww-row { display:flex; align-items:center; justify-content:space-between; gap:10px; width:100%; border:none;
  cursor:pointer; text-align:left; padding:15px 14px; border-radius:14px; background:transparent;
  transition:filter .2s; animation:wwSlide .34s both; }
.ww-row:active { filter:brightness(.95); }
.ww-row .name { display:flex; align-items:center; gap:11px; font-weight:700; font-size:15px; color:var(--ink); min-width:0; }
.ww-row .check { color:var(--accsolid); font-weight:800; flex:none; }
.ww-row .sub { display:block; font-weight:500; font-size:12px; color:var(--sub); margin-top:2px; }
.swatch { width:22px; height:22px; border-radius:7px; flex:none; }
#ww-backdrop { display:none; position:fixed; inset:0; z-index:40; background:rgba(40,30,25,.14); animation:wwFade .25s both; }
#ww-backdrop.show { display:block; }

/* how-to steps */
.step { display:flex; align-items:center; gap:14px; }
.step-num { display:flex; align-items:center; justify-content:center; width:28px; height:28px; border-radius:50%;
  background:var(--soft); color:var(--accsolid); font-weight:800; font-size:14px; flex:none; }
.step-txt { color:var(--ink); font-size:15px; line-height:1.35; }
.step-txt code, .md-body code { font-family:monospace; font-size:13px; background:var(--soft); color:var(--accsolid);
  padding:2px 6px; border-radius:6px; }

/* about page (rendered markdown) */
.md-body { color:var(--ink); font-size:15px; line-height:1.6; }
.md-body h1 { font-size:27px; font-weight:800; margin:2px 0 6px; color:var(--ink); }
.md-body h2 { font-size:18px; font-weight:800; letter-spacing:.01em; margin:26px 0 8px; padding-bottom:7px;
  border-bottom:2px solid var(--border); color:var(--ink); }
.md-body p { margin:10px 0; }
.md-body strong { color:var(--ink); font-weight:700; }
.md-body a { color:var(--accsolid); font-weight:700; }
.md-body ul, .md-body ol { margin:10px 0; padding-left:20px; }
.md-body li { margin:6px 0; }
.md-body li::marker { color:var(--accsolid); }
.md-body blockquote { margin:14px 0; padding:12px 16px; background:var(--soft); border-left:4px solid var(--accsolid);
  border-radius:0 12px 12px 0; color:var(--ink); }
.md-body blockquote p { margin:0; }
.md-body table { width:100%; border-collapse:collapse; margin:12px 0; font-size:13px; }
.md-body th { text-align:left; font-weight:800; padding:9px 10px; background:var(--soft); color:var(--ink); }
.md-body th:first-child { border-radius:10px 0 0 0; } .md-body th:last-child { border-radius:0 10px 0 0; }
.md-body td { padding:9px 10px; border-bottom:1px solid var(--border); color:var(--ink); }
.md-body td code { font-size:11.5px; }

/* toast */
#toast { position:fixed; bottom:32px; left:50%; transform:translateX(-50%); z-index:90; background:var(--ink);
  color:#fff; font-size:13px; font-weight:600; padding:10px 18px; border-radius:99px;
  box-shadow:0 8px 24px var(--popsh); opacity:0; pointer-events:none; transition:opacity .25s;
  max-width:85vw; text-align:center; }
#toast.show { opacity:1; }
</style>
</head>
<body>
<div id="app">
  <div class="hdr">
    <div class="hdr-logo">✦</div>
    <div style="flex:1; min-width:0;">
      <div class="hdr-name">WordWise</div>
      <div class="hdr-sub">Grammar correction, system-wide</div>
    </div>
  </div>

  <div class="tabs">
    <button id="nav-home" class="tab active" hx-get="/screens/home" hx-target="#main-container">Settings</button>
    <button id="nav-about" class="tab" hx-get="/screens/about" hx-target="#main-container">About</button>
  </div>

  <div id="main-container"></div>

  <div id="ww-backdrop" onclick="wwCloseDrops()"></div>
  <div id="toast"></div>
</div>

<script>
var WW_THEMES = ${Themes.toJs()};
window.WW = { theme: ${jsonStr(themeKey)} };

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
  if (!e.detail.target) return;
  /* a swap replaces any open dropdown with closed markup, so the backdrop must go too */
  if (e.detail.target.id === 'model-card') wwCloseDrops();
  if (e.detail.target.id === 'main-container') {
    var path = e.detail.pathInfo && (e.detail.pathInfo.finalRequestPath || e.detail.pathInfo.requestPath);
    if (path && path.indexOf('/screens/') === 0) wwScreenUrl = path;
    var about = wwScreenUrl.indexOf('/screens/about') === 0;
    document.getElementById('nav-home').classList.toggle('active', !about);
    document.getElementById('nav-about').classList.toggle('active', about);
    wwCloseDrops();
    window.scrollTo(0, 0);
    wwPoll();
  }
});

/* ---- live accessibility-service status poller ---- */
function wwApplyStatus(s) {
  var dot = document.getElementById('status-dot');
  if (!dot) return;
  dot.classList.toggle('on', !!s.enabled);
  document.getElementById('status-label').textContent = s.enabled ? 'SERVICE ACTIVE' : 'SERVICE PAUSED';
  document.getElementById('status-btn').textContent = s.enabled ? 'Enabled ✓' : 'Enable';
}
function wwPoll() {
  fetch('/api/status').then(function (r) { return r.json(); }).then(wwApplyStatus).catch(function () {});
}
setInterval(wwPoll, 2000);

/* ---- API key show/hide toggle ---- */
function wwToggleKey(btn) {
  var i = document.getElementById('key-input');
  var show = i.type === 'password';
  i.type = show ? 'text' : 'password';
  btn.textContent = show ? 'HIDE' : 'SHOW';
}

/* ---- save-button feedback ---- */
document.body.addEventListener('ww-saved', function () {
  var b = document.getElementById('save-btn');
  if (!b) return;
  b.textContent = 'Saved ✓';
  b.classList.add('saved');
  clearTimeout(b._h);
  b._h = setTimeout(function () { b.textContent = 'SAVE API KEY'; b.classList.remove('saved'); }, 1700);
});

/* ---- dropdowns ---- */
function wwToggleDrop(id) {
  var d = document.getElementById(id);
  var open = d.classList.contains('open');
  wwCloseDrops();
  if (!open) {
    d.classList.add('open');
    document.getElementById('ww-backdrop').classList.add('show');
  }
}
function wwCloseDrops() {
  document.querySelectorAll('.ww-drop.open').forEach(function (d) { d.classList.remove('open'); });
  document.getElementById('ww-backdrop').classList.remove('show');
}

/* ---- full-theme switching ---- */
function wwApplyTheme(key) {
  var t = WW_THEMES[key];
  if (!t) return;
  var r = document.documentElement.style;
  for (var v in t.vars) r.setProperty(v, t.vars[v]);
  window.WW.theme = key;
  if (window.WwNative && WwNative.setStatusBarColor) WwNative.setStatusBarColor(t.statusBar);
}
function wwSetTheme(key) {
  wwApplyTheme(key);
  wwCloseDrops();
  var t = WW_THEMES[key];
  var name = document.getElementById('theme-name');
  if (name) name.textContent = t.name;
  var sw = document.getElementById('theme-swatch');
  if (sw) sw.style.background = t.swatch;
  document.querySelectorAll('#theme-drop .ww-row').forEach(function (row) {
    var on = row.getAttribute('data-k') === key;
    var c = row.querySelector('.check');
    if (c) c.style.visibility = on ? 'visible' : 'hidden';
  });
  fetch('/api/settings/theme', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'name=' + encodeURIComponent(key) });
}

document.addEventListener('DOMContentLoaded', function () {
  wwApplyTheme(window.WW.theme);
  wwGo('/screens/home');
  wwPoll();
});

/* back-button support: Android calls wwBack() */
function wwBack() {
  if (document.querySelector('.ww-drop.open')) { wwCloseDrops(); return 'handled'; }
  if (wwScreenUrl !== '/screens/home') { wwGo('/screens/home'); return 'handled'; }
  return 'exit';
}
</script>
</body>
</html>"""
    }
}

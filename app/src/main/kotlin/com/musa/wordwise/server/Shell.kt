// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

import com.musa.wordwise.network.ModelId

/**
 * The single-page shell: the htmx runtime, the persistent header with
 * Settings/About tabs, and the design-system CSS and client glue.
 *
 * The stylesheet and the script live in `assets/web/` rather than inline here.
 * That is what allows the Content-Security-Policy to drop
 * `script-src 'unsafe-inline'` — with inline handlers on every control, inline
 * script was unavoidable before — and it means the JavaScript can be
 * syntax-checked and unit tested as a real file instead of being recovered from
 * a Kotlin raw string by regex at test time.
 *
 * The selected theme's CSS variables are applied to `<html>` server-side so the
 * first paint is already correct; the rest of the palette arrives with
 * `/api/themes` when the user switches theme.
 */
object Shell {

    fun page(themeKey: String): String {
        return """<!DOCTYPE html>
<html lang="en" style="${Themes.styleVars(themeKey)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<title>WordWise</title>
<link rel="stylesheet" href="/assets/wordwise.css">
<script src="/assets/htmx.min.js"></script>
<script src="/assets/wordwise.js" defer></script>
</head>
<body data-theme="${esc(themeKey)}" data-default-model="${esc(ModelId.DEFAULT)}">

<div id="app">
  <div class="hdr">
    <div class="hdr-logo">✦</div>
    <div style="flex:1; min-width:0;">
      <div class="hdr-name">WordWise</div>
      <div class="hdr-sub">System wide grammar assistant</div>
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
</body>
</html>"""
    }
}
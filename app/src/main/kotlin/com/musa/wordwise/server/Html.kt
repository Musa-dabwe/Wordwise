// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

/** Tiny helpers for building HTML/JSON safely from Kotlin string templates. */

fun esc(s: String): String = buildString(s.length) {
    for (ch in s) when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(ch)
    }
}

/**
 * Encodes [s] as a JSON string literal, safe to embed inside an inline
 * `<script>` block.
 *
 * `<` and `>` are escaped as well as the mandatory JSON characters: a value
 * containing `</script>` would otherwise terminate the script element and turn
 * whatever follows into markup. That value has to come from preferences, which
 * a backup restore or an `adb` edit can change without passing through the
 * validator that normally rejects it.
 */
fun jsonStr(s: String): String = buildString(s.length + 2) {
    append('"')
    for (ch in s) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        '<' -> append("\\u003c")
        '>' -> append("\\u003e")
        '&' -> append("\\u0026")
        else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}

// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

/** Tiny helpers for building HTML/JSON safely from Kotlin string templates. */

/**
 * Escapes special characters for safe inclusion in HTML content.
 *
 * @param s The string to escape.
 * @return The HTML-escaped string.
 */
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
 * Escapes a string for safe inclusion as a JSON string literal.
 *
 * @param s The string to escape.
 * @return The quoted JSON string literal.
 */
fun jsonStr(s: String): String = buildString(s.length + 2) {
    append('"')
    for (ch in s) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}

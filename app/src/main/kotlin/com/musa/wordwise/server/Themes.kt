// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

/**
 * The four full pastel themes of the WordWise design. Each theme swaps the
 * complete CSS custom-property set (background, ink, accents, fields, popups)
 * rather than a single accent color.
 */
object Themes {

    data class Theme(
        val key: String,
        val name: String,
        val swatch: String,
        val statusBar: String,
        val vars: Map<String, String>
    )

    val ALL: List<Theme> = listOf(
        Theme(
            key = "peach", name = "Soft Peach",
            swatch = "linear-gradient(135deg,#f2a878,#e8895a)",
            statusBar = "#fff7f0",
            vars = mapOf(
                "--bg" to "linear-gradient(180deg,#fff7f0,#fdeadd)",
                "--ink" to "#3d2f28", "--sub" to "#a68a7b", "--lab" to "#b09383",
                "--accent" to "#f0a678", "--accent2" to "#e37f4e",
                "--accsolid" to "#e0763f", "--accsh" to "rgba(227,127,78,.7)",
                "--field" to "#fffaf5", "--soft" to "#fbe4d3", "--border" to "#f0d8c6",
                "--logo" to "linear-gradient(135deg,#f2a878,#e8895a)",
                "--popbg" to "#fffaf5", "--popbd" to "#f2ddcd",
                "--popsh" to "rgba(120,70,40,.35)"
            )
        ),
        Theme(
            key = "lav", name = "Lavender Mist",
            swatch = "linear-gradient(135deg,#bcaef0,#9d8ce0)",
            statusBar = "#f5f2fc",
            vars = mapOf(
                "--bg" to "linear-gradient(180deg,#f5f2fc,#ece6fa)",
                "--ink" to "#3a3352", "--sub" to "#948bb5", "--lab" to "#948bb5",
                "--accent" to "#b3a4e6", "--accent2" to "#9179d6",
                "--accsolid" to "#7b6ec4", "--accsh" to "rgba(145,121,214,.7)",
                "--field" to "rgba(255,255,255,.72)", "--soft" to "rgba(255,255,255,.62)", "--border" to "#e3dcf6",
                "--logo" to "linear-gradient(135deg,#bcaef0,#9d8ce0)",
                "--popbg" to "#faf8ff", "--popbd" to "#e3dcf6",
                "--popsh" to "rgba(80,60,130,.4)"
            )
        ),
        Theme(
            key = "mint", name = "Mint & Sky",
            swatch = "linear-gradient(135deg,#7fd6ab,#6fbfd6)",
            statusBar = "#eefaf3",
            vars = mapOf(
                "--bg" to "linear-gradient(180deg,#eefaf3,#e2f4f2)",
                "--ink" to "#2c4a3f", "--sub" to "#83a89a", "--lab" to "#83a89a",
                "--accent" to "#6fc79e", "--accent2" to "#5bb0c9",
                "--accsolid" to "#3f9e78", "--accsh" to "rgba(91,176,201,.7)",
                "--field" to "#ffffff", "--soft" to "#dcf3e8", "--border" to "#cfeada",
                "--logo" to "linear-gradient(135deg,#7fd6ab,#6fbfd6)",
                "--popbg" to "#ffffff", "--popbd" to "#d7efe4",
                "--popsh" to "rgba(40,110,90,.3)"
            )
        ),
        Theme(
            key = "candy", name = "Candy Pop",
            swatch = "conic-gradient(from 45deg,#ffb3c9,#ffd9a0,#b8e6b0,#a9d8f5,#d4b8f0,#ffb3c9)",
            statusBar = "#fbf5ff",
            vars = mapOf(
                "--bg" to "linear-gradient(180deg,#fbf5ff,#f1ecff)",
                "--ink" to "#302846", "--sub" to "#9a90b5", "--lab" to "#9a90b5",
                "--accent" to "#c58be0", "--accent2" to "#9d6fd6",
                "--accsolid" to "#a56fc9", "--accsh" to "rgba(157,111,214,.7)",
                "--field" to "#ffffff", "--soft" to "#f1ecff", "--border" to "#ece6fb",
                "--logo" to "conic-gradient(from 45deg,#ffb3c9,#ffd9a0,#b8e6b0,#a9d8f5,#d4b8f0,#ffb3c9)",
                "--popbg" to "#ffffff", "--popbd" to "#ece6fb",
                "--popsh" to "rgba(120,80,180,.4)"
            )
        )
    )

    val KEYS: Set<String> = ALL.map { it.key }.toSet()

    fun byKey(key: String): Theme = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** Inline style attribute value applying every var of the theme. */
    fun styleVars(key: String): String =
        byKey(key).vars.entries.joinToString(";") { (k, v) -> "$k:$v" }

    /** The themes serialized as a JS object literal for client-side switching. */
    fun toJs(): String = ALL.joinToString(",", prefix = "{", postfix = "}") { t ->
        val vars = t.vars.entries.joinToString(",") { (k, v) -> "${jsonStr(k)}:${jsonStr(v)}" }
        "${jsonStr(t.key)}:{name:${jsonStr(t.name)},swatch:${jsonStr(t.swatch)},statusBar:${jsonStr(t.statusBar)},vars:{$vars}}"
    }
}

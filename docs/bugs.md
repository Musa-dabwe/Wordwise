# Known Bugs & Fixes

A log of bugs found in WordWise, their root causes, and how they were fixed.

---

## 1. Theme and model dropdowns: selections do nothing

**Status:** Fixed
**Affects:** The pastel UI redesign (`Redesign UI with WordWise Pastel theme system`, PR #33)
**Files:** `app/src/main/kotlin/com/musa/wordwise/server/Shell.kt`

### Symptoms

- Tapping the **Model** or **Theme** selector opens the dropdown normally.
- Tapping any row inside the open dropdown just closes it — the selection is
  never applied, no toast appears, nothing is persisted.
- No errors in logcat or the WebView console; the requests simply never fire.

### Root cause: a CSS stacking-context trap

The click-away overlay `#ww-backdrop` (`position:fixed; z-index:40`) is meant
to sit *below* the open dropdown popup (`.ww-drop.open` is `z-index:50`, its
`.ww-pop` is `z-index:51`), so taps on the popup hit the rows while taps
anywhere else hit the backdrop and close the menu.

That layering silently broke because of the screen entry animation:

```css
@keyframes wwScreen { from { opacity:0; transform:translateX(10px); }
                      to   { opacity:1; transform:none; } }
.screen { animation:wwScreen .3s both; }   /* ← the bug */
```

`animation-fill-mode: both` keeps the final keyframe applied **forever** after
the animation finishes. Because that keyframe animates `transform`, the
browser keeps a computed transform on `.screen` permanently (it computes to
the identity matrix `matrix(1,0,0,1,0,0)`, not `none` — verified with
`getComputedStyle` in Chromium). Any element with a transform other than
`none` becomes a **stacking context**.

`.screen` has no `z-index`, so this stacking context sits at level `auto`
(≈ 0) in the root stacking context. Every `z-index` inside it — including the
dropdown's 50/51 — only competes *within* `.screen` and can never escape it.
The paint order therefore became:

```text
#ww-backdrop (z-index 40)          ← painted on top, catches every tap
└─ .screen stacking context (≈ 0)
   └─ .ww-pop (z-index 51)         ← trapped, painted underneath
```

The invisible backdrop covered the popup, and since its only handler is
`onclick="wwCloseDrops()"`, every tap on a dropdown row was swallowed and
just closed the menu. Both dropdowns broke identically — the model rows
(htmx `hx-post`) and the theme rows (`onclick="wwSetTheme(...)"`) never
received the tap at all.

This was confirmed by reproducing the rendered page in Chromium/Playwright:
`document.elementFromPoint(...)` over a dropdown row returned `#ww-backdrop`,
and Playwright reported `<div id="ww-backdrop"> intercepts pointer events`
when tapping the row.

### The fix

Use `backwards` instead of `both` for the fill mode:

```css
.screen { animation:wwScreen .3s backwards; }
```

The element's natural state is identical to the `to` keyframe
(`opacity:1; transform:none`), so the animation looks exactly the same — but
once it finishes, no keyframe stays applied, the computed transform returns
to `none`, the stacking context dissolves, and the popup's `z-index:50/51`
correctly beats the backdrop's `z-index:40` again.

### Follow-up bug found while verifying: lingering backdrop after model selection

Selecting a model re-renders `#model-card` via an htmx innerHTML swap. The
swapped-in markup is a *closed* dropdown, but the `show` class on
`#ww-backdrop` was set by JS and survives the swap — leaving the page dimmed,
with the next tap anywhere being eaten by the backdrop.

Fixed in the `htmx:afterSwap` listener: when the swap target is
`#model-card`, call `wwCloseDrops()` (which also hides the backdrop), same as
already happened for full screen swaps into `#main-container`.

### How to avoid this class of bug

- Never leave `animation-fill-mode: forwards`/`both` on an animation whose
  keyframes touch `transform`, `opacity`, `filter`, or `perspective` unless
  you *want* a permanent stacking context: a filled keyframe keeps the
  property applied indefinitely, with all its side effects.
- When an overlay/popup pair depends on `z-index` ordering, both elements
  must resolve their `z-index` in the **same** stacking context. Any animated
  ancestor of one of them can silently break the ordering.
- Any state toggled purely in JS (like the backdrop's `show` class) must be
  reconciled after htmx swaps replace the DOM it was coupled to.

### Verification

Reproduced and verified in Chromium (Playwright, mobile viewport + touch
events) against a faithful stub of the Ktor routes rendering the exact
`Shell.kt` template:

- Before: `elementFromPoint` over a row → `#ww-backdrop`; taps close the
  menu, no request fires.
- After: taps hit `.ww-row`; theme switch applies the CSS variables
  instantly, posts `name=<key>` to `/api/settings/theme`, and updates the
  swatch/label/checkmark; model tap posts to `/api/settings/model?m=...`,
  re-renders the card with the new selection, and shows the toast. The
  backdrop is hidden after the model swap.

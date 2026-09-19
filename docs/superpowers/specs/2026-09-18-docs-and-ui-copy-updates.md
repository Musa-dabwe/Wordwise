# Spec: Documentation & UI Copy Updates

## Problem Statement

WordWise has evolved from a Gemini-only grammar corrector to an OpenRouter-powered dual-command assistant (`?fix` + `?ask`), but the UI copy, settings screen, About page, and README still reference the old Gemini-only, `?fix`-only state.

## Solution

Update all user-facing copy across the settings screen, About page, and README to reflect the current state: OpenRouter provider, `?ask` command, and corrected branding.

## Changes

### 1. Header subtitle (Shell.kt:175)

**Current:** `Grammar correction, system-wide`
**New:** `System wide grammar assistant`

No em dashes, no comma. Matches the broader scope (not just grammar correction anymore).

### 2. Remove arrow icon from OpenRouter link (Views.kt:37)

**Current:** `Get a free key at OpenRouter →`
**New:** `Get a free key at OpenRouter`

Remove the trailing `→` arrow.

### 3. Shorten model display (Views.kt:43)

**Current:** `openrouter/free — Free Models Router (OpenRouter)`
**New:** `Openrouter - Free Models Router`

Drop the model ID prefix and provider suffix. Use a simple dash separator.

### 4. Update "How to Use" section (Views.kt:52-58)

Add `?ask` as a second command option. The section should explain both commands:

1. Type your text in any app (WhatsApp, Gmail, etc.)
2. Add `?fix` to correct grammar, or `?ask` to ask AI anything
3. WordWise replaces it with the result

### 5. Update About screen (Views.kt:85-128)

- Update the description to mention both `?fix` and `?ask`
- Update "How it works" to describe both commands
- Update "Tech Stack" AI section to say OpenRouter instead of Gemini
- Update Security section to reference OpenRouter instead of Gemini

### 6. Update README.md

Full rewrite to reflect:
- OpenRouter as the provider (not Gemini)
- `?ask` command alongside `?fix`
- Updated setup instructions (OpenRouter key, not Gemini)
- Updated architecture diagram (OpenRouter endpoint)
- Updated component descriptions (`AiClient` now has `ask()` method)
- Updated system prompts section (both `GRAMMAR_SYSTEM_PROMPT` and `ASK_SYSTEM_PROMPT`)
- Updated models section (OpenRouter free models router)
- Updated security section (OpenRouter endpoint)
- Updated limitations section

## Out of Scope

- Changing any code logic (this is copy-only)
- Adding new UI elements or layouts
- Changing the accessibility service description string (separate concern)

## Files to Modify

| File | Changes |
|------|---------|
| `app/src/main/kotlin/com/musa/wordwise/server/Shell.kt` | Header subtitle |
| `app/src/main/kotlin/com/musa/wordwise/server/Views.kt` | Arrow icon, model display, How to Use, About screen |
| `README.md` | Full documentation update |

# Spec: `?ask` Command — General-Purpose AI Instructions

## Problem Statement

WordWise currently only supports grammar correction via the `?fix` command. Users want to give AI instructions directly from any text field — such as writing content, answering questions, translating text, or generating ideas — without leaving their current app. There is no way to invoke a general-purpose AI task from within WordWise.

## Solution

Add a new trigger command `?ask` that works alongside the existing `?fix` command. When the user types a prompt followed by `?ask` in any text field, WordWise sends the prompt to the AI with a general-purpose instruction-following system prompt and replaces the field with the generated response.

**Example flow:**
```
User types: "write the zambian national anthem for me?ask"
  → WordWise strips "?ask"
  → Sends "write the zambian national anthem for me" to AI with instruct prompt
  → AI generates the anthem
  → Text field is replaced with the generated content
```

## User Stories

1. As a WordWise user, I want to type `?ask` after a prompt so that the AI follows my instruction and returns a result directly in my text field.
2. As a WordWise user, I want to use `?ask` in any app (WhatsApp, Gmail, Notes, etc.) so that I can generate content without switching apps.
3. As a WordWise user, I want the generated text to replace my original prompt so that I see only the AI's response.
4. As a WordWise user, I want to see a spinner while the AI processes my request so that I know WordWise is working.
5. As a WordWise user, I want a clear error message if my request fails so that I know what went wrong.
6. As a WordWise user, I want a warning toast if my prompt exceeds 10,000 words so that I know the request may be truncated or slow.
7. As a WordWise user, I want `?ask` to be ignored in password fields so that my prompts are not sent from sensitive inputs.
8. As a WordWise user, I want `?ask` and `?fix` to coexist so that I can use either command depending on my needs.
9. As a WordWise user, I want the spinner animation to show while the AI generates a response so that I have visual feedback during the wait.
10. As a WordWise user, I want to see a toast if I type `?ask` with no preceding text so that I know a prompt is required.
11. As a WordWise user, I want `?ask` to work even if I have no `?fix` history so that the commands are independent.
12. As a WordWise user, I want the AI response to preserve the original language of my prompt when applicable.
13. As a WordWise user, I want `?ask` to use the same API key and model as `?fix` so that I do not need separate configuration.
14. As a WordWise user, I want a dedicated system prompt for `?ask` that instructs the AI to be helpful, accurate, and concise.
15. As a WordWise user, I want to be able to cancel a running `?ask` by typing another `?ask` or `?fix` so that I am not stuck waiting.
16. As a WordWise user, I want the service to remain responsive after a long `?ask` request so that other `?fix` or `?ask` calls are not blocked.
17. As a WordWise user, I want `?ask` to respect the same rate-limit handling as `?fix` so that I get a clear "try again" message instead of a crash.
18. As a WordWise user, I want the `?ask` trigger to be case-insensitive (e.g., `?Ask`, `?ASK` should also work).
19. As a WordWise user, I want `?ask` to work in multi-line text fields so that I can write longer prompts.
20. As a WordWise user, I want the 10,000-word limit to be clearly communicated in a toast when exceeded.

## Implementation Decisions

### Command Detection

- Add a second regex pattern in `GrammarFixService`: `Regex("""\?ask\s*$""", RegexOption.IGNORE_CASE)`
  - The `\s*` tolerance handles trailing whitespace/newlines that keyboards or autocomplete may insert after "ask."
  - The same `\s*` fix should be applied to the existing `?fix` regex for consistency.
- The `onAccessibilityEvent` method checks for `?fix` first, then `?ask`. Since the patterns are anchored to different literal endings, a single string can never match both — the ordering is a clarity convention, not a conflict resolution.
- The matched trigger (including any trailing whitespace) is stripped from the end of the text, and the remaining text is used as the prompt.

### AiClient Changes

- Add a new system prompt constant `ASK_SYSTEM_PROMPT` in `AiClient`:
  ```
  "You are a helpful, knowledgeable AI assistant. " +
  "Follow the user's instructions precisely. " +
  "Return only the result with no commentary, explanations, or quotation marks. " +
  "Do not use Markdown or any other formatting — output must be plain text suitable for direct insertion into a text field. " +
  "If the request is ambiguous, give your best interpretation."
  ```
  - The plain-text constraint prevents Markdown artifacts (`**bold**`, `- bullets`, `# headers`) from appearing as raw characters in WhatsApp/Gmail/Notes.
- Add a new public method `suspend fun ask(prompt: String, apiKey: String): Result` that mirrors `fixGrammar()` but uses `ASK_SYSTEM_PROMPT` instead of `GRAMMAR_SYSTEM_PROMPT`.
- The `Result` sealed class remains unchanged — `Success`, `RateLimited`, and `Failure` all apply equally to `?ask`.
- The existing `fixGrammar` method remains untouched for backward compatibility.

### GrammarFixService Changes

- Introduce a sealed class `Command` with two variants: `Fix` and `Ask`. The `onAccessibilityEvent` method detects which trigger matched and returns the appropriate `Command`.
- The dispatch block in `onAccessibilityEvent` is refactored into a helper that takes the `Command` and routes to either `AiClient.fixGrammar()` or `AiClient.ask()`.
- A `LARGE_TEXT_THRESHOLD_ASK` constant of 10,000 words is added. Word counting is defined as `text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size` to handle punctuation-heavy and non-Latin prompts consistently.
- The 10,000-word check is dispatched to a background coroutine to avoid frame drops on the main thread for very long inputs. The warning toast is advisory only — the prompt is always sent in full, never silently truncated.
- The spinner, error handling, and text replacement logic is shared between both commands — no duplication.

### Text Replacement

- Both `?fix` and `?ask` use the same `replaceText()` method.
- For `?ask`, the original prompt is replaced entirely with the AI's generated response.
- Before calling `replaceText()` for `?ask`, the original prompt is copied to the system clipboard so the user can paste it back if the AI response is unsatisfactory.
- If the AI returns the same text as the prompt (no change), the original prompt is left in place and `toast_ask_unchanged` is shown.
- If the AI returns empty or whitespace-only text, the original prompt is left in place and `toast_ask_unchanged` is shown. This prevents a bad response from wiping the user's only text in a field.

### Error Handling

- All three `Result` variants (`Success`, `RateLimited`, `Failure`) are handled identically to `?fix`.
- A `CancellationException` from a newer `?ask` or `?fix` cancels the previous job, same as current behavior. Cross-command cancellation also works: typing `?fix` cancels a running `?ask`, and vice versa.
- The `finally` block recycles the `AccessibilityNodeInfo` regardless of outcome.

### Sensitive Fields

- The existing `isSensitiveField()` check applies to both `?fix` and `?ask`. Password fields are always ignored.

### UI / Toasts

- New string resources to add to `strings.xml`:
  - `toast_ask_processing`: "Asking AI..."
  - `toast_ask_done`: "✓ Answered!"
  - `toast_ask_no_text`: "Type a question before ?ask"
  - `toast_ask_unchanged`: "No response generated"
  - `warning_large_text_ask`: "Long prompt — response may be slow"
  - `error_ask_failed`: "Ask failed: %1$s"
- The existing `?fix` toasts remain unchanged.

## Testing Decisions

- **Unit tests for `AiClient.ask()`:** Verify the correct system prompt is used in the request payload. Mock the HTTP layer and assert the request body contains `ASK_SYSTEM_PROMPT`.
- **Unit tests for command detection:** Verify `?ask` regex matches `?ask`, `?Ask`, `?ASK`, and `?ask ` (with trailing space) at end of string. Verify `?fix` still matches its own pattern independently.
- **Unit tests for prompt extraction:** Verify `?ask` (and trailing whitespace) is stripped correctly.
- **Unit tests for sensitive field suppression:** Verify `?ask` is ignored when `isSensitiveField()` returns true (password fields).
- **Unit tests for cross-command cancellation:** Verify that a running `?ask` job is cancelled when `?fix` is triggered, and vice versa.
- **Unit tests for empty/whitespace-only response handling:** Verify that an empty `Result.Success` leaves the original prompt in place and shows `toast_ask_unchanged`.
- **Integration test for text replacement:** Verify the full flow from `onAccessibilityEvent` with `?ask` trigger to `replaceText` with the AI response.
- **Manual testing:** Type `what is 2+2?ask` in Google Keep, WhatsApp, and Gmail. Verify the field is replaced with "4" (or equivalent AI response).
- **Edge cases to test:**
  - Empty prompt: `?ask` alone → toast "Type a question before ?ask"
  - Very long prompt (>10,000 words) → warning toast, still processes in full
  - Password field → `?ask` ignored
  - Concurrent `?ask` calls → only latest completes
  - Concurrent `?fix` then `?ask` → `?ask` cancels `?fix`, only `?ask` completes
  - API key missing → error toast
  - AI returns empty string → original prompt preserved, toast shown
  - AI returns identical text to prompt → original prompt preserved, toast shown

## Out of Scope

- Editing the `?ask` system prompt from the UI (hardcoded for now).
- Multi-turn conversations or chat history within `?ask`.
- Streaming responses (the full response is returned at once).
- `?ask`-specific model selection (uses the same model as `?fix`).
- Saving or caching `?ask` responses.
- A `?ask` shortcut in the accessibility service description string (can be added later).

## Further Notes

- The `?ask` command transforms WordWise from a single-purpose grammar tool into a dual-purpose AI assistant: `?fix` for correction, `?ask` for generation.
- The architecture keeps both commands parallel and independent — adding future commands (e.g., `?translate`, `?summarize`) follows the same pattern: add a regex, a system prompt, and a method.
- The 10,000-word limit is generous for accessibility-based text injection. Most real-world prompts will be far shorter.
- The `IGNORE_CASE` flag on the `?ask` regex is a quality-of-life improvement — users may capitalize the trigger and should not be penalized.

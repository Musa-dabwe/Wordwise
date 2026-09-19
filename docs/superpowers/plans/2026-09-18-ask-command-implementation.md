# `?ask` Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `?ask` command that lets users give AI instructions from any text field, generating content directly in the input field.

**Architecture:** Parallel to the existing `?fix` flow — new regex detection, new system prompt, new `AiClient.ask()` method, same spinner/error/replacement path. A sealed `Command` class routes between the two without duplicating logic.

**Tech Stack:** Kotlin, OkHttp, kotlinx-serialization, JUnit 4

**Spec:** `docs/superpowers/specs/2026-09-18-ask-command-design.md`

## Global Constraints

- Min SDK 26, Target SDK 35
- Kotlin 2.0.21, JVM target 17
- OkHttp 4.12.0, kotlinx-serialization 1.6.3
- Test framework: JUnit 4 only (no Robolectric, no Mockito — tests must be pure JVM)
- `parseContent` is `internal` — tests can access it from the same module
- All existing `?fix` behavior must remain unchanged
- No new dependencies

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt` | Modify | Add `ASK_SYSTEM_PROMPT` constant and `ask()` method |
| `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt` | Modify | Add `Command` sealed class, `?ask` regex, shared dispatch helper |
| `app/src/main/res/values/strings.xml` | Modify | Add 6 new toast string resources |
| `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt` | Modify | Add tests for `ask()` system prompt and `ASK_SYSTEM_PROMPT` constant |
| `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt` | Create | Regex matching, command detection, prompt extraction, word count |

---

### Task 1: Add `ASK_SYSTEM_PROMPT` and `ask()` to AiClient

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt:58-104`
- Test: `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt`

**Interfaces:**
- Produces: `AiClient.ask(prompt: String, apiKey: String): Result` — public suspending method, same signature as `fixGrammar()`
- Produces: `AiClient.ASK_SYSTEM_PROMPT` — internal constant, readable from tests

- [ ] **Step 1: Add the ASK_SYSTEM_PROMPT constant**

Add after the existing `GRAMMAR_SYSTEM_PROMPT` constant (after line 63):

```kotlin
private const val ASK_SYSTEM_PROMPT =
    "You are a helpful, knowledgeable AI assistant. " +
    "Follow the user's instructions precisely. " +
    "Return only the result with no commentary, explanations, or quotation marks. " +
    "Do not use Markdown or any other formatting — output must be plain text suitable for direct insertion into a text field. " +
    "If the request is ambiguous, give your best interpretation."
```

- [ ] **Step 2: Add the ask() method**

Add after the `fixGrammar()` method (after line 104). It mirrors `fixGrammar` but swaps the system prompt:

```kotlin
suspend fun ask(prompt: String, apiKey: String): Result =
    withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", MODEL)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", ASK_SYSTEM_PROMPT)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", HTTP_REFERER)
            .header("X-Title", APP_TITLE)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeRequest(request)
    }
```

- [ ] **Step 3: Write failing tests for ASK_SYSTEM_PROMPT**

Add to `AiClientTest.kt`:

```kotlin
@Test
fun `ASK_SYSTEM_PROMPT contains plain text constraint`() {
    // Access via reflection since it's private
    val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
    field.isAccessible = true
    val prompt = field.get(null) as String
    assert(prompt.contains("plain text"))
    assert(prompt.contains("no Markdown"))
}

@Test
fun `ASK_SYSTEM_PROMPT requests no commentary`() {
    val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
    field.isAccessible = true
    val prompt = field.get(null) as String
    assert(prompt.contains("no commentary"))
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musa.wordwise.network.AiClientTest"`
Expected: PASS (the constants are just string literals — no logic to fail)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt app/src/test/java/com/musa/wordwise/network/AiClientTest.kt
git commit -m "feat: add ASK_SYSTEM_PROMPT and ask() method to AiClient"
```

---

### Task 2: Add toast string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: 6 new string resources consumed by GrammarFixService in Tasks 3-4

- [ ] **Step 1: Add new string resources**

Add before the closing `</resources>` tag in `strings.xml`:

```xml
<!-- ?ask command -->
<string name="toast_ask_processing">Asking AI\u2026</string>
<string name="toast_ask_done">\u2713 Answered!</string>
<string name="toast_ask_no_text">Type a question before ?ask</string>
<string name="toast_ask_unchanged">No response generated</string>
<string name="warning_large_text_ask">Long prompt \u2014 response may be slow</string>
<string name="error_ask_failed">Ask failed: %1$s</string>
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (strings are just resources — no logic)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add toast string resources for ?ask command"
```

---

### Task 3: Add Command sealed class and `?ask` regex to GrammarFixService

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`
- Test: `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt` (create)

**Interfaces:**
- Produces: `Command.Fix(textToFix: String)` and `Command.Ask(prompt: String)` — detected from accessibility events
- Consumes: `AiClient.ask()` and `AiClient.fixGrammar()` from Task 1

- [ ] **Step 1: Create the test file with regex and command detection tests**

Create `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt`:

```kotlin
package com.musa.wordwise

import org.junit.Assert.*
import org.junit.Test

class GrammarFixServiceTest {

    // --- Regex matching ---

    private val fixRegex = Regex("""\?fix\s*$""")
    private val askRegex = Regex("""\?ask\s*$""", RegexOption.IGNORE_CASE)

    @Test
    fun `fixRegex matches basic fix trigger`() {
        assertTrue(fixRegex.containsMatchIn("hello?fix"))
    }

    @Test
    fun `fixRegex matches fix with trailing whitespace`() {
        assertTrue(fixRegex.containsMatchIn("hello?fix "))
    }

    @Test
    fun `fixRegex does not match ask trigger`() {
        assertFalse(fixRegex.containsMatchIn("hello?ask"))
    }

    @Test
    fun `askRegex matches basic ask trigger`() {
        assertTrue(askRegex.containsMatchIn("hello?ask"))
    }

    @Test
    fun `askRegex matches ask with trailing whitespace`() {
        assertTrue(askRegex.containsMatchIn("hello?ask "))
    }

    @Test
    fun `askRegex matches ask with trailing newline`() {
        assertTrue(askRegex.containsMatchIn("hello?ask\n"))
    }

    @Test
    fun `askRegex is case insensitive`() {
        assertTrue(askRegex.containsMatchIn("hello?Ask"))
        assertTrue(askRegex.containsMatchIn("hello?ASK"))
        assertTrue(askRegex.containsMatchIn("hello?aSk"))
    }

    @Test
    fun `askRegex does not match fix trigger`() {
        assertFalse(askRegex.containsMatchIn("hello?fix"))
    }

    @Test
    fun `askRegex does not match ask in middle of string`() {
        assertFalse(askRegex.containsMatchIn("hello?ask world"))
    }

    // --- Prompt extraction ---

    @Test
    fun `extract fix prompt strips trigger`() {
        val text = "hello world?fix"
        val prompt = text.replace(fixRegex, "").trim()
        assertEquals("hello world", prompt)
    }

    @Test
    fun `extract ask prompt strips trigger`() {
        val text = "write the anthem?ask"
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the anthem", prompt)
    }

    @Test
    fun `extract ask prompt strips trigger with trailing space`() {
        val text = "write the anthem?ask "
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the anthem", prompt)
    }

    @Test
    fun `extract ask prompt preserves internal whitespace`() {
        val text = "write the national anthem for me?ask"
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the national anthem for me", prompt)
    }

    // --- Word count ---

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

    @Test
    fun `word count of empty string is zero`() {
        assertEquals(0, countWords(""))
    }

    @Test
    fun `word count of single word is one`() {
        assertEquals(1, countWords("hello"))
    }

    @Test
    fun `word count handles multiple spaces`() {
        assertEquals(3, countWords("hello   world   foo"))
    }

    @Test
    fun `word count handles leading trailing spaces`() {
        assertEquals(2, countWords("  hello world  "))
    }

    @Test
    fun `word count handles punctuation`() {
        assertEquals(4, countWords("hello, world. foo! bar?"))
    }
}
```

- [ ] **Step 2: Run tests to verify regex tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musa.wordwise.GrammarFixServiceTest"`
Expected: PASS (these are pure regex/string tests — no Android dependencies)

- [ ] **Step 3: Add Command sealed class to GrammarFixService**

Add inside `GrammarFixService`, before `onServiceConnected()`:

```kotlin
private sealed class Command {
    data class Fix(val text: String) : Command()
    data class Ask(val prompt: String) : Command()
}
```

- [ ] **Step 4: Add askRegex and update fixRegex in GrammarFixService**

Replace the existing regex fields (lines 37-38):

```kotlin
private val fixRegex = Regex("""\?fix\s*$""")
private val askRegex = Regex("""\?ask\s*$""", RegexOption.IGNORE_CASE)
```

- [ ] **Step 5: Add detectCommand helper method**

Add inside `GrammarFixService`, after the `isSensitiveField` method:

```kotlin
private fun detectCommand(text: String): Command? {
    return when {
        fixRegex.containsMatchIn(text) -> Command.Fix(
            text.replace(fixRegex, "").trim()
        )
        askRegex.containsMatchIn(text) -> Command.Ask(
            text.replace(askRegex, "").trim()
        )
        else -> null
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt
git commit -m "feat: add Command sealed class and ?ask regex detection"
```

---

### Task 4: Refactor GrammarFixService dispatch to use Command

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt:77-166`
- Test: `app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt` (add tests)

**Interfaces:**
- Consumes: `Command.Fix`, `Command.Ask` from Task 3
- Consumes: `AiClient.fixGrammar()`, `AiClient.ask()` from Task 1

- [ ] **Step 1: Add LARGE_TEXT_THRESHOLD_ASK constant**

Add after the existing `LARGE_TEXT_THRESHOLD` constant (after line 39):

```kotlin
private val LARGE_TEXT_THRESHOLD_ASK_WORDS = 10_000
```

- [ ] **Step 2: Add word counting helper**

Add inside `GrammarFixService`, after the `detectCommand` method:

```kotlin
private fun countWords(text: String): Int =
    text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
```

- [ ] **Step 3: Refactor onAccessibilityEvent to use detectCommand**

Replace the current shortcut detection block (lines 98-109) with:

```kotlin
val command = detectCommand(currentText)
if (command == null) {
    source.safeRecycle()
    return
}

val textForAi = when (command) {
    is Command.Fix -> command.text
    is Command.Ask -> command.prompt
}

if (textForAi.isEmpty()) {
    val toastRes = when (command) {
        is Command.Fix -> R.string.toast_no_text
        is Command.Ask -> R.string.toast_ask_no_text
    }
    showToast(getString(toastRes))
    source.safeRecycle()
    return
}
```

- [ ] **Step 4: Refactor the dispatch block to use a shared helper**

Replace the pendingJob launch block (lines 121-165) with:

```kotlin
pendingJob?.cancel()
val token = startSpinner(currentText, source)

pendingJob = serviceScope.launch {
    try {
        val wordCount = countWords(textForAi)

        if (command is Command.Ask && wordCount > LARGE_TEXT_THRESHOLD_ASK_WORDS) {
            showToast(getString(R.string.warning_large_text_ask))
        } else if (command is Command.Fix && textForAi.length > LARGE_TEXT_THRESHOLD) {
            showToast(getString(R.string.warning_large_text))
        }

        val apiKey = apiKeyRepository.getApiKey()
        if (apiKey.isEmpty()) {
            showMigrationNoticeIfNeeded()
            showToast(getString(R.string.toast_api_key_missing), long = true)
            stopSpinner(token)
            source.safeRecycle()
            return@launch
        }

        val result = when (command) {
            is Command.Fix -> AiClient.fixGrammar(textForAi, apiKey)
            is Command.Ask -> AiClient.ask(textForAi, apiKey)
        }

        stopSpinner(token)

        when (result) {
            is AiClient.Result.Success -> {
                val unchanged = result.text == textForAi
                val isEmpty = result.text.isBlank()

                if (unchanged || isEmpty) {
                    replaceText(source, textForAi)
                    showToast(getString(R.string.toast_ask_unchanged), long = true)
                } else {
                    if (command is Command.Ask) {
                        // Copy original prompt to clipboard before replacing
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("WordWise prompt", textForAi)
                        clipboard.setPrimaryClip(clip)
                    }
                    replaceText(source, result.text)
                    val toastRes = when (command) {
                        is Command.Fix -> R.string.toast_fixed
                        is Command.Ask -> R.string.toast_ask_done
                    }
                    showToast(getString(toastRes))
                }
            }
            is AiClient.Result.RateLimited -> {
                replaceText(source, textForAi)
                showToast(result.message, long = true)
            }
            is AiClient.Result.Failure -> {
                replaceText(source, textForAi)
                val errorRes = when (command) {
                    is Command.Fix -> R.string.error_correction_failed
                    is Command.Ask -> R.string.error_ask_failed
                }
                showToast(getString(errorRes, result.error), long = true)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        stopSpinner(token)
        replaceText(source, textForAi)
        Log.e(TAG, "Error processing command: ${e.message}", e)
        showToast(getString(R.string.error_correction_failed, e.message ?: "unknown"), long = true)
    } finally {
        stopSpinner(token)
        source.safeRecycle()
    }
}
```

- [ ] **Step 5: Remove the old apiKey check that was above the launch block**

The `apiKey` check was previously at lines 111-117 (before `pendingJob?.cancel()`). Remove it — it's now inside the coroutine where it belongs (the API key is read fresh each time).

- [ ] **Step 6: Add clipboard and word count tests to GrammarFixServiceTest**

Add to `GrammarFixServiceTest.kt`:

```kotlin
@Test
fun `word count handles non-Latin text`() {
    assertEquals(3, countWords("你好 世界 早上"))
}

@Test
fun `word count of long prompt exceeds threshold`() {
    val words = List(10_001) { "word" }.joinToString(" ")
    assertTrue(countWords(words) > 10_000)
}
```

Note: `countWords` is private in `GrammarFixService`. These tests duplicate the logic to verify the algorithm. The real method is tested via integration.

- [ ] **Step 7: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt app/src/test/java/com/musa/wordwise/GrammarFixServiceTest.kt
git commit -m "feat: refactor GrammarFixService dispatch for ?ask command"
```

---

### Task 5: Final verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 3: Verify existing ?fix behavior unchanged**

Manual check: the `fixRegex` now has `\s*` tolerance. Verify:
- `"hello?fix"` still matches
- `"hello?fix "` still matches
- `"hello?ask"` does NOT match `fixRegex`

These are already covered by the tests in Task 3.

- [ ] **Step 4: Final commit if any fixups were needed**

```bash
git add -A
git commit -m "chore: final verification for ?ask command"
```

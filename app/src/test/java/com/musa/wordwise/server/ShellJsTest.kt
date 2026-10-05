package com.musa.wordwise.server

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Runs the Node test suite for the UI script.
 *
 * The script is a real asset (`app/src/main/assets/web/wordwise.js`) rather than
 * inline in a Kotlin raw string, so these tests read the same bytes the device
 * loads. That is also why the Content Security Policy no longer needs
 * `script-src 'unsafe-inline'`.
 *
 * This wrapper only invokes Node and surfaces its output.
 */
class ShellJsTest {

    @Test
    fun `shell script js tests pass under node`() {
        // JVM working directory is expected to be `app/` (Gradle test task).
        val candidates = listOf(
            File("src/test/js/shell.test.js"),
            File("app/src/test/js/shell.test.js"),
        )
        // `?: fail(...)` would infer `Any`, because JUnit's fail() returns Unit.
        val testFile = candidates.firstOrNull { it.isFile }
        if (testFile == null) {
            fail("shell.test.js not found; cwd=${System.getProperty("user.dir")}, tried $candidates")
            return
        }

        val process = ProcessBuilder("node", "--test", testFile.path)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()

        if (exit != 0) {
            fail("Node JS tests failed (exit $exit). Output:\n$output")
        }
    }
}

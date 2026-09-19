package com.musa.wordwise

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CrossCommandCancellationTest {

    @Test
    fun pendingJob_is_cancelled_when_new_command_arrives() {
        val service = GrammarFixService()
        val scope = service.serviceScope

        val latch = CountDownLatch(1)
        var firstJobCompleted = false

        val firstJob = scope.launch {
            try {
                delay(10_000)
                firstJobCompleted = true
            } catch (e: CancellationException) {
                throw e
            }
        }

        service.pendingJob = firstJob

        service.pendingJob?.cancel()
        val secondJob = scope.launch {
            delay(10_000)
            latch.countDown()
        }
        service.pendingJob = secondJob

        Thread.sleep(200)

        assertFalse("First job should not have completed", firstJobCompleted)
        assertTrue("First job should be cancelled", firstJob.isCancelled)
        assertNotNull("pendingJob should be set", service.pendingJob)
        assertTrue("Second job should be active", secondJob.isActive)

        secondJob.cancel()
    }

    @Test
    fun fix_job_cancelled_by_ask_job_and_vice_versa() {
        val service = GrammarFixService()
        val scope = service.serviceScope

        var fixCompleted = false
        var askCompleted = false

        val fixJob = scope.launch {
            try {
                delay(10_000)
                fixCompleted = true
            } catch (e: CancellationException) {
                throw e
            }
        }
        service.pendingJob = fixJob

        service.pendingJob?.cancel()
        val askJob = scope.launch {
            try {
                delay(10_000)
                askCompleted = true
            } catch (e: CancellationException) {
                throw e
            }
        }
        service.pendingJob = askJob

        Thread.sleep(200)

        assertFalse("Fix job should not have completed", fixCompleted)
        assertTrue("Fix job should be cancelled", fixJob.isCancelled)
        assertFalse("Ask job should not have completed yet", askCompleted)
        assertTrue("Ask job should still be active", askJob.isActive)

        service.pendingJob?.cancel()
        val newFixJob = scope.launch {
            delay(10_000)
        }
        service.pendingJob = newFixJob

        Thread.sleep(200)

        assertFalse("Ask job should not have completed", askCompleted)
        assertTrue("Ask job should be cancelled", askJob.isCancelled)

        newFixJob.cancel()
    }

    @Test
    fun cancelled_job_does_not_affect_new_job() {
        val service = GrammarFixService()
        val scope = service.serviceScope

        var firstResult = ""
        var secondResult = ""

        val firstJob = scope.launch {
            try {
                delay(10_000)
                firstResult = "should not happen"
            } catch (e: CancellationException) {
                throw e
            }
        }
        service.pendingJob = firstJob
        service.pendingJob?.cancel()

        val secondJob = scope.launch {
            delay(50)
            secondResult = "completed"
        }
        service.pendingJob = secondJob

        Thread.sleep(300)

        assertTrue("First result should be empty", firstResult.isEmpty())
        assertTrue("Second result should be completed", secondResult == "completed")

        secondJob.cancel()
    }
}

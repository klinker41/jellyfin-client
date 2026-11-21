package com.makd.afinity.data.manager

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundWorkQueueTest {

    @Test
    fun `run enforces max 2 parallel jobs and executes remaining queued work afterward`() =
        runTest {
            val queue = BackgroundWorkQueue()
            val gates = List(4) { CompletableDeferred<Unit>() }
            val active = AtomicInteger(0)
            val peak = AtomicInteger(0)
            val started = mutableListOf<Int>()

            val jobs =
                List(4) { idx ->
                    async {
                        queue.run("job-$idx") {
                            started += idx
                            val now = active.incrementAndGet()
                            peak.updateAndGet { prev -> maxOf(prev, now) }
                            gates[idx].await()
                            active.decrementAndGet()
                            idx
                        }
                    }
                }

            advanceUntilIdle()
            assertEquals(listOf(0, 1), started)
            assertEquals(2, peak.get())

            gates[0].complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2), started)
            assertEquals(2, peak.get())

            gates[1].complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2, 3), started)

            gates[2].complete(Unit)
            gates[3].complete(Unit)
            assertEquals(listOf(0, 1, 2, 3), jobs.map { it.await() })
            assertEquals(2, peak.get())
        }

    @Test
    fun `nested run on same coroutine reuses BackgroundRequest without deadlocking`() = runTest {
        val queue = BackgroundWorkQueue()
        val gate = CompletableDeferred<Unit>()

        // Hold both permits: one outer job making a nested call, one unrelated job.
        val blocker = async { queue.run("blocker") { gate.await() } }
        val nested = async {
            queue.run("outer") {
                val inner = queue.run("inner") { "nested-result" }
                gate.complete(Unit)
                inner
            }
        }

        assertEquals("nested-result", nested.await())
        blocker.await()
    }

    @Test
    fun `failure inside block releases permit for subsequent work`() = runTest {
        val queue = BackgroundWorkQueue()

        repeat(2) { attempt ->
            var failed = false
            try {
                queue.run("failing-job-$attempt") { throw IllegalStateException("boom") }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
        }

        // Both permits must be released so two subsequent jobs can still execute concurrently.
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        val entered = AtomicInteger(0)
        val jobA = async {
            queue.run("recovered-a") {
                entered.incrementAndGet()
                gateA.await()
                1
            }
        }
        val jobB = async {
            queue.run("recovered-b") {
                entered.incrementAndGet()
                gateB.await()
                2
            }
        }
        advanceUntilIdle()
        assertEquals(2, entered.get())
        gateA.complete(Unit)
        gateB.complete(Unit)
        assertEquals(3, jobA.await() + jobB.await())
    }
}

package trplugins.menu.module.internal.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

class DataStorageQueueTest {

    @Test
    fun `deletion waits for an earlier slow write and read waits for deletion`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failures = mutableListOf<Throwable>()
        val stored = mutableMapOf("other-player" to "keep")
        val queue = DataStorageQueue { failures += it }
        try {
            queue.write {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                stored["player"] = "old"
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            queue.write { stored.remove("player") }
            release.countDown()
            assertEquals(mapOf("other-player" to "keep"), queue.read { stored.toMap() })
            assertTrue(failures.isEmpty())
        } finally {
            release.countDown()
            queue.close()
        }
    }

    @Test
    fun `set after delete survives and close drains pending operations`() {
        val failures = mutableListOf<Throwable>()
        val stored = mutableMapOf<String, String>()
        val queue = DataStorageQueue { failures += it }
        queue.write { stored["player"] = "old" }
        queue.write { stored.remove("player") }
        queue.write { stored["player"] = "new" }
        queue.close()
        assertEquals("new", stored["player"])
        assertTrue(failures.isEmpty())
        assertThrows(RejectedExecutionException::class.java) { queue.write {} }
    }

    @Test
    fun `failed write is reported and does not discard subsequent deletion`() {
        val failure = IllegalStateException("database unavailable")
        val failures = mutableListOf<Throwable>()
        val stored = mutableMapOf("player" to "old")
        val queue = DataStorageQueue { failures += it }
        try {
            queue.write { throw failure }
            queue.write { stored.remove("player") }
            assertTrue(queue.read { stored.isEmpty() })
            assertEquals(listOf(failure), failures)
        } finally {
            queue.close()
        }
    }
}

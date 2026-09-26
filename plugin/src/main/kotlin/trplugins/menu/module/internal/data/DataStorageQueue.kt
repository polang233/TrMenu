package trplugins.menu.module.internal.data

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Serializes player data writes and reads so a pending write cannot overtake a deletion. */
internal class DataStorageQueue(private val onFailure: (Throwable) -> Unit) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TrMenu-PlayerData").apply { isDaemon = true }
    }

    fun write(action: () -> Unit) {
        executor.execute {
            try {
                action()
            } catch (ex: Exception) {
                onFailure(ex)
            }
        }
    }

    /** Called from the asynchronous player-loading task, after all earlier writes. */
    fun <T> read(action: () -> T): T = executor.submit<T> { action() }.get()

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                onFailure(IllegalStateException("Timed out waiting for player data to be saved"))
            }
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            onFailure(ex)
        }
    }
}

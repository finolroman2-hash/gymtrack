package com.example.gymtrack.nutrition

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

internal object NetworkRetry {
    suspend fun <T> untilTimeout(
        timeoutMs: Long = 30_000L,
        gapMs: Long = 1_500L,
        retryIf: (Throwable) -> Boolean = { true },
        block: suspend () -> Result<T>,
    ): Result<T> {
        val start = System.currentTimeMillis()
        var last: Result<T> = Result.failure(IllegalStateException("timeout"))
        while (true) {
            last = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (last.isSuccess) return last
            val err = last.exceptionOrNull()
            if (err != null && !retryIf(err)) return last
            val remaining = timeoutMs - (System.currentTimeMillis() - start)
            if (remaining <= 0L) return last
            delay(min(gapMs, remaining))
        }
    }
}

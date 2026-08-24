package io.github.ameralkhorasani.outpost.core.result

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never captures a [CancellationException].
 *
 * Coroutines signal cancellation by throwing, so a plain `runCatching` around suspending
 * work turns "this coroutine was cancelled" into a `Result.failure` and lets the body
 * carry on inside a cancelled coroutine. The next suspension point then throws somewhere
 * unrelated - which is exactly how the server refresh was crashing the app.
 *
 * Use this for any `runCatching` that wraps suspending calls.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

package io.github.audiz.util

import kotlinx.coroutines.CancellationException

/**
 * Аналог [runCatching] для корутин: ловит ошибки блока в [Result],
 * но пробрасывает [CancellationException], чтобы не ломать отмену корутины.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

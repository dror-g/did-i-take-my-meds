package com.siravorona.utils

import kotlinx.coroutines.*

suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T) : T{
    return withContext(Dispatchers.Main, block)
}
suspend fun <T> withIOContext(block: suspend CoroutineScope.() -> T) : T{
    return withContext(Dispatchers.IO, block)
}

fun launchScope(
    scope: CoroutineScope,
    action: suspend () -> Unit,
    onCatch: (suspend (Exception) -> Unit)? = null,
    onFinally: (suspend () -> Unit)? = null,
    ignoreCancellationExceptions: Boolean = true
) = scope.launch {
    try {
        action()
    } catch (e: Exception) {
        if (onCatch != null) {
            onCatch.invoke(e)
        } else {
            if (e is CancellationException && ignoreCancellationExceptions) {
                // ignore
            } else {
                throw e
            }
        }
    } finally {
        onFinally?.invoke()
    }
}
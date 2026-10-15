package ru.raiffeisen.mobilecashbox.internal.gateway.outcome

import ru.raiffeisen.mobilecashbox.ConnectionFailureReason

internal sealed interface GatewayResult<out T> {
    data class Success<T>(val value: T) : GatewayResult<T>
    data class Failure(val reason: ConnectionFailureReason) : GatewayResult<Nothing>
}

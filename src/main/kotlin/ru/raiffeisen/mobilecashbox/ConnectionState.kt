package ru.raiffeisen.mobilecashbox

public sealed interface ConnectionState {
    public data object Idle : ConnectionState
    public data object Authenticating : ConnectionState
    public data object Authenticated : ConnectionState
    public data class AuthenticationError(public val reason: ConnectionFailureReason) : ConnectionState
}

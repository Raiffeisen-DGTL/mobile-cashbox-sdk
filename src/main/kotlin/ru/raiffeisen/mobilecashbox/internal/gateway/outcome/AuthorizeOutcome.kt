package ru.raiffeisen.mobilecashbox.internal.gateway.outcome

internal sealed interface AuthorizeOutcome {
    data object Authorized : AuthorizeOutcome
}

package ru.raiffeisen.mobilecashbox

public sealed interface AuthorizationCredential {
    public data class Email(public val value: String) : AuthorizationCredential
    public data class Phone(public val value: String) : AuthorizationCredential
    public data class EmailAndPhone(
        public val email: String,
        public val phone: String,
    ) : AuthorizationCredential
}

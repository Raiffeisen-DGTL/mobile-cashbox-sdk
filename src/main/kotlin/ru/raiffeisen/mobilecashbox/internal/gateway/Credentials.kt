package ru.raiffeisen.mobilecashbox.internal.gateway

import ru.raiffeisen.mobilecashbox.AuthorizationCredential

internal data class Credentials(
    val email: String?,
    val phone: String?,
) {
    val isEmpty: Boolean
        get() = email == null && phone == null
}

internal fun AuthorizationCredential.toCredentialContacts(): Credentials {
    val values = when (this) {
        is AuthorizationCredential.Email -> Credentials(email = value, phone = null)
        is AuthorizationCredential.Phone -> Credentials(email = null, phone = value)
        is AuthorizationCredential.EmailAndPhone -> Credentials(email = email, phone = phone)
    }
    return Credentials(
        email = values.email?.takeUnless(String::isBlank),
        phone = values.phone?.takeUnless(String::isBlank),
    )
}

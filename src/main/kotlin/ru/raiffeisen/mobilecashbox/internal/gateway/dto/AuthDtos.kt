package ru.raiffeisen.mobilecashbox.internal.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class OperatorCredentialsRequest(
    val email: String?,
    val phone: String?,
)

@Serializable
internal data class ProblemDetails(
    val title: String? = null,
    val code: String? = null,
    val status: Int? = null,
)

@Serializable
internal data class ExternalAppDto(
    val packageName: String,
    val action: String? = null,
    val payload: Map<String, String> = emptyMap(),
)

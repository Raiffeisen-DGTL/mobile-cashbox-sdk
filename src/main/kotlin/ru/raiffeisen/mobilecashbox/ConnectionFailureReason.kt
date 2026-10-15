package ru.raiffeisen.mobilecashbox

public sealed interface ConnectionFailureReason {
    public data object InvalidToken : ConnectionFailureReason
    public data object InvalidCredential : ConnectionFailureReason
    public data object CashierNotRegistered : ConnectionFailureReason
    public data object NoPermissionForMerchant : ConnectionFailureReason
    public data object CashierNotActivated : ConnectionFailureReason
    public data object NetworkUnavailable : ConnectionFailureReason
    public data object Timeout : ConnectionFailureReason
    public data class ServerError(public val statusCode: Int) : ConnectionFailureReason
    public data class BadRequest(public val problemCode: String?) : ConnectionFailureReason
    public data class Forbidden(public val problemCode: String?) : ConnectionFailureReason
    public data class Unknown(public val message: String) : ConnectionFailureReason
}

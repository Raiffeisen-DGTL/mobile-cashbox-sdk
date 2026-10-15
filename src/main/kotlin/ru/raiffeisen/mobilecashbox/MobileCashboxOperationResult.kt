package ru.raiffeisen.mobilecashbox

public enum class MobileCashboxOperationType {
    PAYMENT,
    REFUND,
    RECEIPT,
    LOGS,
}

public sealed interface MobileCashboxOperationResult {
    public data class Success(
        public val transaction: MobileCashboxTransaction?,
    ) : MobileCashboxOperationResult

    public data class Failure(
        public val code: String?,
        public val description: String?,
    ) : MobileCashboxOperationResult

    public data class TechnicalFailure(
        public val reason: MobileCashboxTechnicalFailureReason,
    ) : MobileCashboxOperationResult
}

public enum class MobileCashboxTechnicalFailureReason {
    RESULT_DATA_KEY_MISSING,
    RESULT_DATA_BLANK,
    INVALID_RESULT_JSON,
    PLATFORM_CANCELED,
    APPLICATION_NOT_INSTALLED,
    INITIALIZATION_FAILED,
}

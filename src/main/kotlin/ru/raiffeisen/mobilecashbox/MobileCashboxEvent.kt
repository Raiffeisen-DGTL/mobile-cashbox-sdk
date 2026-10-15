package ru.raiffeisen.mobilecashbox

public sealed interface MobileCashboxEvent {
    public data class OperationResult(
        public val operationType: MobileCashboxOperationType,
        public val result: MobileCashboxOperationResult,
    ) : MobileCashboxEvent
}

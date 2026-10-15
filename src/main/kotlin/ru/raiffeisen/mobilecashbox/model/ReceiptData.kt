package ru.raiffeisen.mobilecashbox.model

import java.time.LocalDateTime

public class ReceiptData(
    public val receiptEmail: String,
    public val transactionId: String,
    public val transactionDate: LocalDateTime,
)

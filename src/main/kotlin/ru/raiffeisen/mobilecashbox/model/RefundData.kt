package ru.raiffeisen.mobilecashbox.model

import java.math.BigDecimal
import java.time.LocalDateTime

public class RefundData(
    public val amount: BigDecimal,
    public val transactionId: String,
    public val transactionDate: LocalDateTime,
)

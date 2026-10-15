package ru.raiffeisen.mobilecashbox

import java.math.BigDecimal

/**
 * Safe transaction details returned by the external payment application.
 *
 * Provider-specific card and EMV payloads are deliberately not exposed.
 */
public data class MobileCashboxTransaction(
    public val terminalId: Int?,
    public val operationDay: Int?,
    public val transactionNumber: Int?,
    public val reversible: Boolean?,
    public val externalTerminalId: String?,
    public val dateTime: String?,
    public val operationExternalId: String?,
    public val status: String?,
    public val statusDescription: String?,
    public val instrument: String?,
    public val type: String?,
    public val paymentSystemType: String?,
    public val amount: BigDecimal?,
    public val currencyCode: String?,
    public val companyName: String?,
    public val merchant: MobileCashboxMerchant?,
)

public data class MobileCashboxMerchant(
    public val id: String?,
    public val name: String?,
    public val address: String?,
    public val phoneNumber: String?,
)

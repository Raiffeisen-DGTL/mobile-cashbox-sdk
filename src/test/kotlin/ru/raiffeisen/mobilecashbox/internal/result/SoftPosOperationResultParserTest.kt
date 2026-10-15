package ru.raiffeisen.mobilecashbox.internal.result

import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_RESULT_DATA_KEY
import ru.raiffeisen.mobilecashbox.MobileCashboxMerchant
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationResult
import ru.raiffeisen.mobilecashbox.MobileCashboxTechnicalFailureReason
import ru.raiffeisen.mobilecashbox.MobileCashboxTransaction
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SoftPosOperationResultParserTest {

    @Test
    fun `successful result exposes safe transaction details`() {
        val result = parse(
            """
            {
              "result": {"code": "0"},
              "transaction": {
                "terminalId": 17,
                "operationDay": 205,
                "transactionNumber": 42,
                "reversible": true,
                "externalTerminalId": "terminal-external",
                "dateTime": "2026-09-25T12:30:45",
                "operationExternalId": "operation-external",
                "status": "SUCCESS",
                "statusDescription": "Approved",
                "instrument": "CARD",
                "type": "PAYMENT",
                "paymentSystemType": "MIR",
                "instrumentSpecificData": {"pan": "4111111111111111"},
                "amountData": {
                  "currencyCode": "643",
                  "amount": "12345",
                  "amountExponent": "2"
                },
                "company": {"name": "Company"},
                "merchant": {
                  "merchantId": "merchant-id",
                  "name": "Merchant",
                  "address": "Merchant address",
                  "phoneNumber": "+79991234567"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(
            MobileCashboxOperationResult.Success(
                MobileCashboxTransaction(
                    terminalId = 17,
                    operationDay = 205,
                    transactionNumber = 42,
                    reversible = true,
                    externalTerminalId = "terminal-external",
                    dateTime = "2026-09-25T12:30:45",
                    operationExternalId = "operation-external",
                    status = "SUCCESS",
                    statusDescription = "Approved",
                    instrument = "CARD",
                    type = "PAYMENT",
                    paymentSystemType = "MIR",
                    amount = BigDecimal("123.45"),
                    currencyCode = "643",
                    companyName = "Company",
                    merchant = MobileCashboxMerchant(
                        id = "merchant-id",
                        name = "Merchant",
                        address = "Merchant address",
                        phoneNumber = "+79991234567",
                    ),
                )
            ),
            result,
        )
    }

    @Test
    fun `code zero is success regardless of informational transaction status`() {
        val result = assertIs<MobileCashboxOperationResult.Success>(
            parse(
                """{"result":{"code":"0"},"transaction":{"status":"DECLINED"}}"""
            )
        )

        assertEquals("DECLINED", result.transaction?.status)
    }

    @Test
    fun `numeric result and amount fields returned by release SoftPOS are accepted`() {
        val result = assertIs<MobileCashboxOperationResult.Success>(
            parse(
                """
                {
                  "transaction": {
                    "amountData": {
                      "currencyCode": 643,
                      "amount": 500,
                      "amountExponent": 2
                    },
                    "transactionNumber": 5,
                    "dateTime": "Mon Sep 28 14:47:56 GMT+03:00 2026"
                  },
                  "result": {"code": 0}
                }
                """.trimIndent()
            )
        )

        assertEquals(BigDecimal("5.00"), result.transaction?.amount)
        assertEquals("643", result.transaction?.currencyCode)
        assertEquals(5, result.transaction?.transactionNumber)
    }

    @Test
    fun `successful code does not require transaction or status`() {
        assertEquals(
            MobileCashboxOperationResult.Success(transaction = null),
            parse("""{"result":{"code":"0"}}"""),
        )

        val result = assertIs<MobileCashboxOperationResult.Success>(
            parse("""{"result":{"code":"0"},"transaction":{}}""")
        )
        assertEquals(null, result.transaction?.status)
    }

    @Test
    fun `malformed optional amount does not override successful result code`() {
        val result = assertIs<MobileCashboxOperationResult.Success>(
            parse(
                """
                {
                  "result": {"code": "0"},
                  "transaction": {
                    "status": "SUCCESS",
                    "amountData": {"amount": "not-a-number"}
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("SUCCESS", result.transaction?.status)
        assertEquals(null, result.transaction?.amount)
    }

    @Test
    fun `non-zero code is failure using errorData only`() {
        val result = parse(
            """
            {
              "result": {
                "code": "1",
                "description": "ignored result description",
                "hostResponse": {"code": "0", "description": "ignored host response"},
                "errorData": {"code": "027", "description": "cancelled by operator"}
              },
              "transaction": {"status": "SUCCESS"}
            }
            """.trimIndent()
        )

        assertEquals(
            MobileCashboxOperationResult.Failure(
                code = "027",
                description = "cancelled by operator",
            ),
            result,
        )
    }

    @Test
    fun `missing result or errorData is unknown failure`() {
        assertEquals(
            MobileCashboxOperationResult.Failure(code = null, description = null),
            parse("""{"transaction":{"status":"SUCCESS"}}"""),
        )
        assertEquals(
            MobileCashboxOperationResult.Failure(code = null, description = null),
            parse("""{"result":{"code":"999"}}"""),
        )
        assertEquals(
            MobileCashboxOperationResult.Failure(code = "NEW_VENDOR_CODE", description = "unknown"),
            parse(
                """{"result":{"code":"1","errorData":{"code":"NEW_VENDOR_CODE","description":"unknown"}}}"""
            ),
        )
    }

    @Test
    fun `result envelope failures are typed technical failures`() {
        assertTechnicalFailure(
            emptyMap(),
            MobileCashboxTechnicalFailureReason.RESULT_DATA_KEY_MISSING,
        )
        assertTechnicalFailure(
            mapOf(EXTERNAL_APP_RESULT_DATA_KEY to "  "),
            MobileCashboxTechnicalFailureReason.RESULT_DATA_BLANK,
        )
        assertTechnicalFailure(
            mapOf(EXTERNAL_APP_RESULT_DATA_KEY to "not-json"),
            MobileCashboxTechnicalFailureReason.INVALID_RESULT_JSON,
        )
        assertTechnicalFailure(
            mapOf(EXTERNAL_APP_RESULT_DATA_KEY to "[]"),
            MobileCashboxTechnicalFailureReason.INVALID_RESULT_JSON,
        )
    }

    @Test
    fun `unknown vendor fields and structured instrument data are tolerated`() {
        val result = parse(
            """
            {
              "unknownRoot": true,
              "result": {"code": "0", "newProviderField": "value"},
              "transaction": {
                "status": "SUCCESS",
                "unknownTransactionField": 42,
                "instrumentSpecificData": {"maskedPan": "****1234", "nested": {"value": 1}},
                "amountData": {"amount": "10000", "newAmountField": "value"}
              }
            }
            """.trimIndent()
        )

        val success = assertIs<MobileCashboxOperationResult.Success>(result)
        assertEquals("SUCCESS", success.transaction?.status)
        assertEquals(BigDecimal("100.00"), success.transaction?.amount)
    }

    @Test
    fun `amount is restored without floating point conversion`() {
        val amountData = SoftPosAmountDataDto(
            amount = JsonPrimitive("1234567890123456789012"),
            amountExponent = JsonPrimitive("2"),
        )

        assertEquals(
            BigDecimal("12345678901234567890.12"),
            amountData.decimalAmount(),
        )
    }

    @Test
    fun `parser does not write result payload or sensitive fields to logs`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        try {
            PrintStream(output, true, Charsets.UTF_8).use { sink ->
                System.setOut(sink)
                System.setErr(sink)
                parse(
                    """
                    {
                      "result": {"code": "1", "errorData": {"code": "007", "description": "private-description"}},
                      "transaction": {
                        "instrumentSpecificData": {"pan": "4111111111111111"},
                        "merchant": {"name": "private-merchant"}
                      }
                    }
                    """.trimIndent()
                )
            }
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        val logs = output.toString(Charsets.UTF_8)
        assertEquals("", logs)
    }

    private fun parse(payload: String): MobileCashboxOperationResult =
        SoftPosOperationResultParser.parse(mapOf(EXTERNAL_APP_RESULT_DATA_KEY to payload))

    private fun assertTechnicalFailure(
        data: Map<String, String>,
        expectedReason: MobileCashboxTechnicalFailureReason,
    ) {
        val failure = assertIs<MobileCashboxOperationResult.TechnicalFailure>(
            SoftPosOperationResultParser.parse(data)
        )
        assertEquals(expectedReason, failure.reason)
    }
}

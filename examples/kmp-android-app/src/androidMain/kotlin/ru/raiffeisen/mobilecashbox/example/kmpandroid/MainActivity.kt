package ru.raiffeisen.mobilecashbox.example.kmpandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.raiffeisen.mobilecashbox.ConnectionState
import ru.raiffeisen.mobilecashbox.MobileCashboxEvent
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationResult
import ru.raiffeisen.mobilecashbox.android.MobileCashboxExternalAppEffect
import java.time.LocalDateTime

/** Compose UI and Activity Result wiring around the shared [AuthController]. */
class MainActivity : ComponentActivity() {

    private val controller = AuthController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Keep this effect unconditional at the root so Activity Result is registered early.
            MobileCashboxExternalAppEffect(controller.lib)
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MobileCashboxScreen(controller)
                }
            }
        }
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

@Composable
private fun MobileCashboxScreen(controller: AuthController) {
    val connectionState by controller.lib.connectionState.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var paymentAmount by rememberSaveable { mutableStateOf("") }
    var refundAmount by rememberSaveable { mutableStateOf("") }
    var transactionId by rememberSaveable { mutableStateOf("") }
    var transactionDate by rememberSaveable { mutableStateOf("") }
    var receiptEmail by rememberSaveable { mutableStateOf("") }
    var receiptTransactionId by rememberSaveable { mutableStateOf("") }
    var receiptTransactionDate by rememberSaveable { mutableStateOf("") }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var operationStatus by rememberSaveable { mutableStateOf("Operation result: not started") }

    LaunchedEffect(controller) {
        // SharedFlow events are transient, so collect continuously while the Activity is composed.
        controller.lib.events.collect { event ->
            if (event is MobileCashboxEvent.OperationResult) {
                operationInProgress = false
                operationStatus = event.displayText()
            }
        }
    }

    val operationEnabled = connectionState == ConnectionState.Authenticated && !operationInProgress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mobile Cashbox KMP example", style = MaterialTheme.typography.headlineSmall)
        Text("State: $connectionState")

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Cashier email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Cashier phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Button(
            onClick = { controller.authorizeByEmailAndPhone(email, phone) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Authenticate cashier")
        }

        SectionTitle("Payment")
        OutlinedTextField(
            value = paymentAmount,
            onValueChange = { paymentAmount = it },
            label = { Text("Amount, for example 123.45") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            onClick = {
                val amount = paymentAmount.toBigDecimalOrNull()
                if (amount == null) {
                    operationStatus = "Input error: amount must be a decimal number"
                } else {
                    operationInProgress = true
                    operationStatus = "Payment is in progress"
                    controller.performPayment(amount)
                }
            },
            enabled = operationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pay")
        }

        SectionTitle("Refund")
        OutlinedTextField(
            value = refundAmount,
            onValueChange = { refundAmount = it },
            label = { Text("Amount, for example 123.45") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = transactionId,
            onValueChange = { transactionId = it },
            label = { Text("Source transaction ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = transactionDate,
            onValueChange = { transactionDate = it },
            label = { Text("Date: 2026-09-22T12:00:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                val amount = refundAmount.toBigDecimalOrNull()
                val sourceId = transactionId.trim()
                val sourceDate = runCatching { LocalDateTime.parse(transactionDate.trim()) }.getOrNull()
                when {
                    amount == null -> operationStatus = "Input error: amount must be a decimal number"
                    sourceId.isEmpty() -> operationStatus = "Input error: source transaction ID is required"
                    sourceDate == null ->
                        operationStatus = "Input error: use date format 2026-09-22T12:00:00"
                    else -> {
                        operationInProgress = true
                        operationStatus = "Refund is in progress"
                        controller.performRefund(amount, sourceId, sourceDate)
                    }
                }
            },
            enabled = operationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refund")
        }

        SectionTitle("Receipt and logs")
        OutlinedTextField(
            value = receiptEmail,
            onValueChange = { receiptEmail = it },
            label = { Text("Receipt email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = receiptTransactionId,
            onValueChange = { receiptTransactionId = it },
            label = { Text("Receipt source transaction ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = receiptTransactionDate,
            onValueChange = { receiptTransactionDate = it },
            label = { Text("Receipt date: 2026-09-29T14:30:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                val recipient = receiptEmail.trim()
                val sourceId = receiptTransactionId.trim()
                val sourceDate = runCatching {
                    LocalDateTime.parse(receiptTransactionDate.trim())
                }.getOrNull()
                when {
                    recipient.isEmpty() -> operationStatus = "Input error: receipt email is required"
                    sourceId.isEmpty() -> operationStatus = "Input error: source transaction ID is required"
                    sourceDate == null ->
                        operationStatus = "Input error: use date format 2026-09-22T12:00:00"
                    else -> {
                        operationInProgress = true
                        operationStatus = "Receipt sending is in progress"
                        controller.sendReceipt(recipient, sourceId, sourceDate)
                    }
                }
            },
            enabled = operationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send receipt")
        }
        Button(
            onClick = {
                operationInProgress = true
                operationStatus = "Log sending is in progress"
                controller.sendLogs()
            },
            enabled = operationEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send logs")
        }

        HorizontalDivider()
        Text(operationStatus)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

private fun MobileCashboxEvent.OperationResult.displayText(): String =
    when (val outcome = result) {
        is MobileCashboxOperationResult.Success ->
            "$operationType succeeded: transaction=${outcome.transaction?.transactionNumber ?: "not provided"}, " +
                "status=${outcome.transaction?.status ?: "not provided"}"
        is MobileCashboxOperationResult.Failure ->
            "$operationType failed: code=${outcome.code ?: "unknown"}, " +
                "description=${outcome.description ?: "not provided"}"
        is MobileCashboxOperationResult.TechnicalFailure ->
            "$operationType technical failure: ${outcome.reason}"
    }

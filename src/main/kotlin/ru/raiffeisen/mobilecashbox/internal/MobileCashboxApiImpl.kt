package ru.raiffeisen.mobilecashbox.internal

import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.MobileCashboxEvent
import ru.raiffeisen.mobilecashbox.ConnectionState
import ru.raiffeisen.mobilecashbox.ExternalAppResult
import ru.raiffeisen.mobilecashbox.MobileCashboxApi
import ru.raiffeisen.mobilecashbox.MobileCashboxListener
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationResult
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationType
import ru.raiffeisen.mobilecashbox.MobileCashboxTechnicalFailureReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import ru.raiffeisen.mobilecashbox.ExternalAppRequest
import ru.raiffeisen.mobilecashbox.ExternalAppResultHandler
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_RESULT_DATA_KEY
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.AuthorizeOutcome
import ru.raiffeisen.mobilecashbox.internal.gateway.BackendGateway
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.GatewayResult
import ru.raiffeisen.mobilecashbox.internal.gateway.KtorBackendGateway
import ru.raiffeisen.mobilecashbox.internal.result.SoftPosOperationResultParser
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData

internal class MobileCashboxApiImpl(
    private val token: String,
    private val gateway: BackendGateway = KtorBackendGateway(httpClient(token)),
) : MobileCashboxApi, ExternalAppResultHandler {

    // State and listener mutations are serialized by this dispatcher. Consumers must wait for an
    // operation result before starting the next operation.
    private val orchestration = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + orchestration)

    // Delivers listener callbacks on the Main thread (Android). Created lazily because on the JVM
    // target `Dispatchers.Main` is not installed unless a main-looper library is present, so we
    // must not touch it during plain construction (e.g. JVM-only tests).
    private var mainListenerScope: CoroutineScope? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<MobileCashboxEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<MobileCashboxEvent> = _events.asSharedFlow()

    private val _openExternalApp = Channel<ExternalAppRequest>(Channel.BUFFERED)
    override val openExternalApp: Flow<ExternalAppRequest> = _openExternalApp.receiveAsFlow()

    private val listeners = mutableSetOf<MobileCashboxListener>()
    private var authenticationInProgress = false
    private var pendingOperation: MobileCashboxOperationType? = null


    init {
        scope.launch { gateway.pushedEvents.collect(::emitEvent) }
    }

    override fun setUser(credential: AuthorizationCredential) {
        gateway.setCredentials(credential)
    }

    override fun auth() {
        scope.launch {
            if (authenticationInProgress) return@launch
            authenticationInProgress = true
            try {
                setState(ConnectionState.Authenticating)
                when (val result = gateway.checkUser()) {
                    is GatewayResult.Success -> when (result.value) {
                        AuthorizeOutcome.Authorized -> setState(ConnectionState.Authenticated)
                    }
                    is GatewayResult.Failure -> setState(ConnectionState.AuthenticationError(result.reason))
                }
            } finally {
                authenticationInProgress = false
            }
        }
    }

    override fun performPayment(paymentData: PaymentData) {
        performOperation(MobileCashboxOperationType.PAYMENT) {
            gateway.initializeMobileCashboxPayment(paymentData)
        }
    }

    override fun performRefund(refundData: RefundData) {
        performOperation(MobileCashboxOperationType.REFUND) {
            gateway.initializeMobileCashboxRefund(refundData)
        }
    }

    override fun sendReceipt(receiptData: ReceiptData) {
        performOperation(MobileCashboxOperationType.RECEIPT) {
            gateway.initializeMobileCashboxReceipt(receiptData)
        }
    }

    override fun sendLogs() {
        performOperation(MobileCashboxOperationType.LOGS) {
            gateway.initializeMobileCashboxLogs()
        }
    }

    override fun onExternalAppResult(result: ExternalAppResult) {
        scope.launch {
            val operationType = pendingOperation ?: return@launch
            val operationResult = when {
                EXTERNAL_APP_RESULT_DATA_KEY in result.payload ->
                    SoftPosOperationResultParser.parse(result.payload)
                result.resultCode != ANDROID_RESULT_OK ->
                    technicalFailure(MobileCashboxTechnicalFailureReason.PLATFORM_CANCELED)
                else -> SoftPosOperationResultParser.parse(result.payload)
            }

            if (finishOperation(operationType)) {
                emitOperationResult(operationType, operationResult)
            }
        }
    }

    override fun onExternalAppLaunchFailed() {
        scope.launch {
            val operationType = pendingOperation ?: return@launch
            if (finishOperation(operationType)) {
                emitOperationResult(
                    operationType,
                    technicalFailure(MobileCashboxTechnicalFailureReason.APPLICATION_NOT_INSTALLED),
                )
            }
        }
    }

    override fun addListener(listener: MobileCashboxListener) {
        scope.launch { listeners += listener }
    }

    override fun removeListener(listener: MobileCashboxListener) {
        scope.launch { listeners -= listener }
    }

    override fun close() {
        gateway.close()
        _openExternalApp.close()
        scope.cancel()
        mainListenerScope?.cancel()
    }

    private fun setState(newState: ConnectionState) {
        _connectionState.tryEmit(newState)
        dispatchToListeners { it.onStateChanged(newState) }
    }

    private fun emitEvent(event: MobileCashboxEvent) {
        _events.tryEmit(event)
        dispatchToListeners { it.onEvent(event) }
    }

    private fun performOperation(
        operationType: MobileCashboxOperationType,
        initialize: suspend () -> Map<String, String>,
    ) {
        scope.launch {
            pendingOperation = operationType
            try {
                val data = initialize()
                _openExternalApp.send(
                    ExternalAppRequest(
                        application = MobileCashboxEnvironment.externalAppPackage,
                        targetScreen = MobileCashboxEnvironment.externalAppActivity,
                        data = data,
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (finishOperation(operationType)) {
                    emitOperationResult(
                        operationType,
                        technicalFailure(MobileCashboxTechnicalFailureReason.INITIALIZATION_FAILED),
                    )
                }
            }
        }
    }

    private fun finishOperation(operationType: MobileCashboxOperationType): Boolean =
        if (pendingOperation == operationType) {
            pendingOperation = null
            true
        } else {
            false
        }

    private fun emitOperationResult(
        operationType: MobileCashboxOperationType,
        result: MobileCashboxOperationResult,
    ) {
        emitEvent(
            MobileCashboxEvent.OperationResult(
                operationType = operationType,
                result = result,
            )
        )
    }

    private fun technicalFailure(
        reason: MobileCashboxTechnicalFailureReason,
    ): MobileCashboxOperationResult = MobileCashboxOperationResult.TechnicalFailure(reason)

    private companion object {
        const val ANDROID_RESULT_OK: Int = -1
    }

    // Listener callbacks must be delivered on the Main thread so consumers can safely
    // touch UI (Android), while falling back to the orchestration thread when `Dispatchers.Main`
    // is unavailable (e.g. JVM-only tests without an Android main looper).
    private fun dispatchToListeners(
        block: (MobileCashboxListener) -> Unit,
    ) {
        val snapshot = listeners.toList()
        val main = Dispatchers.Main
        // A real Main dispatcher answers `isDispatchNeeded`; the JVM placeholder (used when no
        // Main implementation such as Android/Swing is on the classpath) throws instead. Use that
        // as a safe availability probe so JVM-only tests keep delivering callbacks inline.
        @OptIn(ExperimentalCoroutinesApi::class)
        val mainAvailable = try {
            main.isDispatchNeeded(EmptyCoroutineContext)
        } catch (e: IllegalStateException) {
            false
        }
        if (mainAvailable) {
            val listenerScope = mainListenerScope ?: CoroutineScope(SupervisorJob() + main).also {
                mainListenerScope = it
            }
            listenerScope.launch { snapshot.forEach(block) }
        } else {
            snapshot.forEach(block)
        }
    }
}

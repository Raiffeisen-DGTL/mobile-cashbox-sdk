package ru.raiffeisen.mobilecashbox

public interface MobileCashboxListener {
    public fun onStateChanged(state: ConnectionState) {}
    public fun onEvent(event: MobileCashboxEvent) {}
}

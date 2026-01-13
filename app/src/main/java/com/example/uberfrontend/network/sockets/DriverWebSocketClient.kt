package com.example.uberfrontend.network.socket

import okhttp3.*
import okio.ByteString

class DriverWebSocketClient(
    private val token: String,
    private val listener: Listener
) {

    interface Listener {
        fun onRideRequest(message: String)
        fun onRideCancelled(message: String)
        fun onConnected()
        fun onDisconnected()
    }

    private lateinit var webSocket: WebSocket

    fun connect() {
        val request = Request.Builder()
            .url("ws://10.0.2.2:9090/ws") // emulator → localhost
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = OkHttpClient()
        webSocket = client.newWebSocket(request, socketListener)
    }

    fun disconnect() {
        webSocket.close(1000, "Driver disconnected")
    }

    private val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when {
                text.contains("Ride already accepted") ->
                    listener.onRideCancelled(text)
                else ->
                    listener.onRideRequest(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            listener.onDisconnected()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            listener.onDisconnected()
        }
    }
}

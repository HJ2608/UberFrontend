package com.example.uberfrontend.data.realtime

import android.util.Log
import io.reactivex.disposables.CompositeDisposable
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.StompHeader
import ua.naiksoftware.stomp.dto.LifecycleEvent
import java.net.URLEncoder

object StompManager {
    private const val TAG = "STOMP_FLOW"

    private var stompClient: StompClient? = null
    private val disposables = CompositeDisposable()
    private var connected = false

    fun connect(baseUrl: String, token: String) {
        if (connected && stompClient != null) return

        val headers = listOf(StompHeader("Authorization", "Bearer $token"))

        val encoded = URLEncoder.encode("Bearer $token", "UTF-8")
        val wsUrl = "$baseUrl/ws?token=$encoded"

        val client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl)
        stompClient = client

        disposables.add(
            client.lifecycle().subscribe({ event ->
                Log.e(TAG, "Lifecycle event = ${event.type}")
                when (event.type) {
                    LifecycleEvent.Type.OPENED -> {
                        connected = true
                        Log.e(TAG, "STOMP Connected")
                    }
                    LifecycleEvent.Type.CLOSED -> {
                        connected = false
                        Log.e(TAG, "STOMP Closed")
                    }
                    LifecycleEvent.Type.ERROR -> {
                        connected = false
                        Log.e(TAG, "STOMP Error", event.exception)
                    }
                    else -> Unit
                }
            }, { err ->
                Log.e(TAG, "Lifecycle subscribe error", err)
            })
        )

        client.connect(headers)
    }

    fun clientOrNull(): StompClient? = stompClient

    fun clearSubscriptions() {
        // Clears topic subscriptions you added via disposables.add(...)
        disposables.clear()
        // Note: This also removes lifecycle subscription; if you want lifecycle always on, separate it.
    }

    fun disconnect() {
        try { stompClient?.disconnect() } catch (_: Exception) {}
        stompClient = null
        connected = false
        disposables.clear()
    }
}

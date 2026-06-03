package com.example.mqttapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLastSent: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button

    private val handler = Handler(Looper.getMainLooper())
    private var uptimeSeconds = 0
    private var autoSendRunnable: Runnable? = null
    private var isConnected = false   // ✅ rename for clarity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus   = findViewById(R.id.tvStatus)
        tvLastSent = findViewById(R.id.tvLastSent)
        btnConnect = findViewById(R.id.btnConnect)
        btnSend    = findViewById(R.id.btnSend)

        btnConnect.setOnClickListener { toggleConnection() }
        btnSend.setOnClickListener    { sendData() }
    }

    private fun toggleConnection() {
        if (!isConnected) {
            // ── Connect ───────────────────────────────────────────────
            tvStatus.text = "Connecting..."
            btnConnect.isEnabled = false

            MqttManager.connect(
                onConnected = {
                    runOnUiThread {
                        isConnected = true
                        tvStatus.text = "✅ Connected to Blynk"
                        btnConnect.text = "Disconnect"   // ✅ toggle label
                        btnConnect.isEnabled = true
                        btnSend.isEnabled = true
                        startAutoSend()
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        isConnected = false
                        tvStatus.text = "❌ Failed: ${err.message}"
                        btnConnect.text = "Connect to Blynk"
                        btnConnect.isEnabled = true
                        Toast.makeText(this, "Connection error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
            // ── Disconnect ────────────────────────────────────────────
            autoSendRunnable?.let { handler.removeCallbacks(it) }
            autoSendRunnable = null
            MqttManager.disconnect()

            isConnected = false
            uptimeSeconds = 0
            tvStatus.text = "⚪ Disconnected"
            tvLastSent.text = "No data sent yet"
            btnConnect.text = "Connect to Blynk"   // ✅ restore label
            btnSend.isEnabled = false
        }
    }

    private fun sendData() {
        if (!MqttManager.isConnected()) {
            Toast.makeText(this, "Not connected!", Toast.LENGTH_SHORT).show()
            return
        }

        val temperature = 25.0 + Random.nextDouble(-20.0, 20.0)
        val humidity    = 60.0 + Random.nextDouble(-20.0, 20.0)
        uptimeSeconds  += 5

        MqttManager.publishToPin(0, "%.2f".format(temperature))  // V0 — Temperature
        MqttManager.publishToPin(1, "%.2f".format(humidity))     // V1 — Humidity
        MqttManager.publishToPin(2, uptimeSeconds.toString())    // V2 — Uptime

        tvLastSent.text = "Sent → Temp: ${"%.2f".format(temperature)}°C\n" +
                " → Hum: ${"%.2f".format(humidity)}%\n" +
                " → Uptime: ${uptimeSeconds}s"
    }

    private fun startAutoSend() {
        autoSendRunnable = object : Runnable {
            override fun run() {
                sendData()
                handler.postDelayed(this, 5000)  // every 30 seconds
            }
        }
        handler.post(autoSendRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSendRunnable?.let { handler.removeCallbacks(it) }
        MqttManager.disconnect()
    }
}
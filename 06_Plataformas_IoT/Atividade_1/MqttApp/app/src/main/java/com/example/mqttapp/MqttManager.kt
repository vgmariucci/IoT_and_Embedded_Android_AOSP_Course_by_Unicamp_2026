package com.example.mqttapp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object MqttManager {

    private const val TAG = "MqttManager"

    // ── Blynk MQTT Configuration ──────────────────────────────────────────
    // This could be different regarding your project were allocated in Blynk cloud 
    // Generally, it is tcp://<blynk-cloud-location-prefix>.blynk.cloud). 
    // Here <blynk-cloud-location-prefix> = ny3
    private const val BROKER_URL = "tcp://ny3.blynk.cloud"
    
     
    private const val AUTH_TOKEN = "Your device's OAuth Token from the Blynk IoT platform"
    private const val TEMPLATE_ID   = "Your device template ID"    // ✅ from your Blynk console
    private const val CLIENT_ID  = "Any name you want to give"
    private const val USERNAME   = "device" // Keep the USERNAME = "device"
    // ─────────────────────────────────────────────────────────────────────

    // ✅ Pure Java client — no Android service required
    private var mqttClient: MqttClient? = null

    // ✅ Dedicated IO scope so all MQTT work stays off the main thread
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(onConnected: () -> Unit, onFailure: (Throwable) -> Unit) {
        scope.launch {
            try {
                // MemoryPersistence avoids file I/O permission issues
                val client = MqttClient(BROKER_URL, CLIENT_ID, MemoryPersistence())

                client.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "Connection lost: ${cause?.message}")
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        Log.d(TAG, "Message on $topic: ${message?.toString()}")
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "Delivery complete")
                    }
                })

                val options = MqttConnectOptions().apply {
                    userName           = USERNAME
                    password           = AUTH_TOKEN.toCharArray()
                    isCleanSession     = true
                    connectionTimeout  = 30
                    keepAliveInterval  = 60
                    isAutomaticReconnect = true
                }

                client.connect(options)
                mqttClient = client

                // ✅ Identify template to Blynk — required for new MQTT platform
                val infoTopic = "uplink/info"
                val infoPayload = """{"tmpl":"$TEMPLATE_ID","ver":"0.0.1","build":"1"}"""
                client.publish(
                    infoTopic,
                    MqttMessage(infoPayload.toByteArray()).apply { qos = 1 }
                )

                Log.i(TAG, "Connected and template info sent")
                onConnected()

                Log.i(TAG, "Connected to Blynk MQTT broker")
                onConnected()                       // already on IO thread; caller uses runOnUiThread

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                onFailure(e)
            }
        }
    }

    /**
     * Publish a value to a Blynk virtual pin.
     * @param pin   Virtual pin number (e.g., 0 for V0)
     * @param value Value to send (numeric or string)
     */
    fun publishToPin(pin: Int, value: String) {
        val client = mqttClient
        if (client == null || !client.isConnected) {
            Log.w(TAG, "MQTT not connected — cannot publish")
            return
        }

        scope.launch {
            try {
                val topic   = "uplink/ds/V$pin"
                val message = MqttMessage(value.toByteArray()).apply { qos = 1 }
                client.publish(topic, message)
                Log.i(TAG, "Published to $topic: $value")
            } catch (e: Exception) {
                Log.e(TAG, "Publish failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                mqttClient?.takeIf { it.isConnected }?.disconnect()
                mqttClient = null
                Log.i(TAG, "Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean = mqttClient?.isConnected == true
}
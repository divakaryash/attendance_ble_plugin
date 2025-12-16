package com.example.attendance_plugin

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleAdvertiseService : Service() {

    companion object {
        private const val TAG = "BLE_ADVERTISE"
        private const val CHANNEL_ID = "ble_advertise_channel"
        private const val NOTIFICATION_ID = 1001
        val SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var userData: String = "Unknown"
    private var enrollmentNumber: String = ""

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "✅ Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "❌ Advertising failed: $errorCode")
            when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "Data too large!")
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "Too many advertisers")
                ADVERTISE_FAILED_ALREADY_STARTED -> Log.e(TAG, "Already started")
                ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal error")
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "Feature unsupported")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            createNotificationChannel()

            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = manager?.adapter
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

            Log.d(TAG, "✅ BLE Advertise Service created")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            userData = intent?.getStringExtra("user_data") ?: "Unknown"
            enrollmentNumber = intent?.getStringExtra("enrollment_number") ?: ""

            val notification = createNotification("Broadcasting: $userData ($enrollmentNumber)")
            startForeground(NOTIFICATION_ID, notification)

            startGattServer()
            startAdvertising()

            Log.d(TAG, "✅ Service started for: $userData, Enrollment: $enrollmentNumber")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onStartCommand: ${e.message}")
        }

        return START_STICKY
    }

    private fun startAdvertising() {
        try {
            if (advertiser == null) {
                Log.e(TAG, "❌ Advertiser is null")
                return
            }

            // CRITICAL FIX: Keep advertising data SMALL
            // Put minimal data in advertising packet
            val shortData = "$enrollmentNumber"  // Just enrollment number
            val advertisingData = shortData.toByteArray(StandardCharsets.UTF_8)

            // Limit to 20 bytes max for safety
            val limitedData = if (advertisingData.size > 20) {
                advertisingData.copyOf(20)
            } else {
                advertisingData
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            // MINIMAL advertising data to avoid "data too large" error
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)  // Turn off device name to save space
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), limitedData)
                .build()

            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .build()

            advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)

            Log.d(TAG, "📡 Started advertising: $userData (Enrollment: $enrollmentNumber) - Data size: ${limitedData.size} bytes")
            Log.d(TAG, "Advertising data size: ${advertisingData.size} bytes")
            Log.d(TAG, "Advertising data content: ${String(advertisingData, StandardCharsets.UTF_8)}")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting advertising: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "🛑 Stopped advertising")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error stopping advertising: ${e.message}")
        }
    }

    private fun startGattServer() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

            val service = BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            service.addCharacteristic(characteristic)

            gattServer = manager?.openGattServer(this, object : BluetoothGattServerCallback() {
                override fun onCharacteristicReadRequest(
                    device: BluetoothDevice?,
                    requestId: Int,
                    offset: Int,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    try {
                        // Full data available via GATT read
                        val dataJson = JSONObject().apply {
                            put("userName", userData)
                            put("enrollmentNumber", enrollmentNumber)
                        }
                        val data = dataJson.toString().toByteArray(StandardCharsets.UTF_8)

                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            data
                        )

                        Log.d(TAG, "📤 Sent GATT response to ${device?.address}: $userData ($enrollmentNumber)")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error handling read request: ${e.message}")
                    }
                }

                override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                    super.onConnectionStateChange(device, status, newState)
                    try {
                        val stateStr = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
                        Log.d(TAG, "🔗 Device ${device?.address} $stateStr")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Error in connection state change: ${e.message}")
                    }
                }
            })

            gattServer?.addService(service)
            Log.d(TAG, "✅ GATT Server started")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception starting GATT: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting GATT server: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "BLE Advertising",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Peer-to-peer attendance broadcasting"
                    setShowBadge(false)
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)

                Log.d(TAG, "✅ Notification channel created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating notification channel: ${e.message}")
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Peer Attendance Active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        try {
            stopAdvertising()

            try {
                gattServer?.close()
                gattServer = null
                Log.d(TAG, "✅ GATT server closed")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error closing GATT server: ${e.message}")
            }

            Log.d(TAG, "✅ Service destroyed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onDestroy: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
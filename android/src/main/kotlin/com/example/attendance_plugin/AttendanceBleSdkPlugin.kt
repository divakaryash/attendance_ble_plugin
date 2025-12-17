package com.example.attendance_plugin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class AttendanceBleSdkPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    companion object {
        private const val TAG = "ATTENDANCE_BLE_SDK"
        private const val SCAN_DURATION = 10_000L
        val SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb")
    }

    // Flutter
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null

    private lateinit var attendanceResultChannel: EventChannel
    private var attendanceResultSink: EventChannel.EventSink? = null

    private lateinit var context: Context
    private var minPeerCount = 1
    private var rssiThreshold = -80
    private var scanDuration = 10_000L

    private var currentCourseId: String? = null
    private var currentCourseName: String? = null



    // BLE
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // Advertise
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    private var courseId: String = ""
    private var courseName: String = ""


    // Attendance state
    private var myEnrollmentNumber: String = ""
    private var myUserName: String = ""
    private val detectedPeers = mutableMapOf<String, PeerData>()
    private var scanStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    data class PeerData(
        val enrollmentNumber: String,
        val userName: String,
        val macAddress: String,
        val courseId: String,
        val courseName: String,
        var rssi: Int,
        var lastSeen: Long = System.currentTimeMillis()
    )

    // =========================
    // Plugin lifecycle
    // =========================
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        methodChannel = MethodChannel(
            binding.binaryMessenger,
            "attendance_ble_sdk"
        )
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(
            binding.binaryMessenger,
            "attendance_ble_sdk/scan"
        )
        eventChannel.setStreamHandler(this)
        attendanceResultChannel = EventChannel(
            binding.binaryMessenger,
            "attendance_ble_sdk/attendanceResult"
        )
        attendanceResultChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                attendanceResultSink = events
            }

            override fun onCancel(arguments: Any?) {
                attendanceResultSink = null
            }
        })


        initBluetooth()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        stopBleScan()
        stopBleAdvertising()
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    // =========================
    // MethodChannel
    // =========================
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startScan" -> {
                myEnrollmentNumber = call.argument("enrollmentNumber") ?: ""
                myUserName = call.argument("userName") ?: ""
                call.argument<Int>("minPeerCount")?.let { minPeerCount = it }
                call.argument<Int>("rssiThreshold")?.let { rssiThreshold = it }
                call.argument<Int>("scanDuration")?.let { scanDuration = it.toLong() }

                Log.d(TAG, "📋 Config - minPeerCount: $minPeerCount, rssiThreshold: $rssiThreshold, scanDuration: $scanDuration")

                startBleScan()
                result.success(true)
            }
            "stopScan" -> {
                stopBleScan()
                result.success(true)
            }
            "startAdvertising" -> {
                val enrollment = call.argument<String>("enrollmentNumber") ?: ""
                val name = call.argument<String>("userName") ?: ""
                startBleAdvertising(enrollment, name)
                result.success(true)
            }
            "stopAdvertising" -> {
                stopBleAdvertising()
                result.success(true)
            }
            "setCourseInfo" -> {
                courseId = call.argument<String>("courseId") ?: ""
                courseName = call.argument<String>("courseName") ?: ""

                Log.d(TAG, "📚 Course set: $courseId - $courseName")
                result.success(true)
            }

            else -> result.notImplemented()
        }
    }

    // =========================
    // EventChannel
    // =========================
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    // =========================
    // BLE init
    // =========================
    private fun initBluetooth() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    // =========================
    // BLE Advertising
    // =========================
    @SuppressLint("MissingPermission")
    private fun startBleAdvertising(
        enrollmentNumber: String,
        userName: String
    ) {
        if (isAdvertising) {
            Log.d(TAG, "Already advertising")
            return
        }

        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing BLE advertise permissions")
            return
        }

        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BLE advertiser not supported")
            return
        }

        // Store for reference
        myEnrollmentNumber = enrollmentNumber
        myUserName = userName

        // ⚠️ IMPORTANT: Only advertise enrollment number to stay under 31 byte limit
        // enrollment|courseId|courseName
        val payloadString = "$enrollmentNumber|$courseId|$courseName"
        val payload = payloadString.toByteArray(StandardCharsets.UTF_8)

// BLE limit safety (<= 20 bytes preferred)
        val limitedPayload = if (payload.size > 20) {
            payload.copyOf(20)
        } else {
            payload
        }


        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(
                ParcelUuid(SERVICE_UUID),
                limitedPayload
            )
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .build()

        bluetoothLeAdvertiser?.startAdvertising(
            settings,
            data,
            scanResponse,
            advertiseCallback
        )

        isAdvertising = true
        Log.d(TAG, "📢 BLE advertising started - Enrollment: $enrollmentNumber, Payload size: ${limitedPayload.size} bytes")
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        if (!isAdvertising) return

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "🛑 BLE advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "✅ Advertise success")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "❌ Advertise failed: $errorCode")
            when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "Data too large")
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "Too many advertisers")
                ADVERTISE_FAILED_ALREADY_STARTED -> Log.e(TAG, "Already started")
                ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal error")
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "Feature unsupported")
            }
            isAdvertising = false
        }
    }

    // =========================
    // BLE Scan
    // =========================
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing BLE permissions")
            eventSink?.error("PERMISSION_ERROR", "Missing BLE permissions", null)
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth OFF")
            eventSink?.error("BLUETOOTH_OFF", "Bluetooth is disabled", null)
            return
        }

        detectedPeers.clear()
        scanStartTime = System.currentTimeMillis()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        isScanning = true

        handler.postDelayed({
            stopBleScan()
        }, SCAN_DURATION)

        Log.d(TAG, "🔍 BLE scan started")
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            handler.removeCallbacksAndMessages(null)
            isScanning = false
            Log.d(TAG, "🛑 BLE scan stopped - Found ${detectedPeers.size} peers")
            evaluateAttendanceAndEmit()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    // =========================
    // Scan callback
    // =========================
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord ?: return
            val rssi = result.rssi

            val serviceData = record.serviceData ?: return

            for ((uuid, data) in serviceData) {
                if (uuid.uuid == SERVICE_UUID) {
                    parsePeerData(device.address, data, rssi)
                    break
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            eventSink?.error(
                "SCAN_FAILED",
                "BLE scan failed: $errorCode",
                null
            )
        }
    }

    private fun parsePeerData(
        mac: String,
        data: ByteArray,
        rssi: Int
    ) {
        try {
            val raw = String(data, StandardCharsets.UTF_8).trim()

            if (raw.isEmpty()) {
                Log.w(TAG, "Empty data from $mac")
                return
            }

            // Since we only advertise enrollment number now
            // Expected format: enrollment|courseId|courseName
            val parts = raw.split("|")

            if (parts.size < 3) {
                Log.w(TAG, "Invalid peer payload: $raw")
                return
            }

            val enrollment = parts[0]
            val peerCourseId = parts[1]
            val peerCourseName = parts[2]
            val name = "User_$enrollment"

            // Skip if it's my own device
            if (enrollment == myEnrollmentNumber) {
                Log.d(TAG, "Skipping own device: $enrollment")
                return
            }

            // Sanity check
            if (enrollment.length > 20) {
                Log.w(TAG, "Invalid enrollment length: ${enrollment.length}")
                return
            }

            val currentTime = System.currentTimeMillis()

            if (detectedPeers.containsKey(mac)) {
                // Update existing peer
                detectedPeers[mac]?.apply {
                    this.rssi = rssi
                    this.lastSeen = currentTime
                }
                Log.d(TAG, "Updated peer: $name ($enrollment) - RSSI: $rssi")
            } else {
                // New peer detected
                detectedPeers[mac] = PeerData(
                    enrollmentNumber = enrollment,
                    userName = name,
                    macAddress = mac,
                    courseId = peerCourseId,
                    courseName = peerCourseName,
                    rssi = rssi,
                    lastSeen = currentTime
                )
                Log.d(TAG, "✅ New peer: $name ($enrollment) - RSSI: $rssi")
            }

            // Send to Flutter
            eventSink?.success(
                mapOf(
                    "enrollmentNumber" to enrollment,
                    "userName" to name,
                    "mac" to mac,
                    "rssi" to rssi,
                    "timestamp" to currentTime
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }
    private fun evaluateAttendanceAndEmit() {

        val validPeers = detectedPeers.values.filter { peer ->
            peer.rssi >= rssiThreshold && peer.courseId == courseId
        }
        detectedPeers.values.forEach { peer ->
            Log.d(
                TAG,
                "Peer ${peer.enrollmentNumber} | peerCourse=${peer.courseId} | expectedCourse=$courseId | Match: ${peer.courseId == courseId}"
            )
        }

        // ✅ Debug log
        Log.d(TAG, "🔍 Evaluation - Peers found: ${detectedPeers.size}, Valid peers: ${validPeers.size}, minPeerCount: $minPeerCount, rssiThreshold: $rssiThreshold")

        val status = if (validPeers.size >= minPeerCount) "PRESENT" else "ABSENT"

        val result = mapOf(
            "courseId" to courseId,
            "courseName" to courseName,
            "enrollmentNumber" to myEnrollmentNumber,
            "userName" to myUserName,
            "date" to java.time.LocalDate.now().toString(),
            "timestamp" to System.currentTimeMillis(),
            "markedAt" to System.currentTimeMillis(),
            "status" to status,
            "peerCount" to validPeers.size,
            "nearbyPeers" to validPeers.map {
                mapOf(
                    "enrollmentNumber" to it.enrollmentNumber,
                    "userName" to it.userName,
                    "rssi" to it.rssi
                )
            }
        )

        attendanceResultSink?.success(result)

        Log.d(TAG, "📋 Attendance Result: $status (${validPeers.size}/${minPeerCount} peers, RSSI threshold: $rssiThreshold)")
    }


    // =========================
    // Permissions
    // =========================
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
import 'dart:async';
import 'package:flutter/services.dart';

class AttendanceBleSdk {
  static const MethodChannel _method = MethodChannel('attendance_ble_sdk');
  static const EventChannel _event = EventChannel('attendance_ble_sdk/scan');

  /// Start BLE scan with configuration
  static Future<void> startScan({
    required String enrollmentNumber,
    required String userName,
    int? minPeerCount,        // ✅ NEW
    int? rssiThreshold,       // ✅ NEW
    int? scanDuration,        // ✅ NEW
  }) async {
    await _method.invokeMethod('startScan', {
      'enrollmentNumber': enrollmentNumber,
      'userName': userName,
      if (minPeerCount != null) 'minPeerCount': minPeerCount,
      if (rssiThreshold != null) 'rssiThreshold': rssiThreshold,
      if (scanDuration != null) 'scanDuration': scanDuration,
    });
  }

  /// Stop BLE scan
  static Future<void> stopScan() async {
    await _method.invokeMethod('stopScan');
  }

  /// Start BLE advertising
  static Future<void> startAdvertising({
    required String enrollmentNumber,
    required String userName,
  }) async {
    await _method.invokeMethod('startAdvertising', {
      'enrollmentNumber': enrollmentNumber,
      'userName': userName,
    });
  }

  /// Stop BLE advertising
  static Future<void> stopAdvertising() async {
    await _method.invokeMethod('stopAdvertising');
  }

  /// ✅ UPDATED: Start attendance with configuration
  static Future<void> startAttendance({
    required String enrollmentNumber,
    required String userName,
    required String courseId,
    required String courseName,
    int minPeerCount = 1,
    int rssiThreshold = -80,
    int scanDuration = 10000,
  }) async {
    // ✅ 1. Set course info FIRST!
    await _method.invokeMethod("setCourseInfo", {
      "courseId": courseId,
      "courseName": courseName,
    });

    // ✅ 2. Then start advertising (now includes course info)
    await startAdvertising(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
    );

    // ✅ 3. Finally start scanning
    await startScan(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
      minPeerCount: minPeerCount,
      rssiThreshold: rssiThreshold,
      scanDuration: scanDuration,
    );
  }

  /// Stop attendance (scan + advertising)
  static Future<void> stopAttendance() async {
    await stopScan();
    await stopAdvertising();
  }

  /// Listen to scan results
  static Stream<Map<String, dynamic>> scanStream() {
    return _event.receiveBroadcastStream().map(
          (event) => Map<String, dynamic>.from(event as Map),
    );
  }

  /// Listen to attendance results
  static Stream<Map<String, dynamic>> attendanceResultStream() {
    return const EventChannel('attendance_ble_sdk/attendanceResult')
        .receiveBroadcastStream()
        .map((e) => Map<String, dynamic>.from(e));
  }
}
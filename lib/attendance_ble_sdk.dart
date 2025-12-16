import 'dart:async';
import 'package:flutter/services.dart';

class AttendanceBleSdk {
  static const MethodChannel _method =
  MethodChannel('attendance_ble_sdk');

  static const EventChannel _event =
  EventChannel('attendance_ble_sdk/scan');

  /// Start BLE scan
  /// [enrollmentNumber] - current user enrollment
  /// [userName] - current user name
  static Future<void> startScan({
    required String enrollmentNumber,
    required String userName,
  }) async {
    await _method.invokeMethod(
      'startScan',
      {
        'enrollmentNumber': enrollmentNumber,
        'userName': userName,
      },
    );
  }

  /// Stop BLE scan
  static Future<void> stopScan() async {
    await _method.invokeMethod('stopScan');
  }
  static Future<void> startAdvertising({
    required String enrollmentNumber,
    required String userName,
  }) async {
    await _method.invokeMethod(
      'startAdvertising',
      {
        'enrollmentNumber': enrollmentNumber,
        'userName': userName,
      },
    );
  }

  static Future<void> stopAdvertising() async {
    await _method.invokeMethod('stopAdvertising');
  }
  static Future<void> startAttendance({
    required String enrollmentNumber,
    required String userName,
  }) async {
    await startAdvertising(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
    );
    await startScan(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
    );
  }

  static Future<void> stopAttendance() async {
    await stopScan();
    await stopAdvertising();
  }



  /// Listen to scan results
  /// Each event is a Map sent from Android
  static Stream<Map<String, dynamic>> scanStream() {
    return _event.receiveBroadcastStream().map(
          (event) => Map<String, dynamic>.from(event as Map),
    );
  }
  static Stream<Map<String, dynamic>> attendanceResultStream() {
    return const EventChannel('attendance_ble_sdk/attendanceResult')
        .receiveBroadcastStream()
        .map((e) => Map<String, dynamic>.from(e));
  }

}

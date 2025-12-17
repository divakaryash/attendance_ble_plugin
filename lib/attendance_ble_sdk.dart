// import 'dart:async';
// import 'package:flutter/services.dart';
//
// class AttendanceBleSdk {
//   static const MethodChannel _method =
//   MethodChannel('attendance_ble_sdk');
//
//   static const EventChannel _event =
//   EventChannel('attendance_ble_sdk/scan');
//
//   /// Start BLE scan
//   /// [enrollmentNumber] - current user enrollment
//   /// [userName] - current user name
//   static Future<void> startScan({
//     required String enrollmentNumber,
//     required String userName,
//   }) async {
//     await _method.invokeMethod(
//       'startScan',
//       {
//         'enrollmentNumber': enrollmentNumber,
//         'userName': userName,
//       },
//     );
//   }
//
//   /// Stop BLE scan
//   static Future<void> stopScan() async {
//     await _method.invokeMethod('stopScan');
//   }
//   static Future<void> startAdvertising({
//     required String enrollmentNumber,
//     required String userName,
//   }) async {
//     await _method.invokeMethod(
//       'startAdvertising',
//       {
//         'enrollmentNumber': enrollmentNumber,
//         'userName': userName,
//       },
//     );
//   }
//
//   static Future<void> stopAdvertising() async {
//     await _method.invokeMethod('stopAdvertising');
//   }
//   static Future<void> startAttendance({
//     required String enrollmentNumber,
//     required String userName,
//   }) async {
//     await startAdvertising(
//       enrollmentNumber: enrollmentNumber,
//       userName: userName,
//     );
//     await startScan(
//       enrollmentNumber: enrollmentNumber,
//       userName: userName,
//     );
//   }
//
//   static Future<void> stopAttendance() async {
//     await stopScan();
//     await stopAdvertising();
//   }
//
//
//
//   /// Listen to scan results
//   /// Each event is a Map sent from Android
//   static Stream<Map<String, dynamic>> scanStream() {
//     return _event.receiveBroadcastStream().map(
//           (event) => Map<String, dynamic>.from(event as Map),
//     );
//   }
//   static Stream<Map<String, dynamic>> attendanceResultStream() {
//     return const EventChannel('attendance_ble_sdk/attendanceResult')
//         .receiveBroadcastStream()
//         .map((e) => Map<String, dynamic>.from(e));
//   }
//
// }
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
    int minPeerCount = 1,      // ✅ Default: 1 peer = PRESENT
    int rssiThreshold = -80,   // ✅ Default: -80 dBm
    int scanDuration = 10000,  // ✅ Default: 10 seconds
  }) async {
    await startAdvertising(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
    );
    await startScan(
      enrollmentNumber: enrollmentNumber,
      userName: userName,
      minPeerCount: minPeerCount,      // ✅ Pass config
      rssiThreshold: rssiThreshold,    // ✅ Pass config
      scanDuration: scanDuration,      // ✅ Pass config
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
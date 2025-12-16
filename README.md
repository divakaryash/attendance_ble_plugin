# attendance_plugin

A new Flutter plugin project.

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/to/develop-plugins),
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## Features
- BLE Scan
- BLE Advertise
- Peer discovery
- RSSI-based filtering
- Attendance evaluation
- Backend-agnostic (Firebase / REST / any API)

## Usage

```dart
AttendanceBleSdk.startAttendance(
  enrollmentNumber: "ENR001",
  userName: "Tester",
);

AttendanceBleSdk.attendanceResultStream().listen((result) {
  print(result);
});
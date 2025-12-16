import 'dart:async';
import 'package:attendance_plugin/attendance_ble_sdk.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: TestPage(),
    );
  }
}

class TestPage extends StatefulWidget {
  const TestPage({super.key});

  @override
  State<TestPage> createState() => _TestPageState();
}

class _TestPageState extends State<TestPage> {
  StreamSubscription? _scanSub;
  StreamSubscription? _attendanceSub;

  final List<Map<String, dynamic>> peers = [];

  @override
  void initState() {
    super.initState();

    // 🔹 Live scan stream (many events)
    _scanSub = AttendanceBleSdk.scanStream().listen(
          (data) {
        setState(() {
          // avoid duplicates
          final exists = peers.any(
                (p) => p['enrollmentNumber'] == data['enrollmentNumber'],
          );
          if (!exists) {
            peers.add(data);
          }
        });

        debugPrint("📡 Scan event: $data");
      },
      onError: (e) {
        debugPrint("❌ Scan error: $e");
      },
    );

    // 🔥 FINAL attendance result (ONE event)
    _attendanceSub =
        AttendanceBleSdk.attendanceResultStream().listen((result) async {
          debugPrint("✅ FINAL ATTENDANCE RESULT:");
          debugPrint(result.toString());

          // ⬇️ Upload happens HERE
          await uploadAttendance(result);
        });
  }

  @override
  void dispose() {
    _scanSub?.cancel();
    _attendanceSub?.cancel();

    AttendanceBleSdk.stopScan();
    AttendanceBleSdk.stopAdvertising();

    super.dispose();
  }

  /// 🔐 Request all BLE permissions
  Future<bool> requestBlePermissions() async {
    final statuses = await [
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
      Permission.bluetoothAdvertise,
      Permission.locationWhenInUse,
    ].request();

    return statuses.values.every((s) => s.isGranted);
  }

  Future<void> startScan() async {
    final granted = await requestBlePermissions();
    if (!granted) {
      debugPrint("❌ BLE permissions not granted");
      return;
    }

    await AttendanceBleSdk.startScan(
      enrollmentNumber: "ENR001",
      userName: "Tester",
    );
  }

  Future<void> startAdvertising() async {
    final granted = await requestBlePermissions();
    if (!granted) {
      debugPrint("❌ BLE advertise permission not granted");
      return;
    }

    await AttendanceBleSdk.startAdvertising(
      enrollmentNumber: "ENR001",
      userName: "Tester",
    );
  }

  /// 📤 Upload attendance (APP responsibility)
  Future<void> uploadAttendance(Map<String, dynamic> data) async {
    // For now just log
    debugPrint("📤 Uploading attendance to backend:");
    debugPrint(data.toString());

    // Later:
    // Firebase:
    // await FirebaseFirestore.instance.collection("attendance").add(data);

    // OR REST API:
    // await dio.post("https://api.yourserver.com/attendance", data: data);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("BLE Plugin Test")),
      body: Column(
        children: [
          const SizedBox(height: 12),

          ElevatedButton(
            onPressed: startScan,
            child: const Text("Start Scan"),
          ),

          ElevatedButton(
            onPressed: AttendanceBleSdk.stopScan,
            child: const Text("Stop Scan"),
          ),

          const SizedBox(height: 8),

          ElevatedButton(
            onPressed: startAdvertising,
            child: const Text("Start Advertising"),
          ),

          ElevatedButton(
            onPressed: AttendanceBleSdk.stopAdvertising,
            child: const Text("Stop Advertising"),
          ),

          const Divider(),

          Expanded(
            child: ListView.builder(
              itemCount: peers.length,
              itemBuilder: (context, index) {
                final p = peers[index];
                return ListTile(
                  leading: const Icon(Icons.bluetooth),
                  title: Text(p['userName'] ?? 'Unknown'),
                  subtitle: Text(
                    "${p['enrollmentNumber']} | RSSI: ${p['rssi']}",
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

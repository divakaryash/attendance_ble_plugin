import 'package:flutter_test/flutter_test.dart';
import 'package:attendance_plugin/attendance_plugin.dart';
import 'package:attendance_plugin/attendance_plugin_platform_interface.dart';
import 'package:attendance_plugin/attendance_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAttendancePluginPlatform
    with MockPlatformInterfaceMixin
    implements AttendancePluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final AttendancePluginPlatform initialPlatform = AttendancePluginPlatform.instance;

  test('$MethodChannelAttendancePlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAttendancePlugin>());
  });

  test('getPlatformVersion', () async {
    AttendancePlugin attendancePlugin = AttendancePlugin();
    MockAttendancePluginPlatform fakePlatform = MockAttendancePluginPlatform();
    AttendancePluginPlatform.instance = fakePlatform;

    expect(await attendancePlugin.getPlatformVersion(), '42');
  });
}

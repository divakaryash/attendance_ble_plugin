
import 'attendance_plugin_platform_interface.dart';

class AttendancePlugin {
  Future<String?> getPlatformVersion() {
    return AttendancePluginPlatform.instance.getPlatformVersion();
  }
}

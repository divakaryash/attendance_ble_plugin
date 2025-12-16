import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'attendance_plugin_method_channel.dart';

abstract class AttendancePluginPlatform extends PlatformInterface {
  /// Constructs a AttendancePluginPlatform.
  AttendancePluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static AttendancePluginPlatform _instance = MethodChannelAttendancePlugin();

  /// The default instance of [AttendancePluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelAttendancePlugin].
  static AttendancePluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AttendancePluginPlatform] when
  /// they register themselves.
  static set instance(AttendancePluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}

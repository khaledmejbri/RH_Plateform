import 'dart:io';

import 'package:flutter/services.dart';

const _deviceChannel = MethodChannel('rh_connect/device');

String getPlatformDefaultGatewayBaseUrl() {
  if (Platform.isAndroid) {
    return 'http://10.0.2.2:8080';
  }
  if (Platform.isIOS) {
    return 'http://127.0.0.1:8080';
  }
  return 'http://localhost:8080';
}

/// Émulateur → 10.0.2.2 ; téléphone réel → 127.0.0.1 (adb reverse) ou API_BASE_URL.
Future<String> resolveIoGatewayBaseUrl() async {
  if (Platform.isAndroid) {
    final emulator = await _isAndroidEmulator();
    if (emulator) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://127.0.0.1:8080';
  }
  if (Platform.isIOS) {
    return 'http://127.0.0.1:8080';
  }
  return 'http://localhost:8080';
}

Future<bool> _isAndroidEmulator() async {
  try {
    return await _deviceChannel.invokeMethod<bool>('isEmulator') == true;
  } on PlatformException {
    return false;
  } on MissingPluginException {
    return false;
  }
}

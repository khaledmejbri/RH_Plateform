import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureTokenStorage {
  SecureTokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _keyAccess = 'access_token';

  final FlutterSecureStorage _storage;

  Future<void> saveAccessToken(String token) =>
      _storage.write(key: _keyAccess, value: token);

  Future<String?> readAccessToken() => _storage.read(key: _keyAccess);

  Future<void> clear() => _storage.deleteAll();
}

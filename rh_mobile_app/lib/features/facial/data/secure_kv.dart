import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Magasin clé / valeur chiffré (Keystore / Keychain). Abstrait pour les tests.
abstract class SecureKv {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
  Future<List<String>> cles();
}

/// Implémentation `flutter_secure_storage` : gabarit et identifiant d'appareil ne quittent jamais l'appareil
/// (iOS : `unlocked_this_device`, hors sauvegarde et hors synchronisation iCloud ; Android : Keystore, sans sauvegarde).
class FlutterSecureKv implements SecureKv {
  FlutterSecureKv({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(
                encryptedSharedPreferences: true,
                resetOnError: true,
              ),
              iOptions: IOSOptions(accessibility: KeychainAccessibility.unlocked_this_device),
            );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);

  @override
  Future<List<String>> cles() async => (await _storage.readAll()).keys.toList();
}

/// Magasin en mémoire (tests).
class MemorySecureKv implements SecureKv {
  final Map<String, String> data = {};

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<List<String>> cles() async => data.keys.toList();
}

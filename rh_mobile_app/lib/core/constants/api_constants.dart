import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'default_api_host.dart';

/// Base URL de la gateway.
/// — URL saisie dans l'app (écran de connexion), persistée (prioritaire)
/// — `API_BASE_URL` en `--dart-define`
/// — Android émulateur → `http://10.0.2.2:8080`
/// — téléphone réel → `http://127.0.0.1:8080` (avec `adb reverse tcp:8080 tcp:8080`)
///   ou `API_BASE_URL=http://<IP-PC>:8080`
class ApiConstants {
  ApiConstants._();

  static const String _apiBaseFromDefine = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  static const _keyOverride = 'api_base_url_override';
  static const _storage = FlutterSecureStorage();

  static String _runtimeBase = getPlatformDefaultGatewayBaseUrl();

  static Future<void> init() async {
    try {
      final saved = await _storage.read(key: _keyOverride);
      if (saved != null && saved.isNotEmpty) {
        _runtimeBase = saved;
        return;
      }
    } catch (_) {}
    if (_apiBaseFromDefine.isNotEmpty) {
      _runtimeBase = _apiBaseFromDefine;
      return;
    }
    if (kIsWeb) {
      _runtimeBase = 'http://localhost:8080';
      return;
    }
    _runtimeBase = await resolveIoGatewayBaseUrl();
  }

  static String get baseUrl => _runtimeBase;

  /// Change la gateway à chaud (ex. URL de tunnel HTTPS). Vide → valeur par défaut.
  static Future<void> setBaseUrl(String url) async {
    var value = url.trim();
    while (value.endsWith('/')) {
      value = value.substring(0, value.length - 1);
    }
    if (value.isEmpty) {
      await _storage.delete(key: _keyOverride);
      await init();
      return;
    }
    if (!value.startsWith('http://') && !value.startsWith('https://')) {
      value = 'https://$value';
    }
    await _storage.write(key: _keyOverride, value: value);
    _runtimeBase = value;
  }

  static const String prefixRh = '/api/rh/v1';
  static const String prefixReferentiel = '/api/referentiel/v1';

  static String get plaintes => '$prefixRh/plaintes';
  static String get demandesAdmin => '$prefixRh/demandes-administratives';
  static String get demandesFormations => '$prefixRh/demandes-formations';
  static String get demandesDocumentsAdmin => '$prefixRh/demandes-documents-administratifs';
  static String get demandesDocuments => '$prefixRh/demandes-documents-administratifs';

  static String get presenceMobile => '$prefixRh/mobile/presence';
  static String get presenceAdmin => '$prefixRh/admin/presence';
}


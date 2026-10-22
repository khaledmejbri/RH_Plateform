import 'package:flutter/foundation.dart';

import 'default_api_host.dart';

/// Base URL de la gateway.
/// — `API_BASE_URL` en `--dart-define` (prioritaire)
/// — Android émulateur → `http://10.0.2.2:8080`
/// — téléphone réel → `http://127.0.0.1:8080` (avec `adb reverse tcp:8080 tcp:8080`)
///   ou `API_BASE_URL=http://<IP-PC>:8080`
class ApiConstants {
  ApiConstants._();

  static const String _apiBaseFromDefine = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  static String _runtimeBase = getPlatformDefaultGatewayBaseUrl();

  static Future<void> init() async {
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


import 'package:flutter/foundation.dart';

import 'default_api_host.dart';

/// Base URL de la gateway.
/// — `API_BASE_URL` en `--dart-define` (prioritaire)
/// — sinon Android émulateur → `http://10.0.2.2:8080`, iOS simulateur → `127.0.0.1:8080`
class ApiConstants {
  ApiConstants._();

  static const String _apiBaseFromDefine = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  static String get baseUrl {
    if (_apiBaseFromDefine.isNotEmpty) {
      return _apiBaseFromDefine;
    }
    if (kIsWeb) {
      return 'http://localhost:8080';
    }
    return getPlatformDefaultGatewayBaseUrl();
  }

  static const String prefixRh = '/api/rh/v1';
  static const String prefixReferentiel = '/api/referentiel/v1';

  static String get plaintes => '$prefixRh/plaintes';
  static String get demandesAdmin => '$prefixRh/demandes-administratives';
  static String get demandesFormations => '$prefixRh/demandes-formations';
  static String get demandesDocumentsAdmin => '$prefixRh/demandes-documents-administratifs';
  static String get demandesDocuments => '$prefixRh/demandes-documents-administratifs';
}

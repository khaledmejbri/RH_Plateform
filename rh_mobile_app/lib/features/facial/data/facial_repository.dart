import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'facial_models.dart';

final facialRepositoryProvider = Provider<FacialRepository>((ref) {
  return FacialRepository(ref.watch(dioProvider));
});

/// Erreur nommée du contrat (`code`, `message`, `http_status`).
class FacialApiException implements Exception {
  const FacialApiException({this.code, required this.message, this.statut, this.reseau = false});

  final String? code;
  final String message;
  final int? statut;

  /// Réseau / serveur indisponible (à rejouer), par opposition à un refus du serveur.
  final bool reseau;

  @override
  String toString() => message;

  factory FacialApiException.depuis(Object e) {
    if (e is FacialApiException) return e;
    if (e is DioException) {
      final data = e.response?.data;
      final statut = e.response?.statusCode;
      if (statut == null) {
        return const FacialApiException(
          message: 'Serveur injoignable. Vérifiez votre connexion.',
          reseau: true,
        );
      }
      String? code;
      String? message;
      if (data is Map) {
        code = data['code']?.toString();
        message = data['message']?.toString();
      }
      return FacialApiException(
        code: code,
        statut: statut,
        reseau: statut >= 500,
        message: (message != null && message.trim().isNotEmpty)
            ? message
            : (statut >= 500
                ? 'Service momentanément indisponible. Réessayez plus tard.'
                : 'Opération refusée par le serveur.'),
      );
    }
    return const FacialApiException(message: 'Une erreur est survenue.');
  }
}

/// Endpoints `/api/rh/v1/mobile/presence/verification-faciale` : aucune image ni gabarit n'est envoyé,
/// uniquement des états, des versions et des identifiants.
class FacialRepository {
  FacialRepository(this._dio);

  final Dio _dio;

  static String get _base => '${ApiConstants.presenceMobile}/verification-faciale';

  Future<T> _appel<T>(Future<T> Function() f) async {
    try {
      return await f();
    } on DioException catch (e) {
      throw FacialApiException.depuis(e);
    }
  }

  Future<EtatFacialMoi> moi(String deviceId) => _appel(() async {
        final res = await _dio.get<Map<String, dynamic>>(
          '$_base/moi',
          queryParameters: {'device_id': deviceId},
        );
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });

  Future<ConsentementCourant> consentementCourant() => _appel(() async {
        final res = await _dio.get<Map<String, dynamic>>('$_base/consentement/courant');
        return ConsentementCourant.fromJson(res.data ?? const {});
      });

  /// `201` (ou `200` si la même `idempotency_key` a déjà été appliquée).
  Future<EtatFacialMoi> enroler({
    required String deviceId,
    required String versionConsentement,
    required String modeleVersion,
    required String idempotencyKey,
  }) =>
      _appel(() async {
        final res = await _dio.post<Map<String, dynamic>>(
          '$_base/enrolement',
          data: {
            'device_id': deviceId,
            'version_consentement': versionConsentement,
            'vivacite_reussie': true,
            'modele_version': modeleVersion,
            'idempotency_key': idempotencyKey,
          },
        );
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });

  Future<EtatFacialMoi> accepterConsentement(String version) => _appel(() async {
        final res = await _dio.post<Map<String, dynamic>>(
          '$_base/consentement/acceptation',
          data: {'version_consentement': version},
        );
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });

  Future<EtatFacialMoi> refuserConsentement(String version) => _appel(() async {
        final res = await _dio.post<Map<String, dynamic>>(
          '$_base/consentement/refus',
          data: {'version_consentement': version},
        );
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });

  /// Permis même quand le réglage est INACTIVE.
  Future<EtatFacialMoi> retirerConsentement() => _appel(() async {
        final res = await _dio.post<Map<String, dynamic>>('$_base/consentement/retrait');
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });

  Future<void> signalerAppareilIncompatible({
    required String deviceId,
    required String raison,
  }) =>
      _appel(() async {
        await _dio.post<dynamic>(
          '$_base/appareil-incompatible',
          data: {'device_id': deviceId, 'raison': raison},
        );
      });

  Future<EtatFacialMoi> demanderReenrolement(String deviceId, {required bool gabaritPresent}) => _appel(() async {
        final res = await _dio.post<Map<String, dynamic>>(
          '$_base/demande-reenrolement',
          data: {'device_id': deviceId, 'gabarit_present': gabaritPresent},
        );
        return EtatFacialMoi.fromJson(res.data ?? const {});
      });
}

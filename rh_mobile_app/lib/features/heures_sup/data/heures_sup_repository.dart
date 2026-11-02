import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'heures_sup_models.dart';

final heuresSupRepositoryProvider = Provider<HeuresSupRepository>((ref) {
  return HeuresSupRepository(ref.watch(dioProvider));
});

/// Appels RH Connect `/api/rh/v1/mobile/presence/...` (identité tirée du JWT côté serveur).
/// Période par défaut des heures comptées (le serveur accepte 93 jours au plus).
const periodeParDefautJours = 30;

class HeuresSupRepository {
  HeuresSupRepository(this._dio);

  final Dio _dio;

  static String get _base => ApiConstants.presenceMobile;

  Future<List<SitePointageChoix>> sites() async {
    final res = await _dio.get<dynamic>('$_base/sites');
    return _liste(res.data, SitePointageChoix.fromJson);
  }

  Future<List<Beneficiaire>> beneficiairesPossibles() async {
    final res = await _dio.get<dynamic>('$_base/heures-sup/beneficiaires-possibles');
    return _liste(res.data, Beneficiaire.fromJson);
  }

  Future<DemandeHeuresSup> creerDemande(CreationHeuresSupRequest request) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '$_base/heures-sup/demandes',
      data: request.toJson(),
    );
    return DemandeHeuresSup.fromJson(res.data ?? const {});
  }

  Future<List<DemandeHeuresSup>> mesDemandes({String? statut}) async {
    final res = await _dio.get<dynamic>(
      '$_base/heures-sup/demandes/moi',
      queryParameters: {if (statut != null) 'statut': statut},
    );
    return _liste(res.data, DemandeHeuresSup.fromJson);
  }

  Future<DemandeHeuresSup> annulerDemande(String id) async {
    final res = await _dio.post<Map<String, dynamic>>('$_base/heures-sup/demandes/$id/annuler');
    return DemandeHeuresSup.fromJson(res.data ?? const {});
  }

  /// Heures comptées des bénéficiaires de MES demandes validées (décision 4).
  /// Le serveur exige `du` et `au` (93 jours au plus) : 30 jours glissants par défaut.
  Future<List<HeuresComptees>> heuresComptees({
    DateTime? du,
    DateTime? au,
    String? demandeId,
  }) async {
    final fin = au ?? DateTime.now();
    final debut = du ?? fin.subtract(const Duration(days: periodeParDefautJours));
    final res = await _dio.get<dynamic>(
      '$_base/heures-sup/demandes/moi/comptees',
      queryParameters: {
        'du': dateApi(debut),
        'au': dateApi(fin),
        if (demandeId != null) 'demande_id': demandeId,
      },
    );
    return _liste(res.data, HeuresComptees.fromJson);
  }

  /// Mes propres heures sup : validées à venir et heures comptées (aucun paramètre d'identité).
  Future<MesHeuresSup> mesHeuresSup({DateTime? du, DateTime? au}) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '$_base/heures-sup/moi',
      queryParameters: {
        if (du != null) 'du': dateApi(du),
        if (au != null) 'au': dateApi(au),
      },
    );
    return MesHeuresSup.fromJson(res.data ?? const {});
  }

  List<T> _liste<T>(dynamic data, T Function(Map<String, dynamic>) fromJson) {
    final items = data is List
        ? data
        : (data is Map ? (data['content'] ?? data['items'] ?? data['data']) : null);
    if (items is! List) return <T>[];
    return items.whereType<Map>().map((e) => fromJson(Map<String, dynamic>.from(e))).toList();
  }
}

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'dossier_models.dart';

final dossierRepositoryProvider = Provider<DossierRepository>((ref) {
  return DossierRepository(ref.watch(dioProvider));
});

/// Hiérarchie, annuaire et équipe de l'appelant (référentiel, A7 à A10). L'identité vient du jeton.
class DossierRepository {
  DossierRepository(this._dio);

  final Dio _dio;

  static String get _collab => '${ApiConstants.prefixReferentiel}/collaborateurs/moi';

  Future<Hierarchie> maHierarchie() async {
    final res = await _dio.get<dynamic>('$_collab/hierarchie');
    final data = res.data;
    return data is Map ? Hierarchie.fromJson(Map<String, dynamic>.from(data)) : const Hierarchie();
  }

  Future<FicheAnnuaire> annuaire(String collaborateurId) async {
    final res = await _dio.get<dynamic>('${ApiConstants.prefixReferentiel}/annuaire/$collaborateurId');
    final data = res.data;
    if (data is! Map) throw StateError('Réponse annuaire inattendue');
    return FicheAnnuaire.fromJson(Map<String, dynamic>.from(data));
  }

  /// Collaborateurs dont l'appelant est le valideur (vide s'il n'en a aucun).
  Future<List<PersonneResume>> monEquipe() async {
    final res = await _dio.get<dynamic>('$_collab/equipe');
    final data = res.data;
    if (data is! List) return const [];
    return data
        .whereType<Map>()
        .map((e) => PersonneResume.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// Fiche d'équipe (A10) ; 403 `HORS_PERIMETRE` si l'appelant n'est pas le valideur.
  Future<FicheEquipe> ficheEquipe(String collaborateurId) async {
    final res = await _dio.get<dynamic>('$_collab/equipe/$collaborateurId');
    final data = res.data;
    if (data is! Map) throw StateError('Réponse fiche d’équipe inattendue');
    return FicheEquipe.fromJson(Map<String, dynamic>.from(data));
  }
}

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/format/formats.dart';
import '../../../core/network/api_client.dart';
import 'workflow_models.dart';

final workflowRepositoryProvider = Provider<WorkflowRepository>((ref) {
  return WorkflowRepository(ref.watch(dioProvider));
});

/// Client des endpoints utilisateur `/api/rh/v1/workflow/**` et compléments associés.
class WorkflowRepository {
  WorkflowRepository(this._dio);

  final Dio _dio;

  /// Liste des types ouverts à la saisie (`D-W03`), avec repli `null` (« tous ouverts ») si erreur.
  Future<List<TypeOuvert>?> typesOuverts() async {
    try {
      final resp = await _dio.get('${ApiConstants.workflow}/types-ouverts');
      final data = resp.data;
      if (data is! List) return null;
      return data
          .whereType<Map>()
          .map((e) => TypeOuvert.fromJson(Map<String, dynamic>.from(e)))
          .toList();
    } catch (_) {
      return null;
    }
  }

  Future<CompteurTaches> compteurMesTaches() async {
    final resp = await _dio.get('${ApiConstants.workflow}/taches/moi/compteur');
    return CompteurTaches.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  /// Tâches de l'utilisateur connecté (`statut` = `'A_TRAITER'` ou `'TRAITEE'`).
  Future<PageResponse<TacheListeItem>> mesTaches({
    String statut = 'A_TRAITER',
    int page = 0,
    int taille = 20,
  }) async {
    final resp = await _dio.get(
      '${ApiConstants.workflow}/taches/moi',
      queryParameters: {
        'statut': statut,
        'page': page,
        'taille': taille,
      },
    );
    return PageResponse.fromJson(
      Map<String, dynamic>.from(resp.data as Map),
      TacheListeItem.fromJson,
    );
  }

  Future<TacheDetail> detailTache(String tacheId) async {
    final resp = await _dio.get('${ApiConstants.workflow}/taches/$tacheId');
    return TacheDetail.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  /// Soumet une décision (`APPROUVER`, `REFUSER`, `RENVOYER`, `FAIT`).
  ///
  /// Gère `200 OK` et `202 Accepted` (`DECISION_EN_COURS`).
  Future<DecisionResult> deciderTache({
    required String tacheId,
    required String action,
    String? commentaire,
    required int version,
    required String cleIdempotence,
  }) async {
    final motif = commentaire?.trim();
    final resp = await _dio.post(
      '${ApiConstants.workflow}/taches/$tacheId/decision',
      data: {
        'action': action,
        if (motif != null && motif.isNotEmpty) 'commentaire': motif,
        'version': version,
        'cle_idempotence': cleIdempotence,
      },
    );
    final data = resp.data;
    if (resp.statusCode == 202) {
      return const DecisionResult.enCoursResult();
    }
    if (data is Map) {
      return DecisionResult.fromJson(Map<String, dynamic>.from(data));
    }
    return const DecisionResult(enCours: false);
  }

  /// Répond à une tâche de type `COMPLEMENT` (`D-W8-04` / `D-W5-12`).
  ///
  /// Appelle l'endpoint `/complement` du service propriétaire selon `objetType`,
  /// ou la décision `FAIT` sur la tâche si le type n'a pas d'endpoint dédié.
  Future<void> envoyerComplement({
    required String objetType,
    required String objetId,
    required String tacheId,
    required String commentaire,
    required int version,
    required String cleIdempotence,
  }) async {
    final type = objetType.trim().toUpperCase();
    final texte = commentaire.trim();

    switch (type) {
      case 'CONGE':
      case 'AUTORISATION_SORTIE':
      case 'ORDRE_MISSION':
      case 'DEMANDE':
        await _dio.post(
          '${ApiConstants.demandesAdmin}/$objetId/complement',
          data: {'commentaire': texte},
        );
        return;
      case 'DOCUMENT':
        await _dio.post(
          '${ApiConstants.demandesDocumentsAdmin}/$objetId/complement',
          data: {'commentaire': texte},
        );
        return;
      case 'FORMATION':
        await _dio.post(
          '${ApiConstants.demandesFormations}/$objetId/complement',
          data: {'commentaire': texte},
        );
        return;
      default:
        await deciderTache(
          tacheId: tacheId,
          action: 'FAIT',
          commentaire: texte,
          version: version,
          cleIdempotence: cleIdempotence,
        );
    }
  }

  Future<SuiviInstance> suiviParObjet(String objetType, String objetId) async {
    final resp = await _dio.get('${ApiConstants.workflow}/objets/$objetType/$objetId/suivi');
    return SuiviInstance.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  Future<List<DelegationItem>> delegationsMoi({
    String role = 'DELEGANT',
    bool? actives,
  }) async {
    final resp = await _dio.get(
      '${ApiConstants.workflow}/delegations/moi',
      queryParameters: {
        'role': role,
        if (actives != null) 'actives': actives,
      },
    );
    final data = resp.data;
    if (data is! List) return const [];
    return data
        .whereType<Map>()
        .map((e) => DelegationItem.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  Future<DelegationItem> creerDelegation({
    required String delegueId,
    required DateTime du,
    required DateTime au,
    List<String>? objetTypes,
    String? motif,
  }) async {
    final motifTrim = motif?.trim();
    final resp = await _dio.post(
      '${ApiConstants.workflow}/delegations/moi',
      data: {
        'delegue_id': delegueId,
        'du': formatDateApi(du),
        'au': formatDateApi(au),
        if (objetTypes != null && objetTypes.isNotEmpty) 'objet_types': objetTypes,
        if (motifTrim != null && motifTrim.isNotEmpty) 'motif': motifTrim,
      },
    );
    return DelegationItem.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  Future<void> annulerDelegation(String delegationId) async {
    await _dio.delete('${ApiConstants.workflow}/delegations/moi/$delegationId');
  }
}

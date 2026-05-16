import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'formation_models.dart';

final formationRepositoryProvider = Provider<FormationRepository>((ref) {
  return FormationRepository(ref.watch(dioProvider));
});

class FormationRepository {
  FormationRepository(this._dio);

  final Dio _dio;

  Future<List<FormationItem>> mesDemandes() async {
    final res = await _dio.get<List<dynamic>>(ApiConstants.demandesFormations);
    final list = res.data ?? [];
    return list
        .map((e) => FormationItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<List<FormationItem>> mesDemandesEnTantQueRo() async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesFormations}/mes-demandes-ro');
    final list = res.data ?? [];
    return list
        .map((e) => FormationItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<List<FormationItem>> formationsOuJeSuisCible() async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesFormations}/formations-cible');
    final list = res.data ?? [];
    return list
        .map((e) => FormationItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<List<WorkflowHistoryItem>> obtenirHistorique(String id) async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesFormations}/$id/historique');
    final list = res.data ?? [];
    return list
        .map((e) => WorkflowHistoryItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<List<FormationUniteCible>> unitesCibles() async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesFormations}/cibles/unites');
    final list = res.data ?? [];
    return list
        .map((e) => FormationUniteCible.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<List<FormationCollaborateurCible>> collaborateursCibles() async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesFormations}/cibles/collaborateurs');
    final list = res.data ?? [];
    return list
        .map((e) => FormationCollaborateurCible.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<void> creer({
    required String typeFormation,
    required String organisme,
    required int dureeHeures,
    required num coutEstime,
    required String objectifsPedagogiques,
    required String justification,
    String? uniteCibleIdentifiant,
    List<String>? collaborateursCiblesIdentifiants,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.demandesFormations,
      data: {
        'type_formation': typeFormation,
        'organisme': organisme,
        'duree_heures': dureeHeures,
        'cout_estime': coutEstime,
        'objectifs_pedagogiques': objectifsPedagogiques,
        'justification': justification,
        if (uniteCibleIdentifiant != null) 'unite_cible_identifiant': uniteCibleIdentifiant,
        if (collaborateursCiblesIdentifiants != null)
          'collaborateurs_cibles_identifiants': collaborateursCiblesIdentifiants,
      },
    );
  }

  Future<void> annuler(String id) async {
    await _dio.post<Map<String, dynamic>>('${ApiConstants.demandesFormations}/$id/annuler');
  }

  /// Accepter une invitation à une formation
  Future<void> accepterInvitation(String id) async {
    await _dio.post<Map<String, dynamic>>('${ApiConstants.demandesFormations}/$id/accepter-invitation');
  }

  /// Décliner une invitation à une formation
  Future<void> declinerInvitation(String id, {String? motif}) async {
    await _dio.post<Map<String, dynamic>>(
      '${ApiConstants.demandesFormations}/$id/decliner-invitation',
      data: motif != null ? {'motif': motif} : {},
    );
  }
}

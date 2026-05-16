import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:io';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'demande_admin_models.dart';

final demandeAdminRepositoryProvider = Provider<DemandeAdminRepository>((ref) {
  return DemandeAdminRepository(ref.watch(dioProvider));
});

class DemandeAdminRepository {
  DemandeAdminRepository(this._dio);

  final Dio _dio;

  Future<List<DemandeAdminItem>> mesDemandes({String? typeDemande, String? couvreJour, String? statut}) async {
    final q = <String, dynamic>{};
    if (typeDemande != null) q['type_demande'] = typeDemande;
    if (couvreJour != null) q['couvre_jour'] = couvreJour;
    if (statut != null) q['statut'] = statut;
    final res = await _dio.get<List<dynamic>>(ApiConstants.demandesAdmin, queryParameters: q.isEmpty ? null : q);
    final list = res.data ?? [];
    return list.map((e) => DemandeAdminItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  /// Get only autorisations de sortie history
  Future<List<DemandeAdminItem>> mesAutorisations() async {
    return mesDemandes(typeDemande: 'AUTORISATION_SORTIE');
  }

  Future<DemandeAdminSuivi> suivi(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id/suivi');
    return DemandeAdminSuivi.fromJson(res.data!);
  }

  Future<void> createAutorisationSortie({
    required String dateJour,
    required String heureDebut,
    required String heureFin,
    required String motif,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.demandesAdmin,
      data: {
        'type_demande': 'AUTORISATION_SORTIE',
        'contenu': {
          'date_jour': dateJour,
          'heure_debut': heureDebut,
          'heure_fin': heureFin,
          'motif': motif,
        },
      },
    );
  }

  Future<void> createConge({
    required String dateDebut,
    required String dateFin,
    required String typeConge,
    String? certificatPath,
  }) async {
    if (certificatPath != null && certificatPath.isNotEmpty) {
      final formData = FormData.fromMap({
        'type_demande': 'CONGE',
        'contenu': {
          'date_debut': dateDebut,
          'date_fin': dateFin,
          'type_conge': typeConge,
        },
        'certificat': await MultipartFile.fromFile(
          certificatPath,
          filename: certificatPath.split('/').last,
        ),
      });
      await _dio.post<Map<String, dynamic>>(
        ApiConstants.demandesAdmin,
        data: formData,
        options: Options(
          headers: {'Content-Type': 'multipart/form-data'},
        ),
      );
    } else {
      await _dio.post<Map<String, dynamic>>(
        ApiConstants.demandesAdmin,
        data: {
          'type_demande': 'CONGE',
          'contenu': {
            'date_debut': dateDebut,
            'date_fin': dateFin,
            'type_conge': typeConge,
          },
        },
      );
    }
  }
  /// CDC §M01 : annulation par le demandeur (statut EN_VALIDATION_SUPERIEUR ou EN_VALIDATION_RRH)
  Future<void> annulerDemande(String id) async {
    await _dio.post<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id/annuler');
  }

  /// CDC §M01 : liste des demandes en attente de validation pour le RO connecté
  Future<List<DemandeAdminItem>> demandesEnAttenteRo() async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesAdmin}/en-attente-ro');
    final list = res.data ?? [];
    return list.map((e) => DemandeAdminItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  /// CDC §M01 : ordre de mission
  Future<void> createOrdreMission({
    required String lieu,
    required String dateDebut,
    required String dateFin,
    required String motif,
    String? objectifs,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.demandesAdmin,
      data: {
        'type_demande': 'ORDRE_MISSION',
        'contenu': {
          'lieu': lieu,
          'date_debut': dateDebut,
          'date_fin': dateFin,
          'motif': motif,
          if (objectifs != null && objectifs.isNotEmpty) 'objectifs': objectifs,
        },
      },
    );
  }

  /// CDC §M02 : demande de document administratif
  Future<void> createDemandeDocument({
    required String typeDocument,
    String? motif,
    String? periodeRef,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.demandesDocumentsAdmin,
      data: {
        'type_document': typeDocument,
        if (motif != null && motif.isNotEmpty) 'motif': motif,
        if (periodeRef != null && periodeRef.isNotEmpty) 'periode_reference': periodeRef,
      },
    );
  }

  Future<DemandeAdminSuivi> suiviDocument(String id) async {
    final res = await _dio.get<Map<String, dynamic>>(
        '${ApiConstants.demandesDocumentsAdmin}/$id/suivi');
    return DemandeAdminSuivi.fromJson(res.data!);
  }

  /// RO valide une demande en attente (EN_VALIDATION_SUPERIEUR → EN_VALIDATION_RRH)
  Future<DemandeAdminItem> validerSuperieur(String id) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '${ApiConstants.demandesAdmin}/$id/valider-superieur',
    );
    return DemandeAdminItem.fromJson(res.data!);
  }

  /// RO refuse une demande avec motif obligatoire
  Future<DemandeAdminItem> refuserSuperieur(String id, String motifRefus) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '${ApiConstants.demandesAdmin}/$id/refuser-superieur',
      data: {'motif_refus': motifRefus},
    );
    return DemandeAdminItem.fromJson(res.data!);
  }

  /// CDC §M01 : obtenir l'historique du workflow
  Future<List<WorkflowHistoryItem>> obtenirHistorique(String id) async {
    final res = await _dio.get<List<dynamic>>('${ApiConstants.demandesAdmin}/$id/historique');
    final list = res.data ?? [];
    return list
        .map((e) => WorkflowHistoryItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

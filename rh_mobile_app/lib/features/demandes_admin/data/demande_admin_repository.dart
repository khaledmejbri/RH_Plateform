import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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
    String? periodePremierJour,
    String? periodeDernierJour,
  }) async {
    // Story 7 : le serveur exige `contenu.certificat` ou `pieces_jointes` pour
    // MALADIE/MATERNITE. Pas d’endpoint multipart encore → JSON + marqueur fichier.
    final contenu = <String, dynamic>{
      'date_debut': dateDebut,
      'date_fin': dateFin,
      'type_conge': typeConge,
    };
    // Demi-journées : envoyées pour le congé annuel seulement (le serveur refuse une période sur un autre type).
    if (periodePremierJour != null) contenu['periode_premier_jour'] = periodePremierJour;
    if (periodeDernierJour != null) contenu['periode_dernier_jour'] = periodeDernierJour;
    if (certificatPath != null && certificatPath.isNotEmpty) {
      final name = certificatPath.replaceAll('\\', '/').split('/').last;
      contenu['certificat'] = name.isNotEmpty ? name : 'certificat_fourni';
      contenu['pieces_jointes'] = [contenu['certificat']];
    }
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.demandesAdmin,
      data: {
        'type_demande': 'CONGE',
        'contenu': contenu,
      },
    );
  }
  /// CDC §M01 : annulation par le demandeur (statut EN_VALIDATION_SUPERIEUR ou EN_VALIDATION_RRH)
  Future<void> annulerDemande(String id) async {
    await _dio.post<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id/annuler');
  }

  /// Détail d'une demande (lecture ouverte au demandeur et au valideur attendu).
  Future<DemandeAdminItem> detail(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id');
    return DemandeAdminItem.fromJson(res.data!);
  }

  /// File M01 : demandes dont le connecté est le valideur attendu (manager nœud).
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

  /// Validation 1er niveau : manager ACTIF du nœud (403 si autre nœud).
  Future<DemandeAdminItem> validerSuperieur(String id) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '${ApiConstants.demandesAdmin}/$id/valider-superieur',
    );
    return DemandeAdminItem.fromJson(res.data!);
  }

  /// Refus 1er niveau avec motif obligatoire (403 si non manager du nœud).
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

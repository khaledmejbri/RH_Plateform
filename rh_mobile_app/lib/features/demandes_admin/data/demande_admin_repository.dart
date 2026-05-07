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
}

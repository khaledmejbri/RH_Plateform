import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/format/formats.dart';
import '../../../core/network/api_client.dart';
import 'solde_models.dart';

final soldeRepositoryProvider = Provider<SoldeRepository>((ref) => SoldeRepository(ref.watch(dioProvider)));

/// Solde de congé annuel du collaborateur connecté. L'identité vient du JWT côté serveur.
class SoldeRepository {
  SoldeRepository(this._dio);

  final Dio _dio;

  /// A1.
  Future<SoldeConge> monSolde() async {
    final res = await _dio.get<Map<String, dynamic>>(ApiConstants.congesSoldeMoi);
    return SoldeConge.fromJson(res.data ?? const {});
  }

  /// A2 : mouvements par page de [taille], plus récents d'abord ; [type] filtre (code de mouvement).
  Future<PageMouvements> mesMouvements({int page = 0, int taille = 20, String? type}) async {
    final res = await _dio.get<Map<String, dynamic>>(
      ApiConstants.congesMouvementsMoi,
      queryParameters: {
        'page': page,
        'taille': taille,
        if (type != null && type.isNotEmpty) 'type': type,
      },
    );
    return PageMouvements.fromJson(res.data ?? const {});
  }

  /// A3 : simulation sans écriture d'un congé annuel.
  Future<SimulationConge> simuler({
    required DateTime dateDebut,
    required DateTime dateFin,
    required String periodePremierJour,
    required String periodeDernierJour,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      ApiConstants.congesSimulation,
      queryParameters: {
        'date_debut': formatDateApi(dateDebut),
        'date_fin': formatDateApi(dateFin),
        'periode_premier_jour': periodePremierJour,
        'periode_dernier_jour': periodeDernierJour,
      },
    );
    return SimulationConge.fromJson(res.data ?? const {});
  }
}

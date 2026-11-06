import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'document_models.dart';

final documentRepositoryProvider = Provider<DocumentRepository>((ref) {
  return DocumentRepository(ref.watch(dioProvider));
});

class DocumentRepository {
  DocumentRepository(this._dio);

  final Dio _dio;

  Future<List<DocumentDemandeItem>> mesDemandes() async {
    final res = await _dio.get<List<dynamic>>(ApiConstants.demandesDocuments);
    final list = res.data ?? [];
    return list
        .map((e) => DocumentDemandeItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<DocumentSuivi> suivi(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('${ApiConstants.demandesDocuments}/$id/suivi');
    return DocumentSuivi.fromJson(res.data!);
  }

  Future<void> create({
    required String typeDocument,
    String? commentaire,
    Map<String, dynamic>? contenu,
  }) async {
    final body = <String, dynamic>{
      'type_document': typeDocument,
      if (commentaire != null) 'commentaire_demandeur': commentaire,
      if (contenu != null) 'contenu': contenu,
    };
    await _dio.post<Map<String, dynamic>>(ApiConstants.demandesDocuments, data: body);
  }

  /// Pré-contrôle D-F06 : une seule demande ouverte par type de document.
  /// Ne rattrape aucune erreur : en cas d'échec l'appelant bloque l'envoi (jamais permissif).
  Future<CanRequestResult> verifierRestriction(String typeDocument) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '${ApiConstants.demandesDocuments}/check-restriction',
      queryParameters: {'type_document': typeDocument},
    );
    return CanRequestResult.fromJson(res.data);
  }
}

class CanRequestResult {
  final bool canRequest;
  final String? message;

  const CanRequestResult({required this.canRequest, required this.message});

  /// Réponse absente ou sans `can_request` : refus (jamais permissif).
  factory CanRequestResult.fromJson(Map<String, dynamic>? json) {
    final data = json ?? const <String, dynamic>{};
    return CanRequestResult(
      canRequest: data['can_request'] == true,
      message: data['message'] as String?,
    );
  }
}

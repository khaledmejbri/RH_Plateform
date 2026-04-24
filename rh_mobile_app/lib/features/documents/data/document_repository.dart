import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
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

  Future<CanRequestResult> canRequestAttestationTravail() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>(
        '${ApiConstants.demandesDocuments}/check-restriction',
        queryParameters: {'type_document': 'ATTESTATION_TRAVAIL'},
      );
      final data = res.data!;
      return CanRequestResult(
        canRequest: data['can_request'] as bool? ?? false,
        message: data['message'] as String?,
      );
    } catch (e) {
      debugPrint('Error checking restriction: $e');
      return const CanRequestResult(canRequest: true, message: null);
    }
  }
}

class CanRequestResult {
  final bool canRequest;
  final String? message;

  const CanRequestResult({required this.canRequest, required this.message});
}

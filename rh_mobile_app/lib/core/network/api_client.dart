import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../constants/api_constants.dart';
import '../storage/secure_token_storage.dart';

/// Identifiant collaborateur RH (renseigné après /collaborateurs/moi).
class CollaborateurSession {
  CollaborateurSession._();
  static String? id;
}

final dioProvider = Provider<Dio>((ref) {
  final tokenStorage = ref.watch(secureTokenStorageProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: ApiConstants.baseUrl,
      connectTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(seconds: 30),
      headers: {'Accept': 'application/json'},
    ),
  );

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        options.baseUrl = ApiConstants.baseUrl;
        final token = await tokenStorage.readAccessToken();
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        final collabId = CollaborateurSession.id;
        if (collabId != null && collabId.isNotEmpty) {
          options.headers['X-Collaborateur-Id'] = collabId;
        }
        return handler.next(options);
      },
    ),
  );

  return dio;
});

final secureTokenStorageProvider = Provider<SecureTokenStorage>((ref) {
  return SecureTokenStorage();
});

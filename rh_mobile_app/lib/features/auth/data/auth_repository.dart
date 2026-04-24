import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_token_storage.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(dioProvider), ref.watch(secureTokenStorageProvider));
});

class AuthRepository {
  AuthRepository(this._dio, this._storage);

  final Dio _dio;
  final SecureTokenStorage _storage;

  Future<void> signIn({required String email, required String password}) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/signin',
        data: {
          'nom_utilisateur': email.trim(),
          'mot_de_passe': password,
        },
      );
      final data = response.data;
      final token = data?['jeton_acces'] as String? ??
          data?['access_token'] as String? ??
          data?['accessToken'] as String?;
      if (token == null || token.isEmpty) {
        throw const AuthException('Réponse serveur sans jeton d’accès.');
      }
      await _storage.saveAccessToken(token);
    } on DioException catch (e) {
      throw AuthException(_messageFromDio(e));
    }
  }

  Future<void> signInDemo() async {
    await _storage.saveAccessToken('demo_token_${DateTime.now().millisecondsSinceEpoch}');
  }

  Future<void> signOut() => _storage.clear();

  Future<bool> hasSession() async {
    final t = await _storage.readAccessToken();
    return t != null && t.isNotEmpty;
  }

  String _messageFromDio(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['erreur'] is String) return data['erreur'] as String;
    if (data is Map && data['message'] is String) return data['message'] as String;
    return e.message ?? 'Erreur réseau';
  }
}

class AuthException implements Exception {
  const AuthException(this.message);
  final String message;

  @override
  String toString() => message;
}

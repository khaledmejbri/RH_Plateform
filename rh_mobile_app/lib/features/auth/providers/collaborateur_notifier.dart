import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/providers/auth_notifier.dart';
import '../../../core/network/api_client.dart';
import 'package:dio/dio.dart';

class CollaborateurInfo {
  final String identifiant;
  final String nom;
  final String prenom;
  final String? email;

  CollaborateurInfo({
    required this.identifiant,
    required this.nom,
    required this.prenom,
    this.email,
  });

  factory CollaborateurInfo.fromMap(Map<String, dynamic> map) {
    return CollaborateurInfo(
      identifiant: map['identifiant'] ?? '',
      nom: map['nom'] ?? '',
      prenom: map['prenom'] ?? '',
      email: map['email'],
    );
  }

  @override
  String toString() => 'CollaborateurInfo(id: $identifiant, nom: $nom, prenom: $prenom, email: $email)';
}

class CollaborateurNotifier extends StateNotifier<AsyncValue<CollaborateurInfo?>> {
  CollaborateurNotifier(this._dio) : super(const AsyncValue.data(null));

  final Dio _dio;

  Future<void> fetchMoi() async {
    state = const AsyncValue.loading();
    try {
      debugPrint('[API] Fetching collaborator profile...');
      final res = await _dio.get('/api/referentiel/v1/collaborateurs/moi');
      final info = CollaborateurInfo.fromMap(res.data);
      debugPrint('[API] Profile fetched successfully: $info');
      state = AsyncValue.data(info);
    } catch (e, s) {
      debugPrint('[API] Error fetching profile: $e');
      if (e is DioException) {
        debugPrint('[API] Status: ${e.response?.statusCode}');
        debugPrint('[API] Data: ${e.response?.data}');
      }
      state = AsyncValue.error(e, s);
    }
  }

  void clear() {
    state = const AsyncValue.data(null);
  }
}

final collaborateurNotifierProvider = StateNotifierProvider<CollaborateurNotifier, AsyncValue<CollaborateurInfo?>>((ref) {
  return CollaborateurNotifier(ref.watch(dioProvider));
});

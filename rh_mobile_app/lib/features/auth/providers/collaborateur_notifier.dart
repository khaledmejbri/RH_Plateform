import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/jwt_access_roles.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_token_storage.dart';

class CollaborateurInfo {
  final String identifiant;
  final String name;
  final String prenom;
  final String? email;
  /// Affichage fiche uniquement — ne pas s'en servir pour les flags d'habilitation.
  final String profilAcces;
  final JwtAccessRoles jwtRoles;

  CollaborateurInfo({
    required this.identifiant,
    required this.name,
    required this.prenom,
    this.email,
    this.profilAcces = 'COLLABORATEUR',
    this.jwtRoles = JwtAccessRoles.empty,
  });

  bool get isRo => jwtRoles.isRo;
  bool get isChefDept => jwtRoles.isChefDept;
  bool get isRh => jwtRoles.isRh;
  bool get isAdmin => jwtRoles.isAdmin;
  bool get peutConfigurerPresence => jwtRoles.peutConfigurerPresence;
  bool get peutDemanderHeuresSup => jwtRoles.peutDemanderHeuresSup;

  /// Indice JWT `RO` / `RESPONSABLE`. Afficher aussi la file si `/en-attente-ro` non vide
  /// (USER manager de nœud sans rôle RO).
  bool get peutValiderFile => jwtRoles.peutValiderFile;

  factory CollaborateurInfo.fromMap(
    Map<String, dynamic> map, {
    JwtAccessRoles jwtRoles = JwtAccessRoles.empty,
  }) {
    return CollaborateurInfo(
      identifiant: map['identifiant'] ?? '',
      name: map['name'] ?? '',
      prenom: map['prenom'] ?? '',
      email: map['courriel_professionnel'] ?? map['email'],
      profilAcces: map['profil_acces'] ?? 'COLLABORATEUR',
      jwtRoles: jwtRoles,
    );
  }

  @override
  String toString() =>
      'CollaborateurInfo(id: $identifiant, name: $name, prenom: $prenom, email: $email)';
}

class CollaborateurNotifier extends StateNotifier<AsyncValue<CollaborateurInfo?>> {
  CollaborateurNotifier(this._dio, this._storage)
      : super(const AsyncValue.data(null));

  final Dio _dio;
  final SecureTokenStorage _storage;

  Future<void> fetchMoi() async {
    state = const AsyncValue.loading();
    try {
      debugPrint('[API] Fetching collaborator profile...');
      final res = await _dio.get('/api/referentiel/v1/collaborateurs/moi');
      final data = res.data;
      if (data is! Map) {
        throw StateError('Réponse /collaborateurs/moi inattendue');
      }
      final token = await _storage.readAccessToken();
      final jwtRoles = JwtAccessRoles.fromAccessToken(token);
      final info = CollaborateurInfo.fromMap(
        Map<String, dynamic>.from(data),
        jwtRoles: jwtRoles,
      );
      CollaborateurSession.id = info.identifiant;
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
    CollaborateurSession.id = null;
    state = const AsyncValue.data(null);
  }
}

final collaborateurNotifierProvider =
    StateNotifierProvider<CollaborateurNotifier, AsyncValue<CollaborateurInfo?>>(
        (ref) {
  return CollaborateurNotifier(
    ref.watch(dioProvider),
    ref.watch(secureTokenStorageProvider),
  );
});

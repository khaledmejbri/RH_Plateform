import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/jwt_access_roles.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_token_storage.dart';
import '../../demandes_admin/data/demande_admin_repository.dart';
import '../../organigramme/data/organigramme_repository.dart';
import '../data/profil_logic.dart';

class CollaborateurInfo {
  final String identifiant;
  final String name;
  final String prenom;
  final String? email;
  final String? matricule;
  final String? posteLibelle;
  /// Libellé de l'unité d'affectation (`unite.libelle`).
  final String? uniteLibelle;
  /// Affichage fiche uniquement — ne pas s'en servir pour les flags d'habilitation.
  final String profilAcces;
  final JwtAccessRoles jwtRoles;

  CollaborateurInfo({
    required this.identifiant,
    required this.name,
    required this.prenom,
    this.email,
    this.matricule,
    this.posteLibelle,
    this.uniteLibelle,
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

  /// « Prénom Nom » (l'un ou l'autre peut manquer).
  String get nomComplet => '$prenom $name'.trim();

  /// Initiales réelles pour l'avatar (« ? » si aucun nom).
  String get initiales {
    final i = '${prenom.isNotEmpty ? prenom[0] : ''}${name.isNotEmpty ? name[0] : ''}'.toUpperCase();
    return i.isEmpty ? '?' : i;
  }

  factory CollaborateurInfo.fromMap(
    Map<String, dynamic> map, {
    JwtAccessRoles jwtRoles = JwtAccessRoles.empty,
  }) {
    final unite = map['unite'];
    String? texte(dynamic v) {
      final t = v?.toString().trim();
      return (t == null || t.isEmpty) ? null : t;
    }

    return CollaborateurInfo(
      identifiant: map['identifiant'] ?? '',
      // `/collaborateurs/moi` renvoie `nom` ; `name` est conservé pour les anciens jetons de test.
      name: (map['nom'] ?? map['name'] ?? '').toString(),
      prenom: map['prenom'] ?? '',
      email: map['courriel_professionnel'] ?? map['email'],
      matricule: texte(map['matricule']),
      posteLibelle: texte(map['poste_libelle']),
      uniteLibelle: unite is Map ? texte(unite['libelle']) : null,
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

/// Responsable affiché sur le profil (D-T02.b) ; une erreur de chargement reste distincte de « Non renseigné ».
final profilResponsableProvider = FutureProvider.autoDispose<String>((ref) async {
  final moiId = ref.watch(collaborateurNotifierProvider).valueOrNull?.identifiant ?? '';
  final org = await ref.watch(organigrammeRepositoryProvider).fetch();
  return libelleResponsable(org.racines, moiId);
});

/// Nombre réel de demandes M01 du compte en cours de validation.
final profilDemandesEnCoursProvider = FutureProvider.autoDispose<int>((ref) async {
  final demandes = await ref.watch(demandeAdminRepositoryProvider).mesDemandes();
  return compterDemandesEnCours(demandes);
});

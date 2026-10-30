import 'dart:convert';

/// Rôles JWT du catalogue applicatif (C-S-02).
///
/// Claims lus : `roles` (liste) et/ou `scope` (espace / virgule).
/// Les flags d'habilitation ne viennent **pas** de `profil_acces`.
class JwtAccessRoles {
  static const catalogue = {
    'USER',
    'RO',
    'RESPONSABLE',
    'RH',
    'DIRECTION',
    'ADMIN',
  };

  static const empty = JwtAccessRoles._(roles: {});

  final Set<String> roles;

  const JwtAccessRoles._({required this.roles});

  bool get isRo => roles.contains('RO');

  bool get isChefDept => roles.contains('RESPONSABLE');

  bool get isRh => roles.contains('RH');

  bool get isAdmin => roles.contains('ADMIN');

  /// Pose GPS sites de pointage (P5) — RH | ADMIN uniquement.
  bool get peutConfigurerPresence => isRh || isAdmin;

  /// Indice UI JWT (`RO` / `RESPONSABLE`). La file réelle = `/en-attente-ro`
  /// (manager ACTIF du nœud) — un USER manager de nœud peut aussi valider.
  bool get peutValiderFile => isRo || isChefDept;

  String? get libelleHabilitation {
    if (isRo) return 'Responsable opérationnel';
    if (isChefDept) return 'Chef de département';
    return null;
  }

  static JwtAccessRoles fromAccessToken(String? token) {
    final payload = decodePayload(token);
    if (payload == null) return empty;
    return fromPayload(payload);
  }

  static JwtAccessRoles fromPayload(Map<String, dynamic> payload) {
    final extracted = <String>{
      ..._claimValues(payload['roles']),
      ..._claimValues(payload['scope']),
    };
    final kept = extracted.map(_normalize).where(catalogue.contains).toSet();
    if (kept.isEmpty) return empty;
    return JwtAccessRoles._(roles: kept);
  }

  /// Identifiant du compte (claim `identifiant_utilisateur`), `null` si absent (jeton de démo).
  static String? compteId(String? token) {
    final v = decodePayload(token)?['identifiant_utilisateur'];
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }

  static Map<String, dynamic>? decodePayload(String? token) {
    if (token == null || token.isEmpty) return null;
    final parts = token.split('.');
    if (parts.length != 3) return null;
    try {
      final jsonStr =
          utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
      final decoded = json.decode(jsonStr);
      if (decoded is Map<String, dynamic>) return decoded;
      if (decoded is Map) return Map<String, dynamic>.from(decoded);
      return null;
    } catch (_) {
      return null;
    }
  }

  static Iterable<String> _claimValues(dynamic claim) {
    if (claim == null) return const [];
    if (claim is List) {
      return claim.map((e) => e.toString());
    }
    if (claim is String) {
      return claim.split(RegExp(r'[\s,]+')).where((s) => s.isNotEmpty);
    }
    return [claim.toString()];
  }

  static String _normalize(String raw) {
    var value = raw.trim().toUpperCase();
    if (value.startsWith('ROLE_')) {
      value = value.substring(5);
    }
    return value;
  }
}

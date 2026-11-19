// Informations complémentaires de « Mon dossier » (ticket T12, API V4, JSON snake_case).

String? _texte(Object? brut) {
  final t = brut?.toString().trim();
  return (t == null || t.isEmpty) ? null : t;
}

/// Un champ personnalisé tel que le serveur l'affiche au collaborateur : la valeur est déjà formatée
/// (« 42,5 », « 15/11/2026 », libellé de liste) et n'est jamais recalculée ni arrondie ici.
class ChampPersoAffiche {
  const ChampPersoAffiche({
    required this.code,
    required this.libelle,
    this.typeValeur,
    this.valeurAffichee,
  });

  final String code;
  final String libelle;
  final String? typeValeur;
  final String? valeurAffichee;

  factory ChampPersoAffiche.fromJson(Map<String, dynamic> json) {
    return ChampPersoAffiche(
      code: _texte(json['code']) ?? '',
      libelle: _texte(json['libelle']) ?? 'Information',
      typeValeur: _texte(json['type_valeur']),
      valeurAffichee: _texte(json['valeur_affichee']),
    );
  }

  /// Liste de champs tolérante : clé absente, null ou mal typée = liste vide.
  static List<ChampPersoAffiche> listeDepuis(Object? brut) {
    if (brut is! List) return const [];
    return brut
        .whereType<Map>()
        .map((e) => ChampPersoAffiche.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// Lit la réponse V4 `{ "champs": [...] }`.
  static List<ChampPersoAffiche> listeDepuisReponse(Object? corps) =>
      corps is Map ? listeDepuis(corps['champs']) : const [];
}

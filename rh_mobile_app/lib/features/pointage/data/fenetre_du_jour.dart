// Fenêtre de pointage du jour d'un site (ADR-M09-HORAIRES, D-P12 / D-P16.c).

/// Heures sup validées de l'appelant pour le jour (ouvertures en minutes, 0 = côté non demandé).
class HeuresSupDuJour {
  final String reference;
  final int ouvertureAvantMinutes;
  final int ouvertureApresMinutes;

  const HeuresSupDuJour({
    required this.reference,
    this.ouvertureAvantMinutes = 0,
    this.ouvertureApresMinutes = 0,
  });

  factory HeuresSupDuJour.fromJson(Map<String, dynamic> json) {
    return HeuresSupDuJour(
      reference: (json['reference'] ?? '').toString(),
      ouvertureAvantMinutes: _entier(json['ouverture_avant_minutes']),
      ouvertureApresMinutes: _entier(json['ouverture_apres_minutes']),
    );
  }

  /// « 2 h avant · 1 h après » (côtés non demandés omis).
  String get libelle {
    final parts = <String>[
      if (ouvertureAvantMinutes > 0) '${libelleDuree(ouvertureAvantMinutes)} avant',
      if (ouvertureApresMinutes > 0) '${libelleDuree(ouvertureApresMinutes)} après',
    ];
    return parts.join(' · ');
  }
}

/// Réponse de `POST /fenetre-du-jour` pour le site scanné.
class FenetreDuJour {
  final String? siteId;
  final String? siteLibelle;
  final bool avecHoraire;
  final String? journee;
  final bool ferme;
  final String? horaireDebut;
  final String? horaireFin;
  final String? ouvertureDebut;
  final String? ouvertureFin;
  final List<HeuresSupDuJour> heuresSup;

  const FenetreDuJour({
    this.siteId,
    this.siteLibelle,
    this.avecHoraire = false,
    this.journee,
    this.ferme = false,
    this.horaireDebut,
    this.horaireFin,
    this.ouvertureDebut,
    this.ouvertureFin,
    this.heuresSup = const [],
  });

  factory FenetreDuJour.fromJson(Map<String, dynamic> json) {
    final brut = json['heures_sup'];
    return FenetreDuJour(
      siteId: json['site_id']?.toString(),
      siteLibelle: json['site_libelle']?.toString(),
      avecHoraire: json['avec_horaire'] == true,
      journee: json['journee']?.toString(),
      ferme: json['ferme'] == true,
      horaireDebut: heureHHmm(json['horaire_debut']),
      horaireFin: heureHHmm(json['horaire_fin']),
      ouvertureDebut: heureHHmm(json['ouverture_debut']),
      ouvertureFin: heureHHmm(json['ouverture_fin']),
      heuresSup: brut is List
          ? brut
              .whereType<Map>()
              .map((e) => HeuresSupDuJour.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
    );
  }

  /// Un site sans horaire n'affiche aucun bandeau.
  bool get afficherBandeau => avecHoraire;

  /// « Aujourd'hui 06:00–15:00 · pointage ouvert 05:00–16:00 » ; `null` si l'horaire est incomplet.
  String? get resume {
    if (!avecHoraire || ferme) return null;
    if (horaireDebut == null || horaireFin == null) return null;
    final base = 'Aujourd’hui $horaireDebut–$horaireFin';
    if (ouvertureDebut == null || ouvertureFin == null) return base;
    return '$base · pointage ouvert $ouvertureDebut–$ouvertureFin';
  }

  /// « Heures sup validées aujourd'hui : 2 h avant · 1 h après » ; `null` sans heures sup.
  String? get resumeHeuresSup {
    final libelles = heuresSup.map((h) => h.libelle).where((l) => l.isNotEmpty).toList();
    if (libelles.isEmpty) return null;
    return 'Heures sup validées aujourd’hui : ${libelles.join(' ; ')}';
  }
}

/// Normalise « 06:00:00 » / « 06:00 » en « 06:00 » ; `null` si absent ou vide.
String? heureHHmm(dynamic brut) {
  final s = brut?.toString().trim();
  if (s == null || s.isEmpty) return null;
  return s.length >= 5 ? s.substring(0, 5) : s;
}

/// « 30 min », « 1 h », « 1 h 30 ».
String libelleDuree(int minutes) {
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return '$m min';
  if (m == 0) return '$h h';
  return '$h h ${m.toString().padLeft(2, '0')}';
}

int _entier(dynamic brut) {
  if (brut is num) return brut.toInt();
  return int.tryParse('${brut ?? ''}') ?? 0;
}

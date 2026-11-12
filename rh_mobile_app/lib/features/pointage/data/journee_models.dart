// Modèles « Mes pointages » et fiche d'équipe (ticket 2E, API C6 / C7, JSON snake_case).

import '../../../core/libelles/libelles.dart';
import 'presence_errors.dart';
import 'presence_models.dart';

DateTime? _date(Object? brut) {
  if (brut == null) return null;
  final d = DateTime.tryParse(brut.toString());
  if (d == null) return null;
  return d.isUtc ? d.toLocal() : d;
}

int? _entier(Object? brut) => brut is num ? brut.toInt() : int.tryParse('${brut ?? ''}');

String? _texte(Object? brut) {
  final t = brut?.toString().trim();
  return (t == null || t.isEmpty) ? null : t;
}

/// Un pointage d'une journée (réussi, refusé ou manuel).
class PointageJournee {
  final String id;
  final String type;
  final String? typeLibelle;
  final String statut;
  final String? statutLibelle;
  final DateTime? serverTs;
  final bool manuel;
  final String? motifRejet;

  /// D-P22.a : décision du serveur (le mobile ne recode pas la règle). Champ absent = pas de bouton.
  final bool manuelPossible;

  /// Raison en clair quand le refus n'est pas rattrapable (affichée à la place du bouton).
  final String? manuelImpossibleMotif;

  const PointageJournee({
    required this.id,
    required this.type,
    this.typeLibelle,
    required this.statut,
    this.statutLibelle,
    this.serverTs,
    this.manuel = false,
    this.motifRejet,
    this.manuelPossible = false,
    this.manuelImpossibleMotif,
  });

  factory PointageJournee.fromJson(Map<String, dynamic> json) {
    return PointageJournee(
      id: _texte(json['id']) ?? '',
      type: _texte(json['type']) ?? '',
      typeLibelle: _texte(json['type_libelle']),
      statut: _texte(json['statut']) ?? '',
      statutLibelle: _texte(json['statut_libelle']),
      serverTs: _date(json['server_ts']),
      manuel: json['manuel'] == true,
      motifRejet: _texte(json['motif_rejet']),
      manuelPossible: json['manuel_possible'] == true,
      manuelImpossibleMotif: _texte(json['manuel_impossible_motif']),
    );
  }

  bool get estRejet => PointageStatut.estRejet(statut);

  /// « Entrée » / « Sortie » : libellé du serveur, sinon déduit du type.
  String get typeAffiche => typeLibelle ?? (type == 'SORTIE' ? 'Sortie' : 'Entrée');

  /// Libellé du statut en clair, jamais le code brut.
  String get statutAffiche {
    final l = statutLibelle;
    if (l != null && !Libelles.ressembleAUnCode(l)) return l;
    if (statut == PointageStatut.valide) return 'Validé';
    if (statut == PointageStatut.valideManuel) return 'Validé manuellement';
    if (estRejet) return 'Refusé';
    return Libelles.valeurNonReconnue;
  }

  /// Motif d'un refus : texte du serveur (ex. « Hors zone de pointage (179,8 m pour une zone de 50 m) »),
  /// à défaut libellé générique du statut. `null` si le pointage n'est pas un refus.
  String? get motifAffiche => estRejet ? messageMotifPointage(statut, motifServeur: motifRejet) : null;
}

/// Une journée de présence (C6, C7) : temps, retard et pointages.
class JourneePresence {
  final DateTime? journee;
  final String? siteLibelle;
  final int tempsMinutes;

  /// `null` = non calculable (site sans horaire) ; `0` = à l'heure.
  final int? retardMinutes;
  final String etat;
  final String? etatLibelle;
  final List<PointageJournee> pointages;

  const JourneePresence({
    this.journee,
    this.siteLibelle,
    this.tempsMinutes = 0,
    this.retardMinutes,
    this.etat = '',
    this.etatLibelle,
    this.pointages = const [],
  });

  factory JourneePresence.fromJson(Map<String, dynamic> json) {
    final brut = json['pointages'];
    return JourneePresence(
      journee: _date(json['journee']),
      siteLibelle: _texte(json['site_libelle']),
      tempsMinutes: _entier(json['temps_minutes']) ?? 0,
      retardMinutes: _entier(json['retard_minutes']),
      etat: _texte(json['etat']) ?? '',
      etatLibelle: _texte(json['etat_libelle']),
      pointages: brut is List
          ? brut.whereType<Map>().map((e) => PointageJournee.fromJson(Map<String, dynamic>.from(e))).toList()
          : const [],
    );
  }

  /// « Complet », « En cours », « Pointage incomplet » : libellé du serveur, sinon table.
  String get etatAffiche {
    final l = etatLibelle;
    if (l != null && !Libelles.ressembleAUnCode(l)) return l;
    return Libelles.etatTempsTravail(etat);
  }
}

/// Totaux de la période (C2 / C6 synthèse / bloc `synthese` de C7).
class SynthesePresence {
  final int journeesPointees;
  final int tempsTotalMinutes;
  final int retardsNombre;
  final int retardsCumulMinutes;
  final int journeesIncompletes;
  final int journeesEnCours;
  final int pointagesManuelsValides;
  final int pointagesManuelsEnAttente;
  final int heuresSupMinutes;

  const SynthesePresence({
    this.journeesPointees = 0,
    this.tempsTotalMinutes = 0,
    this.retardsNombre = 0,
    this.retardsCumulMinutes = 0,
    this.journeesIncompletes = 0,
    this.journeesEnCours = 0,
    this.pointagesManuelsValides = 0,
    this.pointagesManuelsEnAttente = 0,
    this.heuresSupMinutes = 0,
  });

  factory SynthesePresence.fromJson(Map<String, dynamic> json) {
    return SynthesePresence(
      journeesPointees: _entier(json['journees_pointees']) ?? 0,
      tempsTotalMinutes: _entier(json['temps_total_minutes']) ?? 0,
      retardsNombre: _entier(json['retards_nombre']) ?? 0,
      retardsCumulMinutes: _entier(json['retards_cumul_minutes']) ?? 0,
      journeesIncompletes: _entier(json['journees_incompletes']) ?? 0,
      journeesEnCours: _entier(json['journees_en_cours']) ?? 0,
      pointagesManuelsValides: _entier(json['pointages_manuels_valides']) ?? 0,
      pointagesManuelsEnAttente: _entier(json['pointages_manuels_en_attente']) ?? 0,
      heuresSupMinutes: _entier(json['heures_sup_minutes']) ?? 0,
    );
  }
}

/// Présence d'un membre de l'équipe vue par son valideur (C7).
class PresenceEquipe {
  final String? nomComplet;
  final String? matricule;
  final SynthesePresence synthese;
  final List<JourneePresence> journees;
  final List<DemandeManuelle> pointagesManuels;

  const PresenceEquipe({
    this.nomComplet,
    this.matricule,
    this.synthese = const SynthesePresence(),
    this.journees = const [],
    this.pointagesManuels = const [],
  });

  factory PresenceEquipe.fromJson(Map<String, dynamic> json) {
    final c = json['collaborateur'];
    final s = json['synthese'];
    final j = json['journees'];
    final m = json['pointages_manuels'];
    return PresenceEquipe(
      nomComplet: c is Map ? _texte(c['nom_complet']) : null,
      matricule: c is Map ? _texte(c['matricule']) : null,
      synthese: s is Map ? SynthesePresence.fromJson(Map<String, dynamic>.from(s)) : const SynthesePresence(),
      journees: j is List
          ? j.whereType<Map>().map((e) => JourneePresence.fromJson(Map<String, dynamic>.from(e))).toList()
          : const [],
      pointagesManuels: m is List
          ? m.whereType<Map>().map((e) => DemandeManuelle.fromJson(Map<String, dynamic>.from(e))).toList()
          : const [],
    );
  }
}

/// Détail d'un pointage manuel (C5) : la demande plus le contexte du refus automatique.
/// Signaux, distance, compteurs et délais sont portés par [demande] (absents pour le demandeur, D-P24.d).
class DemandeManuelleDetail {
  final DemandeManuelle demande;
  final String? siteLibelle;
  final String? uniteLibelle;
  final String? valideurNom;
  final String? motifRejetAutomatique;

  const DemandeManuelleDetail({
    required this.demande,
    this.siteLibelle,
    this.uniteLibelle,
    this.valideurNom,
    this.motifRejetAutomatique,
  });

  factory DemandeManuelleDetail.fromJson(Map<String, dynamic> json) {
    final v = json['valideur'];
    final p = json['pointage_rejete'];
    return DemandeManuelleDetail(
      demande: DemandeManuelle.fromJson(json),
      siteLibelle: _texte(json['site_libelle']),
      uniteLibelle: _texte(json['unite_libelle']),
      valideurNom: v is Map ? _texte(v['nom_complet']) : null,
      motifRejetAutomatique: p is Map
          ? messageMotifPointage(_texte(p['statut']), motifServeur: _texte(p['motif_rejet']))
          : null,
    );
  }

  /// « Omar Belhadj » ou « les RH » (valideur absent = RH, D-H03).
  String get valideurAffiche => valideurNom ?? demande.valideurLibelle;
}

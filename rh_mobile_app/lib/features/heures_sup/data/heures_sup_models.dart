// Modèles heures supplémentaires (ADR-M09-HORAIRES, D-P13 à D-P16). JSON en snake_case.

import '../../pointage/data/fenetre_du_jour.dart';

/// Statuts d'une demande d'heures sup.
abstract final class StatutHeuresSup {
  static const enAttente = 'EN_ATTENTE';
  static const validee = 'VALIDEE';
  static const refusee = 'REFUSEE';
  static const annulee = 'ANNULEE';
  static const expiree = 'EXPIREE';

  static String libelle(String statut) => switch (statut) {
        enAttente => 'En attente',
        validee => 'Validée',
        refusee => 'Refusée',
        annulee => 'Annulée',
        expiree => 'Expirée',
        _ => statut,
      };
}

/// Collaborateur que le demandeur peut choisir (périmètre D-H03).
class Beneficiaire {
  final String collaborateurId;
  final String matricule;
  final String nom;

  const Beneficiaire({required this.collaborateurId, this.matricule = '', this.nom = ''});

  factory Beneficiaire.fromJson(Map<String, dynamic> json) => Beneficiaire(
        collaborateurId: (json['collaborateur_id'] ?? json['id'] ?? '').toString(),
        matricule: (json['matricule'] ?? '').toString(),
        nom: (json['nom'] ?? '').toString(),
      );

  String get libelle => matricule.isEmpty ? nom : '$nom ($matricule)';
}

/// Site actif proposé à la saisie (`GET /sites`).
class SitePointageChoix {
  final String siteId;
  final String libelle;
  final bool avecHoraire;

  const SitePointageChoix({required this.siteId, required this.libelle, this.avecHoraire = false});

  factory SitePointageChoix.fromJson(Map<String, dynamic> json) => SitePointageChoix(
        siteId: (json['site_id'] ?? json['id'] ?? '').toString(),
        libelle: (json['libelle'] ?? '').toString(),
        avecHoraire: json['avec_horaire'] == true,
      );
}

/// Heures comptées d'un bénéficiaire pour une journée (D-P14).
class HeuresComptees {
  final String? collaborateurId;
  final String matricule;
  final String nom;
  final String? siteLibelle;
  final String? journee;
  final String? horaireDebut;
  final String? horaireFin;
  /// Instants ISO ; `null` sans pointage (la ligne existe quand même, à 00:00).
  final String? entree;
  final String? sortie;

  /// Plages validées au format « 04:00-06:00 » ; `null` si le côté n'est pas demandé.
  final String? plageAvant;
  final String? plageApres;
  final int minutesComptees;

  /// « hh:mm » calculé par le serveur.
  final String heuresComptees;
  final String? referenceDemande;

  /// `COMPLET`, `INCOMPLET` ou `EN_COURS`.
  final String etat;

  const HeuresComptees({
    this.collaborateurId,
    this.matricule = '',
    this.nom = '',
    this.siteLibelle,
    this.journee,
    this.horaireDebut,
    this.horaireFin,
    this.entree,
    this.sortie,
    this.plageAvant,
    this.plageApres,
    this.minutesComptees = 0,
    this.heuresComptees = '00:00',
    this.referenceDemande,
    this.etat = 'COMPLET',
  });

  factory HeuresComptees.fromJson(Map<String, dynamic> json) {
    final minutes = json['minutes_comptees'];
    return HeuresComptees(
      collaborateurId: json['collaborateur_id']?.toString(),
      matricule: (json['matricule'] ?? '').toString(),
      nom: (json['nom'] ?? '').toString(),
      siteLibelle: json['site_libelle']?.toString(),
      journee: json['journee']?.toString(),
      horaireDebut: heureHHmm(json['horaire_debut']),
      horaireFin: heureHHmm(json['horaire_fin']),
      entree: json['entree']?.toString(),
      sortie: json['sortie']?.toString(),
      plageAvant: _texteOuNull(json['plage_avant']),
      plageApres: _texteOuNull(json['plage_apres']),
      minutesComptees: minutes is num ? minutes.toInt() : int.tryParse('${minutes ?? ''}') ?? 0,
      heuresComptees: (json['heures_comptees'] ?? '00:00').toString(),
      referenceDemande: json['reference_demande']?.toString(),
      etat: (json['etat'] ?? 'COMPLET').toString(),
    );
  }

  /// « Avant 04:00-06:00 · Après 15:00-16:00 » ; vide si aucune plage.
  String get plagesLibelle => [
        if (plageAvant != null) 'Avant $plageAvant',
        if (plageApres != null) 'Après $plageApres',
      ].join(' · ');

  /// Aucun pointage pour cette journée de demande validée.
  bool get sansPointage => entree == null && sortie == null;

  String get etatLibelle => switch (etat) {
        'INCOMPLET' => 'Pointage incomplet',
        'EN_COURS' => 'En cours',
        _ => 'Complet',
      };
}

String? _texteOuNull(dynamic v) {
  final s = v?.toString().trim();
  return (s == null || s.isEmpty) ? null : s;
}

/// Élément de `a_venir` (`GET /heures-sup/moi`) : une journée d'heures sup validée.
class HeuresSupAVenir {
  final String reference;
  final String? siteId;
  final String? siteLibelle;

  /// `yyyy-MM-dd`.
  final String? journee;
  final int ouvertureAvantMinutes;
  final int ouvertureApresMinutes;

  const HeuresSupAVenir({
    this.reference = '',
    this.siteId,
    this.siteLibelle,
    this.journee,
    this.ouvertureAvantMinutes = 0,
    this.ouvertureApresMinutes = 0,
  });

  factory HeuresSupAVenir.fromJson(Map<String, dynamic> json) {
    int entier(dynamic v) => v is num ? v.toInt() : int.tryParse('${v ?? ''}') ?? 0;
    return HeuresSupAVenir(
      reference: (json['reference'] ?? '').toString(),
      siteId: json['site_id']?.toString(),
      siteLibelle: json['site_libelle']?.toString(),
      journee: json['journee']?.toString(),
      ouvertureAvantMinutes: entier(json['ouverture_avant_minutes']),
      ouvertureApresMinutes: entier(json['ouverture_apres_minutes']),
    );
  }

  String get journeeLibelle => formaterDateIso(journee);

  /// « 2 h avant · 1 h après ».
  String get ouverturesLibelle => HeuresSupDuJour(
        reference: reference,
        ouvertureAvantMinutes: ouvertureAvantMinutes,
        ouvertureApresMinutes: ouvertureApresMinutes,
      ).libelle;
}

/// Réponse `DemandeHeuresSupResponse` (`GET /heures-sup/demandes/moi`).
class DemandeHeuresSup {
  final String id;
  final String reference;
  final String statut;
  final String? siteLibelle;
  final String? dateDebut;
  final String? dateFin;
  final List<String> jours;
  final int ouvertureAvantMinutes;
  final int ouvertureApresMinutes;
  final String motif;
  final List<Beneficiaire> beneficiaires;
  final String? decidePar;
  final String? motifRefus;
  final String? annulePar;
  final String? motifAnnulation;

  const DemandeHeuresSup({
    required this.id,
    this.reference = '',
    required this.statut,
    this.siteLibelle,
    this.dateDebut,
    this.dateFin,
    this.jours = const [],
    this.ouvertureAvantMinutes = 0,
    this.ouvertureApresMinutes = 0,
    this.motif = '',
    this.beneficiaires = const [],
    this.decidePar,
    this.motifRefus,
    this.annulePar,
    this.motifAnnulation,
  });

  factory DemandeHeuresSup.fromJson(Map<String, dynamic> json) {
    int entier(dynamic v) => v is num ? v.toInt() : int.tryParse('${v ?? ''}') ?? 0;
    final jours = json['jours'];
    final benef = json['beneficiaires'];
    return DemandeHeuresSup(
      id: (json['id'] ?? '').toString(),
      reference: (json['reference'] ?? '').toString(),
      statut: (json['statut'] ?? '').toString(),
      siteLibelle: json['site_libelle']?.toString(),
      dateDebut: json['date_debut']?.toString(),
      dateFin: json['date_fin']?.toString(),
      jours: jours is List ? jours.map((e) => e.toString()).toList() : const [],
      ouvertureAvantMinutes: entier(json['ouverture_avant_minutes']),
      ouvertureApresMinutes: entier(json['ouverture_apres_minutes']),
      motif: (json['motif'] ?? '').toString(),
      beneficiaires: benef is List
          ? benef
              .whereType<Map>()
              .map((e) => Beneficiaire.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      decidePar: json['decide_par']?.toString(),
      motifRefus: json['motif_refus']?.toString(),
      annulePar: json['annule_par']?.toString(),
      motifAnnulation: json['motif_annulation']?.toString(),
    );
  }

  bool get enAttente => statut == StatutHeuresSup.enAttente;
  bool get validee => statut == StatutHeuresSup.validee;
  String get statutLibelle => StatutHeuresSup.libelle(statut);

  /// « 2 h avant · 1 h après ».
  String get ouverturesLibelle => HeuresSupDuJour(
        reference: reference,
        ouvertureAvantMinutes: ouvertureAvantMinutes,
        ouvertureApresMinutes: ouvertureApresMinutes,
      ).libelle;

  /// « 03/11/2026 » ou « 03/11/2026 → 07/11/2026 » (dates ISO converties, sinon texte brut).
  String get periodeLibelle {
    final d = formaterDateIso(dateDebut);
    final f = formaterDateIso(dateFin);
    if (d.isEmpty) return '';
    return (f.isEmpty || f == d) ? d : '$d → $f';
  }
}

/// Réponse de `GET /heures-sup/moi`.
class MesHeuresSup {
  final List<HeuresSupAVenir> aVenir;
  final List<HeuresComptees> comptees;

  const MesHeuresSup({this.aVenir = const [], this.comptees = const []});

  factory MesHeuresSup.fromJson(Map<String, dynamic> json) {
    List<T> liste<T>(dynamic brut, T Function(Map<String, dynamic>) f) => brut is List
        ? brut.whereType<Map>().map((e) => f(Map<String, dynamic>.from(e))).toList()
        : <T>[];
    return MesHeuresSup(
      aVenir: liste(json['a_venir'], HeuresSupAVenir.fromJson),
      comptees: liste(json['comptees'], HeuresComptees.fromJson),
    );
  }
}

/// `2026-11-03` → `03/11/2026` ; valeur non ISO renvoyée telle quelle ; `null` → ''.
String formaterDateIso(String? iso) {
  if (iso == null || iso.isEmpty) return '';
  final m = RegExp(r'^(\d{4})-(\d{2})-(\d{2})').firstMatch(iso);
  if (m == null) return iso;
  return '${m.group(3)}/${m.group(2)}/${m.group(1)}';
}

/// `yyyy-MM-dd` pour l'API.
String dateApi(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Corps de `POST /heures-sup/demandes`.
class CreationHeuresSupRequest {
  final List<String> collaborateurIds;
  final String siteId;
  final DateTime dateDebut;
  final DateTime? dateFin;
  final int ouvertureAvantMinutes;
  final int ouvertureApresMinutes;
  final String motif;

  const CreationHeuresSupRequest({
    required this.collaborateurIds,
    required this.siteId,
    required this.dateDebut,
    this.dateFin,
    this.ouvertureAvantMinutes = 0,
    this.ouvertureApresMinutes = 0,
    required this.motif,
  });

  Map<String, dynamic> toJson() => {
        'collaborateur_ids': collaborateurIds,
        'site_id': siteId,
        'date_debut': dateApi(dateDebut),
        if (dateFin != null) 'date_fin': dateApi(dateFin!),
        if (ouvertureAvantMinutes > 0) 'ouverture_avant_minutes': ouvertureAvantMinutes,
        if (ouvertureApresMinutes > 0) 'ouverture_apres_minutes': ouvertureApresMinutes,
        'motif': motif.trim(),
      };
}

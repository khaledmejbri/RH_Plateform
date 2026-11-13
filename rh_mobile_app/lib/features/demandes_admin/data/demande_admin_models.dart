import '../../../core/format/formats.dart';
import '../../../core/libelles/libelles.dart';

class DemandeAdminItem {
  DemandeAdminItem({
    required this.id,
    required this.typeDemande,
    required this.statut,
    this.periodeDebut,
    this.periodeFin,
    this.motifRefus,
    this.contenu,
    this.demandeurNom,
    this.demandeurMatricule,
    this.demandeurUnite,
    this.numero,
    this.valideurNom,
    this.creeLe,
    this.statutLibelleApi,
    this.typeCongeLibelleApi,
    this.joursDecomptes,
    this.periodePremierJourLibelle,
    this.periodeDernierJourLibelle,
    this.annuleeApresApprobation = false,
    this.motifAnnulation,
    this.annuleParNom,
    this.annuleApresApprobationLe,
    this.joursRecredites,
    this.soldeDisponibleDemandeur,
    this.soldeReserveAutresDemandes,
    this.soldeApresDemande,
    this.peutAnnuler = false,
  });

  final String id;
  final String typeDemande;
  final String statut;
  final String? periodeDebut;
  final String? periodeFin;
  final String? motifRefus;
  final Map<String, dynamic>? contenu;

  /// SCRUM-18 : identité du demandeur (renvoyée par l'API depuis SCRUM-15) pour la file du RO.
  final String? demandeurNom;
  final String? demandeurMatricule;
  final String? demandeurUnite;

  /// Numéro lisible `DEM-AAAAMM-NNNN` (D-L02), absent tant que le serveur ne l'envoie pas.
  final String? numero;

  /// Responsable qui doit valider (`valideur_attendu_nom_complet`).
  final String? valideurNom;

  /// Date de dépôt (ISO).
  final String? creeLe;

  /// Libellés servis avec la demande (`statut_libelle`, `type_conge_libelle`, instantané du lot 3) ;
  /// à défaut, le référentiel chargé, sinon « Valeur non reconnue ».
  final String? statutLibelleApi;
  final String? typeCongeLibelleApi;

  /// Jours décomptés du solde (congé annuel), absents pour les autres types et les demandes antérieures aux soldes.
  final double? joursDecomptes;
  final String? periodePremierJourLibelle;
  final String? periodeDernierJourLibelle;

  /// Annulée par la RH après approbation : motif, auteur, date et jours recrédités.
  final bool annuleeApresApprobation;
  final String? motifAnnulation;
  final String? annuleParNom;
  final String? annuleApresApprobationLe;
  final double? joursRecredites;

  /// Champs réservés au décideur de l'étape en cours : solde acquis du demandeur, part réservée par ses
  /// autres demandes, solde après cette demande.
  final double? soldeDisponibleDemandeur;
  final double? soldeReserveAutresDemandes;
  final double? soldeApresDemande;

  /// Vrai seulement pour le demandeur d'une demande en cours (absent = faux : bouton masqué).
  final bool peutAnnuler;

  /// Vrai si le serveur a envoyé le solde du demandeur (le connecté est le décideur).
  bool get aSoldeDecideur => soldeDisponibleDemandeur != null;

  /// « Solde acquis : 10 jours, dont 2 jours réservés par d'autres demandes » ; null hors décideur.
  String? get soldeDecideurAffiche => libelleSoldeDecideur(soldeDisponibleDemandeur, soldeReserveAutresDemandes);

  /// Type de congé (`contenu.type_conge`), pour un congé.
  String? get typeConge => contenu?['type_conge'] as String?;

  /// Titre lisible : « Congé annuel », « Autorisation de sortie »…
  String get titre => Libelles.titreDemande(
        typeDemande,
        typeCongeCode: typeConge,
        typeCongeLibelleApi: typeCongeLibelleApi,
      );

  /// Libellé du type de congé : celui de l'API, sinon le référentiel chargé ; null si pas de type.
  String? get typeCongeLibelle {
    final code = (typeConge ?? '').trim();
    final api = (typeCongeLibelleApi ?? '').trim();
    if (code.isEmpty && api.isEmpty) return null;
    return Libelles.avecRepli(typeCongeLibelleApi, typeConge, Libelles.typeConge);
  }

  /// Statut lisible, jamais le code.
  String get statutLibelle => Libelles.avecRepli(statutLibelleApi, statut, Libelles.statutDemande);

  /// Référence affichée : le numéro s'il existe, jamais l'identifiant technique.
  String? get referenceAffichee {
    final n = numero?.trim();
    return (n == null || n.isEmpty) ? null : n;
  }

  /// Pièce jointe déclarée (certificat MALADIE / MATERNITE) — référence, pas de fichier (upload = cible CDC).
  String? get certificat => contenu?['certificat'] as String?;

  bool get isAutorisationSortie => typeDemande == 'AUTORISATION_SORTIE';
  String? get heureDebut => contenu?['heure_debut'] as String?;
  String? get heureFin => contenu?['heure_fin'] as String?;

  factory DemandeAdminItem.fromJson(Map<String, dynamic> j) {
    final contenuRaw = j['contenu'];
    return DemandeAdminItem(
      id: j['identifiant']?.toString() ?? '',
      typeDemande: j['type_demande'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      periodeDebut: j['periode_debut'] as String?,
      periodeFin: j['periode_fin'] as String?,
      motifRefus: j['motif_refus'] as String?,
      contenu: contenuRaw is Map<String, dynamic> ? contenuRaw : null,
      demandeurNom: j['demandeur_nom'] as String?,
      demandeurMatricule: j['demandeur_matricule'] as String?,
      demandeurUnite: j['demandeur_unite'] as String?,
      numero: j['numero'] as String?,
      valideurNom: j['valideur_attendu_nom_complet'] as String?,
      creeLe: (j['cree_le'] ?? j['date_creation']) as String?,
      statutLibelleApi: j['statut_libelle'] as String?,
      typeCongeLibelleApi: j['type_conge_libelle'] as String?,
      joursDecomptes: lireDecimal(j['jours_decomptes']),
      periodePremierJourLibelle: j['periode_premier_jour_libelle'] as String?,
      periodeDernierJourLibelle: j['periode_dernier_jour_libelle'] as String?,
      annuleeApresApprobation: j['annulee_apres_approbation'] == true,
      motifAnnulation: j['motif_annulation'] as String?,
      annuleParNom: j['annule_par_nom'] as String?,
      annuleApresApprobationLe: j['annule_apres_approbation_le'] as String?,
      joursRecredites: lireDecimal(j['jours_recredites']),
      soldeDisponibleDemandeur: lireDecimal(j['solde_disponible_demandeur']),
      soldeReserveAutresDemandes: lireDecimal(j['solde_reserve_autres_demandes']),
      soldeApresDemande: lireDecimal(j['solde_apres_demande']),
      peutAnnuler: j['peut_annuler'] == true,
    );
  }
}

class WorkflowEtape {
  WorkflowEtape({required this.code, required this.libelle, required this.terminee, required this.enCours});

  final String code;
  final String libelle;
  final bool terminee;
  final bool enCours;

  /// Libellé lisible de l'étape, jamais son code.
  String get libelleAffiche => Libelles.etapeSuivi(code, libelle);

  factory WorkflowEtape.fromJson(Map<String, dynamic> j) {
    return WorkflowEtape(
      code: j['code'] as String? ?? '',
      libelle: j['libelle'] as String? ?? '',
      terminee: j['terminee'] as bool? ?? false,
      enCours: j['en_cours'] as bool? ?? false,
    );
  }
}

class DemandeAdminSuivi {
  DemandeAdminSuivi({
    required this.id,
    required this.typeDemande,
    required this.statut,
    required this.etapeSuperieurRequise,
    required this.etapes,
  });

  final String id;
  final String typeDemande;
  final String statut;
  final bool etapeSuperieurRequise;
  final List<WorkflowEtape> etapes;

  factory DemandeAdminSuivi.fromJson(Map<String, dynamic> j) {
    final raw = j['etapes'] as List<dynamic>? ?? [];
    return DemandeAdminSuivi(
      id: j['identifiant']?.toString() ?? '',
      typeDemande: j['type_demande'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      etapeSuperieurRequise: j['etape_superieur_requise'] as bool? ?? false,
      etapes: raw.map((e) => WorkflowEtape.fromJson(Map<String, dynamic>.from(e as Map))).toList(),
    );
  }
}

class WorkflowHistoryItem {
  WorkflowHistoryItem({
    required this.id,
    required this.action,
    required this.dateAction,
    this.acteurNom,
    this.commentaire,
  });

  final String id;
  final String action;
  final String dateAction;
  final String? acteurNom;
  final String? commentaire;

  factory WorkflowHistoryItem.fromJson(Map<String, dynamic> json) {
    return WorkflowHistoryItem(
      id: json['identifiant']?.toString() ?? '',
      action: json['action'] as String? ?? '',
      dateAction: json['date_action'] as String? ?? '',
      acteurNom: json['acteur_nom'] as String?,
      commentaire: json['commentaire'] as String?,
    );
  }

  String get actionLabel => Libelles.actionHistorique(action);

  /// Commentaire sans code (anciens commentaires stockés masqués).
  String? get commentaireAffiche => Libelles.commentaireHistorique(commentaire);
}

/// Texte du décideur : « Solde acquis : 10 jours, dont 2 jours réservés par d'autres demandes ».
/// La part réservée n'apparaît que si elle est connue et non nulle. Null si le solde est inconnu.
String? libelleSoldeDecideur(double? acquis, double? reserveAutres) {
  if (acquis == null) return null;
  final base = 'Solde acquis : ${formatJours(acquis)}';
  if (reserveAutres == null || reserveAutres <= 0) return base;
  final accord = reserveAutres < 2 ? 'réservé' : 'réservés';
  return '$base, dont ${formatJours(reserveAutres)} $accord par d’autres demandes';
}

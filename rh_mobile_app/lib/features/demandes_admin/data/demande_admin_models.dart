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

  /// Type de congé (`contenu.type_conge`), pour un congé.
  String? get typeConge => contenu?['type_conge'] as String?;

  /// Titre lisible : « Congé annuel », « Autorisation de sortie »…
  String get titre => Libelles.titreDemande(typeDemande, typeCongeCode: typeConge);

  /// Statut lisible, jamais le code.
  String get statutLibelle => Libelles.statutDemande(statut);

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

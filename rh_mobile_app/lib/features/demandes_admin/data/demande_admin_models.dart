class DemandeAdminItem {
  DemandeAdminItem({
    required this.id,
    required this.typeDemande,
    required this.statut,
    this.periodeDebut,
    this.periodeFin,
    this.motifRefus,
    this.contenu,
  });

  final String id;
  final String typeDemande;
  final String statut;
  final String? periodeDebut;
  final String? periodeFin;
  final String? motifRefus;
  final Map<String, dynamic>? contenu;

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
    );
  }
}

class WorkflowEtape {
  WorkflowEtape({required this.code, required this.libelle, required this.terminee, required this.enCours});

  final String code;
  final String libelle;
  final bool terminee;
  final bool enCours;

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

  String get actionLabel {
    switch (action) {
      case 'CREATION_DEMANDE':
        return 'Demande créée';
      case 'SOUMISE_A_RO':
        return 'Soumise au RO';
      case 'VALIDATION_RO':
        return 'Validée par le RO';
      case 'REFUS_RO':
        return 'Refusée par le RO';
      case 'APPROBATION_RRH':
        return 'Approuvée par le RH';
      case 'REFUS_RRH':
        return 'Refusée par le RH';
      case 'ANNULATION_DEMANDEUR':
        return 'Annulée';
      default:
        return action.replaceAll('_', ' ').toLowerCase();
    }
  }
}

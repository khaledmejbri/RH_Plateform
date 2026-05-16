class FormationItem {
  FormationItem({
    required this.id,
    required this.typeFormation,
    required this.organisme,
    required this.dureeHeures,
    required this.statut,
    required this.origine,
    required this.cible,
    this.uniteCibleLibelle,
    this.coutEstime,
    this.justification,
    this.commentaireRh,
    this.creeLe,
  });

  final String id;
  final String typeFormation;
  final String organisme;
  final int dureeHeures;
  final String statut;
  final String origine;
  final String cible;
  final String? uniteCibleLibelle;
  final num? coutEstime;
  final String? justification;
  final String? commentaireRh;
  final String? creeLe;

  factory FormationItem.fromJson(Map<String, dynamic> json) {
    return FormationItem(
      id: json['identifiant']?.toString() ?? '',
      typeFormation: json['type_formation'] as String? ?? '',
      organisme: json['organisme'] as String? ?? '',
      dureeHeures: json['duree_heures'] as int? ?? 0,
      statut: json['statut'] as String? ?? '',
      origine: json['origine'] as String? ?? '',
      cible: json['cible'] as String? ?? '',
      uniteCibleLibelle: json['unite_cible_libelle'] as String?,
      coutEstime: json['cout_estime'] as num?,
      justification: json['justification'] as String?,
      commentaireRh: json['commentaire_rh'] as String?,
      creeLe: json['cree_le'] as String?,
    );
  }
}

class FormationUniteCible {
  FormationUniteCible({
    required this.id,
    required this.code,
    required this.libelle,
  });

  final String id;
  final String code;
  final String libelle;

  factory FormationUniteCible.fromJson(Map<String, dynamic> json) {
    return FormationUniteCible(
      id: json['identifiant']?.toString() ?? '',
      code: json['code'] as String? ?? '',
      libelle: json['libelle'] as String? ?? '',
    );
  }
}

class FormationCollaborateurCible {
  FormationCollaborateurCible({
    required this.id,
    required this.nomComplet,
    required this.matricule,
  });

  final String id;
  final String nomComplet;
  final String matricule;

  factory FormationCollaborateurCible.fromJson(Map<String, dynamic> json) {
    return FormationCollaborateurCible(
      id: json['identifiant']?.toString() ?? '',
      nomComplet: json['nom_complet'] as String? ?? '',
      matricule: json['matricule'] as String? ?? '',
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
      case 'SOUMISE_A_RH':
        return 'Soumise au RH';
      case 'NOTIFICATION_ENVOYEE':
        return 'Notification envoyée';
      case 'INTEGRATION_PLAN':
        return 'Intégrée au plan';
      case 'REFUS_RRH':
        return 'Refusée par RH';
      case 'ANNULATION_DEMANDEUR':
        return 'Annulée';
      default:
        return action.replaceAll('_', ' ').toLowerCase();
    }
  }
}

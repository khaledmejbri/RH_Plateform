class EvaluationItem {
  final String id;
  final String campaignNom;
  final String statut;
  final String collaborateurId;
  final String superieurId;
  final String superieurNom;
  final String? etapeActuelle;
  final int? scoreSur20;
  final DateTime creeLe;
  final DateTime? validationCollaborateurLe;
  final DateTime? validationSuperieurLe;

  EvaluationItem({
    required this.id,
    required this.campaignNom,
    required this.statut,
    required this.collaborateurId,
    required this.superieurId,
    required this.superieurNom,
    this.etapeActuelle,
    this.scoreSur20,
    required this.creeLe,
    this.validationCollaborateurLe,
    this.validationSuperieurLe,
  });

  factory EvaluationItem.fromJson(Map<String, dynamic> json) {
    return EvaluationItem(
      id: json['identifiant'] as String,
      campaignNom: json['campaignNom'] as String? ?? 'Campagne d\'évaluation',
      statut: json['statut'] as String,
      collaborateurId: json['collaborateurIdentifiant'] as String,
      superieurId: json['superieurIdentifiant'] as String,
      superieurNom: json['superieurNom'] as String? ?? 'Manager',
      etapeActuelle: json['etapeActuelle'] as String?,
      scoreSur20: json['scoreSur20'] as int?,
      creeLe: DateTime.parse(json['creeLe'] as String),
      validationCollaborateurLe: json['validationCollaborateurLe'] != null
          ? DateTime.parse(json['validationCollaborateurLe'] as String)
          : null,
      validationSuperieurLe: json['validationSuperieurLe'] != null
          ? DateTime.parse(json['validationSuperieurLe'] as String)
          : null,
    );
  }
}

class EvaluationQuestion {
  final String id;
  final String libelle;
  final String typeQuestion;
  final int ordre;
  final bool obligatoire;
  final String? optionsReponses;
  final int? valeurMinimale;
  final int? valeurMaximale;

  EvaluationQuestion({
    required this.id,
    required this.libelle,
    required this.typeQuestion,
    required this.ordre,
    required this.obligatoire,
    this.optionsReponses,
    this.valeurMinimale,
    this.valeurMaximale,
  });

  factory EvaluationQuestion.fromJson(Map<String, dynamic> json) {
    return EvaluationQuestion(
      id: json['identifiant'] as String,
      libelle: json['libelle'] as String,
      typeQuestion: json['typeQuestion'] as String,
      ordre: json['ordre'] as int,
      obligatoire: json['obligatoire'] as bool,
      optionsReponses: json['optionsReponses'] as String?,
      valeurMinimale: json['valeurMinimale'] as int?,
      valeurMaximale: json['valeurMaximale'] as int?,
    );
  }
}

class EvaluationAnswer {
  final String id;
  final String questionId;
  final String? reponseCollaborateur;
  final String? reponseManager;
  final String? commentaireManager;
  final int? noteAttribuee;
  final DateTime? reponduParCollaborateurLe;
  final DateTime? reponduParManagerLe;

  EvaluationAnswer({
    required this.id,
    required this.questionId,
    this.reponseCollaborateur,
    this.reponseManager,
    this.commentaireManager,
    this.noteAttribuee,
    this.reponduParCollaborateurLe,
    this.reponduParManagerLe,
  });

  factory EvaluationAnswer.fromJson(Map<String, dynamic> json) {
    return EvaluationAnswer(
      id: json['identifiant'] as String,
      questionId: json['question']['identifiant'] as String,
      reponseCollaborateur: json['reponseCollaborateur'] as String?,
      reponseManager: json['reponseManager'] as String?,
      commentaireManager: json['commentaireManager'] as String?,
      noteAttribuee: json['noteAttribuee'] as int?,
      reponduParCollaborateurLe: json['reponduParCollaborateurLe'] != null
          ? DateTime.parse(json['reponduParCollaborateurLe'] as String)
          : null,
      reponduParManagerLe: json['reponduParManagerLe'] != null
          ? DateTime.parse(json['reponduParManagerLe'] as String)
          : null,
    );
  }
}

class TechnicalQuestion {
  final String id;
  final String competence;
  final String description;
  final List<String> niveauxPermis;
  final int ordre;

  TechnicalQuestion({
    required this.id,
    required this.competence,
    required this.description,
    required this.niveauxPermis,
    required this.ordre,
  });

  factory TechnicalQuestion.fromJson(Map<String, dynamic> json) {
    return TechnicalQuestion(
      id: json['identifiant'] as String,
      competence: json['competence'] as String,
      description: json['description'] as String,
      niveauxPermis: (json['niveauxPermis'] as String)
          .split(',')
          .map((e) => e.trim())
          .toList(),
      ordre: json['ordre'] as int,
    );
  }
}

enum SkillLevel {
  debutant,
  intermediaire,
  avance,
  expert,
}

extension SkillLevelExtension on SkillLevel {
  String get label {
    return switch (this) {
      SkillLevel.debutant => 'Débutant',
      SkillLevel.intermediaire => 'Intermédiaire',
      SkillLevel.avance => 'Avancé',
      SkillLevel.expert => 'Expert',
    };
  }

  static SkillLevel fromString(String value) {
    return switch (value.toUpperCase()) {
      'DEBUTANT' => SkillLevel.debutant,
      'INTERMEDIAIRE' => SkillLevel.intermediaire,
      'AVANCE' => SkillLevel.avance,
      'EXPERT' => SkillLevel.expert,
      _ => SkillLevel.debutant,
    };
  }
}

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
      campaignNom: json['campaignNom'] as String? ?? 'Campagne evaluation',
      statut: json['statut'] as String? ?? 'EN_ATTENTE_VALIDATION_CROISEE',
      collaborateurId: json['collaborateurIdentifiant'] as String? ?? '',
      superieurId: json['superieurIdentifiant'] as String? ?? json['superieurNom'] as String? ?? '',
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
  final List<String> optionsReponses;
  final num? valeurMinimale;
  final num? valeurMaximale;
  final String? sectionCode;
  final String? sectionLibelle;
  final num poids;
  final List<String> labelsEchelle;
  final String? reponseExistante;
  final int? noteExistante;

  EvaluationQuestion({
    required this.id,
    required this.libelle,
    required this.typeQuestion,
    required this.ordre,
    required this.obligatoire,
    this.optionsReponses = const [],
    this.valeurMinimale,
    this.valeurMaximale,
    this.sectionCode,
    this.sectionLibelle,
    this.poids = 1,
    this.labelsEchelle = const [],
    this.reponseExistante,
    this.noteExistante,
  });

  factory EvaluationQuestion.fromJson(Map<String, dynamic> json) {
    final rawOptions = json['optionsReponses'] ?? json['options'];
    return EvaluationQuestion(
      id: json['identifiant'] as String,
      libelle: json['libelle'] as String? ?? json['intitule'] as String? ?? '',
      typeQuestion: json['typeQuestion'] as String? ?? json['type'] as String? ?? 'PARAGRAPH',
      ordre: json['ordre'] as int? ?? 0,
      obligatoire: json['obligatoire'] as bool? ?? false,
      optionsReponses: rawOptions is List
          ? rawOptions.map((e) => e.toString()).toList()
          : rawOptions is String && rawOptions.isNotEmpty
              ? rawOptions.split(',').map((e) => e.trim()).toList()
              : const [],
      valeurMinimale: json['valeurMinimale'] as num?,
      valeurMaximale: json['valeurMaximale'] as num?,
      sectionCode: json['sectionCode'] as String?,
      sectionLibelle: json['sectionLibelle'] as String?,
      poids: json['poids'] as num? ?? 1,
      labelsEchelle: json['labelsEchelle'] is List
          ? (json['labelsEchelle'] as List).map((e) => e.toString()).toList()
          : const [],
      reponseExistante: json['reponseExistante'] as String?,
      noteExistante: json['noteExistante'] as int?,
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
      questionId: json['question'] is Map
          ? json['question']['identifiant'] as String? ?? json['question']['id'] as String? ?? ''
          : '',
      reponseCollaborateur: json['reponseCollaborateur'] as String?,
      reponseManager: json['reponseManager'] as String?,
      commentaireManager: json['commentaireManager'] as String?,
      noteAttribuee: json['noteCollaborateur'] as int? ?? json['noteAttribuee'] as int?,
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
  final String? niveauAutoEvaluation;
  final String? commentaire;

  TechnicalQuestion({
    required this.id,
    required this.competence,
    required this.description,
    required this.niveauxPermis,
    required this.ordre,
    this.niveauAutoEvaluation,
    this.commentaire,
  });

  factory TechnicalQuestion.fromJson(Map<String, dynamic> json) {
    final rawLevels = json['niveauxPermis'] ?? json['niveauxAttendus'];
    return TechnicalQuestion(
      id: json['identifiant'] as String,
      competence: json['competence'] as String,
      description: json['description'] as String? ?? '',
      niveauxPermis: rawLevels is String
          ? rawLevels.split(',').map((e) => e.trim()).toList()
          : const ['Beginner', 'Supervised', 'Autonomous', 'Advanced', 'Expert'],
      ordre: json['ordre'] as int? ?? 0,
      niveauAutoEvaluation: json['niveauAutoEvaluation'] as String?,
      commentaire: json['commentaire'] as String?,
    );
  }
}

enum SkillLevel {
  debutant,
  supervise,
  autonome,
  avance,
  expert,
}

extension SkillLevelExtension on SkillLevel {
  String get label {
    return switch (this) {
      SkillLevel.debutant => 'Beginner',
      SkillLevel.supervise => 'Supervised',
      SkillLevel.autonome => 'Autonomous',
      SkillLevel.avance => 'Advanced',
      SkillLevel.expert => 'Expert',
    };
  }

  String get apiValue {
    return switch (this) {
      SkillLevel.debutant => 'DEBUTANT',
      SkillLevel.supervise => 'SUPERVISE',
      SkillLevel.autonome => 'AUTONOME',
      SkillLevel.avance => 'AVANCE',
      SkillLevel.expert => 'EXPERT',
    };
  }

  int get score {
    return switch (this) {
      SkillLevel.debutant => 1,
      SkillLevel.supervise => 2,
      SkillLevel.autonome => 3,
      SkillLevel.avance => 4,
      SkillLevel.expert => 5,
    };
  }

  static SkillLevel fromString(String value) {
    return switch (value.toUpperCase()) {
      'DEBUTANT' => SkillLevel.debutant,
      'SUPERVISE' => SkillLevel.supervise,
      'INTERMEDIAIRE' => SkillLevel.supervise,
      'AUTONOME' => SkillLevel.autonome,
      'AVANCE' => SkillLevel.avance,
      'EXPERT' => SkillLevel.expert,
      _ => SkillLevel.debutant,
    };
  }
}

class EvaluationAnalytics {
  final num selfAverage;
  final num managerAverage;
  final num finalScore;
  final num averageGap;
  final num discrepancyPercentage;
  final List<String> strengths;
  final List<String> improvementAreas;
  final List<String> recommendations;

  EvaluationAnalytics({
    required this.selfAverage,
    required this.managerAverage,
    required this.finalScore,
    required this.averageGap,
    required this.discrepancyPercentage,
    required this.strengths,
    required this.improvementAreas,
    required this.recommendations,
  });

  factory EvaluationAnalytics.fromJson(Map<String, dynamic> json) {
    List<String> readList(String key) => json[key] is List
        ? (json[key] as List).map((e) => e.toString()).toList()
        : const [];

    return EvaluationAnalytics(
      selfAverage: json['selfAverage'] as num? ?? 0,
      managerAverage: json['managerAverage'] as num? ?? 0,
      finalScore: json['finalScore'] as num? ?? 0,
      averageGap: json['averageGap'] as num? ?? 0,
      discrepancyPercentage: json['discrepancyPercentage'] as num? ?? 0,
      strengths: readList('strengths'),
      improvementAreas: readList('improvementAreas'),
      recommendations: readList('recommendations'),
    );
  }
}

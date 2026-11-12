import '../../../core/libelles/libelles.dart';

/// Modèles M07 — évaluations mobile (E5).
class EvaluationItem {
  final String id;
  final String campaignNom;
  final String? campaignType;
  final String statut;
  final String collaborateurId;
  final String? collaborateurNom;
  final String? collaborateurPoste;
  final String superieurId;
  /// Nom du responsable hiérarchique ; null si l'API ne le fournit pas.
  final String? superieurNom;
  final String? etapeActuelle;
  final int? scoreSur20;
  final String? familleMetierCode;
  final String? familleMetierLibelle;
  final String? niveauSeniorite;

  /// Libellé du niveau fourni par l'API (`niveau_seniorite_libelle`), prioritaire sur la table.
  final String? niveauSenioriteLibelle;
  final String? couleurAlerte;
  final DateTime creeLe;
  final DateTime? validationCollaborateurLe;
  final DateTime? validationSuperieurLe;

  EvaluationItem({
    required this.id,
    required this.campaignNom,
    this.campaignType,
    required this.statut,
    required this.collaborateurId,
    this.collaborateurNom,
    this.collaborateurPoste,
    required this.superieurId,
    this.superieurNom,
    this.etapeActuelle,
    this.scoreSur20,
    this.familleMetierCode,
    this.familleMetierLibelle,
    this.niveauSeniorite,
    this.niveauSenioriteLibelle,
    this.couleurAlerte,
    required this.creeLe,
    this.validationCollaborateurLe,
    this.validationSuperieurLe,
  });

  bool get isArchived => statut == 'ARCHIVEE';

  /// « Sarra Khemiri — Développeuse backend », ou null sans nom.
  String? get collaborateurLibelle {
    if (collaborateurNom == null) return null;
    return collaborateurPoste == null
        ? collaborateurNom
        : '$collaborateurNom — $collaborateurPoste';
  }

  bool get isTermine =>
      statut == 'VALIDEE' || statut == 'ARCHIVEE';

  /// Action collab encore requise (auto-évaluation).
  bool get actionCollaborateurRequise =>
      !isArchived &&
      validationCollaborateurLe == null &&
      statut != 'VALIDEE' &&
      statut != 'VALIDEE_COLLABORATEUR';

  /// Action manager encore requise.
  bool get actionManagerRequise =>
      !isArchived &&
      validationSuperieurLe == null &&
      (statut == 'VALIDEE_COLLABORATEUR' ||
          statut == 'EN_ATTENTE_VALIDATION_CROISEE');

  /// Vrai si l'évaluation a une note finale : validée par le responsable. Avant, l'API renvoie 0
  /// par défaut : ne jamais l'afficher comme une note (D-L01.f, R8).
  bool get aUneNote =>
      scoreSur20 != null && (statut == 'VALIDEE' || validationSuperieurLe != null);

  /// « Développement backend · Confirmé » : libellé de l'API, sinon table, jamais le code brut.
  String? get profilSnapshotLabel {
    final parts = <String>[];
    final code = familleMetierCode?.trim() ?? '';
    final lib = familleMetierLibelle?.trim() ?? '';
    if (code.isNotEmpty || lib.isNotEmpty) {
      parts.add(Libelles.familleMetier(code.isEmpty ? null : code, lib.isEmpty ? null : lib));
    }
    if ((niveauSeniorite ?? '').trim().isNotEmpty || (niveauSenioriteLibelle ?? '').trim().isNotEmpty) {
      parts.add(Libelles.avecRepli(
        niveauSenioriteLibelle,
        niveauSeniorite,
        (c) => Libelles.niveauSeniorite(c),
      ));
    }
    if (parts.isEmpty) return null;
    return parts.join(' · ');
  }

  /// Statut lisible. Vu par le responsable, une évaluation dont le collaborateur n'a pas encore
  /// validé est « En attente du collaborateur » (M-74), pas « En cours ».
  String statutLibelle({bool asManager = false}) {
    if (asManager &&
        validationCollaborateurLe == null &&
        statut != 'VALIDEE' &&
        statut != 'VALIDEE_COLLABORATEUR' &&
        statut != 'VALIDEE_SUPERIEUR' &&
        statut != 'ARCHIVEE') {
      return Libelles.statutEvaluation('EN_ATTENTE_COLLABORATEUR');
    }
    return EvaluationLabels.statut(statut);
  }

  factory EvaluationItem.fromJson(Map<String, dynamic> json) {
    final superieurRaw = json['superieurIdentifiant'] as String? ??
        json['superieurNom'] as String? ??
        '';
    return EvaluationItem(
      id: json['identifiant'] as String? ?? json['id'] as String? ?? '',
      campaignNom: json['campaignNom'] as String? ??
          json['campagneNom'] as String? ??
          'Campagne d’évaluation',
      campaignType: json['campaignType'] as String? ?? json['campagneType'] as String?,
      statut: json['statut'] as String? ?? 'EN_ATTENTE_VALIDATION_CROISEE',
      collaborateurId: json['collaborateurIdentifiant'] as String? ?? '',
      collaborateurNom: _displayName(json['collaborateurNom'] as String?),
      collaborateurPoste: _displayName(json['collaborateurPoste'] as String?),
      superieurId: superieurRaw,
      superieurNom: _displayName(json['superieurNom'] as String?),
      etapeActuelle: json['etapeActuelle'] as String?,
      scoreSur20: (json['scoreSur20'] as num?)?.toInt(),
      familleMetierCode: json['familleMetierCode'] as String? ??
          json['famille_metier_code'] as String? ??
          json['roleMetier'] as String?,
      familleMetierLibelle: json['familleMetierLibelle'] as String? ??
          json['famille_metier_libelle'] as String?,
      niveauSeniorite: json['niveauSeniorite'] as String? ??
          json['niveau_seniorite'] as String?,
      niveauSenioriteLibelle: json['niveauSenioriteLibelle'] as String? ??
          json['niveau_seniorite_libelle'] as String?,
      couleurAlerte: json['couleurAlerte'] as String? ?? json['couleur_alerte'] as String?,
      creeLe: DateTime.parse(
        json['creeLe'] as String? ?? DateTime.now().toIso8601String(),
      ),
      validationCollaborateurLe: _parseInstant(json['validationCollaborateurLe']),
      validationSuperieurLe: _parseInstant(json['validationSuperieurLe']),
    );
  }

  static DateTime? _parseInstant(dynamic value) {
    if (value == null) return null;
    if (value is String && value.isNotEmpty) return DateTime.tryParse(value);
    return null;
  }

  /// Évite d’afficher un UUID brut comme « nom ».
  static String? _displayName(String? raw) {
    if (raw == null || raw.trim().isEmpty) return null;
    final v = raw.trim();
    final uuidLike = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    );
    if (uuidLike.hasMatch(v)) return null;
    return v;
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
  final String? reponseManagerExistante;
  final int? noteManagerExistante;

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
    this.reponseManagerExistante,
    this.noteManagerExistante,
  });

  factory EvaluationQuestion.fromJson(Map<String, dynamic> json) {
    final rawOptions = json['optionsReponses'] ?? json['options'];
    return EvaluationQuestion(
      id: json['identifiant'] as String? ?? json['id'] as String? ?? '',
      libelle: json['libelle'] as String? ?? json['intitule'] as String? ?? '',
      typeQuestion:
          json['typeQuestion'] as String? ?? json['type'] as String? ?? 'PARAGRAPH',
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
      reponseManagerExistante: json['reponseManagerExistante'] as String? ??
          json['reponseManager'] as String?,
      noteManagerExistante:
          json['noteManagerExistante'] as int? ?? json['noteManager'] as int?,
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
  final int? noteCollaborateur;
  final int? noteManager;
  final DateTime? reponduParCollaborateurLe;
  final DateTime? reponduParManagerLe;

  EvaluationAnswer({
    required this.id,
    required this.questionId,
    this.reponseCollaborateur,
    this.reponseManager,
    this.commentaireManager,
    this.noteAttribuee,
    this.noteCollaborateur,
    this.noteManager,
    this.reponduParCollaborateurLe,
    this.reponduParManagerLe,
  });

  factory EvaluationAnswer.fromJson(Map<String, dynamic> json) {
    String questionId = '';
    if (json['question'] is Map) {
      final q = json['question'] as Map;
      questionId = q['identifiant'] as String? ?? q['id'] as String? ?? '';
    } else {
      questionId = json['questionIdentifiant'] as String? ??
          json['questionId'] as String? ??
          '';
    }
    return EvaluationAnswer(
      id: json['identifiant'] as String? ?? json['id'] as String? ?? '',
      questionId: questionId,
      reponseCollaborateur: json['reponseCollaborateur'] as String?,
      reponseManager: json['reponseManager'] as String?,
      commentaireManager: json['commentaireManager'] as String?,
      noteAttribuee: json['noteAttribuee'] as int?,
      noteCollaborateur: json['noteCollaborateur'] as int?,
      noteManager: json['noteManager'] as int?,
      reponduParCollaborateurLe:
          EvaluationItem._parseInstant(json['reponduParCollaborateurLe']),
      reponduParManagerLe:
          EvaluationItem._parseInstant(json['reponduParManagerLe']),
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
    List<String> levels;
    if (rawLevels is List) {
      levels = rawLevels.map((e) => e.toString()).toList();
    } else if (rawLevels is String && rawLevels.isNotEmpty) {
      levels = rawLevels.split(',').map((e) => e.trim()).toList();
    } else {
      levels = const ['Débutant', 'Supervisé', 'Autonome', 'Avancé', 'Expert'];
    }
    return TechnicalQuestion(
      id: json['identifiant'] as String? ?? json['id'] as String? ?? '',
      competence: json['competence'] as String? ?? json['libelle'] as String? ?? '',
      description: json['description'] as String? ?? '',
      niveauxPermis: levels,
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
  /// Nom du niveau, lu dans le référentiel `ECHELLE_ETOILES` (écran Paramètres).
  String get label => Libelles.niveauNote(score);

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
      'DEBUTANT' || '1' => SkillLevel.debutant,
      'SUPERVISE' || 'INTERMEDIAIRE' || '2' => SkillLevel.supervise,
      'AUTONOME' || '3' => SkillLevel.autonome,
      'AVANCE' || '4' => SkillLevel.avance,
      'EXPERT' || '5' => SkillLevel.expert,
      _ => SkillLevel.debutant,
    };
  }

  static SkillLevel fromScore(int score) {
    return switch (score) {
      1 => SkillLevel.debutant,
      2 => SkillLevel.supervise,
      3 => SkillLevel.autonome,
      4 => SkillLevel.avance,
      5 => SkillLevel.expert,
      _ => SkillLevel.debutant,
    };
  }
}

class EvaluationAnalytics {
  final num selfAverage;

  /// D-E15 : absents tant que l'évaluation n'est pas validée (collaborateur évalué).
  final num? managerAverage;
  final num? finalScore;
  final num? averageGap;
  final num? discrepancyPercentage;
  final List<String> strengths;
  final List<String> improvementAreas;
  final List<String> recommendations;

  EvaluationAnalytics({
    required this.selfAverage,
    this.managerAverage,
    this.finalScore,
    this.averageGap,
    this.discrepancyPercentage,
    required this.strengths,
    required this.improvementAreas,
    required this.recommendations,
  });

  /// Vrai si une note finale existe (jamais affichée à 0 par défaut, D-E15, R8).
  bool get aNoteFinale => finalScore != null;

  factory EvaluationAnalytics.fromJson(Map<String, dynamic> json) {
    List<String> readList(String key) => json[key] is List
        ? (json[key] as List).map((e) => e.toString()).toList()
        : const [];

    return EvaluationAnalytics(
      selfAverage: json['selfAverage'] as num? ?? 0,
      managerAverage: json['managerAverage'] as num?,
      finalScore: json['finalScore'] as num?,
      averageGap: json['averageGap'] as num?,
      discrepancyPercentage: json['discrepancyPercentage'] as num?,
      strengths: readList('strengths'),
      improvementAreas: readList('improvementAreas'),
      recommendations: readList('recommendations'),
    );
  }
}

/// Question du détail mobile (B7). D-E15 : `reponseResponsable`, `noteResponsable` et
/// `commentaireResponsable` sont absents pour le collaborateur évalué avant la validation.
class QuestionDetailMobile {
  final String questionId;
  final String libelle;
  final String? section;
  final String? type;
  final String? reponseCollaborateur;
  final int? noteCollaborateur;
  final String? reponseResponsable;
  final int? noteResponsable;
  final String? commentaireResponsable;

  const QuestionDetailMobile({
    required this.questionId,
    required this.libelle,
    this.section,
    this.type,
    this.reponseCollaborateur,
    this.noteCollaborateur,
    this.reponseResponsable,
    this.noteResponsable,
    this.commentaireResponsable,
  });

  factory QuestionDetailMobile.fromJson(Map<String, dynamic> json) {
    String? texte(Object? v) {
      final t = v?.toString().trim();
      return (t == null || t.isEmpty) ? null : t;
    }

    int? entier(Object? v) => v is num ? v.toInt() : null;
    return QuestionDetailMobile(
      questionId: texte(json['questionId']) ?? '',
      libelle: texte(json['libelle']) ?? '',
      section: texte(json['section']),
      type: texte(json['type']),
      reponseCollaborateur: texte(json['reponseCollaborateur']),
      noteCollaborateur: entier(json['noteCollaborateur']),
      reponseResponsable: texte(json['reponseResponsable']),
      noteResponsable: entier(json['noteResponsable']),
      commentaireResponsable: texte(json['commentaireResponsable']),
    );
  }

  /// Vrai si le serveur a fourni une donnée du responsable pour cette question.
  bool get aDonneesResponsable =>
      noteResponsable != null || reponseResponsable != null || commentaireResponsable != null;
}

/// Détail d'une évaluation pour le mobile (B7, `EvaluationDetailMobile`, champs vides absents).
class EvaluationDetailMobile {
  final String statut;
  final String? monRole;

  /// Texte à afficher quand les notes du responsable sont masquées (D-E15).
  final String? messageAttente;
  final int? scoreSur20;
  final double? moyenneCollaborateur;
  final double? moyenneResponsable;
  final List<QuestionDetailMobile> questionsGenerales;
  final List<QuestionDetailMobile> competencesTechniques;

  const EvaluationDetailMobile({
    required this.statut,
    this.monRole,
    this.messageAttente,
    this.scoreSur20,
    this.moyenneCollaborateur,
    this.moyenneResponsable,
    this.questionsGenerales = const [],
    this.competencesTechniques = const [],
  });

  factory EvaluationDetailMobile.fromJson(Map<String, dynamic> json) {
    List<QuestionDetailMobile> questions(Object? brut) => brut is List
        ? brut
            .whereType<Map>()
            .map((e) => QuestionDetailMobile.fromJson(Map<String, dynamic>.from(e)))
            .toList()
        : <QuestionDetailMobile>[];
    final scores = json['scores'];
    final message = json['messageAttente']?.toString().trim();
    return EvaluationDetailMobile(
      statut: json['statut']?.toString() ?? '',
      monRole: json['monRole']?.toString(),
      messageAttente: (message == null || message.isEmpty) ? null : message,
      scoreSur20: (json['scoreSur20'] as num?)?.toInt(),
      moyenneCollaborateur:
          scores is Map ? (scores['moyenneCollaborateur'] as num?)?.toDouble() : null,
      moyenneResponsable:
          scores is Map ? (scores['moyenneResponsable'] as num?)?.toDouble() : null,
      questionsGenerales: questions(json['questionsGenerales']),
      competencesTechniques: questions(json['competencesTechniques']),
    );
  }

  /// Toutes les questions (générales puis compétences) portant une donnée du responsable.
  List<QuestionDetailMobile> get questionsAvecNotesResponsable => [
        ...questionsGenerales,
        ...competencesTechniques,
      ].where((q) => q.aDonneesResponsable).toList();

  /// D-E15 : les notes du responsable ne sont montrées que si le serveur les fournit et
  /// qu'aucun message d'attente ne les masque.
  bool get afficherNotesResponsable =>
      messageAttente == null && questionsAvecNotesResponsable.isNotEmpty;
}

/// Libellés FR métier (microcopy E5 / UX § 5.2).
class EvaluationLabels {
  static String statut(String statut) => Libelles.statutEvaluation(statut);

  static String etape(String? etape) {
    return switch (etape) {
      'EVALUATION_GENERALE' => 'Étape 1 sur 2 — Questions générales',
      'EVALUATION_TECHNIQUE' => 'Étape 2 sur 2 — Compétences',
      null || '' => '',
      _ => Libelles.valeurNonReconnue,
    };
  }

  static String etapeCourt(String? etape) {
    return switch (etape) {
      'EVALUATION_GENERALE' => 'Étape 1 sur 2 · Questions générales',
      'EVALUATION_TECHNIQUE' => 'Étape 2 sur 2 · Compétences',
      null || '' => '',
      _ => Libelles.valeurNonReconnue,
    };
  }

  static String niveauSeniorite(String code) => Libelles.niveauSeniorite(code);

  /// Famille métier : libellé de l'API en priorité, puis référentiel chargé (voir [Libelles.familleMetier]).
  static String familleMetier(String codeOrLabel, [String? libelleApi]) =>
      Libelles.familleMetier(codeOrLabel, libelleApi);

  /// Appréciation d'une note sur 20 : libellé du référentiel `APPRECIATION` (voir [Libelles.appreciation]).
  static String appreciation(int? scoreSur20) => Libelles.appreciation(scoreSur20);

  static String alerte(String? code) {
    return switch (code?.toUpperCase()) {
      'VERT' => 'Situation stable',
      'ORANGE' => 'Alerte RH',
      'ROUGE' => 'Plan d’action · escalade DG',
      _ => '',
    };
  }
}

/// Parse deep-link E5 depuis content notif `EVALUATION_CAMPAGNE_OUVERTE|…`.
class EvaluationNotificationLink {
  final String? evaluationId;
  final String? campagneId;
  final String displayMessage;

  const EvaluationNotificationLink({
    this.evaluationId,
    this.campagneId,
    required this.displayMessage,
  });

  bool get hasDeepLink => evaluationId != null && evaluationId!.isNotEmpty;

  static EvaluationNotificationLink? tryParse({
    required String subject,
    required String content,
  }) {
    final raw = content.trim();
    final combined = '$subject $content'.toLowerCase();
    final isEval = raw.startsWith('EVALUATION_CAMPAGNE_OUVERTE') ||
        combined.contains('évaluation') ||
        combined.contains('evaluation') ||
        combined.contains('auto-évaluation') ||
        combined.contains('campagne');

    if (!isEval && !raw.contains('evaluation=')) {
      return null;
    }

    String? evaluationId;
    String? campagneId;
    var display = raw;

    if (raw.startsWith('EVALUATION_CAMPAGNE_OUVERTE')) {
      final parts = raw.split('|');
      for (final part in parts) {
        if (part.startsWith('evaluation=')) {
          evaluationId = part.substring('evaluation='.length).trim();
        } else if (part.startsWith('campagne=')) {
          campagneId = part.substring('campagne='.length).trim();
        }
      }
      // Dernier segment = message FR si présent.
      if (parts.length > 1) {
        final last = parts.last.trim();
        if (!last.startsWith('evaluation=') &&
            !last.startsWith('campagne=') &&
            last != 'EVALUATION_CAMPAGNE_OUVERTE') {
          display = last;
        } else {
          display = subject.isNotEmpty
              ? subject
              : 'Votre évaluation est ouverte. Complétez votre auto-évaluation.';
        }
      }
    } else {
      final evalMatch =
          RegExp(r'evaluation[=:]([0-9a-fA-F-]{36})').firstMatch(raw);
      evaluationId = evalMatch?.group(1);
      display = raw.isNotEmpty ? raw : subject;
    }

    return EvaluationNotificationLink(
      evaluationId: evaluationId,
      campagneId: campagneId,
      displayMessage: display,
    );
  }
}

/// Compétence technique sans note du RO (réponse 422 ou état de validation).
class CompetenceManquante {
  final String questionId;
  final String libelle;

  const CompetenceManquante({required this.questionId, required this.libelle});

  factory CompetenceManquante.fromJson(Map<String, dynamic> json) {
    return CompetenceManquante(
      questionId: (json['questionId'] ?? '').toString(),
      libelle: (json['libelle'] ?? '').toString(),
    );
  }

  static List<CompetenceManquante> listFromJson(Object? raw) {
    if (raw is! List) return const [];
    return raw
        .whereType<Map>()
        .map((e) => CompetenceManquante.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }
}

/// État de la validation de l’évaluation par le RO
/// (`GET .../validate/manager/etat`, SCRUM-56, D-E10).
/// Ne porte aucune information d’alerte d’écart.
class EtatValidationSuperieur {
  final String evaluationId;
  final bool dejaValidee;
  final bool collaborateurAValide;
  final bool avecCompetencesTechniques;
  final List<CompetenceManquante> competencesManquantes;
  final int nbCompetencesManquantes;
  final bool peutValider;

  const EtatValidationSuperieur({
    required this.evaluationId,
    required this.dejaValidee,
    required this.collaborateurAValide,
    required this.avecCompetencesTechniques,
    required this.competencesManquantes,
    required this.nbCompetencesManquantes,
    required this.peutValider,
  });

  factory EtatValidationSuperieur.fromJson(Map<String, dynamic> json) {
    final manquantes =
        CompetenceManquante.listFromJson(json['competencesManquantes']);
    return EtatValidationSuperieur(
      evaluationId: (json['evaluationId'] ?? '').toString(),
      dejaValidee: json['dejaValidee'] == true,
      collaborateurAValide: json['collaborateurAValide'] == true,
      avecCompetencesTechniques: json['avecCompetencesTechniques'] == true,
      competencesManquantes: manquantes,
      nbCompetencesManquantes:
          (json['nbCompetencesManquantes'] as num?)?.toInt() ??
              manquantes.length,
      peutValider: json['peutValider'] == true,
    );
  }

  /// Raison affichée quand le bouton est inactif ; null si la validation est possible.
  String? get raisonInactif {
    if (peutValider || dejaValidee) return null;
    if (!collaborateurAValide) {
      return 'Le collaborateur n’a pas encore validé son auto-évaluation.';
    }
    if (nbCompetencesManquantes > 0) {
      final noms =
          competencesManquantes.map((c) => c.libelle).where((l) => l.isNotEmpty);
      final suffixe = noms.isEmpty ? '' : ' : ${noms.join(', ')}';
      final n = nbCompetencesManquantes;
      return n == 1
          ? '1 compétence technique reste à noter$suffixe.'
          : '$n compétences techniques restent à noter$suffixe.';
    }
    return 'La validation n’est pas possible pour le moment.';
  }
}

/// Échec d’une action de validation RO, avec le code d’erreur du serveur.
class ValidationSuperieurException implements Exception {
  final int? statut;
  final String? code;
  final List<CompetenceManquante> competencesManquantes;
  final String message;

  const ValidationSuperieurException({
    required this.message,
    this.statut,
    this.code,
    this.competencesManquantes = const [],
  });

  bool get accesRefuse => statut == 403 || code == 'PAS_RO_EVALUATION';

  @override
  String toString() => message;
}

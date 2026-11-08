import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/evaluations/data/evaluation_models.dart';

void main() {
  group('D-E15 : notes du responsable masquées avant la validation', () {
    test('collaborateur, évaluation en cours : message d’attente, aucune note du responsable', () {
      final d = EvaluationDetailMobile.fromJson({
        'statut': 'VALIDEE_COLLABORATEUR',
        'monRole': 'COLLABORATEUR',
        'messageAttente': 'Les notes de votre responsable seront visibles après la validation.',
        'questionsGenerales': [
          {'questionId': 'q1', 'libelle': 'Qualité du travail', 'noteCollaborateur': 4},
        ],
      });
      expect(d.messageAttente, isNotNull);
      expect(d.scoreSur20, isNull);
      expect(d.afficherNotesResponsable, isFalse);
      expect(d.questionsGenerales.single.noteResponsable, isNull);
    });

    test('évaluation VALIDEE : notes du responsable en face, pas de message d’attente', () {
      final d = EvaluationDetailMobile.fromJson({
        'statut': 'VALIDEE',
        'monRole': 'COLLABORATEUR',
        'scoreSur20': 15,
        'scores': {'moyenneCollaborateur': 4.0, 'moyenneResponsable': 3.5},
        'questionsGenerales': [
          {
            'questionId': 'q1',
            'libelle': 'Qualité du travail',
            'noteCollaborateur': 4,
            'noteResponsable': 3,
            'commentaireResponsable': 'Bien',
          },
        ],
        'competencesTechniques': [
          {'questionId': 'q2', 'libelle': 'Java', 'noteCollaborateur': 3, 'noteResponsable': 4},
        ],
      });
      expect(d.messageAttente, isNull);
      expect(d.scoreSur20, 15);
      expect(d.moyenneResponsable, 3.5);
      expect(d.afficherNotesResponsable, isTrue);
      expect(d.questionsAvecNotesResponsable, hasLength(2));
    });

    test('statistiques sans champs du responsable : aucune valeur à zéro, aucune note finale', () {
      final a = EvaluationAnalytics.fromJson({
        'selfAverage': 3.8,
        'aNotesCollaborateur': true,
      });
      expect(a.selfAverage, 3.8);
      expect(a.managerAverage, isNull);
      expect(a.finalScore, isNull);
      expect(a.averageGap, isNull);
      expect(a.aNoteFinale, isFalse);
    });

    test('statistiques complètes après validation', () {
      final a = EvaluationAnalytics.fromJson({
        'selfAverage': 4,
        'managerAverage': 3.5,
        'finalScore': 3.75,
        'averageGap': -0.5,
      });
      expect(a.aNoteFinale, isTrue);
      expect(a.finalScore, 3.75);
    });

    test('liste /moi : scoreSur20 absent = pas de note', () {
      final e = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campagneNom': 'T4',
        'statut': 'VALIDEE_COLLABORATEUR',
        'creeLe': '2026-11-01T10:00:00',
      });
      expect(e.scoreSur20, isNull);
      expect(e.aUneNote, isFalse);
    });

    test('/reponses : questionId lu à la racine, champs du responsable optionnels', () {
      final r = EvaluationAnswer.fromJson({
        'identifiant': 'r1',
        'questionId': 'q9',
        'noteCollaborateur': 4,
      });
      expect(r.questionId, 'q9');
      expect(r.noteManager, isNull);
      expect(r.reponseManager, isNull);
      expect(r.commentaireManager, isNull);
    });
  });
}

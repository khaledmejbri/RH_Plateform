import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/evaluations/data/evaluation_models.dart';
import 'package:rh_mobile_app/features/evaluations/data/evaluation_repository.dart';

void main() {
  group('EvaluationNotificationLink', () {
    test('parse EVALUATION_CAMPAGNE_OUVERTE content', () {
      const content =
          'EVALUATION_CAMPAGNE_OUVERTE|campagne=11111111-1111-1111-1111-111111111111|evaluation=22222222-2222-2222-2222-222222222222|La campagne « Annuelle 2026 » est active.';
      final link = EvaluationNotificationLink.tryParse(
        subject: 'Votre évaluation est ouverte',
        content: content,
      );
      expect(link, isNotNull);
      expect(link!.evaluationId, '22222222-2222-2222-2222-222222222222');
      expect(link.campagneId, '11111111-1111-1111-1111-111111111111');
      expect(link.displayMessage, contains('Annuelle 2026'));
    });

    test('parse loose evaluation mention without prefix', () {
      final link = EvaluationNotificationLink.tryParse(
        subject: 'Évaluation ouverte',
        content: 'Merci de compléter votre auto-évaluation',
      );
      expect(link, isNotNull);
      expect(link!.hasDeepLink, isFalse);
    });
  });

  group('EvaluationItem snapshot', () {
    test('profilSnapshotLabel from famille + niveau', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'Campagne',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'superieurNom': 'Manager Test',
        'creeLe': '2026-08-12T10:00:00Z',
        'famille_metier_code': 'DEV_LOGICIEL',
        'niveau_seniorite': 'SENIOR',
      });
      expect(item.profilSnapshotLabel, contains('Développement'));
      expect(item.profilSnapshotLabel, contains('Senior'));
    });

    test('filters archived action', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'Campagne',
        'statut': 'ARCHIVEE',
        'superieurNom': 'Manager',
        'creeLe': '2026-08-12T10:00:00Z',
      });
      expect(item.isArchived, isTrue);
      expect(item.actionCollaborateurRequise, isFalse);
    });

    // SCRUM-33 / SCRUM-34
    test('noms du responsable et du collaborateur', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'Campagne',
        'statut': 'VALIDEE_COLLABORATEUR',
        'superieurIdentifiant': '0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0',
        'superieurNom': 'Omar Belhadj',
        'collaborateurNom': 'Sarra Khemiri',
        'collaborateurPoste': 'Développeuse backend',
        'creeLe': '2026-10-27T10:00:00Z',
      });
      expect(item.superieurNom, 'Omar Belhadj');
      expect(item.collaborateurLibelle, 'Sarra Khemiri — Développeuse backend');
    });

    test('sans nom ni UUID affiché', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'Campagne',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'superieurNom': '0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0',
        'creeLe': '2026-10-27T10:00:00Z',
      });
      expect(item.superieurNom, isNull);
      expect(item.collaborateurLibelle, isNull);
    });
  });

  group('EtatValidationSuperieur', () {
    test('désérialise un état prêt à valider', () {
      final etat = EtatValidationSuperieur.fromJson({
        'evaluationId': 'e1',
        'dejaValidee': false,
        'collaborateurAValide': true,
        'avecCompetencesTechniques': true,
        'competencesManquantes': [],
        'nbCompetencesManquantes': 0,
        'peutValider': true,
      });
      expect(etat.peutValider, isTrue);
      expect(etat.raisonInactif, isNull);
    });

    test('raison : collaborateur non validé', () {
      final etat = EtatValidationSuperieur.fromJson({
        'evaluationId': 'e1',
        'collaborateurAValide': false,
        'peutValider': false,
      });
      expect(etat.raisonInactif, contains('n’a pas encore validé'));
    });

    test('raison : compétences manquantes listées', () {
      final etat = EtatValidationSuperieur.fromJson({
        'evaluationId': 'e1',
        'collaborateurAValide': true,
        'avecCompetencesTechniques': true,
        'competencesManquantes': [
          {'questionId': 'q1', 'libelle': 'Java'},
          {'questionId': 'q2', 'libelle': 'SQL'},
        ],
        'nbCompetencesManquantes': 2,
        'peutValider': false,
      });
      expect(etat.competencesManquantes.length, 2);
      expect(etat.raisonInactif, contains('2 compétences'));
      expect(etat.raisonInactif, contains('Java, SQL'));
    });

    test('déjà validée : pas de raison', () {
      final etat = EtatValidationSuperieur.fromJson({
        'evaluationId': 'e1',
        'dejaValidee': true,
        'peutValider': false,
      });
      expect(etat.raisonInactif, isNull);
    });
  });

  group('messageValidationSuperieur', () {
    test('403 PAS_RO_EVALUATION', () {
      final m = messageValidationSuperieur(statut: 403, code: 'PAS_RO_EVALUATION');
      expect(m, contains('pas le responsable'));
    });

    test('409 collaborateur non validé', () {
      expect(
        messageValidationSuperieur(statut: 409, code: 'COLLABORATEUR_NON_VALIDE'),
        contains('n’a pas encore validé'),
      );
    });

    test('422 avec compétences manquantes', () {
      final m = messageValidationSuperieur(
        statut: 422,
        code: 'COMPETENCES_NON_NOTEES',
        competencesManquantes: const [
          CompetenceManquante(questionId: 'q1', libelle: 'Java'),
        ],
      );
      expect(m, contains('Java'));
    });
  });
}

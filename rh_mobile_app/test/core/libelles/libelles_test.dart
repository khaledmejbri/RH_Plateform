import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/features/demandes_admin/data/demande_admin_models.dart';
import 'package:rh_mobile_app/features/evaluations/data/evaluation_models.dart';

void main() {
  tearDown(Libelles.reinitialiser);

  group('codes inconnus', () {
    test('un code inconnu donne « Valeur non reconnue », jamais le code ni « En cours »', () {
      expect(Libelles.statutEvaluation('STATUT_FUTUR'), 'Valeur non reconnue');
      expect(Libelles.statutDemande('NOUVEAU_STATUT'), 'Valeur non reconnue');
      expect(Libelles.statutDocument(null), 'Valeur non reconnue');
      expect(EvaluationLabels.statut('STATUT_FUTUR'), 'Valeur non reconnue');
    });
  });

  group('demandes', () {
    test('types, statuts et sigles internes', () {
      expect(Libelles.typeDemande('CONGE'), 'Congé');
      expect(Libelles.typeDemande('ORDRE_MISSION'), 'Ordre de mission');
      expect(Libelles.titreDemande('CONGE', typeCongeCode: 'ANNUEL'), 'Congé annuel');
      expect(Libelles.statutDemande('EN_VALIDATION_RRH'), 'En attente de validation RH');
      expect(Libelles.statutDemande('ANNULEE'), 'Annulée');
    });

    test('étapes de suivi : libellé seul, jamais le code', () {
      expect(Libelles.etapeSuivi('DEPOT', 'Demande enregistrée'), 'Dépôt');
      expect(Libelles.etapeSuivi('RO', 'Validation — Omar Belhadj'), 'Validation — Omar Belhadj');
      expect(Libelles.etapeSuivi('RO'), 'Validation par le responsable');
      expect(Libelles.etapeSuivi('RRH', 'Approbation RRH'), 'Validation RH');
      expect(Libelles.etapeSuivi('CLOTURE', 'Clôture'), 'Clôture');
      expect(Libelles.etapeSuivi('ETAPE_INCONNUE'), 'Valeur non reconnue');
    });

    test('actions d’historique et commentaires sans code', () {
      expect(Libelles.actionHistorique('SOUMISE_A_RO'), 'Soumise au responsable');
      expect(Libelles.actionHistorique('VALIDATION_RO'), 'Validée par le responsable');
      expect(Libelles.commentaireHistorique('Demande CONGE créée'), isNull);
      expect(Libelles.commentaireHistorique('Approbation RRH'), isNull);
      expect(Libelles.commentaireHistorique('Demande de congé annuel créée'), 'Demande de congé annuel créée');
      expect(Libelles.commentaireHistorique('Justificatif CNSS absent'), 'Justificatif CNSS absent');
      expect(Libelles.commentaireHistorique('Étape APPROBATION_RRH'), isNull);
      expect(Libelles.assainir('Envoyée au RRH'), 'Envoyée au RH');
    });

    test('modèle de demande : numéro optionnel, jamais l’identifiant', () {
      final sans = DemandeAdminItem.fromJson({
        'identifiant': '49f92ff1-aaaa-bbbb-cccc-1234567890ab',
        'type_demande': 'CONGE',
        'statut': 'EN_VALIDATION_SUPERIEUR',
        'contenu': {'type_conge': 'ANNUEL'},
      });
      expect(sans.referenceAffichee, isNull);
      expect(sans.titre, 'Congé annuel');
      expect(sans.statutLibelle, 'En attente du responsable');

      final avec = DemandeAdminItem.fromJson({
        'identifiant': 'x',
        'type_demande': 'AUTORISATION_SORTIE',
        'statut': 'APPROUVEE',
        'numero': 'DEM-202611-0003',
        'valideur_attendu_nom_complet': 'Omar Belhadj',
      });
      expect(avec.referenceAffichee, 'DEM-202611-0003');
      expect(avec.valideurNom, 'Omar Belhadj');
    });
  });

  group('documents, formations, plaintes', () {
    test('types de document en français', () {
      expect(Libelles.typeDocument('ATTESTATION_TRAVAIL'), 'Attestation de travail');
      expect(Libelles.typeDocument('BULLETIN_PAIE'), 'Bulletin de paie');
    });

    test('statuts', () {
      expect(Libelles.statutPlainte('EN_ANALYSE'), 'En analyse');
      expect(Libelles.statutFormation('ANNULEE'), 'Annulée');
      expect(Libelles.statutDocument('EN_ATTENTE_FILE'), 'En file d’attente');
    });
  });

  group('évaluations', () {
    test('famille métier : libellé de l’API prioritaire, repli sur la table', () {
      expect(Libelles.familleMetier('DEV_BACKEND', 'Développement backend'), 'Développement backend');
      expect(Libelles.familleMetier('DEV_BACKEND'), 'Développement backend');
      expect(Libelles.familleMetier('DEV_BACKEND', 'DEV_BACKEND'), 'Développement backend');
      expect(Libelles.familleMetier('FAMILLE_X'), 'Valeur non reconnue');
    });

    test('profil de l’évaluation : « Développement backend · Confirmé », sans code brut', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'T4',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'creeLe': '2026-11-01T10:00:00Z',
        'famille_metier_code': 'DEV_BACKEND',
        'famille_metier_libelle': 'Développement backend',
        'niveau_seniorite': 'CONFIRME',
      });
      expect(item.profilSnapshotLabel, 'Développement backend · Confirmé');
      expect(item.profilSnapshotLabel, isNot(contains('DEV_BACKEND')));
    });

    test('niveau : libellé de l’API prioritaire, repli sur la table (Sénior, Chef d’équipe)', () {
      EvaluationItem avec(Map<String, dynamic> extra) => EvaluationItem.fromJson({
            'identifiant': 'e1',
            'campaignNom': 'T4',
            'statut': 'EN_COURS',
            'creeLe': '2026-11-01T10:00:00Z',
            ...extra,
          });
      expect(avec({'niveau_seniorite': 'SENIOR'}).profilSnapshotLabel, 'Sénior');
      expect(avec({'niveau_seniorite': 'TEAM_LEAD'}).profilSnapshotLabel, "Chef d'équipe");
      expect(
        avec({'niveau_seniorite': 'SENIOR', 'niveau_seniorite_libelle': 'Expert confirmé'}).profilSnapshotLabel,
        'Expert confirmé',
      );
    });

    test('pas de score factice sans note', () {
      final nonNotee = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'T4',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'creeLe': '2026-11-01T10:00:00Z',
        'scoreSur20': 0,
      });
      expect(nonNotee.aUneNote, isFalse);

      final notee = EvaluationItem.fromJson({
        'identifiant': 'e2',
        'campaignNom': 'T4',
        'statut': 'VALIDEE',
        'creeLe': '2026-11-01T10:00:00Z',
        'scoreSur20': 17,
      });
      expect(notee.aUneNote, isTrue);
    });

    test('statut vu par le responsable : « En attente du collaborateur »', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'T4',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'creeLe': '2026-11-01T10:00:00Z',
      });
      expect(item.statutLibelle(asManager: true), 'En attente du collaborateur');
      expect(item.statutLibelle(), 'En cours');
    });

    test('notes 1 à 5 nommées', () {
      expect(Libelles.noteAvecNiveau(3), '3 — Autonome');
      expect(Libelles.noteAvecNiveau(9), '9 — Valeur non reconnue');
    });
  });

  group('organigramme (D-H06)', () {
    test('intitulé dérivé du type du nœud managé', () {
      expect(Libelles.intituleResponsable('Direction'), 'Directeur');
      expect(Libelles.intituleResponsable('DIRECTION', feminin: true), 'Directrice');
      expect(Libelles.intituleResponsable('Direction Générale'), 'Directeur général');
      expect(Libelles.intituleResponsable('Département'), 'Chef de département');
      expect(Libelles.intituleResponsable('Service'), 'Chef de service');
      expect(Libelles.intituleResponsable('Service', titrePoste: 'Responsable support'), 'Responsable support');
      expect(Libelles.intituleResponsable('Inconnu'), '');
    });

    test('profils d’accès', () {
      expect(Libelles.profilAcces('RH'), 'Ressources humaines');
      expect(Libelles.profilAcces('DIRECTION'), 'Direction (consultation)');
    });
  });

  group('table alimentée par l’API (lot 3)', () {
    test('une surcharge prime sur la table locale', () {
      Libelles.surcharger('statut_demande', {'APPROUVEE': 'Acceptée'});
      expect(Libelles.statutDemande('APPROUVEE'), 'Acceptée');
      Libelles.reinitialiser();
      expect(Libelles.statutDemande('APPROUVEE'), 'Approuvée');
    });
  });
}

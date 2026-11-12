import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/features/demandes_admin/data/demande_admin_models.dart';
import 'package:rh_mobile_app/features/evaluations/data/evaluation_models.dart';
import 'package:rh_mobile_app/features/formations/data/formation_models.dart';
import 'package:rh_mobile_app/features/plaintes/data/plainte_models.dart';

import '../../support/libelles_test_data.dart';

void main() {
  setUp(chargerLibellesDeTest);
  tearDown(Libelles.reinitialiser);

  group('codes inconnus', () {
    test('un code inconnu donne « Valeur non reconnue », jamais le code ni « En cours »', () {
      expect(Libelles.statutEvaluation('STATUT_FUTUR'), 'Valeur non reconnue');
      expect(Libelles.statutDemande('NOUVEAU_STATUT'), 'Valeur non reconnue');
      expect(Libelles.statutDocument(null), 'Valeur non reconnue');
      expect(EvaluationLabels.statut('STATUT_FUTUR'), 'Valeur non reconnue');
    });
  });

  group('pseudo-statut et repli sur le libellé de l’objet (lot 3)', () {
    test('EN_ATTENTE_COLLABORATEUR : libellé local fixe, même sans référentiel chargé', () {
      expect(Libelles.statutEvaluation('EN_ATTENTE_COLLABORATEUR'), 'En attente du collaborateur');
      Libelles.reinitialiser();
      expect(Libelles.statutEvaluation('EN_ATTENTE_COLLABORATEUR'), 'En attente du collaborateur');
      expect(Libelles.statutEvaluation('VALIDEE'), 'Valeur non reconnue');
    });

    test('le responsable voit « En attente du collaborateur » avant la validation du collaborateur', () {
      final item = EvaluationItem.fromJson({
        'identifiant': 'e1',
        'campaignNom': 'T4',
        'statut': 'EN_ATTENTE_VALIDATION_CROISEE',
        'creeLe': '2026-11-01T10:00:00Z',
      });
      expect(item.statutLibelle(asManager: true), 'En attente du collaborateur');
      expect(item.statutLibelle(), 'En cours');
    });

    test('type de congé désactivé (absent du référentiel actif) : libellé de la demande prioritaire', () {
      expect(Libelles.typeConge('CONGE_SPECIAL'), 'Valeur non reconnue');
      final item = DemandeAdminItem.fromJson({
        'identifiant': 'd1',
        'type_demande': 'CONGE',
        'statut': 'APPROUVEE',
        'statut_libelle': 'Approuvée',
        'type_conge_libelle': 'Congé spécial',
        'contenu': {'type_conge': 'CONGE_SPECIAL'},
      });
      expect(item.titre, 'Congé spécial');
      expect(item.typeCongeLibelle, 'Congé spécial');
      expect(Libelles.titreDemande('CONGE', typeCongeCode: 'CONGE_SPECIAL'), 'Valeur non reconnue');
    });

    test('type de congé actif : libellé de l’API, sinon référentiel', () {
      DemandeAdminItem avec(Map<String, dynamic> extra) => DemandeAdminItem.fromJson({
            'identifiant': 'd1',
            'type_demande': 'CONGE',
            'statut': 'SOUMISE',
            'contenu': {'type_conge': 'ANNUEL'},
            ...extra,
          });
      expect(avec({}).typeCongeLibelle, 'Congé annuel');
      expect(avec({'type_conge_libelle': 'Congé payé'}).titre, 'Congé payé');
      expect(avec({'type_conge_libelle': 'Congé payé'}).typeCongeLibelle, 'Congé payé');
    });

    test('libellé de statut de l’API prioritaire pour formations et plaintes', () {
      final f = FormationItem.fromJson({'identifiant': 'f1', 'statut': 'FORMATION_FUTURE', 'statut_libelle': 'Planifiée'});
      expect(f.statutLibelle, 'Planifiée');
      final f2 = FormationItem.fromJson({'identifiant': 'f1', 'statut': 'INTEGREE_PLAN'});
      expect(f2.statutLibelle, 'Intégrée au plan');
      final p = PlainteItem.fromJson({'identifiant': 'p1', 'statut': 'ARCHIVEE_X', 'statut_libelle': 'Archivée'});
      expect(p.statutLibelle, 'Archivée');
    });
  });

  group('référentiels indexés séparément', () {
    test('un renommage de STATUT_DEMANDE n’est pas écrasé par STATUT_FORMATION (même code)', () {
      Libelles.charger({
        'STATUT_DEMANDE': [const ParametreValeur(code: 'EN_VALIDATION_RRH', libelle: 'Chez les RH (demande)')],
      });
      expect(Libelles.statutDemande('EN_VALIDATION_RRH'), 'Chez les RH (demande)');
      expect(Libelles.statutFormation('EN_VALIDATION_RRH'), 'En attente de validation RH');
      Libelles.charger({
        'STATUT_FORMATION': [const ParametreValeur(code: 'EN_VALIDATION_RRH', libelle: 'Chez les RH (formation)')],
      });
      expect(Libelles.statutDemande('EN_VALIDATION_RRH'), 'Chez les RH (demande)');
      expect(Libelles.statutFormation('EN_VALIDATION_RRH'), 'Chez les RH (formation)');
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
    test('famille métier : libellé de l’API prioritaire, repli sur le référentiel chargé', () {
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

    test('niveau : libellé de l’API prioritaire, repli sur le référentiel chargé (Sénior, Chef d’équipe)', () {
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

  group('listes paramétrées (lot 3)', () {
    test('un renommage serveur est visible après rechargement', () {
      expect(Libelles.statutDemande('APPROUVEE'), 'Approuvée');
      Libelles.charger({
        'STATUT_DEMANDE': [const ParametreValeur(code: 'APPROUVEE', libelle: 'Acceptée')],
      });
      expect(Libelles.statutDemande('APPROUVEE'), 'Acceptée');
      // Le rechargement d'un référentiel ne touche pas les autres.
      expect(Libelles.typeDocument('BULLETIN_PAIE'), 'Bulletin de paie');
    });

    test('aucune liste de secours : sans chargement, tout est « Valeur non reconnue »', () {
      Libelles.reinitialiser();
      expect(Libelles.statutDemande('APPROUVEE'), 'Valeur non reconnue');
      expect(Libelles.typeConge('ANNUEL'), 'Valeur non reconnue');
      expect(Libelles.typeDocument('ATTESTATION_TRAVAIL'), 'Valeur non reconnue');
      expect(Libelles.statutEvaluation('VALIDEE'), 'Valeur non reconnue');
      expect(Libelles.niveauNote(3), 'Valeur non reconnue');
      expect(Libelles.appreciation(16), 'Valeur non reconnue');
      expect(Libelles.valeurs(Referentiel.typeConge), isEmpty);
    });

    test('un code absent de la liste chargée donne « Valeur non reconnue »', () {
      expect(Libelles.typeConge('CONGE_FUTUR'), 'Valeur non reconnue');
    });

    test('appréciation : bornes fixes, libellé du référentiel', () {
      expect(Libelles.appreciation(null), '');
      expect(Libelles.appreciation(7), 'Insuffisant');
      expect(Libelles.appreciation(8), 'À améliorer');
      expect(Libelles.appreciation(14), 'Satisfaisant');
      expect(Libelles.appreciation(17), 'Positif');
      expect(Libelles.appreciation(18), 'Excellent');
    });

    test('mapping JSON : valeurs triées par ordre, entrées sans code ignorées, actives seulement', () {
      final r = ParametresReponse.depuisJson({
        'version': 'W/"abc"',
        'referentiels': {
          'type_conge': [
            {'code': 'B', 'libelle': 'Second', 'ordre': 2, 'actif': true, 'piece_jointe_obligatoire': true},
            {'code': 'A', 'libelle': 'Premier', 'ordre': 1},
            {'libelle': 'Sans code', 'ordre': 3},
            {'code': 'C', 'libelle': 'Désactivé', 'ordre': 4, 'actif': false},
          ],
          'TYPE_DOCUMENT': [
            {'code': 'X', 'libelle': 'Doc X', 'ordre': 1, 'sla_heures': 48, 'couleur': 'BLEU'},
          ],
        },
      });
      expect(r.version, 'W/"abc"');
      final conges = r.referentiels['TYPE_CONGE']!;
      expect(conges.map((v) => v.code).toList(), ['A', 'B', 'C']);
      expect(conges[0].pieceJointeObligatoire, isFalse);
      expect(conges[1].pieceJointeObligatoire, isTrue);
      expect(r.referentiels['TYPE_DOCUMENT']!.single.slaHeures, 48);
      expect(r.referentiels['TYPE_DOCUMENT']!.single.couleur, 'BLEU');
      Libelles.reinitialiser();
      Libelles.charger(r.referentiels);
      expect(Libelles.valeurs(Referentiel.typeConge).map((v) => v.code).toList(), ['A', 'B']);
    });

    test('charge utile illisible : erreur, jamais une table vide', () {
      expect(() => ParametresReponse.depuisJson('oops'), throwsFormatException);
      expect(() => ParametresReponse.depuisJson({'version': 'x'}), throwsFormatException);
    });

    test('libellé servi avec la demande prioritaire (type désactivé depuis)', () {
      final d = DemandeAdminItem.fromJson({
        'identifiant': 'x',
        'type_demande': 'CONGE',
        'statut': 'APPROUVEE',
        'statut_libelle': 'Validée par la RH',
        'type_conge_libelle': 'Congé de mariage',
        'contenu': {'type_conge': 'DESACTIVE'},
      });
      expect(d.titre, 'Congé de mariage');
      expect(d.statutLibelle, 'Validée par la RH');
    });
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/format/formats.dart';
import 'package:rh_mobile_app/core/notifications/notification_model.dart';
import 'package:rh_mobile_app/core/notifications/notification_navigation.dart';
import 'package:rh_mobile_app/features/conges/data/solde_models.dart';
import 'package:rh_mobile_app/features/demandes_admin/data/demande_admin_models.dart';

void main() {
  group('formatJours (D-S14)', () {
    test('accord singulier sous 2, pluriel à partir de 2', () {
      expect(formatJours(0), '0 jour');
      expect(formatJours(0.5), '0,5 jour');
      expect(formatJours(1), '1 jour');
      expect(formatJours(1.5), '1,5 jour');
      expect(formatJours(2), '2 jours');
      expect(formatJours(12.5), '12,5 jours');
    });

    test('mouvement signé avec le signe moins typographique', () {
      expect(formatJoursSigne(1.5), '+1,5 jour');
      expect(formatJoursSigne(2), '+2 jours');
      expect(formatJoursSigne(-5), '−5 jours');
      expect(formatJoursSigne(0), '0 jour');
    });

    test('lireDecimal tolère nombre, texte et absence', () {
      expect(lireDecimal(12.5), 12.5);
      expect(lireDecimal(3), 3.0);
      expect(lireDecimal('1,5'), 1.5);
      expect(lireDecimal(null), isNull);
      expect(lireDecimal('abc'), isNull);
    });
  });

  group('modèles du solde', () {
    test('Solde', () {
      final s = SoldeConge.fromJson({'acquis': 12.5, 'reserve': 2.0, 'disponible': 10.5});
      expect(s.acquis, 12.5);
      expect(s.reserve, 2.0);
      expect(s.disponible, 10.5);
    });

    test('mouvements : intitulés, détails, signe et pagination', () {
      final page = PageMouvements.fromJson({
        'contenu': [
          {
            'type': 'ACQUISITION',
            'type_libelle': 'Acquisition',
            'nombre': 1.5,
            'solde_apres': 11.5,
            'cree_le': '2026-12-01T00:05:00Z',
            'auteur_nom': 'Système (automatique)',
            'mois_acquisition_libelle': 'décembre 2026',
          },
          {
            'type': 'CONGE_PRIS',
            'type_libelle': 'Congé pris',
            'nombre': -5.0,
            'solde_apres': 5.0,
            'demande_id': 'd1',
            'demande_numero': 'DEM-202612-0001',
          },
          {
            'type': 'AJUSTEMENT',
            'type_libelle': 'Ajustement',
            'nombre': 2.0,
            'solde_apres': 12.0,
            'motif': 'Report 2025',
            'auteur_nom': 'Amel Trabelsi',
          },
        ],
        'page': 0,
        'total_pages': 2,
        'total_elements': 21,
      });
      expect(page.aSuite, isTrue);
      expect(page.contenu[0].intitule, 'Acquisition décembre 2026');
      expect(page.contenu[0].nombreAffiche, '+1,5 jour');
      expect(page.contenu[1].intitule, 'Congé pris DEM-202612-0001');
      expect(page.contenu[1].nombreAffiche, '−5 jours');
      expect(page.contenu[1].demandeId, 'd1');
      expect(page.contenu[2].intitule, 'Ajustement');
      expect(page.contenu[2].details, 'Report 2025 — Amel Trabelsi');
      expect(PageMouvements.fromJson({'contenu': [], 'page': 1, 'total_pages': 2}).aSuite, isFalse);
    });

    test('simulation sans et avec blocage', () {
      final ok = SimulationConge.fromJson({
        'jours_decomptes': 2.0,
        'disponible': 12.5,
        'disponible_apres': 10.5,
        'jours_feries': [],
        'blocage': null,
      });
      expect(ok.bloquee, isFalse);
      expect(ok.joursOuvres, '2 jours ouvrés');
      expect(SimulationConge.fromJson({'jours_decomptes': 1.0}).joursOuvres, '1 jour ouvré');

      final bloque = SimulationConge.fromJson({
        'jours_decomptes': 3.5,
        'disponible': 3.0,
        'disponible_apres': -0.5,
        'blocage': {'code': 'SOLDE_INSUFFISANT', 'libelle': 'Solde insuffisant : 3,5 jours demandés, 3 jours disponibles'},
      });
      expect(bloque.bloquee, isTrue);
      expect(bloque.blocage!.libelle, contains('Solde insuffisant'));
    });

    test('choix de périodes : un jour, premier jour, dernier jour', () {
      expect(PeriodeJour.premierJour(unSeulJour: true), ['JOURNEE', 'MATIN', 'APRES_MIDI']);
      expect(PeriodeJour.premierJour(unSeulJour: false), ['JOURNEE', 'APRES_MIDI']);
      expect(PeriodeJour.dernierJour, ['JOURNEE', 'MATIN']);
      expect(PeriodeJour.libelle('APRES_MIDI'), 'Après-midi');
    });
  });

  group('demande : champs du solde', () {
    test('champs du décideur et texte « dont … réservés »', () {
      final d = DemandeAdminItem.fromJson({
        'identifiant': 'x',
        'type_demande': 'CONGE',
        'statut': 'EN_VALIDATION_SUPERIEUR',
        'jours_decomptes': 2.0,
        'solde_disponible_demandeur': 10.0,
        'solde_reserve_autres_demandes': 3.0,
        'solde_apres_demande': 8.0,
      });
      expect(d.joursDecomptes, 2.0);
      expect(d.soldeDecideurAffiche, 'Solde acquis : 10 jours, dont 3 jours réservés par d’autres demandes');
      expect(d.soldeApresDemande, 8.0);
    });

    test('absent hors décideur ; part réservée nulle omise', () {
      final d = DemandeAdminItem.fromJson({'identifiant': 'x', 'type_demande': 'CONGE', 'statut': 'APPROUVEE'});
      expect(d.aSoldeDecideur, isFalse);
      expect(d.soldeDecideurAffiche, isNull);
      expect(libelleSoldeDecideur(10, 0), 'Solde acquis : 10 jours');
      expect(libelleSoldeDecideur(10, 1), 'Solde acquis : 10 jours, dont 1 jour réservé par d’autres demandes');
    });

    test('annulation par la RH', () {
      final d = DemandeAdminItem.fromJson({
        'identifiant': 'x',
        'type_demande': 'CONGE',
        'statut': 'ANNULEE',
        'annulee_apres_approbation': true,
        'motif_annulation': 'Erreur de saisie',
        'annule_par_nom': 'Amel Trabelsi',
        'jours_recredites': 2.0,
      });
      expect(d.annuleeApresApprobation, isTrue);
      expect(d.motifAnnulation, 'Erreur de saisie');
      expect(d.joursRecredites, 2.0);
    });
  });

  test('la notification d’ajustement ouvre « Mon solde de congé »', () {
    final n = AppNotification.fromMap({
      'id': 'n1',
      'sujet': 'Solde ajusté',
      'contenu': 'Votre solde de congé a été ajusté : +2 jours (solde : 12 jours). Motif : Report 2025',
      'objet_type': 'SOLDE_CONGE',
    });
    expect(routePourNotification(n), '/conges/solde');
  });
}

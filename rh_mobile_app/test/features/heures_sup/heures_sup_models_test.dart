import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/heures_sup/data/heures_sup_models.dart';
import 'package:rh_mobile_app/features/heures_sup/data/heures_sup_validation.dart';

void main() {
  group('parsing', () {
    test('DemandeHeuresSup', () {
      final d = DemandeHeuresSup.fromJson({
        'id': 'd1',
        'reference': 'HS-1A2B3C4D',
        'statut': 'EN_ATTENTE',
        'site_libelle': 'Siège',
        'date_debut': '2026-11-03',
        'date_fin': '2026-11-05',
        'jours': ['2026-11-03', '2026-11-04'],
        'ouverture_avant_minutes': 120,
        'ouverture_apres_minutes': 0,
        'motif': 'Clôture',
        'beneficiaires': [
          {'collaborateur_id': 'c1', 'matricule': 'M1', 'nom': 'Sarra Khemiri'},
        ],
      });
      expect(d.enAttente, isTrue);
      expect(d.statutLibelle, 'En attente');
      expect(d.periodeLibelle, '03/11/2026 → 05/11/2026');
      expect(d.ouverturesLibelle, '2 h avant');
      expect(d.beneficiaires.single.libelle, 'Sarra Khemiri (M1)');
      expect(d.jours, hasLength(2));
    });

    test('HeuresComptees avec etat', () {
      final h = HeuresComptees.fromJson({
        'nom': 'Ahmed',
        'journee': '2026-11-03',
        'minutes_comptees': 105,
        'heures_comptees': '01:45',
        'reference_demande': 'HS-1',
        'etat': 'INCOMPLET',
      });
      expect(h.minutesComptees, 105);
      expect(h.heuresComptees, '01:45');
      expect(h.etatLibelle, 'Pointage incomplet');
    });

    test('HeuresComptees : plages, instants ISO, ligne sans pointage', () {
      final h = HeuresComptees.fromJson({
        'nom': 'Sarra',
        'journee': '2026-11-03',
        'plage_avant': '04:00-06:00',
        'plage_apres': '',
        'entree': '2026-11-03T04:10:00+01:00',
        'sortie': '2026-11-03T15:40:00+01:00',
        'minutes_comptees': 105,
        'heures_comptees': '01:45',
        'etat': 'COMPLET',
      });
      expect(h.plageAvant, '04:00-06:00');
      expect(h.plageApres, isNull);
      expect(h.plagesLibelle, 'Avant 04:00-06:00');
      expect(h.sansPointage, isFalse);

      final vide = HeuresComptees.fromJson({
        'nom': 'Ahmed',
        'journee': '2026-11-04',
        'plage_avant': '04:00-06:00',
        'heures_comptees': '00:00',
        'minutes_comptees': 0,
        'etat': 'EN_COURS',
      });
      expect(vide.sansPointage, isTrue);
      expect(vide.heuresComptees, '00:00');
      expect(vide.etatLibelle, 'En cours');
    });

    test('a_venir : forme réelle de GET /heures-sup/moi', () {
      final m = MesHeuresSup.fromJson({
        'a_venir': [
          {
            'reference': 'HS-1A2B3C4D',
            'site_id': 's1',
            'site_libelle': 'Siège',
            'journee': '2026-11-03',
            'ouverture_avant_minutes': 120,
            'ouverture_apres_minutes': 30,
          },
        ],
        'comptees': [],
      });
      final a = m.aVenir.single;
      expect(a.journeeLibelle, '03/11/2026');
      expect(a.ouverturesLibelle, '2 h avant · 30 min après');
      expect(a.siteLibelle, 'Siège');
    });

    test('MesHeuresSup tolère les champs absents', () {
      final m = MesHeuresSup.fromJson({});
      expect(m.aVenir, isEmpty);
      expect(m.comptees, isEmpty);
    });

    test('statuts', () {
      expect(StatutHeuresSup.libelle('EXPIREE'), 'Expirée');
      expect(StatutHeuresSup.libelle('AUTRE'), 'AUTRE');
    });

    test('requête : ouverture à 0 omise, date_fin omise si même jour', () {
      final r = construireDemande(
        collaborateurIds: ['c1', 'c2'],
        siteId: 's1',
        dateDebut: DateTime(2026, 11, 3),
        dateFin: DateTime(2026, 11, 3),
        avantMinutes: 120,
        apresMinutes: 0,
        motif: '  Clôture  ',
      );
      final json = r.toJson();
      expect(json['collaborateur_ids'], ['c1', 'c2']);
      expect(json['date_debut'], '2026-11-03');
      expect(json.containsKey('date_fin'), isFalse);
      expect(json['ouverture_avant_minutes'], 120);
      expect(json.containsKey('ouverture_apres_minutes'), isFalse);
      expect(json['motif'], 'Clôture');
    });
  });

  group('validation du formulaire', () {
    String? valider({
      int nb = 1,
      String? site = 's1',
      DateTime? fin,
      int avant = 60,
      int apres = 0,
      String motif = 'Clôture',
    }) =>
        validerFormulaireHeuresSup(
          nbBeneficiaires: nb,
          siteId: site,
          dateDebut: DateTime(2026, 11, 3),
          dateFin: fin,
          avantMinutes: avant,
          apresMinutes: apres,
          motif: motif,
        );

    test('formulaire complet', () => expect(valider(), isNull));
    test('aucun collaborateur', () => expect(valider(nb: 0), contains('au moins un')));
    test('plus de 50 collaborateurs', () => expect(valider(nb: 51), contains('50')));
    test('site manquant', () => expect(valider(site: null), contains('site')));
    test('motif vide', () => expect(valider(motif: '   '), contains('motif')));
    test('aucune ouverture', () => expect(valider(avant: 0, apres: 0), contains('ouverture')));
    test('durée hors pas de 30 min', () => expect(valider(avant: 45), contains('invalide')));
    test('durée au-delà de 4 h', () => expect(valider(avant: 270), contains('invalide')));
    test('fin avant début', () => expect(valider(fin: DateTime(2026, 11, 2)), contains('précède')));
    test('31 jours acceptés, 32 refusés', () {
      expect(valider(fin: DateTime(2026, 12, 3)), isNull);
      expect(valider(fin: DateTime(2026, 12, 4)), contains('31 jours'));
    });
    test('une ouverture après seule suffit', () => expect(valider(avant: 0, apres: 30), isNull));
  });
}

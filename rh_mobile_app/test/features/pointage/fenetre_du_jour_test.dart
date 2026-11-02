import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/pointage/data/fenetre_du_jour.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_errors.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';
import 'package:rh_mobile_app/features/pointage/presentation/fenetre_du_jour_banner.dart';

Widget _enrober(FenetreDuJour f) =>
    MaterialApp(home: Scaffold(body: FenetreDuJourBanner(fenetre: f)));

void main() {
  group('FenetreDuJour.fromJson', () {
    test('site ouvert avec heures sup', () {
      final f = FenetreDuJour.fromJson({
        'site_id': 's1',
        'site_libelle': 'Siège',
        'avec_horaire': true,
        'journee': '2026-11-03',
        'ferme': false,
        'horaire_debut': '06:00:00',
        'horaire_fin': '15:00',
        'ouverture_debut': '05:00',
        'ouverture_fin': '16:00',
        'heures_sup': [
          {'reference': 'HS-1', 'ouverture_avant_minutes': 120, 'ouverture_apres_minutes': 0},
        ],
      });
      expect(f.resume, 'Aujourd’hui 06:00–15:00 · pointage ouvert 05:00–16:00');
      expect(f.resumeHeuresSup, contains('2 h avant'));
      expect(f.resumeHeuresSup, isNot(contains('après')));
    });

    test('site sans horaire : pas de bandeau', () {
      final f = FenetreDuJour.fromJson({'site_id': 's1', 'avec_horaire': false});
      expect(f.afficherBandeau, isFalse);
      expect(f.resume, isNull);
    });

    test('libelleDuree', () {
      expect(libelleDuree(30), '30 min');
      expect(libelleDuree(60), '1 h');
      expect(libelleDuree(90), '1 h 30');
    });
  });

  group('FenetreDuJourBanner', () {
    testWidgets('affiche horaire, fenêtre et heures sup', (tester) async {
      await tester.pumpWidget(_enrober(FenetreDuJour.fromJson({
        'avec_horaire': true,
        'ferme': false,
        'horaire_debut': '06:00',
        'horaire_fin': '15:00',
        'ouverture_debut': '05:00',
        'ouverture_fin': '16:00',
        'heures_sup': [
          {'reference': 'HS-1', 'ouverture_avant_minutes': 60, 'ouverture_apres_minutes': 30},
        ],
      })));
      expect(find.text('Aujourd’hui 06:00–15:00 · pointage ouvert 05:00–16:00'), findsOneWidget);
      expect(find.textContaining('1 h avant · 30 min après'), findsOneWidget);
    });

    testWidgets('site fermé aujourd’hui', (tester) async {
      await tester.pumpWidget(_enrober(const FenetreDuJour(avecHoraire: true, ferme: true)));
      expect(find.text('Site fermé aujourd’hui'), findsOneWidget);
    });

    testWidgets('aucun bandeau pour un site sans horaire', (tester) async {
      await tester.pumpWidget(_enrober(const FenetreDuJour(avecHoraire: false)));
      expect(find.byKey(const Key('fenetre_du_jour_banner')), findsNothing);
    });
  });

  group('REJETE_HORS_HORAIRE', () {
    test('libellé français et pas de pointage manuel', () {
      expect(messageMotifPointage(PointageStatut.rejeteHorsHoraire), contains('horaires'));
      expect(PointageStatut.estRejet(PointageStatut.rejeteHorsHoraire), isTrue);
      expect(PointageStatut.manuelPossible(PointageStatut.rejeteHorsHoraire), isFalse);
      expect(PointageStatut.manuelPossible(PointageStatut.rejeteHorsZone), isTrue);
    });

    test('le message serveur prime', () {
      expect(
        messageMotifPointage(PointageStatut.rejeteHorsHoraire,
            motifServeur: 'Pointage ouvert de 05:00 à 16:00'),
        'Pointage ouvert de 05:00 à 16:00',
      );
    });

    test('jamais la base d’un pointage manuel pendant un blocage', () {
      final maintenant = DateTime(2026, 11, 3, 10);
      final liste = [
        PointageHistoriqueItem(
          id: 'h1',
          statut: PointageStatut.rejeteHorsHoraire,
          type: 'ENTREE',
          serverTs: DateTime(2026, 11, 3, 9),
        ),
      ];
      expect(dernierRejetPourBlocage(liste, maintenant), isNull);
    });
  });

  test('messageParCodeErreur : HEURE_HORS_FENETRE', () {
    expect(messageParCodeErreur('HEURE_HORS_FENETRE'), contains('fenêtre'));
    expect(messageParCodeErreur('INCONNU'), isNull);
  });
}

// SCRUM-86 : pointage manuel sécurisé (D-P22.a, D-P24, D-P24.b, D-P24.e).

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/pointage/data/demande_manuelle_textes.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_errors.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';
import 'package:rh_mobile_app/features/pointage/presentation/validation_manuelle_dialog.dart';

Map<String, dynamic> _demandeSignalee() => {
      'id': 'd1',
      'statut': 'EN_ATTENTE',
      'statut_libelle': 'En attente',
      'type': 'ENTREE',
      'heure_declaree': '2026-11-11T08:00:00Z',
      'justification': 'GPS faible',
      'avec_signal': true,
      'signaux': [
        {'code': 'DISTANCE_SUP_2X_RAYON', 'libelle': 'Position à 280 m du site (zone de 50 m)'},
        {'code': 'SITE_QR_PAPIER', 'libelle': 'Site à QR papier : le code peut avoir été photographié'},
      ],
      'distance_metres': 280.4,
      'rayon_metres': 50,
      'precision_metres': 35.0,
      'mode_qr_libelle': 'Papier',
      'delai_demande_minutes': 100,
      'manuels_du_mois': 2,
      'seuil_mois': 3,
      'refus_du_jour': 2,
      'echeance': '2026-11-12T08:00:00',
    };

void main() {
  group('manuel_possible (D-P22.a)', () {
    test('PointageResult : lit manuel_possible et le motif', () {
      final ok = PointageResult.fromJson({'id': 'p1', 'statut': 'REJETE_HORS_ZONE', 'manuel_possible': true});
      expect(ok.manuelPossible, isTrue);
      final non = PointageResult.fromJson({
        'id': 'p2',
        'statut': 'REJETE_POSITION_SIMULEE',
        'manuel_possible': false,
        'manuel_impossible_motif': 'position simulée détectée',
      });
      expect(non.manuelPossible, isFalse);
      expect(non.manuelImpossibleMotif, 'position simulée détectée');
    });

    test('champ absent : pas de bouton, même pour un refus', () {
      final r = PointageResult.fromJson({'id': 'p3', 'statut': 'REJETE_HORS_ZONE'});
      expect(r.manuelPossible, isFalse);
      expect(PointageHistoriqueItem.fromJson({'id': 'p3', 'statut': 'REJETE_HORS_ZONE'}).manuelPossible, isFalse);
    });

    test('historique : manuel_possible lu', () {
      final h = PointageHistoriqueItem.fromJson({'id': 'h', 'statut': 'REJETE_HORS_ZONE', 'manuel_possible': true});
      expect(h.manuelPossible, isTrue);
    });
  });

  group('DemandeManuelle (signaux et statut Expirée)', () {
    test('lit signaux, distance, délais, compteurs, échéance', () {
      final d = DemandeManuelle.fromJson(_demandeSignalee());
      expect(d.avecSignal, isTrue);
      expect(d.signaux.map((s) => s.code), ['DISTANCE_SUP_2X_RAYON', 'SITE_QR_PAPIER']);
      expect(d.modeQrLibelle, 'Papier');
      expect(d.manuelsDuMois, 2);
      expect(d.seuilMois, 3);
      expect(d.refusDuJour, 2);
      expect(d.echeance, isNotNull);
    });

    test('demandeur : aucun champ réservé, aucune ligne valideur', () {
      final d = DemandeManuelle.fromJson({'id': 'd', 'statut': 'EN_ATTENTE', 'type': 'ENTREE'});
      expect(d.avecSignal, isFalse);
      expect(d.signaux, isEmpty);
      expect(lignesInfosValideur(d), isEmpty);
    });

    test('EXPIREE : libellé « Expirée », ni en attente ni validée', () {
      final d = DemandeManuelle.fromJson({'id': 'd', 'statut': 'EXPIREE', 'type': 'SORTIE'});
      expect(d.statutLibelle, 'Expirée');
      expect(d.expiree, isTrue);
      expect(d.enAttente, isFalse);
    });

    test('textes du valideur : position, précision, délai « 1 h 40 », sans coordonnées', () {
      final d = DemandeManuelle.fromJson(_demandeSignalee());
      expect(libellePosition(d), '280 m du site (zone de 50 m)');
      expect(libellePrecision(d), '± 35 m');
      expect(libelleDelaiDemande(d), 'Demandée 1 h 40 après la tentative');
      final lignes = lignesInfosValideur(d);
      expect(lignes.map((l) => l.$1), contains('Mode QR'));
      expect(lignes.map((l) => l.$2).join(' ').toLowerCase(), isNot(contains('latitude')));
    });

    test('délai de moins d’une heure : « 40 min »', () {
      final d = DemandeManuelle.fromJson({'id': 'd', 'statut': 'VALIDEE', 'delai_decision_minutes': 40});
      expect(libelleDelaiDecision(d), 'Décidée 40 min après la tentative');
    });

    test('échéance : « Expire le » en attente, « Expirée depuis » si expirée', () {
      final a = DemandeManuelle.fromJson(_demandeSignalee());
      expect(libelleEcheance(a), startsWith('Expire le 12/11 à '));
      final e = DemandeManuelle.fromJson({..._demandeSignalee(), 'statut': 'EXPIREE'});
      expect(libelleEcheance(e), startsWith('Expirée depuis le '));
    });
  });

  group('commentaire de validation (D-P24.b)', () {
    test('avec signal : obligatoire', () {
      expect(erreurCommentaireValidation('', avecSignal: true), isNotNull);
      expect(erreurCommentaireValidation('     ', avecSignal: true), isNotNull);
    });

    test('sans signal : facultatif', () {
      expect(erreurCommentaireValidation('', avecSignal: false), isNull);
    });

    test('moins de 10 caractères après trim : refusé, avec ou sans signal', () {
      expect(erreurCommentaireValidation('  123456789  ', avecSignal: true), isNotNull);
      expect(erreurCommentaireValidation('123456789', avecSignal: false), isNotNull);
      expect(erreurCommentaireValidation('  1234567890  ', avecSignal: true), isNull);
    });

    test('plus de 500 caractères : refusé', () {
      expect(erreurCommentaireValidation('a' * 501, avecSignal: false), isNotNull);
      expect(erreurCommentaireValidation('a' * 500, avecSignal: false), isNull);
    });
  });

  testWidgets('dialogue : avec signal, « Valider » reste inactif sous 10 caractères', (tester) async {
    String? resultat = 'inchangé';
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (ctx) => TextButton(
          onPressed: () async => resultat = await showDialog<String>(
            context: ctx,
            builder: (_) => ValidationManuelleDialog(
              element: 'Entrée — Sarra',
              avecSignal: true,
              signaux: DemandeManuelle.fromJson(_demandeSignalee()).signaux,
            ),
          ),
          child: const Text('ouvrir'),
        ),
      ),
    ));
    await tester.tap(find.text('ouvrir'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Position à 280 m du site'), findsOneWidget);

    FilledButton bouton() => tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Valider'));
    expect(bouton().onPressed, isNull);
    await tester.enterText(find.byKey(const Key('commentaire_validation')), 'court');
    await tester.pump();
    expect(bouton().onPressed, isNull);
    await tester.enterText(find.byKey(const Key('commentaire_validation')), '  Vérifié avec le responsable  ');
    await tester.pump();
    expect(bouton().onPressed, isNotNull);
    await tester.tap(find.widgetWithText(FilledButton, 'Valider'));
    await tester.pumpAndSettle();
    expect(resultat, 'Vérifié avec le responsable');
  });

  group('erreurs', () {
    DioException erreur(int code, Map<String, dynamic> data) => DioException(
          requestOptions: RequestOptions(path: '/x'),
          response: Response(requestOptions: RequestOptions(path: '/x'), statusCode: code, data: data),
        );

    test('le message serveur est affiché tel quel (422 à la création)', () {
      const message = 'Ce refus ne peut pas faire l’objet d’un pointage manuel : position simulée détectée.';
      expect(messageErreurPresence(erreur(422, {'code': 'REFUS_NON_RATTRAPABLE', 'message': message})), message);
    });

    test('409 DEMANDE_EXPIREE : message du serveur, repli français sinon', () {
      expect(
        messageErreurPresence(erreur(409, {'code': 'DEMANDE_EXPIREE', 'message': 'Demande expirée.'})),
        'Demande expirée.',
      );
      expect(messageErreurPresence(erreur(409, {'code': 'DEMANDE_EXPIREE'})), contains('expiré'));
      expect(codeErreurPresence(erreur(409, {'code': 'DEMANDE_EXPIREE'})), 'DEMANDE_EXPIREE');
    });
  });

  group('jourHeureTunis', () {
    test("convertit depuis l'UTC avec un décalage fixe de +1 h", () {
      expect(jourHeureTunis(DateTime.utc(2026, 11, 11, 7, 2)), '11/11 à 08:02');
      expect(jourHeureTunis(DateTime.utc(2026, 7, 1, 12, 5)), '01/07 à 13:05');
    });

    test('passe au jour suivant après 23 h UTC', () {
      expect(jourHeureTunis(DateTime.utc(2026, 11, 11, 23, 30)), '12/11 à 00:30');
    });

    test("ne dépend pas du fuseau de l'appareil", () {
      final utc = DateTime.utc(2026, 11, 11, 7, 2);
      expect(jourHeureTunis(utc.toLocal()), jourHeureTunis(utc));
    });
  });
}

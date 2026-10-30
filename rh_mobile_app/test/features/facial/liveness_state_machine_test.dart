import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/data/liveness_state_machine.dart';

FaceObservation _vis({
  int id = 1,
  double? g,
  double? d,
  double? yaw = 0,
}) =>
    FaceObservation(faceCount: 1, trackingId: id, leftEyeOpen: g, rightEyeOpen: d, headYaw: yaw);

void main() {
  late DateTime maintenant;
  DateTime horloge() => maintenant;

  setUp(() => maintenant = DateTime(2026, 10, 30, 8, 0, 0));

  LivenessStateMachine machine(List<LivenessChallenge> defis, {Duration? chrono = const Duration(seconds: 10)}) =>
      LivenessStateMachine(defis: defis, chrono: chrono, horloge: horloge);

  group('tirage des défis', () {
    test('2 défis toujours distincts, parmi les 3', () {
      final random = Random(7);
      for (var i = 0; i < 200; i++) {
        final defis = LivenessStateMachine.tirerDefis(2, random);
        expect(defis, hasLength(2));
        expect(defis.toSet(), hasLength(2));
        expect(LivenessChallenge.values, containsAll(defis));
      }
    });

    test('tous les défis sont tirables', () {
      final random = Random(1);
      final vus = <LivenessChallenge>{};
      for (var i = 0; i < 100; i++) {
        vus.addAll(LivenessStateMachine.tirerDefis(2, random));
      }
      expect(vus, LivenessChallenge.values.toSet());
    });
  });

  group('défis', () {
    test('clignement : ouvert, fermé puis ouvert', () {
      final m = machine([LivenessChallenge.cligner]);
      expect(m.mettreAJour(_vis(g: 0.9, d: 0.9, yaw: 25)), LivenessPhase.defis);
      expect(m.mettreAJour(_vis(g: 0.1, d: 0.1, yaw: 25)), LivenessPhase.defis);
      // Réouverture des yeux mais tête encore tournée : défi validé, attente de la prise de face.
      expect(m.mettreAJour(_vis(g: 0.9, d: 0.9, yaw: 25)), LivenessPhase.attenteFace);
      expect(m.defiCourant, isNull);
    });

    test('un seul oeil fermé ne valide pas le clignement', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      m.mettreAJour(_vis(g: 0.1, d: 0.9));
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      expect(m.phase, LivenessPhase.defis);
    });

    test('tête à gauche puis à droite', () {
      final m = machine([LivenessChallenge.teteGauche, LivenessChallenge.teteDroite]);
      expect(m.mettreAJour(_vis(yaw: 5)), LivenessPhase.defis);
      m.mettreAJour(_vis(yaw: 25));
      expect(m.indexDefi, 1);
      m.mettreAJour(_vis(yaw: 25)); // mauvais sens pour le second défi
      expect(m.indexDefi, 1);
      expect(m.mettreAJour(_vis(yaw: -25)), LivenessPhase.attenteFace);
    });

    test('sens de rotation inversable', () {
      final m = LivenessStateMachine(
        defis: [LivenessChallenge.teteGauche],
        inverserSensRotation: true,
        horloge: horloge,
      );
      m.mettreAJour(_vis(yaw: 25));
      expect(m.indexDefi, 0);
      m.mettreAJour(_vis(yaw: -25));
      expect(m.indexDefi, 1);
    });
  });

  group('prise de comparaison', () {
    test('réussie seulement visage de face (|angle Y| < 10), sur la même image', () {
      final m = machine([LivenessChallenge.teteGauche, LivenessChallenge.teteDroite]);
      m.mettreAJour(_vis(yaw: 25));
      m.mettreAJour(_vis(yaw: -25));
      expect(m.mettreAJour(_vis(yaw: -15)), LivenessPhase.attenteFace);
      expect(m.mettreAJour(_vis(yaw: 12)), LivenessPhase.attenteFace);
      expect(m.mettreAJour(_vis(yaw: 4)), LivenessPhase.reussi);
      expect(m.termine, isTrue);
    });

    test('une fois terminée, la machine ne bouge plus', () {
      final m = machine([LivenessChallenge.teteGauche]);
      m.mettreAJour(_vis(yaw: 25));
      m.mettreAJour(_vis(yaw: 0));
      expect(m.phase, LivenessPhase.reussi);
      expect(m.mettreAJour(const FaceObservation(faceCount: 2)), LivenessPhase.reussi);
    });
  });

  group('échecs', () {
    test('chrono de 10 s à partir de l\'affichage du premier défi', () {
      final m = machine([LivenessChallenge.cligner]);
      // Avant demarrer(), rien ne court.
      maintenant = maintenant.add(const Duration(seconds: 30));
      expect(m.tempsRestant, isNull);
      expect(m.tick(), LivenessPhase.defis);

      m.demarrer();
      maintenant = maintenant.add(const Duration(seconds: 9));
      expect(m.tempsRestant, const Duration(seconds: 1));
      expect(m.tick(), LivenessPhase.defis);
      maintenant = maintenant.add(const Duration(seconds: 2));
      expect(m.tick(), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.delai);
    });

    test('image reçue après le délai : échec', () {
      final m = machine([LivenessChallenge.teteGauche])..demarrer();
      maintenant = maintenant.add(const Duration(seconds: 11));
      expect(m.mettreAJour(_vis(yaw: 25)), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.delai);
    });

    test('sans chrono (enrôlement) : pas de délai', () {
      final m = machine([LivenessChallenge.teteGauche], chrono: null)..demarrer();
      maintenant = maintenant.add(const Duration(minutes: 5));
      expect(m.tick(), LivenessPhase.defis);
      expect(m.tempsRestant, isNull);
    });

    test('plusieurs visages : échec', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      expect(m.mettreAJour(const FaceObservation(faceCount: 2, trackingId: 1)), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.plusieursVisages);
    });

    test('changement de trackingId : échec', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(id: 1, g: 0.9, d: 0.9));
      expect(m.mettreAJour(_vis(id: 2, g: 0.1, d: 0.1)), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.changementDeVisage);
    });

    test('perte du visage de 200 ms : tolérée, le contrôle continue', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      expect(m.mettreAJour(const FaceObservation.aucunVisage()), LivenessPhase.defis);
      maintenant = maintenant.add(const Duration(milliseconds: 200));
      expect(m.mettreAJour(const FaceObservation.aucunVisage()), LivenessPhase.defis);
      expect(m.mettreAJour(_vis(g: 0.1, d: 0.1)), LivenessPhase.defis);
      expect(m.mettreAJour(_vis(g: 0.9, d: 0.9)), LivenessPhase.attenteFace);
    });

    test('perte du visage de 400 ms : perte de suivi (image reçue)', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      m.mettreAJour(const FaceObservation.aucunVisage());
      maintenant = maintenant.add(const Duration(milliseconds: 400));
      expect(m.mettreAJour(const FaceObservation.aucunVisage()), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.perteDeSuivi);
    });

    test('perte du visage de 400 ms : perte de suivi (minuteur, sans image)', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      m.mettreAJour(const FaceObservation.aucunVisage());
      maintenant = maintenant.add(const Duration(milliseconds: 400));
      expect(m.tick(), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.perteDeSuivi);
    });

    test('visage réapparu avec un autre trackingId après 200 ms : échec', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(id: 1, g: 0.9, d: 0.9));
      m.mettreAJour(const FaceObservation.aucunVisage());
      maintenant = maintenant.add(const Duration(milliseconds: 200));
      expect(m.mettreAJour(_vis(id: 2, g: 0.9, d: 0.9)), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.changementDeVisage);
    });

    test('plusieurs visages pendant la tolérance : échec', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      m.mettreAJour(const FaceObservation.aucunVisage());
      expect(m.mettreAJour(const FaceObservation(faceCount: 2, trackingId: 1)), LivenessPhase.echec);
      expect(m.echec, LivenessFailure.plusieursVisages);
    });

    test('suivi perdu (trackingId absent) après un visage suivi : échec', () {
      final m = machine([LivenessChallenge.cligner]);
      m.mettreAJour(_vis(g: 0.9, d: 0.9));
      expect(
        m.mettreAJour(const FaceObservation(faceCount: 1, leftEyeOpen: 0.9, rightEyeOpen: 0.9, headYaw: 0)),
        LivenessPhase.echec,
      );
    });

    test('avant tout visage, on attend sans échouer', () {
      final m = machine([LivenessChallenge.cligner]);
      expect(m.mettreAJour(const FaceObservation.aucunVisage()), LivenessPhase.defis);
      expect(m.mettreAJour(const FaceObservation.aucunVisage()), LivenessPhase.defis);
    });
  });

  test('scénario complet : 2 défis tirés au hasard puis prise de face', () {
    final m = LivenessStateMachine(random: Random(3), horloge: horloge);
    expect(m.defis, hasLength(2));
    for (final defi in List.of(m.defis)) {
      switch (defi) {
        case LivenessChallenge.cligner:
          m.mettreAJour(_vis(g: 0.9, d: 0.9, yaw: 20));
          m.mettreAJour(_vis(g: 0.1, d: 0.1, yaw: 20));
          m.mettreAJour(_vis(g: 0.9, d: 0.9, yaw: 20));
        case LivenessChallenge.teteGauche:
          m.mettreAJour(_vis(yaw: 25));
        case LivenessChallenge.teteDroite:
          m.mettreAJour(_vis(yaw: -25));
      }
    }
    expect(m.phase, LivenessPhase.attenteFace);
    expect(m.mettreAJour(_vis(yaw: 0)), LivenessPhase.reussi);
  });
}

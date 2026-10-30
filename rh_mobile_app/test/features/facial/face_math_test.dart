import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/data/face_math.dart';
import 'package:rh_mobile_app/features/facial/data/face_proof.dart';

void main() {
  group('scoreDepuisCosinus (formule de l\'ADR, t = 0,60)', () {
    test('cos = 0 -> 0', () => expect(scoreDepuisCosinus(0), 0));
    test('cos négatif -> 0 (borné)', () => expect(scoreDepuisCosinus(-0.4), 0));
    test('cos = t -> 92 (point de fonctionnement)', () => expect(scoreDepuisCosinus(0.6), 92));
    test('cos = 1 -> 100', () => expect(scoreDepuisCosinus(1), 100));
    test('au-dessus de 1 (erreur numérique) -> 100', () => expect(scoreDepuisCosinus(1.0001), 100));
    test('cos = 0,3 -> 46 (partie basse, linéaire)', () => expect(scoreDepuisCosinus(0.3), 46));
    test('cos = 0,8 -> 96 (partie haute, linéaire)', () => expect(scoreDepuisCosinus(0.8), 96));
    test('t calibré différent', () {
      expect(scoreDepuisCosinus(0.5, t: 0.5), 92);
      expect(scoreDepuisCosinus(0.75, t: 0.5), 96);
    });
    test('monotone croissant', () {
      var precedent = -1;
      for (var c = -0.2; c <= 1.0; c += 0.01) {
        final s = scoreDepuisCosinus(c);
        expect(s, greaterThanOrEqualTo(precedent));
        precedent = s;
      }
    });
  });

  group('cosinus et gabarit', () {
    test('vecteurs identiques -> 1', () => expect(cosinus([1, 2, 3], [1, 2, 3]), closeTo(1, 1e-9)));
    test('orthogonaux -> 0', () => expect(cosinus([1, 0], [0, 1]), closeTo(0, 1e-9)));
    test('opposés -> -1', () => expect(cosinus([1, 0], [-1, 0]), closeTo(-1, 1e-9)));
    test('vecteur nul -> 0', () => expect(cosinus([0, 0], [1, 1]), 0));
    test('tailles différentes -> erreur', () => expect(() => cosinus([1], [1, 2]), throwsArgumentError));

    test('normaliserL2 ramène la norme à 1', () {
      final v = normaliserL2([3, 4]);
      expect(v[0], closeTo(0.6, 1e-9));
      expect(v[1], closeTo(0.8, 1e-9));
    });

    test('meilleur cosinus sur les 3 embeddings d\'enrôlement', () {
      final gabarit = [
        [1.0, 0.0],
        [0.0, 1.0],
        [0.7071, 0.7071],
      ];
      expect(meilleurCosinus([0.7071, 0.7071], gabarit), closeTo(1, 1e-3));
      expect(meilleurCosinus([1.0, 0.0], gabarit), closeTo(1, 1e-9));
    });

    test('preuve : embedding identique -> score 100, vivant réussi', () {
      final g = [
        [0.6, 0.8],
        [0.8, 0.6],
        [0.0, 1.0],
      ];
      final p = FaceProof.depuisEmbedding([0.6, 0.8], g);
      expect(p.score, 100);
      expect(p.vivacite, isTrue);
      expect(p.aPreuve, isTrue);
    });

    test('preuves particulières', () {
      expect(const FaceProof.vivaciteEchouee().score, isNull);
      expect(const FaceProof.vivaciteEchouee().vivacite, isFalse);
      expect(const FaceProof.aucune().aPreuve, isFalse);
    });
  });
}

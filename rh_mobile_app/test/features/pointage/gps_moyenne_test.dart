import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/pointage/data/gps_moyenne.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_gps.dart';

void main() {
  group('GpsCalcul.moyenne (D-P03)', () {
    test('aucune mesure avec précision → null', () {
      expect(GpsCalcul.moyenne(const [GpsFix(latitude: 1, longitude: 1)]), isNull);
    });

    test('écarte les mesures 2× moins précises que la meilleure', () {
      final m = GpsCalcul.moyenne(const [
        GpsFix(latitude: 36.0, longitude: 10.0, accuracyMeters: 4),
        GpsFix(latitude: 36.0, longitude: 10.0, accuracyMeters: 6),
        GpsFix(latitude: 37.0, longitude: 11.0, accuracyMeters: 40),
      ])!;
      expect(m.nbMesures, 2);
      expect(m.latitude, closeTo(36.0, 1e-9));
      expect(m.precisionMetres, inInclusiveRange(4, 6));
    });

    test('pondère vers la mesure la plus précise', () {
      final m = GpsCalcul.moyenne(const [
        GpsFix(latitude: 36.0000, longitude: 10.0, accuracyMeters: 3),
        GpsFix(latitude: 36.0001, longitude: 10.0, accuracyMeters: 6),
      ])!;
      expect(m.latitude, lessThan(36.00005));
    });

    test('signale une position simulée', () {
      final m = GpsCalcul.moyenne(const [
        GpsFix(latitude: 36, longitude: 10, accuracyMeters: 3, isMock: true),
      ])!;
      expect(m.positionSimulee, isTrue);
    });
  });

  test('distance Haversine ≈ 25 m pour 25/111320 degré de latitude', () {
    final d = GpsCalcul.distanceMetres(36.8065, 10.1815, 36.8065 + 25 / 111320, 10.1815);
    expect(d, closeTo(25, 0.5));
  });

  group('verdictTerrain (règles D-P01)', () {
    test('dans la zone', () {
      expect(
        verdictTerrain(distanceMetres: 12, precisionMetres: 5, rayonMetres: 20, positionSimulee: false),
        VerdictTerrain.dansLaZone,
      );
    });
    test('précision > rayon', () {
      expect(
        verdictTerrain(distanceMetres: 5, precisionMetres: 30, rayonMetres: 20, positionSimulee: false),
        VerdictTerrain.precisionInsuffisante,
      );
    });
    test('hors zone', () {
      expect(
        verdictTerrain(distanceMetres: 25, precisionMetres: 5, rayonMetres: 20, positionSimulee: false),
        VerdictTerrain.horsZone,
      );
    });
    test('position simulée prioritaire', () {
      expect(
        verdictTerrain(distanceMetres: 1, precisionMetres: 2, rayonMetres: 20, positionSimulee: true),
        VerdictTerrain.positionSimulee,
      );
    });
  });
}

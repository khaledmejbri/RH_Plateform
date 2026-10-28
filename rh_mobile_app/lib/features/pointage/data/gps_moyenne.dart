import 'dart:math' as math;

import 'presence_gps.dart';

/// Résultat de la pose d'un emplacement par moyenne de mesures (D-P03).
class MoyenneGps {
  const MoyenneGps({
    required this.latitude,
    required this.longitude,
    required this.precisionMetres,
    required this.nbMesures,
    required this.positionSimulee,
  });

  final double latitude;
  final double longitude;

  /// Précision retenue : moyenne des précisions des mesures gardées, pondérée comme la position.
  final double precisionMetres;
  final int nbMesures;

  /// Au moins une mesure venait d'une appli de faux GPS.
  final bool positionSimulee;
}

/// Calculs GPS côté mobile : moyenne pondérée des mesures et distance au site.
abstract final class GpsCalcul {
  /// Les mesures plus de 2 fois moins précises que la meilleure sont écartées.
  static const facteurEcart = 2.0;

  /// Moyenne pondérée par 1/précision² des mesures avec précision connue ; null si aucune.
  static MoyenneGps? moyenne(List<GpsFix> mesures) {
    final avecPrecision = mesures
        .where((m) => m.accuracyMeters != null && m.accuracyMeters! > 0)
        .toList();
    if (avecPrecision.isEmpty) return null;

    final meilleure = avecPrecision
        .map((m) => m.accuracyMeters!)
        .reduce(math.min);
    final gardees = avecPrecision
        .where((m) => m.accuracyMeters! <= meilleure * facteurEcart)
        .toList();

    var sommePoids = 0.0;
    var lat = 0.0;
    var lon = 0.0;
    var precision = 0.0;
    for (final m in gardees) {
      final poids = 1 / (m.accuracyMeters! * m.accuracyMeters!);
      sommePoids += poids;
      lat += m.latitude * poids;
      lon += m.longitude * poids;
      precision += m.accuracyMeters! * poids;
    }
    return MoyenneGps(
      latitude: lat / sommePoids,
      longitude: lon / sommePoids,
      precisionMetres: precision / sommePoids,
      nbMesures: gardees.length,
      positionSimulee: mesures.any((m) => m.isMock),
    );
  }

  /// Distance Haversine en mètres (même formule que svc-presence).
  static double distanceMetres(
    double lat1,
    double lon1,
    double lat2,
    double lon2,
  ) {
    const rayonTerre = 6371000.0;
    double rad(double d) => d * math.pi / 180;
    final dLat = rad(lat2 - lat1);
    final dLon = rad(lon2 - lon1);
    final a = math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(rad(lat1)) *
            math.cos(rad(lat2)) *
            math.sin(dLon / 2) *
            math.sin(dLon / 2);
    return 2 * rayonTerre * math.asin(math.min(1, math.sqrt(a)));
  }
}

/// Verdict affiché par l'écran de test terrain (D-P02), aligné sur les règles serveur D-P01.
enum VerdictTerrain { dansLaZone, horsZone, precisionInsuffisante, positionSimulee }

VerdictTerrain verdictTerrain({
  required double distanceMetres,
  required double? precisionMetres,
  required int rayonMetres,
  required bool positionSimulee,
}) {
  if (positionSimulee) return VerdictTerrain.positionSimulee;
  if (precisionMetres == null || precisionMetres > rayonMetres) {
    return VerdictTerrain.precisionInsuffisante;
  }
  return distanceMetres <= rayonMetres
      ? VerdictTerrain.dansLaZone
      : VerdictTerrain.horsZone;
}

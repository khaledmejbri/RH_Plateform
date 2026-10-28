import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

/// Position GPS appareil (lat/lon/accuracy) pour pointage / pose site.
class GpsFix {
  const GpsFix({
    required this.latitude,
    required this.longitude,
    this.accuracyMeters,
    this.isMock = false,
  });

  final double latitude;
  final double longitude;
  final double? accuracyMeters;

  /// Position fournie par une appli de faux GPS (Android isMock, iOS isSimulatedBySoftware).
  final bool isMock;
}

/// GPS appareil : permission_handler + canal natif Android `rh_connect/location`.
class PresenceGps {
  PresenceGps._();

  static const _channel = MethodChannel('rh_connect/location');

  /// Position courante ou message d’erreur métier (jamais silent success).
  /// [fresh] : ignore la dernière position en cache (mesures successives de la pose du site).
  static Future<({GpsFix? position, String? error})> obtain({
    bool fresh = false,
  }) async {
    try {
      final serviceOk = await _channel.invokeMethod<bool>('isLocationEnabled');
      if (serviceOk != true) {
        return (
          position: null,
          error:
              'La localisation est désactivée. Activez le GPS pour pointer.',
        );
      }
    } on MissingPluginException {
      return (
        position: null,
        error:
            'Localisation indisponible sur cette plateforme. Utilisez un iPhone ou un Android.',
      );
    } on PlatformException catch (e) {
      if (e.code == 'DISABLED') {
        return (
          position: null,
          error:
              'La localisation est désactivée. Activez le GPS pour pointer.',
        );
      }
      return (
        position: null,
        error: e.message ??
            'Impossible d’obtenir la position GPS. Réessayez à l’extérieur.',
      );
    }

    var status = await Permission.locationWhenInUse.status;
    if (status.isDenied) {
      status = await Permission.locationWhenInUse.request();
    }
    if (status.isPermanentlyDenied) {
      return (
        position: null,
        error:
            'Localisation définitivement refusée. Autorisez-la dans les réglages de l’appareil.',
      );
    }
    if (!status.isGranted) {
      return (
        position: null,
        error:
            'Autorisation de localisation refusée. Activez-la dans les réglages.',
      );
    }

    try {
      final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'getCurrentPosition',
        {'fresh': fresh},
      );
      if (raw == null) {
        return (
          position: null,
          error:
              'Impossible d’obtenir la position GPS. Réessayez à l’extérieur.',
        );
      }
      final lat = (raw['latitude'] as num?)?.toDouble();
      final lon = (raw['longitude'] as num?)?.toDouble();
      if (lat == null || lon == null) {
        return (
          position: null,
          error:
              'Impossible d’obtenir la position GPS. Réessayez à l’extérieur.',
        );
      }
      final accuracy = (raw['accuracyMeters'] as num?)?.toDouble();
      return (
        position: GpsFix(
          latitude: lat,
          longitude: lon,
          accuracyMeters: accuracy,
          isMock: raw['isMock'] == true,
        ),
        error: null,
      );
    } on PlatformException catch (e) {
      if (e.code == 'DISABLED') {
        return (
          position: null,
          error:
              'La localisation est désactivée. Activez le GPS pour pointer.',
        );
      }
      return (
        position: null,
        error: e.message ??
            'Impossible d’obtenir la position GPS. Réessayez à l’extérieur.',
      );
    } catch (_) {
      return (
        position: null,
        error:
            'Impossible d’obtenir la position GPS. Réessayez à l’extérieur.',
      );
    }
  }

  /// Mesures successives pendant [duree] (pose du site, D-P03).
  /// [onMesure] reçoit chaque mesure ; renvoie la liste ou l'erreur si aucune mesure.
  static Future<({List<GpsFix> mesures, String? error})> echantillonner({
    Duration duree = const Duration(seconds: 15),
    int maxMesures = 15,
    void Function(GpsFix mesure)? onMesure,
  }) async {
    final mesures = <GpsFix>[];
    final fin = DateTime.now().add(duree);
    String? derniereErreur;
    while (DateTime.now().isBefore(fin) && mesures.length < maxMesures) {
      final r = await obtain(fresh: true);
      if (r.position != null) {
        mesures.add(r.position!);
        onMesure?.call(r.position!);
      } else {
        derniereErreur = r.error;
        if (mesures.isEmpty) break;
      }
      await Future<void>.delayed(const Duration(milliseconds: 800));
    }
    return (
      mesures: mesures,
      error: mesures.isEmpty
          ? (derniereErreur ?? 'Impossible d’obtenir la position GPS.')
          : null,
    );
  }
}

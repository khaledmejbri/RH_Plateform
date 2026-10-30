import 'dart:math';

import 'install_marker.dart';
import 'secure_kv.dart';

/// UUID v4 aléatoire (sécurisé).
String genererUuidV4([Random? random]) {
  final r = random ?? Random.secure();
  final bytes = List<int>.generate(16, (_) => r.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final h = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-'
      '${h.substring(16, 20)}-${h.substring(20)}';
}

/// Identifiant d'appareil par installation (ADR-M09-FACE § 2).
///
/// UUID v4 généré au premier lancement, conservé dans le stockage sécurisé (clé `rh_device_id`).
/// iOS : le Keychain survit à la désinstallation ; un [InstallMarker] (fichier du dossier de l'appli)
/// absent au démarrage signale une nouvelle installation : identifiant effacé, [surReinstallation]
/// appelée (effacement des gabarits et de la file d'attente) puis marqueur recréé.
class DeviceIdService {
  DeviceIdService({
    required SecureKv kv,
    InstallMarker? marker,
    Future<void> Function()? surReinstallation,
    String Function()? generateur,
  })  : _kv = kv,
        _marker = marker,
        _surReinstallation = surReinstallation,
        _generateur = generateur ?? genererUuidV4;

  static const cle = 'rh_device_id';

  final SecureKv _kv;
  final InstallMarker? _marker;
  final Future<void> Function()? _surReinstallation;
  final String Function() _generateur;
  String? _cache;

  /// Identifiant de cet appareil (créé si besoin).
  Future<String> obtenir() async {
    final c = _cache;
    if (c != null) return c;
    final marker = _marker;
    if (marker != null && !await marker.existe()) {
      await _kv.delete(cle);
      await _surReinstallation?.call();
      await marker.creer();
    }
    var id = await _kv.read(cle);
    if (id == null || id.isEmpty) {
      id = _generateur();
      await _kv.write(cle, id);
    }
    _cache = id;
    return id;
  }
}

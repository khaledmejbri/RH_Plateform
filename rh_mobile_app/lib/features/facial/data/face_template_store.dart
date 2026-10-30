import 'dart:convert';

import 'secure_kv.dart';

/// Gabarit facial local : 3 embeddings d'enrôlement, **jamais envoyés au serveur**, stockés par compte
/// (`facial_gabarit_<compteId>`) dans le stockage sécurisé (ThisDeviceOnly).
class FaceTemplateStore {
  FaceTemplateStore(this._kv);

  static const prefixe = 'facial_gabarit_';
  final SecureKv _kv;

  String _cle(String compteId) => '$prefixe$compteId';

  Future<void> enregistrer(String compteId, List<List<double>> embeddings) =>
      _kv.write(_cle(compteId), jsonEncode(embeddings));

  /// `null` si absent ou illisible.
  Future<List<List<double>>?> lire(String compteId) async {
    final raw = await _kv.read(_cle(compteId));
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List || decoded.isEmpty) return null;
      return [
        for (final v in decoded)
          [for (final x in (v as List)) (x as num).toDouble()],
      ];
    } catch (_) {
      return null;
    }
  }

  Future<bool> existe(String compteId) async => (await lire(compteId)) != null;

  Future<void> effacer(String compteId) => _kv.delete(_cle(compteId));

  /// Réinstallation : efface les gabarits de tous les comptes.
  Future<void> effacerTout() async {
    for (final k in await _kv.cles()) {
      if (k.startsWith(prefixe)) await _kv.delete(k);
    }
  }
}

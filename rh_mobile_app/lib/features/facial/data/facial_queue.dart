import 'dart:convert';

import 'secure_kv.dart';

enum TypeOperationFaciale { retrait, enrolement }

/// Opération à rejouer quand le réseau revient. Rattachée à un compte : elle n'est envoyée qu'avec le jeton
/// de ce compte.
class OperationFaciale {
  const OperationFaciale({
    required this.type,
    required this.compteId,
    required this.creeLe,
    this.deviceId,
    this.versionConsentement,
    this.modeleVersion,
    this.idempotencyKey,
  });

  final TypeOperationFaciale type;
  final String compteId;
  final DateTime creeLe;

  /// Enrôlement uniquement.
  final String? deviceId;
  final String? versionConsentement;
  final String? modeleVersion;
  final String? idempotencyKey;

  Map<String, dynamic> toJson() => {
        'type': type.name,
        'compte_id': compteId,
        'cree_le': creeLe.toUtc().toIso8601String(),
        if (deviceId != null) 'device_id': deviceId,
        if (versionConsentement != null) 'version_consentement': versionConsentement,
        if (modeleVersion != null) 'modele_version': modeleVersion,
        if (idempotencyKey != null) 'idempotency_key': idempotencyKey,
      };

  static OperationFaciale? tryFromJson(Object? raw) {
    if (raw is! Map) return null;
    final type = TypeOperationFaciale.values
        .where((t) => t.name == raw['type'])
        .cast<TypeOperationFaciale?>()
        .firstWhere((_) => true, orElse: () => null);
    final compte = raw['compte_id'];
    if (type == null || compte is! String) return null;
    return OperationFaciale(
      type: type,
      compteId: compte,
      creeLe: DateTime.tryParse('${raw['cree_le']}') ?? DateTime.now().toUtc(),
      deviceId: raw['device_id'] as String?,
      versionConsentement: raw['version_consentement'] as String?,
      modeleVersion: raw['modele_version'] as String?,
      idempotencyKey: raw['idempotency_key'] as String?,
    );
  }
}

/// Résultat de l'envoi d'une opération.
enum ResultatEnvoi {
  /// Appliquée par le serveur : retirée de la file.
  ok,

  /// Refus définitif (409, 422...) : retirée, rien à rejouer.
  definitif,

  /// Réseau ou serveur indisponible : conservée, on s'arrête.
  reessayer,
}

/// File d'attente persistante des opérations faciales (stockage sécurisé).
///
/// Règles : le **retrait est prioritaire** (toujours envoyé en premier) et annule tout enrôlement en attente
/// du même compte ; un enrôlement porte son `idempotency_key` (un rejeu n'est jamais compté deux fois) ;
/// un seul enrôlement en attente par compte (le dernier).
class FacialQueue {
  FacialQueue(this._kv);

  static const cle = 'facial_file_attente';
  final SecureKv _kv;

  Future<List<OperationFaciale>> toutes() async {
    final raw = await _kv.read(cle);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];
      return decoded.map(OperationFaciale.tryFromJson).whereType<OperationFaciale>().toList();
    } catch (_) {
      return [];
    }
  }

  Future<void> _ecrire(List<OperationFaciale> ops) async {
    if (ops.isEmpty) {
      await _kv.delete(cle);
    } else {
      await _kv.write(cle, jsonEncode(ops.map((o) => o.toJson()).toList()));
    }
  }

  Future<void> ajouter(OperationFaciale op) async {
    final ops = await toutes();
    switch (op.type) {
      case TypeOperationFaciale.retrait:
        ops.removeWhere((o) => o.compteId == op.compteId);
        ops.add(op);
      case TypeOperationFaciale.enrolement:
        ops.removeWhere(
            (o) => o.compteId == op.compteId && o.type == TypeOperationFaciale.enrolement);
        ops.add(op);
    }
    await _ecrire(ops);
  }

  Future<bool> aRetraitEnAttente(String compteId) async => (await toutes())
      .any((o) => o.compteId == compteId && o.type == TypeOperationFaciale.retrait);

  /// Opérations du compte dans l'ordre d'envoi (retrait d'abord).
  Future<List<OperationFaciale>> pourCompte(String compteId) async {
    final ops = (await toutes()).where((o) => o.compteId == compteId).toList();
    ops.sort((a, b) {
      final pa = a.type == TypeOperationFaciale.retrait ? 0 : 1;
      final pb = b.type == TypeOperationFaciale.retrait ? 0 : 1;
      return pa != pb ? pa - pb : a.creeLe.compareTo(b.creeLe);
    });
    return ops;
  }

  /// Envoie les opérations du compte ; s'arrête au premier [ResultatEnvoi.reessayer].
  /// Renvoie le nombre d'opérations réglées (appliquées ou refusées définitivement).
  Future<int> vider(
    String compteId,
    Future<ResultatEnvoi> Function(OperationFaciale op) envoyer,
  ) async {
    var regles = 0;
    for (final op in await pourCompte(compteId)) {
      final res = await envoyer(op);
      if (res == ResultatEnvoi.reessayer) break;
      await _retirer(op);
      regles++;
    }
    return regles;
  }

  Future<void> _retirer(OperationFaciale op) async {
    final ops = await toutes();
    ops.removeWhere((o) =>
        o.compteId == op.compteId &&
        o.type == op.type &&
        o.idempotencyKey == op.idempotencyKey &&
        o.creeLe == op.creeLe);
    await _ecrire(ops);
  }

  /// Réinstallation : file vidée.
  Future<void> effacerTout() => _kv.delete(cle);
}

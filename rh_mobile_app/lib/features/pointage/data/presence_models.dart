// Modèles M09 — présence QR + GPS (vague Must).

enum TypePointage {
  entree('ENTREE'),
  sortie('SORTIE');

  const TypePointage(this.apiValue);
  final String apiValue;

  static TypePointage? tryParse(String? raw) {
    if (raw == null) return null;
    final v = raw.trim().toUpperCase();
    for (final t in TypePointage.values) {
      if (t.apiValue == v) return t;
    }
    return null;
  }
}

/// Statuts stables du contrat API (HTTP 200 y compris pour REJETE_*).
abstract final class PointageStatut {
  static const valide = 'VALIDE';
  static const rejeteQrInvalide = 'REJETE_QR_INVALIDE';
  static const rejeteQrExpire = 'REJETE_QR_EXPIRE';
  static const rejeteQrRevoque = 'REJETE_QR_REVOQUE';
  static const rejeteHorsZone = 'REJETE_HORS_ZONE';
  static const rejeteSiteInactif = 'REJETE_SITE_INACTIF';
  static const rejeteEmplacementAbsent = 'REJETE_EMPLACEMENT_ABSENT';
  static const rejetePrecisionGps = 'REJETE_PRECISION_GPS';
  static const rejetePositionSimulee = 'REJETE_POSITION_SIMULEE';
  static const valideManuel = 'VALIDE_MANUEL';

  static bool estValide(String? s) => s == valide;

  static bool estRejet(String? s) =>
      s != null && s.startsWith('REJETE_');
}

class PointageRequest {
  final String qrToken;
  final TypePointage type;
  final double latitude;
  final double longitude;
  final double? accuracyMeters;
  final DateTime? clientTimestamp;
  final String? deviceId;
  final String idempotencyKey;

  /// Indicateur OS « position simulée » (D-P01).
  final bool positionSimulee;

  const PointageRequest({
    required this.qrToken,
    required this.type,
    required this.latitude,
    required this.longitude,
    this.accuracyMeters,
    this.clientTimestamp,
    this.deviceId,
    required this.idempotencyKey,
    this.positionSimulee = false,
  });

  Map<String, dynamic> toJson() => {
        'qr_token': qrToken,
        'type': type.apiValue,
        'latitude': latitude,
        'longitude': longitude,
        if (accuracyMeters != null) 'accuracy_metres': accuracyMeters,
        if (clientTimestamp != null)
          'client_timestamp': clientTimestamp!.toUtc().toIso8601String(),
        if (deviceId != null && deviceId!.isNotEmpty) 'device_id': deviceId,
        'idempotency_key': idempotencyKey,
        'position_simulee': positionSimulee,
      };
}

class PointageResult {
  /// Identifiant du pointage (y compris rejeté : base d'une demande manuelle, D-P04).
  final String? id;
  final String statut;
  final DateTime? serverTs;
  final String? siteId;
  final String? siteLibelle;
  final double? distanceMetres;
  final String? motifRejet;

  const PointageResult({
    this.id,
    required this.statut,
    this.serverTs,
    this.siteId,
    this.siteLibelle,
    this.distanceMetres,
    this.motifRejet,
  });

  bool get isValide => PointageStatut.estValide(statut);
  bool get isRejet => PointageStatut.estRejet(statut);

  factory PointageResult.fromJson(Map<String, dynamic> json) {
    return PointageResult(
      id: json['id'] as String? ?? json['identifiant'] as String?,
      statut: (json['statut'] as String?)?.trim() ?? '',
      serverTs: _parseDate(json['serverTs'] ?? json['server_ts']),
      siteId: json['siteId'] as String? ?? json['site_id'] as String?,
      siteLibelle:
          json['siteLibelle'] as String? ?? json['site_libelle'] as String?,
      distanceMetres: _parseDouble(
        json['distanceMetres'] ?? json['distance_metres'],
      ),
      motifRejet:
          json['motifRejet'] as String? ?? json['motif_rejet'] as String?,
    );
  }
}

class PointageHistoriqueItem {
  final String id;
  final String statut;
  final String type;
  final DateTime? serverTs;
  final String? siteId;
  final String? siteLibelle;
  final double? distanceMetres;
  final String? motifRejet;

  const PointageHistoriqueItem({
    required this.id,
    required this.statut,
    required this.type,
    this.serverTs,
    this.siteId,
    this.siteLibelle,
    this.distanceMetres,
    this.motifRejet,
  });

  factory PointageHistoriqueItem.fromJson(Map<String, dynamic> json) {
    return PointageHistoriqueItem(
      id: json['identifiant'] as String? ??
          json['id'] as String? ??
          '',
      statut: (json['statut'] as String?)?.trim() ?? '',
      type: (json['type'] as String?)?.trim() ?? '',
      serverTs: _parseDate(json['serverTs'] ?? json['server_ts']),
      siteId: json['siteId'] as String? ?? json['site_id'] as String?,
      siteLibelle:
          json['siteLibelle'] as String? ?? json['site_libelle'] as String?,
      distanceMetres: _parseDouble(
        json['distanceMetres'] ?? json['distance_metres'],
      ),
      motifRejet:
          json['motifRejet'] as String? ?? json['motif_rejet'] as String?,
    );
  }
}

class PresenceSite {
  final String id;
  final String code;
  final String libelle;
  final double? latitude;
  final double? longitude;
  final int rayonMetres;
  final bool actif;

  /// `EXTERIEUR` (défaut) ou `INTERIEUR` — D-P01.
  final String typeLieu;
  final double? precisionAncrageMetres;
  final DateTime? emplacementUpdatedAt;

  const PresenceSite({
    required this.id,
    required this.code,
    required this.libelle,
    this.latitude,
    this.longitude,
    this.rayonMetres = 50,
    this.actif = true,
    this.typeLieu = 'EXTERIEUR',
    this.precisionAncrageMetres,
    this.emplacementUpdatedAt,
  });

  bool get hasEmplacement => latitude != null && longitude != null;

  bool get interieur => typeLieu == 'INTERIEUR';

  String get typeLieuLibelle => interieur ? 'Intérieur' : 'Extérieur';

  /// Précision exigée pour poser l'emplacement (D-P03).
  double get precisionAncrageRequise => interieur ? 25 : 10;

  factory PresenceSite.fromJson(Map<String, dynamic> json) {
    return PresenceSite(
      id: json['identifiant'] as String? ??
          json['id'] as String? ??
          json['siteId'] as String? ??
          '',
      code: json['code'] as String? ?? '',
      libelle: json['libelle'] as String? ?? '',
      latitude: _parseDouble(json['latitude']),
      longitude: _parseDouble(json['longitude']),
      rayonMetres: _parseInt(json['rayonMetres'] ?? json['rayon_metres']) ?? 50,
      actif: json['actif'] as bool? ?? true,
      typeLieu: (json['type_lieu'] ?? json['typeLieu'] ?? 'EXTERIEUR').toString(),
      precisionAncrageMetres: _parseDouble(
        json['precision_ancrage_metres'] ?? json['precisionAncrageMetres'],
      ),
      emplacementUpdatedAt: _parseDate(
        json['emplacement_updated_at'] ?? json['emplacementUpdatedAt'],
      ),
    );
  }
}

/// Demande de pointage manuel après un rejet (D-P04).
class DemandeManuelle {
  final String id;
  final String statut;
  final String type;
  final DateTime? heureDeclaree;
  final String justification;
  final String? collaborateurNom;
  final String? motifRejetInitial;
  final String? valideurNom;
  final bool validationParRh;
  final String? decidePar;
  final DateTime? decideLe;
  final String? motifRefus;
  final DateTime? creeLe;

  const DemandeManuelle({
    required this.id,
    required this.statut,
    required this.type,
    this.heureDeclaree,
    this.justification = '',
    this.collaborateurNom,
    this.motifRejetInitial,
    this.valideurNom,
    this.validationParRh = false,
    this.decidePar,
    this.decideLe,
    this.motifRefus,
    this.creeLe,
  });

  bool get enAttente => statut == 'EN_ATTENTE';
  bool get validee => statut == 'VALIDEE';

  String get statutLibelle => switch (statut) {
        'EN_ATTENTE' => 'En attente',
        'VALIDEE' => 'Validée',
        'REFUSEE' => 'Refusée',
        _ => statut,
      };

  String get typeLibelle => type == 'SORTIE' ? 'Sortie' : 'Entrée';

  /// « Omar Belhadj » ou « les RH » quand aucun responsable n'est trouvé (D-H03).
  String get valideurLibelle =>
      validationParRh || valideurNom == null ? 'les RH' : valideurNom!;

  factory DemandeManuelle.fromJson(Map<String, dynamic> json) {
    return DemandeManuelle(
      id: json['id'] as String? ?? '',
      statut: (json['statut'] as String?) ?? '',
      type: (json['type'] as String?) ?? 'ENTREE',
      heureDeclaree: _parseDate(json['heure_declaree']),
      justification: (json['justification'] as String?) ?? '',
      collaborateurNom: json['collaborateur_nom'] as String?,
      motifRejetInitial: json['motif_rejet_initial'] as String?,
      valideurNom: json['valideur_nom'] as String?,
      validationParRh: json['validation_par_rh'] == true,
      decidePar: json['decide_par'] as String?,
      decideLe: _parseDate(json['decide_le']),
      motifRefus: json['motif_refus'] as String?,
      creeLe: _parseDate(json['cree_le']),
    );
  }
}

DateTime? _parseDate(dynamic raw) {
  if (raw == null) return null;
  if (raw is DateTime) return raw;
  return DateTime.tryParse(raw.toString());
}

double? _parseDouble(dynamic raw) {
  if (raw == null) return null;
  if (raw is num) return raw.toDouble();
  return double.tryParse(raw.toString());
}

int? _parseInt(dynamic raw) {
  if (raw == null) return null;
  if (raw is int) return raw;
  if (raw is num) return raw.toInt();
  return int.tryParse(raw.toString());
}

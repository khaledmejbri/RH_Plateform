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
  static const rejeteVisageNonReconnu = 'REJETE_VISAGE_NON_RECONNU';
  static const rejeteVivacite = 'REJETE_VIVACITE';
  static const rejeteVerificationFacialeAbsente = 'REJETE_VERIFICATION_FACIALE_ABSENTE';

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

  /// Preuve faciale (ADR-M09-FACE) : score 0-100 calculé sur l'appareil et résultat du vivant.
  /// Jamais d'image ni de vecteur. Absents si aucune étape visage.
  final int? scoreFacial;
  final bool? vivaciteReussie;
  final String? modeleFacialVersion;

  /// `ANDROID`, `IOS` ou `WEB`.
  final String? plateforme;

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
    this.scoreFacial,
    this.vivaciteReussie,
    this.modeleFacialVersion,
    this.plateforme,
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
        if (scoreFacial != null) 'score_facial': scoreFacial,
        if (vivaciteReussie != null) 'vivacite_reussie': vivaciteReussie,
        if (modeleFacialVersion != null) 'modele_facial_version': modeleFacialVersion,
        if (plateforme != null) 'plateforme': plateforme,
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

  /// `VERIFIEE`, `NON_CONSENTIE` ou `NON_DISPONIBLE` (nul si aucun contrôle facial).
  final String? verificationFaciale;
  final int? scoreFacial;
  final bool? vivaciteReussie;
  final int? echecsConsecutifs;

  /// Fin du blocage (3e échec facial).
  final DateTime? bloqueJusquA;

  const PointageResult({
    this.id,
    required this.statut,
    this.serverTs,
    this.siteId,
    this.siteLibelle,
    this.distanceMetres,
    this.motifRejet,
    this.verificationFaciale,
    this.scoreFacial,
    this.vivaciteReussie,
    this.echecsConsecutifs,
    this.bloqueJusquA,
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
      verificationFaciale: json['verification_faciale'] as String?,
      scoreFacial: (json['score_facial'] as num?)?.toInt(),
      vivaciteReussie: json['vivacite_reussie'] as bool?,
      echecsConsecutifs: (json['echecs_consecutifs'] as num?)?.toInt(),
      bloqueJusquA: DateTime.tryParse('${json['bloque_jusqu_a'] ?? ''}'),
    );
  }

  /// Pointage valide sans contrôle du visage (consentement refusé / retiré, appareil incompatible).
  bool get sansVerificationFaciale =>
      verificationFaciale == 'NON_CONSENTIE' || verificationFaciale == 'NON_DISPONIBLE';
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

/// Durée du blocage après 3 échecs faciaux (D-P10).
const dureeBlocageFacial = Duration(minutes: 15);

/// Dernier pointage `REJETE_*` utilisable pour une demande manuelle pendant un blocage facial (D-P10.3) :
/// rejet du jour (date locale de [maintenant]) ou, si [bloqueJusquA] est connu, rejet de la veille dont l'heure
/// est à moins de [dureeBlocageFacial] de cette fin (ex. 23 h 55 pour un blocage jusqu'à 00 h 10).
/// `null` si aucun.
PointageHistoriqueItem? dernierRejetPourBlocage(
  List<PointageHistoriqueItem> historique,
  DateTime maintenant, {
  DateTime? bloqueJusquA,
}) {
  final debutFenetre = bloqueJusquA?.toLocal().subtract(dureeBlocageFacial);
  PointageHistoriqueItem? best;
  for (final p in historique) {
    final ts = p.serverTs?.toLocal();
    if (!PointageStatut.estRejet(p.statut) || ts == null || p.id.isEmpty) continue;
    final memeJour =
        ts.year == maintenant.year && ts.month == maintenant.month && ts.day == maintenant.day;
    final dansFenetre = debutFenetre != null && !ts.isBefore(debutFenetre) && !ts.isAfter(maintenant);
    if (!memeJour && !dansFenetre) continue;
    if (best == null || ts.isAfter(best.serverTs!.toLocal())) best = p;
  }
  return best;
}

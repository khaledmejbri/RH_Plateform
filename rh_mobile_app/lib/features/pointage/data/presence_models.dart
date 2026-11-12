// Modèles M09 — présence QR + GPS (vague Must).

import '../../../core/libelles/libelles.dart';

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

  /// Pointage hors de la fenêtre horaire du site (D-P12) : rejet définitif du jour,
  /// pas rattrapable par un pointage manuel (décision 5).
  static const rejeteHorsHoraire = 'REJETE_HORS_HORAIRE';

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

  /// D-P22.a : le serveur seul dit si le refus est rattrapable. Champ absent = pas de bouton.
  final bool manuelPossible;

  /// Raison en clair quand le refus n'est pas rattrapable (`null` sinon).
  final String? manuelImpossibleMotif;

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
    this.manuelPossible = false,
    this.manuelImpossibleMotif,
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
      manuelPossible: json['manuel_possible'] == true,
      manuelImpossibleMotif: _texteNonVide(json['manuel_impossible_motif']),
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

  /// D-P22.a : décision du serveur ; champ absent = pas de bouton.
  final bool manuelPossible;
  final String? manuelImpossibleMotif;

  const PointageHistoriqueItem({
    required this.id,
    required this.statut,
    required this.type,
    this.serverTs,
    this.siteId,
    this.siteLibelle,
    this.distanceMetres,
    this.motifRejet,
    this.manuelPossible = false,
    this.manuelImpossibleMotif,
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
      manuelPossible: json['manuel_possible'] == true,
      manuelImpossibleMotif: _texteNonVide(json['manuel_impossible_motif']),
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

/// Signal de vigilance d'une demande (D-P24.a) : code stable et libellé en clair du serveur.
class SignalVigilance {
  final String code;
  final String libelle;

  const SignalVigilance({required this.code, required this.libelle});

  factory SignalVigilance.fromJson(Map<String, dynamic> json) => SignalVigilance(
        code: (json['code'] ?? '').toString(),
        libelle: (json['libelle'] ?? json['code'] ?? '').toString(),
      );
}

/// Demande de pointage manuel après un rejet (D-P04, D-P21 à D-P26).
///
/// Les champs réservés à qui décide (signaux, compteurs, délais, commentaire) sont absents de l'API pour
/// le demandeur (D-P24.d) : tous nullables, rien n'est déduit côté mobile.
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

  /// Libellé du statut fourni par le serveur (prioritaire sur la table locale).
  final String? statutLibelleServeur;

  /// Date limite de décision ; passée, la demande devient « Expirée » (D-P23).
  final DateTime? echeance;
  final List<SignalVigilance> signaux;
  final bool? avecSignalServeur;
  final String? modeQrLibelle;
  final double? distanceMetres;
  final double? rayonMetres;
  final double? precisionMetres;
  final int? delaiDemandeMinutes;
  final int? manuelsDuMois;
  final int? seuilMois;
  final int? refusDuJour;
  final String? commentaireValideur;
  final int? delaiDecisionMinutes;

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
    this.statutLibelleServeur,
    this.echeance,
    this.signaux = const [],
    this.avecSignalServeur,
    this.modeQrLibelle,
    this.distanceMetres,
    this.rayonMetres,
    this.precisionMetres,
    this.delaiDemandeMinutes,
    this.manuelsDuMois,
    this.seuilMois,
    this.refusDuJour,
    this.commentaireValideur,
    this.delaiDecisionMinutes,
  });

  bool get enAttente => statut == 'EN_ATTENTE';
  bool get validee => statut == 'VALIDEE';
  bool get expiree => statut == 'EXPIREE';

  /// Vrai si le serveur déclare un signal (`avec_signal`) ou en liste au moins un.
  bool get avecSignal => avecSignalServeur ?? signaux.isNotEmpty;

  String get statutLibelle {
    final l = statutLibelleServeur;
    if (l != null && !Libelles.ressembleAUnCode(l)) return l;
    return switch (statut) {
      'EN_ATTENTE' => 'En attente',
      'VALIDEE' => 'Validée',
      'REFUSEE' => 'Refusée',
      'EXPIREE' => 'Expirée',
      _ => Libelles.valeurNonReconnue,
    };
  }

  String get typeLibelle => type == 'SORTIE' ? 'Sortie' : 'Entrée';

  /// « Omar Belhadj » ou « les RH » quand aucun responsable n'est trouvé (D-H03).
  String get valideurLibelle =>
      validationParRh || valideurNom == null ? 'les RH' : valideurNom!;

  factory DemandeManuelle.fromJson(Map<String, dynamic> json) {
    final brutSignaux = json['signaux'];
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
      statutLibelleServeur: _texteNonVide(json['statut_libelle']),
      echeance: _parseDate(json['echeance']),
      signaux: brutSignaux is List
          ? brutSignaux
              .whereType<Map>()
              .map((e) => SignalVigilance.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      avecSignalServeur: json['avec_signal'] is bool ? json['avec_signal'] as bool : null,
      modeQrLibelle: _texteNonVide(json['mode_qr_libelle']),
      distanceMetres: _parseDouble(json['distance_metres']),
      rayonMetres: _parseDouble(json['rayon_metres']),
      precisionMetres: _parseDouble(json['precision_metres']),
      delaiDemandeMinutes: _parseInt(json['delai_demande_minutes']),
      manuelsDuMois: _parseInt(json['manuels_du_mois']),
      seuilMois: _parseInt(json['seuil_mois']),
      refusDuJour: _parseInt(json['refus_du_jour']),
      commentaireValideur: _texteNonVide(json['commentaire_valideur']),
      delaiDecisionMinutes: _parseInt(json['delai_decision_minutes']),
    );
  }
}

String? _texteNonVide(dynamic raw) {
  final t = raw?.toString().trim();
  return (t == null || t.isEmpty) ? null : t;
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

/// Dernier pointage `REJETE_*` rattrapable (`manuel_possible` du serveur) utilisable pour une demande manuelle pendant un blocage facial (D-P10.3) :
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
    if (!p.manuelPossible || ts == null || p.id.isEmpty) continue;
    final memeJour =
        ts.year == maintenant.year && ts.month == maintenant.month && ts.day == maintenant.day;
    final dansFenetre = debutFenetre != null && !ts.isBefore(debutFenetre) && !ts.isAfter(maintenant);
    if (!memeJour && !dansFenetre) continue;
    if (best == null || ts.isAfter(best.serverTs!.toLocal())) best = p;
  }
  return best;
}

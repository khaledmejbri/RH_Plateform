// Modèles et routage de la reconnaissance faciale (ADR-M09-FACE, contrat d'API mobile).

abstract final class FacialStatut {
  static const nonEnrole = 'NON_ENROLE';
  static const enrole = 'ENROLE';
  static const refuse = 'REFUSE';
  static const revoque = 'REVOQUE';
  static const aReenroler = 'A_REENROLER';
}

/// Raisons d'incompatibilité de l'appareil (`POST /appareil-incompatible`).
abstract final class RaisonIncompatible {
  static const sansCameraFrontale = 'SANS_CAMERA_FRONTALE';
  static const modeleNonChargeable = 'MODELE_NON_CHARGEABLE';
}

/// Réponse de `GET /moi` (et des écritures) : aucun seuil n'est exposé au mobile.
class EtatFacialMoi {
  const EtatFacialMoi({
    required this.verificationActive,
    required this.controleExige,
    required this.statut,
    this.dateMiseEnService,
    this.versionConsentementCourante,
    this.versionConsentementAcceptee,
    this.reconsentementRequis = false,
    this.ecranConsentementRequis = false,
    this.appareilCorrespond,
    this.appareilIncompatible = false,
    this.gabaritAEffacer = false,
    this.echecsConsecutifs = 0,
    this.bloqueJusquA,
    this.decideLe,
  });

  final bool verificationActive;
  final bool controleExige;
  final String? dateMiseEnService;
  final String? versionConsentementCourante;
  final String statut;
  final String? versionConsentementAcceptee;
  final bool reconsentementRequis;
  final bool ecranConsentementRequis;

  /// Nul si aucun appareil n'est enrôlé.
  final bool? appareilCorrespond;
  final bool appareilIncompatible;
  final bool gabaritAEffacer;
  final int echecsConsecutifs;
  final DateTime? bloqueJusquA;
  final DateTime? decideLe;

  bool get estEnrole => statut == FacialStatut.enrole || statut == FacialStatut.aReenroler;

  bool estBloque(DateTime maintenant) => bloqueJusquA != null && bloqueJusquA!.isAfter(maintenant);

  factory EtatFacialMoi.fromJson(Map<String, dynamic> j) {
    bool b(String k) => j[k] == true;
    return EtatFacialMoi(
      verificationActive: b('verification_active'),
      controleExige: b('controle_exige'),
      dateMiseEnService: j['date_mise_en_service'] as String?,
      versionConsentementCourante: j['version_consentement_courante'] as String?,
      statut: (j['statut'] as String?) ?? FacialStatut.nonEnrole,
      versionConsentementAcceptee: j['version_consentement_acceptee'] as String?,
      reconsentementRequis: b('reconsentement_requis'),
      ecranConsentementRequis: b('ecran_consentement_requis'),
      appareilCorrespond: j['appareil_correspond'] as bool?,
      appareilIncompatible: b('appareil_incompatible'),
      gabaritAEffacer: b('gabarit_a_effacer'),
      echecsConsecutifs: (j['echecs_consecutifs'] as num?)?.toInt() ?? 0,
      bloqueJusquA: DateTime.tryParse('${j['bloque_jusqu_a'] ?? ''}'),
      decideLe: DateTime.tryParse('${j['decide_le'] ?? ''}'),
    );
  }
}

/// Réponse de `GET /consentement/courant`.
class ConsentementCourant {
  const ConsentementCourant({required this.version, required this.texte, this.publieLe});

  final String version;
  final String texte;
  final DateTime? publieLe;

  factory ConsentementCourant.fromJson(Map<String, dynamic> j) => ConsentementCourant(
        version: (j['version'] as String?) ?? '',
        texte: (j['texte'] as String?) ?? '',
        publieLe: DateTime.tryParse('${j['publie_le'] ?? ''}'),
      );
}

/// Écran de consentement à imposer à la connexion.
enum FacialRoute { aucune, consentement, reconsentement }

/// Décision de routage calculée à partir de `GET /moi` et de l'état local.
class FacialDecision {
  const FacialDecision({
    this.route = FacialRoute.aucune,
    this.effacerGabarit = false,
    this.reenrolementProposable = false,
  });

  final FacialRoute route;

  /// `gabarit_a_effacer` : le gabarit local doit être supprimé.
  final bool effacerGabarit;

  /// Gabarit local perdu ou appareil différent de l'appareil enrôlé : proposer `POST /demande-reenrolement`.
  final bool reenrolementProposable;

  static const aucune = FacialDecision();
}

/// Routage consentement / reconsentement (pur, testable).
///
/// - réglage INACTIVE (ou état inconnu) : aucun écran (seul l'effacement d'un gabarit périmé reste appliqué) ;
/// - web ou appareil incompatible : aucun écran ;
/// - `reconsentement_requis` : écran de reconsentement (ENROLE / A_REENROLER) ;
/// - `ecran_consentement_requis` (actif et NON_ENROLE) : écran de consentement puis enrôlement.
FacialDecision decider(
  EtatFacialMoi? etat, {
  required bool gabaritLocal,
  required bool plateformeNative,
  bool incompatibleLocal = false,
}) {
  if (etat == null) return FacialDecision.aucune;
  // L'effacement du gabarit (retrait, refus, déblocage RH) s'applique même si le réglage est INACTIVE.
  final effacer = etat.gabaritAEffacer && gabaritLocal;
  if (!etat.verificationActive) return FacialDecision(effacerGabarit: effacer);
  if (!plateformeNative || etat.appareilIncompatible || incompatibleLocal) {
    return FacialDecision(effacerGabarit: effacer);
  }
  final gabaritApresEffacement = effacer ? false : gabaritLocal;
  FacialRoute route = FacialRoute.aucune;
  if (etat.reconsentementRequis && etat.estEnrole) {
    route = FacialRoute.reconsentement;
  } else if (etat.ecranConsentementRequis && etat.statut == FacialStatut.nonEnrole) {
    route = FacialRoute.consentement;
  }
  final reenrol = etat.statut == FacialStatut.enrole &&
      (etat.appareilCorrespond == false ||
          (etat.appareilCorrespond == true && !gabaritApresEffacement));
  return FacialDecision(
    route: route,
    effacerGabarit: effacer,
    reenrolementProposable: reenrol,
  );
}

/// Étape visage du pointage requise (après le GPS) : réglage actif, date de mise en service atteinte,
/// collaborateur enrôlé sur **cet** appareil avec son gabarit local, plateforme native.
/// État inconnu (`GET /moi` en échec) : pas d'étape visage, le serveur décide (rejet puis pointage manuel D-P04).
bool controleFacialRequis(
  EtatFacialMoi? etat, {
  required bool gabaritLocal,
  required bool plateformeNative,
}) {
  return etat != null &&
      plateformeNative &&
      gabaritLocal &&
      etat.verificationActive &&
      etat.controleExige &&
      etat.estEnrole &&
      etat.appareilCorrespond == true;
}

/// Plateforme envoyée au pointage : `ANDROID`, `IOS` ou `WEB`.
String plateformeApi({required bool web, required bool ios}) => web ? 'WEB' : (ios ? 'IOS' : 'ANDROID');

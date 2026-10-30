import 'package:flutter/foundation.dart' show debugPrint;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/jwt_access_roles.dart';
import '../../../core/network/api_client.dart';
import '../data/device_id_service.dart';
import '../data/face_math.dart';
import '../data/face_template_store.dart';
import '../data/facial_models.dart';
import '../data/facial_queue.dart';
import '../data/facial_repository.dart';
import '../data/install_marker.dart';
import '../data/secure_kv.dart';
import '../engine/face_engine.dart';

final secureKvProvider = Provider<SecureKv>((ref) => FlutterSecureKv());

final faceTemplateStoreProvider =
    Provider<FaceTemplateStore>((ref) => FaceTemplateStore(ref.watch(secureKvProvider)));

final facialQueueProvider = Provider<FacialQueue>((ref) => FacialQueue(ref.watch(secureKvProvider)));

final deviceIdServiceProvider = Provider<DeviceIdService>((ref) {
  final store = ref.watch(faceTemplateStoreProvider);
  final queue = ref.watch(facialQueueProvider);
  return DeviceIdService(
    kv: ref.watch(secureKvProvider),
    marker: creerInstallMarker(),
    // Nouvelle installation (iOS : Keychain conservé) : gabarits et file d'attente effacés.
    surReinstallation: () async {
      await store.effacerTout();
      await queue.effacerTout();
    },
  );
});

final faceEngineProvider = Provider<FaceEngine>((ref) {
  final engine = creerFaceEngine();
  ref.onDispose(engine.liberer);
  return engine;
});

class FacialState {
  const FacialState({
    this.charge = false,
    this.chargement = false,
    this.etat,
    this.decision = FacialDecision.aucune,
    this.gabaritLocal = false,
    this.incompatibleLocal = false,
    this.indisponibleVersion = false,
    this.retraitEnAttente = false,
    this.enrolementEnAttente = false,
    this.erreur,
    this.compteId,
    this.deviceId,
  });

  /// Premier chargement terminé (réussi ou non).
  final bool charge;
  final bool chargement;
  final EtatFacialMoi? etat;
  final FacialDecision decision;
  final bool gabaritLocal;
  final bool incompatibleLocal;

  /// Modèle absent du paquet : vérification faciale indisponible dans cette version (rien signalé au serveur).
  final bool indisponibleVersion;
  final bool retraitEnAttente;
  final bool enrolementEnAttente;
  final String? erreur;
  final String? compteId;
  final String? deviceId;

  FacialState copyWith({
    bool? charge,
    bool? chargement,
    EtatFacialMoi? etat,
    FacialDecision? decision,
    bool? gabaritLocal,
    bool? incompatibleLocal,
    bool? indisponibleVersion,
    bool? retraitEnAttente,
    bool? enrolementEnAttente,
    String? erreur,
    bool effacerErreur = false,
    String? compteId,
    String? deviceId,
  }) {
    return FacialState(
      charge: charge ?? this.charge,
      chargement: chargement ?? this.chargement,
      etat: etat ?? this.etat,
      decision: decision ?? this.decision,
      gabaritLocal: gabaritLocal ?? this.gabaritLocal,
      incompatibleLocal: incompatibleLocal ?? this.incompatibleLocal,
      indisponibleVersion: indisponibleVersion ?? this.indisponibleVersion,
      retraitEnAttente: retraitEnAttente ?? this.retraitEnAttente,
      enrolementEnAttente: enrolementEnAttente ?? this.enrolementEnAttente,
      erreur: effacerErreur ? null : (erreur ?? this.erreur),
      compteId: compteId ?? this.compteId,
      deviceId: deviceId ?? this.deviceId,
    );
  }

  /// Rien à afficher côté visage (web, jeton de démo, réglage inactif sans enrôlement).
  bool get afficherEntreeProfil {
    final e = etat;
    return e != null && (e.verificationActive || e.estEnrole || e.statut == FacialStatut.refuse);
  }
}

/// Orchestration de la reconnaissance faciale : état serveur (`GET /moi`, jamais bloquant pour l'accueil),
/// gabarit local, file d'attente, consentement et enrôlement.
class FacialController extends StateNotifier<FacialState> {
  FacialController(this._ref) : super(const FacialState());

  final Ref _ref;

  FaceEngine get _engine => _ref.read(faceEngineProvider);
  FacialRepository get _repo => _ref.read(facialRepositoryProvider);
  FaceTemplateStore get _gabarits => _ref.read(faceTemplateStoreProvider);
  FacialQueue get _file => _ref.read(facialQueueProvider);

  bool get plateformeNative => _engine.prisEnCharge;

  Future<String?> _compteId() async {
    final token = await _ref.read(secureTokenStorageProvider).readAccessToken();
    return JwtAccessRoles.compteId(token);
  }

  /// À la connexion (accueil). Ne lève jamais : en cas d'échec l'accueil reste accessible.
  Future<void> demarrer() async {
    if (!plateformeNative) {
      state = const FacialState(charge: true);
      return;
    }
    state = const FacialState(chargement: true);
    try {
      final compte = await _compteId();
      if (compte == null) {
        state = const FacialState(charge: true);
        return;
      }
      final deviceId = await _ref.read(deviceIdServiceProvider).obtenir();
      state = state.copyWith(compteId: compte, deviceId: deviceId);
      await _synchroniser();
    } catch (e) {
      debugPrint('[Facial] Démarrage impossible : $e');
      state = state.copyWith(charge: true, chargement: false, erreur: 'Vérification faciale indisponible.');
    }
  }

  /// Relit `GET /moi` (avant un pointage, après une action). Erreur réseau : l'état précédent est conservé.
  Future<void> rafraichir() async {
    if (!plateformeNative || state.compteId == null) return;
    try {
      await _synchroniser();
    } catch (e) {
      debugPrint('[Facial] Rafraîchissement impossible : $e');
    }
  }

  Future<void> _synchroniser() async {
    final compte = state.compteId!;
    final deviceId = state.deviceId!;
    // Le retrait en attente part avant toute autre requête (D-P07.d).
    await vider();
    EtatFacialMoi? etat;
    String? erreur;
    try {
      etat = await _repo.moi(deviceId);
    } on FacialApiException catch (e) {
      erreur = e.message;
    }
    var gabarit = await _gabarits.existe(compte);
    var decision = decider(
      etat,
      gabaritLocal: gabarit,
      plateformeNative: plateformeNative,
      incompatibleLocal: state.incompatibleLocal,
    );
    // Un enrôlement du même compte encore en file (réseau absent) garde son gabarit local.
    final enrolementEnFile = (await _file.pourCompte(compte))
        .any((o) => o.type == TypeOperationFaciale.enrolement);
    if (decision.effacerGabarit && !enrolementEnFile) {
      await _gabarits.effacer(compte);
      gabarit = false;
      decision = decider(
        etat,
        gabaritLocal: false,
        plateformeNative: plateformeNative,
        incompatibleLocal: state.incompatibleLocal,
      );
    }
    var incompatible = state.incompatibleLocal;
    var indisponibleVersion = state.indisponibleVersion;
    if (etat != null && decision.route == FacialRoute.consentement) {
      // Compatibilité constatée par l'appli avant tout écran (D-P07.g).
      switch (await _engine.preparer()) {
        case EtatMoteur.pret:
          break;
        case EtatMoteur.sansCameraFrontale:
          incompatible = true;
          decision = FacialDecision.aucune;
          await _signalerIncompatible(deviceId, RaisonIncompatible.sansCameraFrontale);
        case EtatMoteur.modeleNonChargeable:
          incompatible = true;
          decision = FacialDecision.aucune;
          await _signalerIncompatible(deviceId, RaisonIncompatible.modeleNonChargeable);
        case EtatMoteur.modeleAbsent:
          // Défaut d'empaquetage : rien n'est signalé au serveur, aucun écran de consentement.
          debugPrint('[Facial] Modèle absent : vérification faciale indisponible dans cette version.');
          indisponibleVersion = true;
          decision = FacialDecision.aucune;
        case EtatMoteur.camerasIndisponibles:
          // Échec ponctuel : on réessaiera au prochain lancement.
          debugPrint('[Facial] Caméras indisponibles : nouvel essai au prochain lancement.');
          decision = FacialDecision.aucune;
      }
    }
    state = state.copyWith(
      charge: true,
      chargement: false,
      etat: etat,
      decision: decision,
      gabaritLocal: gabarit,
      incompatibleLocal: incompatible,
      indisponibleVersion: indisponibleVersion,
      retraitEnAttente: await _file.aRetraitEnAttente(compte),
      enrolementEnAttente: (await _file.pourCompte(compte))
          .any((o) => o.type == TypeOperationFaciale.enrolement),
      erreur: erreur,
      effacerErreur: erreur == null,
    );
  }

  Future<void> _signalerIncompatible(String deviceId, String raison) async {
    try {
      await _repo.signalerAppareilIncompatible(deviceId: deviceId, raison: raison);
    } on FacialApiException catch (e) {
      debugPrint('[Facial] Signalement incompatibilité refusé : ${e.message}');
    }
  }

  /// Rejoue la file d'attente du compte (retrait d'abord). Ne lève jamais.
  Future<void> vider() async {
    final compte = state.compteId;
    if (compte == null) return;
    try {
      await _file.vider(compte, _envoyer);
    } catch (e) {
      debugPrint('[Facial] Vidage de la file impossible : $e');
    }
  }

  Future<ResultatEnvoi> _envoyer(OperationFaciale op) async {
    try {
      switch (op.type) {
        case TypeOperationFaciale.retrait:
          await _repo.retirerConsentement();
        case TypeOperationFaciale.enrolement:
          await _repo.enroler(
            deviceId: op.deviceId ?? '',
            versionConsentement: op.versionConsentement ?? '',
            modeleVersion: op.modeleVersion ?? FaceModelInfo.version,
            idempotencyKey: op.idempotencyKey ?? '',
          );
      }
      return ResultatEnvoi.ok;
    } on FacialApiException catch (e) {
      if (e.reseau) return ResultatEnvoi.reessayer;
      // Refus du serveur (409 / 403 / 422) : l'enrôlement est abandonné, le gabarit local effacé (D-P07.d).
      if (op.type == TypeOperationFaciale.enrolement) await _gabarits.effacer(op.compteId);
      return ResultatEnvoi.definitif;
    }
  }

  // ----------------------------------------------------------------------------------------------
  // Actions utilisateur
  // ----------------------------------------------------------------------------------------------

  /// Enrôlement : gabarit conservé sur l'appareil, puis `POST /enrolement`. Réseau absent : mis en file
  /// (même `idempotency_key`) et gabarit conservé ; refus du serveur : gabarit effacé et erreur relancée.
  Future<void> enroler({
    required String versionConsentement,
    required List<List<double>> embeddings,
  }) async {
    final compte = state.compteId;
    final deviceId = state.deviceId;
    if (compte == null || deviceId == null) {
      throw const FacialApiException(message: 'Compte indisponible. Reconnectez-vous.');
    }
    if (await _file.aRetraitEnAttente(compte)) {
      await vider();
      if (await _file.aRetraitEnAttente(compte)) {
        throw const FacialApiException(
          message: 'Un retrait de consentement est en cours de transmission. Réessayez quand le réseau revient.',
          reseau: true,
        );
      }
    }
    final cle = genererUuidV4();
    await _gabarits.enregistrer(compte, embeddings);
    try {
      final etat = await _repo.enroler(
        deviceId: deviceId,
        versionConsentement: versionConsentement,
        modeleVersion: FaceModelInfo.version,
        idempotencyKey: cle,
      );
      state = state.copyWith(
        etat: etat,
        gabaritLocal: true,
        decision: decider(etat, gabaritLocal: true, plateformeNative: plateformeNative),
        effacerErreur: true,
      );
    } on FacialApiException catch (e) {
      if (e.reseau) {
        await _file.ajouter(OperationFaciale(
          type: TypeOperationFaciale.enrolement,
          compteId: compte,
          creeLe: DateTime.now().toUtc(),
          deviceId: deviceId,
          versionConsentement: versionConsentement,
          modeleVersion: FaceModelInfo.version,
          idempotencyKey: cle,
        ));
        state = state.copyWith(
          gabaritLocal: true,
          enrolementEnAttente: true,
          decision: FacialDecision.aucune,
        );
        rethrow;
      }
      await _gabarits.effacer(compte);
      state = state.copyWith(gabaritLocal: false);
      await rafraichir();
      rethrow;
    }
  }

  /// Reconsentement accepté : reste ENROLE (ou A_REENROLER), version mise à jour.
  Future<void> accepterReconsentement(String version) async {
    final etat = await _repo.accepterConsentement(version);
    _appliquer(etat);
  }

  /// Refus du premier consentement (NON_ENROLE -> REFUSE) ou du reconsentement (-> REVOQUE, gabarit effacé).
  Future<void> refuser(String version) async {
    final compte = state.compteId;
    final etat = await _repo.refuserConsentement(version);
    if (compte != null && etat.statut != FacialStatut.enrole) {
      await _gabarits.effacer(compte);
      state = state.copyWith(gabaritLocal: false);
    }
    _appliquer(etat);
  }

  /// Retrait : effacement **immédiat** sur l'appareil, puis envoi ; sans réseau, le retrait est mis en
  /// file et part avant toute autre requête. Renvoie `true` si le serveur l'a enregistré.
  Future<bool> retirer() async {
    final compte = state.compteId;
    if (compte == null) return false;
    await _gabarits.effacer(compte);
    state = state.copyWith(gabaritLocal: false, decision: FacialDecision.aucune);
    try {
      final etat = await _repo.retirerConsentement();
      _appliquer(etat);
      return true;
    } on FacialApiException catch (e) {
      if (e.reseau) {
        await _file.ajouter(OperationFaciale(
          type: TypeOperationFaciale.retrait,
          compteId: compte,
          creeLe: DateTime.now().toUtc(),
        ));
        state = state.copyWith(retraitEnAttente: true, enrolementEnAttente: false);
        return false;
      }
      // 409 AUCUN_CONSENTEMENT_ACTIF : rien à retirer côté serveur.
      await rafraichir();
      rethrow;
    }
  }

  /// Demande explicite de ré-enrôlement (nouvel appareil ou gabarit perdu).
  Future<void> demanderReenrolement() async {
    final deviceId = state.deviceId;
    if (deviceId == null) return;
    final etat = await _repo.demanderReenrolement(deviceId, gabaritPresent: state.gabaritLocal);
    _appliquer(etat);
  }

  void _appliquer(EtatFacialMoi etat) {
    final gabarit = state.gabaritLocal;
    state = state.copyWith(
      etat: etat,
      decision: decider(
        etat,
        gabaritLocal: gabarit,
        plateformeNative: plateformeNative,
        incompatibleLocal: state.incompatibleLocal,
      ),
      effacerErreur: true,
    );
  }

  /// Gabarit local du compte (pour la comparaison au pointage).
  Future<List<List<double>>?> gabaritPourPointage() async {
    final compte = state.compteId;
    if (compte == null) return null;
    return _gabarits.lire(compte);
  }

  /// Marque localement le gabarit manquant (illisible).
  void gabaritAbsent() => state = state.copyWith(gabaritLocal: false);

  /// Au pointage : identifiant d'appareil (toujours envoyé, même réglage INACTIVE).
  Future<String?> deviceIdPourPointage() async {
    try {
      return state.deviceId ?? await _ref.read(deviceIdServiceProvider).obtenir();
    } catch (_) {
      return null;
    }
  }

  void reinitialiser() => state = const FacialState();
}

final facialControllerProvider =
    StateNotifierProvider<FacialController, FacialState>((ref) => FacialController(ref));

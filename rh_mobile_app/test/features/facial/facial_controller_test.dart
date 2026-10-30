import 'dart:convert';

import 'package:camera/camera.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/network/api_client.dart';
import 'package:rh_mobile_app/core/storage/secure_token_storage.dart';
import 'package:rh_mobile_app/features/facial/data/device_id_service.dart';
import 'package:rh_mobile_app/features/facial/data/facial_models.dart';
import 'package:rh_mobile_app/features/facial/data/facial_queue.dart';
import 'package:rh_mobile_app/features/facial/data/facial_repository.dart';
import 'package:rh_mobile_app/features/facial/data/face_template_store.dart';
import 'package:rh_mobile_app/features/facial/data/secure_kv.dart';
import 'package:rh_mobile_app/features/facial/engine/face_engine.dart';
import 'package:rh_mobile_app/features/facial/providers/facial_controller.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';

class _Jetons extends SecureTokenStorage {
  @override
  Future<String?> readAccessToken() async {
    String b64(Object o) => base64Url.encode(utf8.encode(jsonEncode(o))).replaceAll('=', '');
    return '${b64({'alg': 'none'})}.${b64({'identifiant_utilisateur': 'compte-1'})}.sig';
  }
}

class _Moteur implements FaceEngine {
  _Moteur(this.etat);
  final EtatMoteur etat;
  int preparations = 0;

  @override
  bool get prisEnCharge => true;
  @override
  Future<EtatMoteur> preparer() async {
    preparations++;
    return etat;
  }

  @override
  Future<CameraDescription?> cameraFrontale() async => null;
  @override
  Future<FaceAnalysis?> analyser(CameraImage image, CameraDescription camera) async => null;
  @override
  Future<void> liberer() async {}
}

class _Depot implements FacialRepository {
  _Depot(this.etat);
  EtatFacialMoi etat;
  final List<String> appels = [];
  final List<String> raisons = [];

  @override
  Future<EtatFacialMoi> moi(String deviceId) async {
    appels.add('moi');
    return etat;
  }

  @override
  Future<void> signalerAppareilIncompatible({required String deviceId, required String raison}) async {
    appels.add('incompatible');
    raisons.add(raison);
  }

  @override
  Future<EtatFacialMoi> retirerConsentement() async {
    appels.add('retrait');
    return etat;
  }

  @override
  Future<EtatFacialMoi> enroler({
    required String deviceId,
    required String versionConsentement,
    required String modeleVersion,
    required String idempotencyKey,
  }) async {
    appels.add('enrolement');
    throw const FacialApiException(message: 'réseau', reseau: true);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _consent = EtatFacialMoi(
  verificationActive: true,
  controleExige: true,
  statut: FacialStatut.nonEnrole,
  ecranConsentementRequis: true,
);

void main() {
  late MemorySecureKv kv;

  ProviderContainer conteneur(_Moteur moteur, _Depot depot) {
    final c = ProviderContainer(overrides: [
      secureKvProvider.overrideWithValue(kv),
      secureTokenStorageProvider.overrideWithValue(_Jetons()),
      faceEngineProvider.overrideWithValue(moteur),
      facialRepositoryProvider.overrideWithValue(depot),
      deviceIdServiceProvider.overrideWithValue(DeviceIdService(kv: kv)),
    ]);
    addTearDown(c.dispose);
    return c;
  }

  setUp(() => kv = MemorySecureKv());

  group('appareil incompatible (D-P07.g)', () {
    test('modèle absent : rien signalé au serveur, aucun écran, état « indisponible dans cette version »', () async {
      final depot = _Depot(_consent);
      final c = conteneur(_Moteur(EtatMoteur.modeleAbsent), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      final s = c.read(facialControllerProvider);
      expect(depot.appels, isNot(contains('incompatible')));
      expect(s.decision.route, FacialRoute.aucune);
      expect(s.indisponibleVersion, isTrue);
      expect(s.incompatibleLocal, isFalse);
    });

    test('échec ponctuel des caméras : rien signalé, nouvel essai au prochain lancement', () async {
      final depot = _Depot(_consent);
      final c = conteneur(_Moteur(EtatMoteur.camerasIndisponibles), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(depot.appels, isNot(contains('incompatible')));
      expect(c.read(facialControllerProvider).decision.route, FacialRoute.aucune);
      expect(c.read(facialControllerProvider).incompatibleLocal, isFalse);
    });

    test('aucune caméra frontale : SANS_CAMERA_FRONTALE signalé', () async {
      final depot = _Depot(_consent);
      final c = conteneur(_Moteur(EtatMoteur.sansCameraFrontale), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(depot.raisons, ['SANS_CAMERA_FRONTALE']);
      expect(c.read(facialControllerProvider).incompatibleLocal, isTrue);
    });

    test('modèle refusé par l\'interpréteur : MODELE_NON_CHARGEABLE signalé', () async {
      final depot = _Depot(_consent);
      final c = conteneur(_Moteur(EtatMoteur.modeleNonChargeable), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(depot.raisons, ['MODELE_NON_CHARGEABLE']);
    });

    test('moteur prêt : écran de consentement', () async {
      final depot = _Depot(_consent);
      final c = conteneur(_Moteur(EtatMoteur.pret), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(c.read(facialControllerProvider).decision.route, FacialRoute.consentement);
    });

    test('ENROLE : le moteur n\'est pas préparé au démarrage (préparation paresseuse à l\'usage)', () async {
      final moteur = _Moteur(EtatMoteur.pret);
      final depot = _Depot(const EtatFacialMoi(
        verificationActive: true,
        controleExige: true,
        statut: FacialStatut.enrole,
        appareilCorrespond: true,
      ));
      final c = conteneur(moteur, depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(moteur.preparations, 0);
    });
  });

  group('gabarit', () {
    test('conservé tant qu\'un enrôlement du même compte est en file', () async {
      final store = FaceTemplateStore(kv);
      await store.enregistrer('compte-1', [
        [0.1, 0.2],
      ]);
      await FacialQueue(kv).ajouter(OperationFaciale(
        type: TypeOperationFaciale.enrolement,
        compteId: 'compte-1',
        creeLe: DateTime.utc(2026, 10, 30),
        deviceId: 'd',
        versionConsentement: 'v1',
        modeleVersion: 'm',
        idempotencyKey: 'k',
      ));
      // Le serveur ne connaît pas encore l'enrôlement : NON_ENROLE avec gabarit_a_effacer.
      final depot = _Depot(const EtatFacialMoi(
        verificationActive: true,
        controleExige: true,
        statut: FacialStatut.nonEnrole,
        gabaritAEffacer: true,
      ));
      final c = conteneur(_Moteur(EtatMoteur.pret), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(await store.existe('compte-1'), isTrue);
    });

    test('effacé sur gabarit_a_effacer sans enrôlement en file', () async {
      final store = FaceTemplateStore(kv);
      await store.enregistrer('compte-1', [
        [0.1, 0.2],
      ]);
      final depot = _Depot(const EtatFacialMoi(
        verificationActive: true,
        controleExige: true,
        statut: FacialStatut.revoque,
        gabaritAEffacer: true,
      ));
      final c = conteneur(_Moteur(EtatMoteur.pret), depot);
      await c.read(facialControllerProvider.notifier).demarrer();
      expect(await store.existe('compte-1'), isFalse);
    });
  });

  group('dernier rejet pour blocage (D-P10.3)', () {
    PointageHistoriqueItem p(String id, String statut, DateTime ts) =>
        PointageHistoriqueItem(id: id, statut: statut, type: 'ENTREE', serverTs: ts);

    test('retient le rejet le plus récent du jour, ignore valides, anciens et sans id', () {
      final now = DateTime(2026, 10, 30, 14, 0);
      final r = dernierRejetPourBlocage([
        p('a', 'VALIDE', DateTime(2026, 10, 30, 13, 55)),
        p('b', 'REJETE_VIVACITE', DateTime(2026, 10, 30, 13, 50)),
        p('c', 'REJETE_VISAGE_NON_RECONNU', DateTime(2026, 10, 30, 13, 58)),
        p('d', 'REJETE_VIVACITE', DateTime(2026, 10, 29, 23, 59)),
        p('', 'REJETE_VIVACITE', DateTime(2026, 10, 30, 13, 59)),
      ], now);
      expect(r?.id, 'c');
    });

    test('rejet de la veille à 23 h 55, blocage jusqu\'à 00 h 10 : retenu ; hors fenêtre : ignoré', () {
      final now = DateTime(2026, 10, 31, 0, 5);
      final fin = DateTime(2026, 10, 31, 0, 10);
      final liste = [
        p('veille-proche', 'REJETE_VIVACITE', DateTime(2026, 10, 30, 23, 55)),
        p('veille-ancien', 'REJETE_VIVACITE', DateTime(2026, 10, 30, 23, 40)),
      ];
      expect(dernierRejetPourBlocage(liste, now, bloqueJusquA: fin)?.id, 'veille-proche');
      expect(dernierRejetPourBlocage(liste, now), isNull, reason: 'sans fin de blocage : jour courant seulement');
      expect(dernierRejetPourBlocage([liste[1]], now, bloqueJusquA: fin), isNull);
    });

    test('aucun rejet aujourd\'hui : null', () {
      expect(dernierRejetPourBlocage([p('a', 'VALIDE', DateTime(2026, 10, 30, 8))], DateTime(2026, 10, 30, 14)), isNull);
    });
  });
}

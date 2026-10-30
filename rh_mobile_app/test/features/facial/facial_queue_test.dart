import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/data/face_template_store.dart';
import 'package:rh_mobile_app/features/facial/data/facial_queue.dart';
import 'package:rh_mobile_app/features/facial/data/secure_kv.dart';

OperationFaciale _enrol(String compte, String cle, {int s = 0}) => OperationFaciale(
      type: TypeOperationFaciale.enrolement,
      compteId: compte,
      creeLe: DateTime.utc(2026, 10, 30, 8, 0, s),
      deviceId: 'dev',
      versionConsentement: 'v1',
      modeleVersion: 'mfn-qaihub-1',
      idempotencyKey: cle,
    );

OperationFaciale _retrait(String compte, {int s = 5}) => OperationFaciale(
      type: TypeOperationFaciale.retrait,
      compteId: compte,
      creeLe: DateTime.utc(2026, 10, 30, 8, 0, s),
    );

void main() {
  late MemorySecureKv kv;
  late FacialQueue file;

  setUp(() {
    kv = MemorySecureKv();
    file = FacialQueue(kv);
  });

  test('le retrait est envoyé avant l’enrôlement, même ajouté avant', () async {
    await file.ajouter(_retrait('c1', s: 1));
    await file.ajouter(_enrol('c1', 'k1', s: 0));
    final ordre = (await file.pourCompte('c1')).map((o) => o.type).toList();
    expect(ordre, [TypeOperationFaciale.retrait, TypeOperationFaciale.enrolement]);
  });

  test('un retrait annule l\'enrôlement en attente du même compte seulement', () async {
    await file.ajouter(_enrol('c1', 'k1'));
    await file.ajouter(_enrol('c2', 'k2'));
    await file.ajouter(_retrait('c1'));
    expect((await file.pourCompte('c1')).map((o) => o.type), [TypeOperationFaciale.retrait]);
    expect((await file.pourCompte('c2')).single.idempotencyKey, 'k2');
    expect(await file.aRetraitEnAttente('c1'), isTrue);
    expect(await file.aRetraitEnAttente('c2'), isFalse);
  });

  test('un seul enrôlement en attente par compte (le dernier)', () async {
    await file.ajouter(_enrol('c1', 'k1'));
    await file.ajouter(_enrol('c1', 'k2', s: 9));
    expect((await file.pourCompte('c1')).single.idempotencyKey, 'k2');
  });

  test('l\'enrôlement garde son idempotency_key à travers la persistance', () async {
    await file.ajouter(_enrol('c1', 'cle-stable'));
    final relue = await FacialQueue(kv).pourCompte('c1');
    expect(relue.single.idempotencyKey, 'cle-stable');
    expect(relue.single.deviceId, 'dev');
    expect(relue.single.versionConsentement, 'v1');
  });

  test('vidage : opérations réglées retirées, arrêt au premier échec réseau', () async {
    await file.ajouter(_retrait('c1'));
    final envoyees = <TypeOperationFaciale>[];
    var regles = await file.vider('c1', (op) async {
      envoyees.add(op.type);
      return ResultatEnvoi.reessayer;
    });
    expect(regles, 0);
    expect(envoyees, [TypeOperationFaciale.retrait]);
    expect(await file.aRetraitEnAttente('c1'), isTrue, reason: 'conservé pour un prochain essai');

    regles = await file.vider('c1', (op) async => ResultatEnvoi.ok);
    expect(regles, 1);
    expect(await file.toutes(), isEmpty);
    expect(kv.data.containsKey(FacialQueue.cle), isFalse);
  });

  test('refus définitif du serveur : retiré sans nouvel essai', () async {
    await file.ajouter(_enrol('c1', 'k1'));
    await file.vider('c1', (op) async => ResultatEnvoi.definitif);
    expect(await file.pourCompte('c1'), isEmpty);
  });

  test('les opérations d\'un autre compte ne sont jamais envoyées', () async {
    await file.ajouter(_retrait('c1'));
    var appels = 0;
    await file.vider('c2', (op) async {
      appels++;
      return ResultatEnvoi.ok;
    });
    expect(appels, 0);
    expect(await file.aRetraitEnAttente('c1'), isTrue);
  });

  test('file corrompue : ignorée sans plantage', () async {
    kv.data[FacialQueue.cle] = 'pas du json';
    expect(await file.toutes(), isEmpty);
  });

  test('gabarit : enregistré par compte, lu, effacé', () async {
    final store = FaceTemplateStore(kv);
    await store.enregistrer('c1', [
      [0.1, 0.2],
      [0.3, 0.4],
      [0.5, 0.6],
    ]);
    expect(kv.data.keys, contains('facial_gabarit_c1'));
    expect((await store.lire('c1'))!.length, 3);
    expect(await store.existe('c2'), isFalse);
    await store.effacer('c1');
    expect(await store.existe('c1'), isFalse);
  });
}

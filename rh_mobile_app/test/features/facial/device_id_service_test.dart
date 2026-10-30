import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/data/device_id_service.dart';
import 'package:rh_mobile_app/features/facial/data/face_template_store.dart';
import 'package:rh_mobile_app/features/facial/data/install_marker.dart';
import 'package:rh_mobile_app/features/facial/data/secure_kv.dart';

class _MarqueurMemoire implements InstallMarker {
  bool present = false;
  int creations = 0;

  @override
  Future<bool> existe() async => present;

  @override
  Future<void> creer() async {
    present = true;
    creations++;
  }
}

void main() {
  test('UUID v4 valide', () {
    final id = genererUuidV4();
    expect(id, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
    expect(genererUuidV4(), isNot(id));
  });

  test('généré au premier lancement puis stable', () async {
    final kv = MemorySecureKv();
    var n = 0;
    final s1 = DeviceIdService(kv: kv, generateur: () => 'id-${++n}');
    final id = await s1.obtenir();
    expect(id, 'id-1');
    expect(kv.data[DeviceIdService.cle], 'id-1');
    // Nouveau service (nouveau lancement) : même identifiant relu du stockage sécurisé.
    final s2 = DeviceIdService(kv: kv, generateur: () => 'id-${++n}');
    expect(await s2.obtenir(), 'id-1');
    expect(n, 1);
  });

  test('sans marqueur (Android) : jamais d\'effacement', () async {
    final kv = MemorySecureKv()..data[DeviceIdService.cle] = 'ancien';
    var efface = false;
    final s = DeviceIdService(kv: kv, surReinstallation: () async => efface = true);
    expect(await s.obtenir(), 'ancien');
    expect(efface, isFalse);
  });

  group('marqueur d\'installation iOS', () {
    test('marqueur absent = réinstallation : identifiant et gabarits effacés, marqueur recréé', () async {
      final kv = MemorySecureKv()
        ..data[DeviceIdService.cle] = 'ancien-id'
        ..data['${FaceTemplateStore.prefixe}compte-1'] = '[[1.0]]'
        ..data['access_token'] = 'jeton';
      final marker = _MarqueurMemoire();
      final store = FaceTemplateStore(kv);
      final s = DeviceIdService(
        kv: kv,
        marker: marker,
        surReinstallation: store.effacerTout,
        generateur: () => 'nouvel-id',
      );
      expect(await s.obtenir(), 'nouvel-id');
      expect(kv.data.containsKey('${FaceTemplateStore.prefixe}compte-1'), isFalse);
      expect(kv.data['access_token'], 'jeton', reason: 'seuls identifiant et gabarits sont concernés');
      expect(marker.creations, 1);
    });

    test('marqueur présent : identifiant conservé', () async {
      final kv = MemorySecureKv()..data[DeviceIdService.cle] = 'ancien-id';
      final marker = _MarqueurMemoire()..present = true;
      var efface = false;
      final s = DeviceIdService(kv: kv, marker: marker, surReinstallation: () async => efface = true);
      expect(await s.obtenir(), 'ancien-id');
      expect(efface, isFalse);
      expect(marker.creations, 0);
    });

    test('deuxième appel : pas de nouvelle vérification', () async {
      final kv = MemorySecureKv();
      final marker = _MarqueurMemoire();
      final s = DeviceIdService(kv: kv, marker: marker, generateur: () => 'x');
      await s.obtenir();
      marker.present = false; // simule une suppression en cours de session : ignorée (cache)
      expect(await s.obtenir(), 'x');
      expect(marker.creations, 1);
    });
  });
}

import 'package:camera/camera.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/engine/face_engine.dart';

class _MoteurFactice extends FaceEngineBase {
  _MoteurFactice(this.resultats);

  final List<EtatMoteur> resultats;
  final List<String> journal = [];

  @override
  bool get prisEnCharge => true;

  @override
  Future<EtatMoteur> preparerImpl() async {
    journal.add('preparer');
    return resultats[(journal.where((e) => e == 'preparer').length - 1).clamp(0, resultats.length - 1)];
  }

  @override
  Future<CameraDescription?> cameraFrontaleImpl() async {
    journal.add('camera');
    return const CameraDescription(
      name: 'front',
      lensDirection: CameraLensDirection.front,
      sensorOrientation: 270,
    );
  }

  @override
  Future<FaceAnalysis?> analyserImpl(CameraImage image, CameraDescription camera) async => null;

  @override
  Future<void> libererImpl() async {}
}

void main() {
  test('parcours ENROLE sans passage par le consentement : preparer avant l\'ouverture de la caméra', () async {
    final moteur = _MoteurFactice([EtatMoteur.pret]);
    // Aucun appel préalable à preparer() : la caméra est demandée directement (écran de vérification).
    final cam = await moteur.cameraFrontale();
    expect(cam, isNotNull);
    expect(moteur.journal, ['preparer', 'camera']);
  });

  test('preparer idempotent : une seule préparation, résultat mis en cache', () async {
    final moteur = _MoteurFactice([EtatMoteur.pret]);
    await Future.wait([moteur.preparer(), moteur.preparer()]);
    await moteur.cameraFrontale();
    await moteur.cameraFrontale();
    expect(moteur.journal.where((e) => e == 'preparer'), hasLength(1));
  });

  test('moteur non prêt (modèle absent) : pas de caméra', () async {
    final moteur = _MoteurFactice([EtatMoteur.modeleAbsent]);
    expect(await moteur.cameraFrontale(), isNull);
    expect(moteur.journal, ['preparer']);
  });

  test('échec ponctuel des caméras : non mis en cache, nouvel essai', () async {
    final moteur = _MoteurFactice([EtatMoteur.camerasIndisponibles, EtatMoteur.pret]);
    expect(await moteur.preparer(), EtatMoteur.camerasIndisponibles);
    expect(await moteur.preparer(), EtatMoteur.pret);
    expect(await moteur.preparer(), EtatMoteur.pret);
    expect(moteur.journal.where((e) => e == 'preparer'), hasLength(2));
  });

  test('résultat définitif (sans caméra frontale) conservé', () async {
    final moteur = _MoteurFactice([EtatMoteur.sansCameraFrontale, EtatMoteur.pret]);
    expect(await moteur.preparer(), EtatMoteur.sansCameraFrontale);
    expect(await moteur.preparer(), EtatMoteur.sansCameraFrontale);
  });
}

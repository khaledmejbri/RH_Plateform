import 'package:camera/camera.dart';

import 'face_engine.dart';

/// Web : moteur vide (aucun écran facial, aucune dépendance à `dart:ffi`).
FaceEngine creerFaceEngine() => _FaceEngineVide();

class _FaceEngineVide implements FaceEngine {
  @override
  bool get prisEnCharge => false;

  @override
  Future<EtatMoteur> preparer() async => EtatMoteur.modeleAbsent;

  @override
  Future<CameraDescription?> cameraFrontale() async => null;

  @override
  Future<FaceAnalysis?> analyser(CameraImage image, CameraDescription camera) async => null;

  @override
  Future<void> liberer() async {}
}

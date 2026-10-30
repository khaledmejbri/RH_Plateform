import 'package:camera/camera.dart';

import '../data/liveness_state_machine.dart';

export 'face_engine_stub.dart' if (dart.library.ffi) 'face_engine_native.dart';

/// Résultat de l'analyse d'une image : observation pour la vivacité, embedding à la demande
/// (calculé sur la même image, jamais conservé ni envoyé).
class FaceAnalysis {
  const FaceAnalysis({required this.observation, this.calculerEmbedding});

  final FaceObservation observation;

  /// `null` si aucun visage unique exploitable. Vecteur L2-normalisé de [FaceModelInfo.tailleEmbedding] valeurs.
  final Future<List<double>?> Function()? calculerEmbedding;
}

/// État de préparation du moteur.
enum EtatMoteur {
  pret,

  /// `availableCameras()` a réussi mais ne contient aucune caméra frontale : incompatibilité réelle.
  sansCameraFrontale,

  /// Asset `.tflite` absent du paquet : défaut d'empaquetage, **pas** une incompatibilité de l'appareil.
  modeleAbsent,

  /// Asset présent mais refusé par l'interpréteur, ou formes inattendues : incompatibilité réelle.
  modeleNonChargeable,

  /// Échec ponctuel de `availableCameras()` : rien n'est signalé, nouvel essai au prochain appel.
  camerasIndisponibles,
}

/// Moteur facial interchangeable (ADR : `FaceEmbedder`). Implémentation native (ML Kit + MobileFaceNet sur
/// LiteRT) uniquement quand `dart:ffi` existe ; sur le web, implémentation vide : aucun écran facial.
abstract class FaceEngine {
  /// `false` sur le web : l'appli n'affiche aucun écran facial et envoie `plateforme = WEB`.
  bool get prisEnCharge;

  /// Charge le modèle et vérifie la caméra frontale. Idempotent : le résultat définitif est mis en cache
  /// (sauf [EtatMoteur.camerasIndisponibles]). Ne lève jamais d'exception.
  Future<EtatMoteur> preparer();

  /// Caméra frontale ; prépare le moteur au besoin. `null` si le moteur n'est pas [EtatMoteur.pret].
  Future<CameraDescription?> cameraFrontale();

  /// Analyse une image du flux caméra (format `nv21` sur Android, `bgra8888` sur iOS) ; prépare le moteur au besoin.
  Future<FaceAnalysis?> analyser(CameraImage image, CameraDescription camera);

  Future<void> liberer();
}

/// Base commune : [preparer] idempotent, appelé paresseusement avant toute utilisation du moteur
/// (parcours ENROLE sans passage par l'écran de consentement, réactivation depuis le profil...).
abstract class FaceEngineBase implements FaceEngine {
  Future<EtatMoteur>? _preparation;

  /// Préparation réelle (chargement du modèle, recherche de la caméra).
  Future<EtatMoteur> preparerImpl();
  Future<CameraDescription?> cameraFrontaleImpl();
  Future<FaceAnalysis?> analyserImpl(CameraImage image, CameraDescription camera);
  Future<void> libererImpl();

  @override
  Future<EtatMoteur> preparer() {
    final en = _preparation;
    if (en != null) return en;
    final p = preparerImpl().then((etat) {
      if (etat == EtatMoteur.camerasIndisponibles) _preparation = null; // nouvel essai possible
      return etat;
    }).catchError((Object _) {
      _preparation = null;
      return EtatMoteur.camerasIndisponibles;
    });
    _preparation = p;
    return p;
  }

  @override
  Future<CameraDescription?> cameraFrontale() async {
    if (await preparer() != EtatMoteur.pret) return null;
    return cameraFrontaleImpl();
  }

  @override
  Future<FaceAnalysis?> analyser(CameraImage image, CameraDescription camera) async {
    if (await preparer() != EtatMoteur.pret) return null;
    return analyserImpl(image, camera);
  }

  @override
  Future<void> liberer() async {
    // Attend la préparation en cours : sinon le détecteur / l'interpréteur créés après coup fuiraient.
    final en = _preparation;
    _preparation = null;
    if (en != null) {
      try {
        await en;
      } catch (_) {}
    }
    await libererImpl();
  }
}

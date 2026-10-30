import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show Size;

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart' show compute, defaultTargetPlatform, TargetPlatform, debugPrint;
import 'package:flutter/services.dart' show rootBundle;
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:image/image.dart' as img;
import 'package:tflite_flutter/tflite_flutter.dart';

import '../data/face_math.dart';
import '../data/liveness_state_machine.dart';
import 'face_engine.dart';

/// Moteur natif : ML Kit (détection, yeux, angle, suivi) + MobileFaceNet sur LiteRT (embedding 128).
FaceEngine creerFaceEngine() => _FaceEngineNatif();

class _FaceEngineNatif extends FaceEngineBase {
  FaceDetector? _detector;
  Interpreter? _interpreter;
  CameraDescription? _front;

  @override
  bool get prisEnCharge => true;

  @override
  Future<CameraDescription?> cameraFrontaleImpl() async => _front;

  @override
  Future<EtatMoteur> preparerImpl() async {
    // 1. Caméras : un échec ponctuel n'est pas une incompatibilité (nouvel essai au prochain appel).
    try {
      final cams = await availableCameras();
      for (final c in cams) {
        if (c.lensDirection == CameraLensDirection.front) {
          _front = c;
          break;
        }
      }
    } catch (e) {
      debugPrint('[Facial] Caméras indisponibles (échec ponctuel) : $e');
      return EtatMoteur.camerasIndisponibles;
    }
    if (_front == null) return EtatMoteur.sansCameraFrontale;

    // 2. Asset absent = défaut d'empaquetage, pas une incompatibilité de l'appareil.
    final ByteData data;
    try {
      data = await rootBundle.load(FaceModelInfo.assetPath);
    } catch (e) {
      debugPrint('[Facial] Modèle ${FaceModelInfo.assetPath} absent de cette version : $e');
      return EtatMoteur.modeleAbsent;
    }

    // 3. Asset présent mais refusé par l'interpréteur ou formes inattendues : incompatibilité réelle.
    try {
      final interp = Interpreter.fromBuffer(data.buffer.asUint8List());
      final entree = interp.getInputTensor(0).shape;
      final sortie = interp.getOutputTensor(0).shape;
      final tailleOk = entree.length == 4 &&
          entree[1] == FaceModelInfo.tailleEntree &&
          entree[2] == FaceModelInfo.tailleEntree &&
          entree[3] == 3 &&
          sortie.isNotEmpty &&
          sortie.last == FaceModelInfo.tailleEmbedding;
      if (!tailleOk) {
        interp.close();
        debugPrint('[Facial] Forme du modèle inattendue : $entree -> $sortie');
        return EtatMoteur.modeleNonChargeable;
      }
      _interpreter = interp;
      _detector = FaceDetector(
        options: FaceDetectorOptions(
          enableClassification: true,
          enableTracking: true,
          minFaceSize: 0.3,
          performanceMode: FaceDetectorMode.fast,
        ),
      );
      return EtatMoteur.pret;
    } catch (e) {
      debugPrint('[Facial] Modèle non chargeable : $e');
      return EtatMoteur.modeleNonChargeable;
    }
  }

  @override
  Future<FaceAnalysis?> analyserImpl(CameraImage image, CameraDescription camera) async {
    final detector = _detector;
    if (detector == null) return null;
    final input = _versInputImage(image, camera);
    if (input == null) return null;
    final List<Face> faces;
    try {
      faces = await detector.processImage(input);
    } catch (e) {
      debugPrint('[Facial] Détection impossible : $e');
      return null;
    }
    if (faces.isEmpty) return const FaceAnalysis(observation: FaceObservation.aucunVisage());
    if (faces.length > 1) {
      return FaceAnalysis(observation: FaceObservation(faceCount: faces.length));
    }
    final f = faces.first;
    return FaceAnalysis(
      observation: FaceObservation(
        faceCount: 1,
        trackingId: f.trackingId,
        leftEyeOpen: f.leftEyeOpenProbability,
        rightEyeOpen: f.rightEyeOpenProbability,
        headYaw: f.headEulerAngleY,
      ),
      calculerEmbedding: () async => _embedding(image, camera, f),
    );
  }

  InputImage? _versInputImage(CameraImage image, CameraDescription camera) {
    final rotation = InputImageRotationValue.fromRawValue(camera.sensorOrientation);
    if (rotation == null) return null;
    final android = defaultTargetPlatform == TargetPlatform.android;
    if (image.planes.isEmpty) return null;
    // Android : une seule plane NV21 attendue ; iOS : BGRA 8 bits.
    if (android && image.planes.length != 1) return null;
    return InputImage.fromBytes(
      bytes: image.planes.first.bytes,
      metadata: InputImageMetadata(
        size: Size(image.width.toDouble(), image.height.toDouble()),
        rotation: rotation,
        format: android ? InputImageFormat.nv21 : InputImageFormat.bgra8888,
        bytesPerRow: image.planes.first.bytesPerRow,
      ),
    );
  }

  Future<List<double>?> _embedding(CameraImage image, CameraDescription camera, Face face) async {
    final interp = _interpreter;
    if (interp == null) return null;
    try {
      final box = face.boundingBox;
      // Conversion, rotation, recadrage et normalisation : hors du thread principal.
      final entree = await compute(
        _preparerEntree,
        _EntreeImage(
          planes: [for (final p in image.planes) p.bytes],
          bytesPerRow: [for (final p in image.planes) p.bytesPerRow],
          bytesPerPixel: [for (final p in image.planes) p.bytesPerPixel ?? 1],
          width: image.width,
          height: image.height,
          ios: defaultTargetPlatform == TargetPlatform.iOS,
          rotation: camera.sensorOrientation,
          boxCx: box.center.dx,
          boxCy: box.center.dy,
          boxCote: math.max(box.width, box.height),
        ),
      );
      const taille = FaceModelInfo.tailleEntree;
      if (entree.length != taille * taille * 3) return null;
      var k = 0;
      final tenseur = [
        List.generate(taille, (_) {
          return List.generate(taille, (_) {
            final px = <double>[entree[k], entree[k + 1], entree[k + 2]];
            k += 3;
            return px;
          });
        }),
      ];
      final sortie = [List<double>.filled(FaceModelInfo.tailleEmbedding, 0)];
      interp.run(tenseur, sortie);
      return normaliserL2(sortie.first);
    } catch (e) {
      debugPrint('[Facial] Embedding impossible : $e');
      return null;
    }
  }

  @override
  Future<void> libererImpl() async {
    try {
      await _detector?.close();
    } catch (_) {}
    _detector = null;
    _interpreter?.close();
    _interpreter = null;
  }
}

/// Données envoyées à l'isolat (uniquement des types transmissibles).
class _EntreeImage {
  const _EntreeImage({
    required this.planes,
    required this.bytesPerRow,
    required this.bytesPerPixel,
    required this.width,
    required this.height,
    required this.ios,
    required this.rotation,
    required this.boxCx,
    required this.boxCy,
    required this.boxCote,
  });

  final List<Uint8List> planes;
  final List<int> bytesPerRow;
  final List<int> bytesPerPixel;
  final int width;
  final int height;
  final bool ios;
  final int rotation;
  final double boxCx;
  final double boxCy;
  final double boxCote;
}

/// Image caméra -> tenseur 112x112x3 normalisé (aplati, ordre RGB). Liste vide en cas d'échec.
List<double> _preparerEntree(_EntreeImage e) {
  final complet = _rgb(e);
  if (complet == null) return const [];
  final droite = e.rotation % 360 == 0 ? complet : img.copyRotate(complet, angle: e.rotation);
  // Carré centré sur le visage, marge de 15 %, borné à l'image.
  final cote = e.boxCote * 1.15;
  var x = (e.boxCx - cote / 2).round();
  var y = (e.boxCy - cote / 2).round();
  var w = cote.round();
  x = x.clamp(0, math.max(0, droite.width - 1));
  y = y.clamp(0, math.max(0, droite.height - 1));
  w = math.min(w, math.min(droite.width - x, droite.height - y));
  if (w < 40) return const [];
  final recadre = img.copyCrop(droite, x: x, y: y, width: w, height: w);
  const taille = FaceModelInfo.tailleEntree;
  final petit = img.copyResize(
    recadre,
    width: taille,
    height: taille,
    interpolation: img.Interpolation.linear,
  );
  final out = List<double>.filled(taille * taille * 3, 0);
  var k = 0;
  for (var py = 0; py < taille; py++) {
    for (var px = 0; px < taille; px++) {
      final p = petit.getPixel(px, py);
      out[k++] = (p.r - FaceModelInfo.entreeMoyenne) / FaceModelInfo.entreeEcart;
      out[k++] = (p.g - FaceModelInfo.entreeMoyenne) / FaceModelInfo.entreeEcart;
      out[k++] = (p.b - FaceModelInfo.entreeMoyenne) / FaceModelInfo.entreeEcart;
    }
  }
  return out;
}

img.Image? _rgb(_EntreeImage e) {
  final w = e.width, h = e.height;
  final out = img.Image(width: w, height: h, numChannels: 3);
  if (e.ios) {
    final bytes = e.planes.first;
    final stride = e.bytesPerRow.first;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final i = y * stride + x * 4;
        out.setPixelRgb(x, y, bytes[i + 2], bytes[i + 1], bytes[i]); // BGRA
      }
    }
    return out;
  }
  if (e.planes.length == 1) {
    // NV21 : plan Y puis VU entrelacés.
    final bytes = e.planes.first;
    final stride = e.bytesPerRow.first;
    final base = stride * h;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final yy = bytes[y * stride + x];
        final uvi = base + (y >> 1) * stride + (x & ~1);
        if (uvi + 1 >= bytes.length) return null;
        _ecrireYuv(out, x, y, yy, bytes[uvi + 1] - 128, bytes[uvi] - 128);
      }
    }
    return out;
  }
  if (e.planes.length == 3) {
    // YUV 4:2:0 en trois plans (repli).
    final py = e.planes[0], pu = e.planes[1], pv = e.planes[2];
    final pixelStride = e.bytesPerPixel[1];
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final yy = py[y * e.bytesPerRow[0] + x];
        final ci = (y >> 1) * e.bytesPerRow[1] + (x >> 1) * pixelStride;
        if (ci >= pu.length || ci >= pv.length) return null;
        _ecrireYuv(out, x, y, yy, pu[ci] - 128, pv[ci] - 128);
      }
    }
    return out;
  }
  return null;
}

void _ecrireYuv(img.Image out, int x, int y, int yy, int u, int v) {
  final r = (yy + 1.402 * v).round().clamp(0, 255);
  final g = (yy - 0.344136 * u - 0.714136 * v).round().clamp(0, 255);
  final b = (yy + 1.772 * u).round().clamp(0, 255);
  out.setPixelRgb(x, y, r, g, b);
}

import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart' show TargetPlatform, defaultTargetPlatform, debugPrint;
import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../data/liveness_state_machine.dart';
import '../engine/face_engine.dart';

/// Issue d'une prise : un embedding (jamais l'image) ou un échec de vivacité / de caméra.
class FaceCaptureOutcome {
  const FaceCaptureOutcome.reussi(List<double> this.embedding)
      : echec = null,
        cameraIndisponible = null;
  const FaceCaptureOutcome.vivaciteEchouee(LivenessFailure this.echec)
      : embedding = null,
        cameraIndisponible = null;
  const FaceCaptureOutcome.cameraIndisponible(String this.cameraIndisponible)
      : embedding = null,
        echec = null;

  final List<double>? embedding;
  final LivenessFailure? echec;

  /// Message (caméra refusée, ouverture impossible, capture impossible) : pas un échec de vivacité.
  final String? cameraIndisponible;
}

/// Message français pour un moteur non prêt.
String messageMoteur(EtatMoteur etat) {
  switch (etat) {
    case EtatMoteur.modeleAbsent:
      return 'La vérification faciale n’est pas disponible dans cette version de l’application.';
    case EtatMoteur.modeleNonChargeable:
      return 'Ce téléphone ne peut pas exécuter la vérification faciale.';
    case EtatMoteur.camerasIndisponibles:
      return 'Impossible d’accéder aux caméras. Réessayez.';
    case EtatMoteur.sansCameraFrontale:
    case EtatMoteur.pret:
      return 'Aucune caméra frontale disponible.';
  }
}

/// Caméra frontale + contrôle du vivant : la logique est dans [LivenessStateMachine], ce widget ne fait que
/// brancher le flux d'images. L'image n'est ni enregistrée ni envoyée ; seul l'embedding est remis à [onTermine].
class FaceLivenessView extends StatefulWidget {
  const FaceLivenessView({
    super.key,
    required this.engine,
    required this.onTermine,
    this.nbDefis = 2,
    this.chrono = const Duration(seconds: 10),
    this.etiquette,
  });

  final FaceEngine engine;
  final void Function(FaceCaptureOutcome outcome) onTermine;
  final int nbDefis;

  /// `null` à l'enrôlement (hors chrono).
  final Duration? chrono;
  final String? etiquette;

  @override
  State<FaceLivenessView> createState() => _FaceLivenessViewState();
}

class _FaceLivenessViewState extends State<FaceLivenessView> {
  CameraController? _camera;
  CameraDescription? _description;
  late LivenessStateMachine _machine;
  Timer? _minuteur;
  bool _occupe = false;
  bool _fini = false;

  @override
  void initState() {
    super.initState();
    _machine = LivenessStateMachine(
      nbDefis: widget.nbDefis,
      chrono: widget.chrono,
      // La convention de signe de l'angle de tête diffère selon la plateforme : à valider sur téléphone.
      inverserSensRotation: defaultTargetPlatform == TargetPlatform.iOS,
    );
    _ouvrirCamera();
  }

  Future<void> _ouvrirCamera() async {
    try {
      final desc = await widget.engine.cameraFrontale();
      if (desc == null) {
        _terminer(FaceCaptureOutcome.cameraIndisponible(messageMoteur(await widget.engine.preparer())));
        return;
      }
      _description = desc;
      final controller = CameraController(
        desc,
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: defaultTargetPlatform == TargetPlatform.iOS
            ? ImageFormatGroup.bgra8888
            : ImageFormatGroup.nv21,
      );
      await controller.initialize();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      _camera = controller;
      setState(() {});
      // Le chrono démarre à l'affichage du premier défi.
      _machine.demarrer();
      _minuteur = Timer.periodic(const Duration(milliseconds: 250), (_) => _tick());
      await controller.startImageStream(_surImage);
    } on CameraException catch (e) {
      final refuse = e.code.toLowerCase().contains('access');
      _terminer(FaceCaptureOutcome.cameraIndisponible(
        refuse
            ? 'L’accès à la caméra est refusé. Autorisez-le dans les réglages du téléphone.'
            : 'Impossible d’ouvrir la caméra.',
      ));
    } catch (e) {
      debugPrint('[Facial] Caméra : $e');
      _terminer(const FaceCaptureOutcome.cameraIndisponible('Impossible d’ouvrir la caméra.'));
    }
  }

  void _tick() {
    if (_fini) return;
    if (_machine.tick() == LivenessPhase.echec) {
      _terminer(FaceCaptureOutcome.vivaciteEchouee(_machine.echec!));
    } else if (mounted) {
      setState(() {});
    }
  }

  Future<void> _surImage(CameraImage image) async {
    final desc = _description;
    if (_occupe || _fini || desc == null) return;
    _occupe = true;
    try {
      final analyse = await widget.engine.analyser(image, desc);
      if (_fini || analyse == null) return;
      final phase = _machine.mettreAJour(analyse.observation);
      if (phase == LivenessPhase.echec) {
        _terminer(FaceCaptureOutcome.vivaciteEchouee(_machine.echec!));
      } else if (phase == LivenessPhase.reussi) {
        final calcul = analyse.calculerEmbedding;
        final embedding = calcul == null ? null : await calcul();
        if (embedding == null) {
          _terminer(const FaceCaptureOutcome.cameraIndisponible(
              'Capture du visage impossible. Réessayez.'));
        } else {
          _terminer(FaceCaptureOutcome.reussi(embedding));
        }
      } else if (mounted) {
        setState(() {});
      }
    } finally {
      _occupe = false;
    }
  }

  void _terminer(FaceCaptureOutcome outcome) {
    if (_fini) return;
    _fini = true;
    _minuteur?.cancel();
    _arreterFlux();
    if (mounted) widget.onTermine(outcome);
  }

  Future<void> _arreterFlux() async {
    final c = _camera;
    if (c == null) return;
    try {
      if (c.value.isStreamingImages) await c.stopImageStream();
    } catch (_) {}
  }

  @override
  void dispose() {
    _fini = true;
    _minuteur?.cancel();
    final c = _camera;
    _camera = null;
    if (c != null) {
      // Le flux est arrêté avec le contrôleur ; aucune image n'est conservée.
      c.dispose();
    }
    super.dispose();
  }

  String get _consigne {
    final defi = _machine.defiCourant;
    if (defi != null) return defi.consigne;
    return 'Regardez droit devant vous';
  }

  @override
  Widget build(BuildContext context) {
    final camera = _camera;
    final reste = _machine.tempsRestant;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.etiquette != null) ...[
          Text(
            widget.etiquette!,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(color: AppTheme.textSecondary),
          ),
          const SizedBox(height: 8),
        ],
        Container(
          width: 260,
          height: 260,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: AppTheme.primary, width: 4),
          ),
          child: ClipOval(
            child: camera == null || !camera.value.isInitialized
                ? const Center(child: CircularProgressIndicator())
                : FittedBox(
                    fit: BoxFit.cover,
                    child: SizedBox(
                      width: camera.value.previewSize?.height ?? 260,
                      height: camera.value.previewSize?.width ?? 260,
                      child: CameraPreview(camera),
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 20),
        Semantics(
          liveRegion: true,
          child: Text(
            _consigne,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var i = 0; i < _machine.defis.length; i++)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Icon(
                  i < _machine.indexDefi ? Icons.check_circle_rounded : Icons.radio_button_unchecked,
                  size: 18,
                  color: i < _machine.indexDefi ? AppTheme.success : AppTheme.textTertiary,
                ),
              ),
            if (reste != null) ...[
              const SizedBox(width: 12),
              Text('${reste.inSeconds + 1} s', style: Theme.of(context).textTheme.bodySmall),
            ],
          ],
        ),
      ],
    );
  }
}

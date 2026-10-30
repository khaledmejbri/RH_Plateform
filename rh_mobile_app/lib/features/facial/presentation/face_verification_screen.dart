import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/face_proof.dart';
import '../providers/facial_controller.dart';
import 'face_liveness_view.dart';

/// Étape visage du pointage (après le GPS) : 2 défis en 10 s, une prise de face, comparaison sur l'appareil.
///
/// Se ferme avec une [FaceProof] (score + vivant, ou vivant échoué), avec `null` si l'utilisateur annule.
/// Caméra impossible ou gabarit illisible : l'utilisateur choisit d'envoyer sans preuve (rejet serveur puis
/// pointage manuel D-P04) ou d'annuler.
class FaceVerificationScreen extends ConsumerStatefulWidget {
  const FaceVerificationScreen({super.key});

  @override
  ConsumerState<FaceVerificationScreen> createState() => _FaceVerificationScreenState();
}

class _FaceVerificationScreenState extends ConsumerState<FaceVerificationScreen> {
  List<List<double>>? _gabarit;
  bool _pret = false;
  String? _probleme;
  bool _fermee = false;

  @override
  void initState() {
    super.initState();
    _charger();
  }

  Future<void> _charger() async {
    final g = await ref.read(facialControllerProvider.notifier).gabaritPourPointage();
    if (!mounted) return;
    setState(() {
      _gabarit = g;
      _pret = true;
      if (g == null) {
        _probleme = 'Votre gabarit facial est introuvable sur ce téléphone.';
        ref.read(facialControllerProvider.notifier).gabaritAbsent();
      }
    });
  }

  void _fermer(FaceProof? preuve) {
    if (_fermee || !mounted) return;
    _fermee = true;
    Navigator.of(context).pop(preuve);
  }

  void _surPrise(FaceCaptureOutcome o) {
    final emb = o.embedding;
    final gabarit = _gabarit;
    if (emb != null && gabarit != null) {
      _fermer(FaceProof.depuisEmbedding(emb, gabarit));
    } else if (o.echec != null) {
      _fermer(const FaceProof.vivaciteEchouee());
    } else {
      setState(() => _probleme = o.cameraIndisponible ?? 'Capture impossible.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final engine = ref.watch(faceEngineProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Vérification du visage'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: !_pret
              ? const Center(child: CircularProgressIndicator())
              : _probleme != null
                  ? Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Icon(Icons.no_photography_outlined, size: 56, color: AppTheme.error),
                        const SizedBox(height: 16),
                        Text(_probleme!, textAlign: TextAlign.center),
                        const SizedBox(height: 8),
                        const Text(
                          'Sans vérification faciale, le pointage sera refusé : vous pourrez alors demander '
                          'un pointage manuel à votre responsable.',
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 24),
                        FilledButton(
                          onPressed: () => _fermer(const FaceProof.aucune()),
                          child: const Text('Envoyer sans vérification'),
                        ),
                        TextButton(onPressed: () => _fermer(null), child: const Text('Annuler')),
                      ],
                    )
                  : Center(
                      child: SingleChildScrollView(
                        child: FaceLivenessView(
                          engine: engine,
                          onTermine: _surPrise,
                          etiquette: 'Vous avez 10 secondes',
                        ),
                      ),
                    ),
        ),
      ),
    );
  }
}

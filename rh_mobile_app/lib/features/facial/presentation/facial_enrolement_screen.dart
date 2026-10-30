import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../data/facial_errors.dart';
import '../data/facial_repository.dart';
import '../providers/facial_controller.dart';
import 'face_liveness_view.dart';

enum _Etape { intro, capture, envoi, termine, enAttente, erreur }

/// Enrôlement (D-P07) : contrôle du vivant (2 défis) puis 3 prises de face, hors chrono. Le gabarit reste sur
/// l'appareil ; le serveur reçoit seulement l'état d'enrôlement (version du consentement, `device_id`).
class FacialEnrolementScreen extends ConsumerStatefulWidget {
  const FacialEnrolementScreen({super.key, required this.versionConsentement});

  final String versionConsentement;

  @override
  ConsumerState<FacialEnrolementScreen> createState() => _FacialEnrolementScreenState();
}

class _FacialEnrolementScreenState extends ConsumerState<FacialEnrolementScreen> {
  static const _nbPrises = 3;

  _Etape _etape = _Etape.intro;
  final List<List<double>> _embeddings = [];
  int _tentative = 0;
  String? _message;

  void _demarrer() {
    setState(() {
      _embeddings.clear();
      _message = null;
      _tentative++;
      _etape = _Etape.capture;
    });
  }

  void _surPrise(FaceCaptureOutcome o) {
    if (!mounted) return;
    final emb = o.embedding;
    if (emb != null) {
      _message = null;
      _embeddings.add(emb);
      if (_embeddings.length >= _nbPrises) {
        _envoyer();
      } else {
        setState(() => _tentative++);
      }
      return;
    }
    setState(() {
      _message = o.echec?.message ?? o.cameraIndisponible ?? 'Capture impossible. Réessayez.';
      // Échec de vivacité : on reprend la prise en cours ; caméra indisponible : écran d'erreur.
      if (o.cameraIndisponible != null) {
        _etape = _Etape.erreur;
      } else {
        _tentative++;
      }
    });
  }

  Future<void> _envoyer() async {
    setState(() => _etape = _Etape.envoi);
    try {
      await ref.read(facialControllerProvider.notifier).enroler(
            versionConsentement: widget.versionConsentement,
            embeddings: List.of(_embeddings),
          );
      if (!mounted) return;
      setState(() => _etape = _Etape.termine);
    } catch (e) {
      if (!mounted) return;
      final reseau = e is FacialApiException && e.reseau;
      setState(() {
        _message = reseau
            ? 'Pas de réseau : votre enrôlement est conservé sur ce téléphone et sera transmis dès que '
                'la connexion revient.'
            : messageErreurFaciale(e);
        _etape = reseau ? _Etape.enAttente : _Etape.erreur;
      });
    } finally {
      _embeddings.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final engine = ref.watch(faceEngineProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Enrôlement du visage')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: switch (_etape) {
            _Etape.intro => _carte(
                icone: Icons.face_rounded,
                titre: 'Enregistrer votre visage',
                texte: 'Nous allons vérifier que vous êtes bien vivant (deux consignes simples), puis '
                    'faire $_nbPrises prises de face. Placez-vous dans un endroit bien éclairé, seul devant la '
                    'caméra. Aucune photo n’est enregistrée ni envoyée.',
                action: FilledButton(onPressed: _demarrer, child: const Text('Commencer')),
              ),
            _Etape.capture => Column(
                children: [
                  Text(
                    'Prise ${_embeddings.length + 1} sur $_nbPrises',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (_message != null) ...[
                    const SizedBox(height: 8),
                    Text(_message!, style: const TextStyle(color: AppTheme.error), textAlign: TextAlign.center),
                  ],
                  const SizedBox(height: 16),
                  Expanded(
                    child: Center(
                      child: SingleChildScrollView(
                        child: FaceLivenessView(
                          key: ValueKey('prise-$_tentative'),
                          engine: engine,
                          // 1re prise : 2 défis ; les suivantes : 1 défi pour varier la pose. Hors chrono.
                          nbDefis: _embeddings.isEmpty ? 2 : 1,
                          chrono: null,
                          onTermine: _surPrise,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            _Etape.envoi => const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 16),
                    Text('Enregistrement de votre enrôlement…'),
                  ],
                ),
              ),
            _Etape.termine => _carte(
                icone: Icons.check_circle_rounded,
                couleur: AppTheme.success,
                titre: 'Enrôlement terminé',
                texte: 'Votre visage sera vérifié à chaque pointage, à partir de la date de mise en service.',
                action: FilledButton(onPressed: () => context.pop(), child: const Text('Terminer')),
              ),
            _Etape.enAttente => _carte(
                icone: Icons.cloud_upload_outlined,
                titre: 'Enrôlement en attente',
                texte: _message ?? '',
                action: FilledButton(onPressed: () => context.pop(), child: const Text('Fermer')),
              ),
            _Etape.erreur => _carte(
                icone: Icons.error_outline_rounded,
                couleur: AppTheme.error,
                titre: 'Enrôlement impossible',
                texte: _message ?? 'Une erreur est survenue.',
                action: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    FilledButton(onPressed: _demarrer, child: const Text('Réessayer')),
                    TextButton(onPressed: () => context.pop(), child: const Text('Fermer')),
                  ],
                ),
              ),
          },
        ),
      ),
    );
  }

  Widget _carte({
    required IconData icone,
    required String titre,
    required String texte,
    required Widget action,
    Color couleur = AppTheme.primary,
  }) {
    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Icon(icone, size: 64, color: couleur),
            const SizedBox(height: 16),
            Text(
              titre,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            Text(texte, textAlign: TextAlign.center),
            const SizedBox(height: 24),
            action,
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../data/facial_errors.dart';
import '../data/facial_models.dart';
import '../data/facial_repository.dart';
import '../providers/facial_controller.dart';

final _consentementCourantProvider = FutureProvider.autoDispose<ConsentementCourant>((ref) {
  return ref.watch(facialRepositoryProvider).consentementCourant();
});

/// Consentement (premier écran, avant enrôlement) ou reconsentement (nouvelle version du texte, D-P07.f).
/// Le texte est servi par le serveur : jamais embarqué dans l'appli.
class FacialConsentementScreen extends ConsumerStatefulWidget {
  const FacialConsentementScreen({super.key, this.mode});

  /// `null` : déduit du routage (`GET /moi`).
  final FacialRoute? mode;

  @override
  ConsumerState<FacialConsentementScreen> createState() => _FacialConsentementScreenState();
}

class _FacialConsentementScreenState extends ConsumerState<FacialConsentementScreen> {
  bool _busy = false;
  String? _erreur;

  FacialRoute get _mode {
    final m = widget.mode ?? ref.read(facialControllerProvider).decision.route;
    return m == FacialRoute.aucune ? FacialRoute.consentement : m;
  }

  Future<void> _accepter(ConsentementCourant c) async {
    if (_mode == FacialRoute.reconsentement) {
      await _executer(() async {
        await ref.read(facialControllerProvider.notifier).accepterReconsentement(c.version);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Nouveau consentement enregistré.')),
        );
        context.pop();
      });
    } else {
      context.pushReplacement('/facial/enrolement', extra: c.version);
    }
  }

  Future<void> _refuser(ConsentementCourant c) async {
    final reconsent = _mode == FacialRoute.reconsentement;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(reconsent ? 'Refuser le nouveau texte ?' : 'Refuser la vérification faciale ?'),
        content: Text(
          reconsent
              ? 'Votre consentement sera retiré et votre gabarit effacé de ce téléphone. '
                  'Vous pourrez toujours pointer avec le QR et le GPS.'
              : 'Vous pointerez avec le QR et le GPS, sans pénalité. '
                  'Vous pourrez changer d’avis plus tard depuis votre profil.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Refuser')),
        ],
      ),
    );
    if (ok != true) return;
    await _executer(() async {
      await ref.read(facialControllerProvider.notifier).refuser(c.version);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Décision enregistrée. Le pointage QR + GPS reste possible.')),
      );
      context.pop();
    });
  }

  Future<void> _executer(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _erreur = null;
    });
    try {
      await action();
    } catch (e) {
      if (!mounted) return;
      setState(() => _erreur = messageErreurFaciale(e));
      if (e is FacialApiException && e.code == 'VERSION_CONSENTEMENT_OBSOLETE') {
        ref.invalidate(_consentementCourantProvider);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final texte = ref.watch(_consentementCourantProvider);
    final reconsent = _mode == FacialRoute.reconsentement;
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: Text(reconsent ? 'Nouveau consentement' : 'Vérification faciale'),
      ),
      body: SafeArea(
        child: texte.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.cloud_off_rounded, size: 48, color: AppTheme.textTertiary),
                const SizedBox(height: 16),
                Text(messageErreurFaciale(FacialApiException.depuis(e)), textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => ref.invalidate(_consentementCourantProvider),
                  child: const Text('Réessayer'),
                ),
                TextButton(onPressed: () => context.pop(), child: const Text('Plus tard')),
              ],
            ),
          ),
          data: (c) => Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        reconsent
                            ? 'Le texte de consentement a changé'
                            : 'Vérifier votre visage au pointage',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'La comparaison se fait sur votre téléphone. Aucune photo ni donnée '
                        'biométrique n’est envoyée : le serveur reçoit seulement un score, le résultat '
                        'du contrôle du vivant et l’identifiant de l’appareil.',
                      ),
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.surface,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.border),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Texte version ${c.version}',
                              style: Theme.of(context).textTheme.labelMedium,
                            ),
                            const SizedBox(height: 8),
                            Text(c.texte),
                          ],
                        ),
                      ),
                      if (_erreur != null) ...[
                        const SizedBox(height: 12),
                        Text(_erreur!, style: const TextStyle(color: AppTheme.error)),
                      ],
                    ],
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    FilledButton(
                      onPressed: _busy ? null : () => _accepter(c),
                      child: Text(reconsent ? 'J’accepte le nouveau texte' : 'J’accepte et je continue'),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton(
                      onPressed: _busy ? null : () => _refuser(c),
                      child: const Text('Je refuse'),
                    ),
                    TextButton(
                      onPressed: _busy ? null : () => context.pop(),
                      child: const Text('Plus tard'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

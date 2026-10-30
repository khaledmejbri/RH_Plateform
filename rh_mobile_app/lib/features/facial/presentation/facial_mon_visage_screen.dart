import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../data/facial_errors.dart';
import '../data/facial_models.dart';
import '../engine/face_engine.dart';
import '../providers/facial_controller.dart';
import 'face_liveness_view.dart';

/// Libellé du statut d'enrôlement.
String libelleStatutFacial(String statut) {
  switch (statut) {
    case FacialStatut.enrole:
      return 'Enrôlé sur cet appareil';
    case FacialStatut.refuse:
      return 'Vérification faciale refusée';
    case FacialStatut.revoque:
      return 'Consentement retiré';
    case FacialStatut.aReenroler:
      return 'Ré-enrôlement demandé (en attente de déblocage par les RH)';
    default:
      return 'Pas encore enrôlé';
  }
}

/// Profil > « Vérification faciale » : statut, retrait du consentement (à tout moment, même si le réglage
/// est inactif), demande de ré-enrôlement et nouvelle décision après un refus (D-P07.a / .b).
class FacialMonVisageScreen extends ConsumerStatefulWidget {
  const FacialMonVisageScreen({super.key});

  @override
  ConsumerState<FacialMonVisageScreen> createState() => _FacialMonVisageScreenState();
}

class _FacialMonVisageScreenState extends ConsumerState<FacialMonVisageScreen> {
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(facialControllerProvider.notifier).rafraichir());
  }

  void _info(String texte, {bool erreur = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(texte), backgroundColor: erreur ? AppTheme.error : null),
    );
  }

  Future<void> _executer(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (e) {
      _info(messageErreurFaciale(e), erreur: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Réactivation après un refus : le moteur est préparé ici (aucun passage préalable par le consentement).
  Future<void> _activer() async {
    setState(() => _busy = true);
    final etat = await ref.read(faceEngineProvider).preparer();
    if (!mounted) return;
    setState(() => _busy = false);
    if (etat == EtatMoteur.pret) {
      context.push('/facial/consentement', extra: FacialRoute.consentement);
    } else {
      _info(messageMoteur(etat), erreur: true);
    }
  }

  Future<void> _retirer() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Retirer mon consentement ?'),
        content: const Text(
          'Votre gabarit sera effacé de ce téléphone immédiatement. Vous pointerez avec le QR et le GPS. '
          'Pour être de nouveau vérifié, un déblocage par les RH sera nécessaire.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Retirer')),
        ],
      ),
    );
    if (ok != true) return;
    await _executer(() async {
      final envoye = await ref.read(facialControllerProvider.notifier).retirer();
      _info(envoye
          ? 'Consentement retiré. Votre gabarit a été effacé.'
          : 'Gabarit effacé. Le retrait sera transmis dès que le réseau revient.');
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(facialControllerProvider);
    final etat = s.etat;
    final statut = etat?.statut ?? FacialStatut.nonEnrole;
    final peutRetirer = etat != null && etat.estEnrole && !s.retraitEnAttente;
    final peutActiver = etat != null &&
        etat.verificationActive &&
        statut == FacialStatut.refuse &&
        !s.incompatibleLocal &&
        !etat.appareilIncompatible;
    final peutDemander = etat != null &&
        etat.verificationActive &&
        (s.decision.reenrolementProposable || statut == FacialStatut.revoque);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Vérification faciale')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            if (s.chargement)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (etat == null)
              _Bandeau(
                icone: Icons.cloud_off_rounded,
                texte: s.erreur ?? 'État de la vérification faciale indisponible.',
                couleur: AppTheme.warning,
                fond: AppTheme.warningSurface,
              )
            else ...[
              Text(libelleStatutFacial(statut),
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              if (!etat.verificationActive)
                const Text('La vérification faciale n’est pas activée pour le moment.')
              else if (!etat.controleExige)
                const Text('La vérification faciale sera exigée au pointage à partir de la date de mise en service.'),
              if (s.indisponibleVersion) ...[
                const SizedBox(height: 8),
                const Text('Vérification faciale indisponible dans cette version de l’application.'),
              ],
              if (etat.appareilIncompatible || s.incompatibleLocal) ...[
                const SizedBox(height: 8),
                const Text('Cet appareil n’est pas compatible : le pointage reste en QR + GPS.'),
              ],
              if (s.enrolementEnAttente) ...[
                const SizedBox(height: 12),
                const _Bandeau(
                  icone: Icons.cloud_upload_outlined,
                  texte: 'Votre enrôlement sera transmis dès que le réseau revient.',
                  couleur: AppTheme.primary,
                  fond: AppTheme.primarySurface,
                ),
              ],
              if (s.retraitEnAttente) ...[
                const SizedBox(height: 12),
                const _Bandeau(
                  icone: Icons.cloud_upload_outlined,
                  texte: 'Votre retrait de consentement sera transmis dès que le réseau revient.',
                  couleur: AppTheme.primary,
                  fond: AppTheme.primarySurface,
                ),
              ],
            ],
            const SizedBox(height: 24),
            if (peutActiver)
              FilledButton.icon(
                onPressed: _busy ? null : _activer,
                icon: const Icon(Icons.face_rounded),
                label: const Text('Activer la vérification faciale'),
              ),
            if (peutDemander) ...[
              FilledButton.icon(
                onPressed: _busy
                    ? null
                    : () => _executer(() async {
                          await ref.read(facialControllerProvider.notifier).demanderReenrolement();
                          _info('Demande envoyée aux RH. Vous serez notifié au déblocage.');
                        }),
                icon: const Icon(Icons.phonelink_setup_rounded),
                label: const Text('Demander le ré-enrôlement'),
              ),
              const SizedBox(height: 8),
              const Text(
                'À utiliser après un changement de téléphone, une réinstallation ou après un retrait. '
                'En attendant, le pointage manuel reste possible.',
                style: TextStyle(fontSize: 12),
              ),
            ],
            if (peutRetirer) ...[
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _busy ? null : _retirer,
                icon: const Icon(Icons.delete_outline_rounded),
                label: const Text('Retirer mon consentement'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _Bandeau extends StatelessWidget {
  const _Bandeau({required this.icone, required this.texte, required this.couleur, required this.fond});

  final IconData icone;
  final String texte;
  final Color couleur;
  final Color fond;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(12)),
      child: Row(
        children: [
          Icon(icone, color: couleur),
          const SizedBox(width: 12),
          Expanded(child: Text(texte)),
        ],
      ),
    );
  }
}

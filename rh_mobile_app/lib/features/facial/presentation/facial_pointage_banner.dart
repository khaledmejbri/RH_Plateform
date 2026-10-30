import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../data/facial_models.dart';
import '../providers/facial_controller.dart';

/// Bandeau d'état facial sur l'écran de pointage. Invisible tant qu'il n'y a rien à signaler
/// (web, réglage inactif, jeton de démo, `GET /moi` en échec).
class FacialPointageBanner extends ConsumerWidget {
  const FacialPointageBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(facialControllerProvider);
    final etat = s.etat;
    if (etat == null || !etat.verificationActive) return const SizedBox.shrink();

    if (s.decision.route != FacialRoute.aucune) {
      return _carte(
        context,
        icone: Icons.face_rounded,
        texte: s.decision.route == FacialRoute.reconsentement
            ? 'Un nouveau texte de consentement est à valider.'
            : 'Terminez votre enrôlement facial pour pointer avec votre visage.',
        bouton: 'Continuer',
        onTap: () => context.push('/facial/consentement'),
      );
    }
    if (s.decision.reenrolementProposable) {
      return _carte(
        context,
        icone: Icons.phonelink_setup_rounded,
        texte: 'Ce téléphone n’est pas celui de votre enrôlement. Demandez le ré-enrôlement '
            'ou utilisez le pointage manuel.',
        bouton: 'Voir',
        onTap: () => context.push('/facial/mon-visage'),
      );
    }
    if (s.enrolementEnAttente) {
      return _carte(
        context,
        icone: Icons.cloud_upload_outlined,
        texte: 'Enrôlement en attente de transmission (réseau indisponible).',
      );
    }
    return const SizedBox.shrink();
  }

  Widget _carte(
    BuildContext context, {
    required IconData icone,
    required String texte,
    String? bouton,
    VoidCallback? onTap,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.warningSurface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(icone, color: AppTheme.warning),
          const SizedBox(width: 12),
          Expanded(child: Text(texte, style: Theme.of(context).textTheme.bodySmall)),
          if (bouton != null) TextButton(onPressed: onTap, child: Text(bouton)),
        ],
      ),
    );
  }
}

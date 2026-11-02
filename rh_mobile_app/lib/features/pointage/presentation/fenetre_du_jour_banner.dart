import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../data/fenetre_du_jour.dart';

/// Bandeau de l'horaire du jour du site scanné (D-P16.c).
/// Rien pour un site sans horaire ; « Site fermé aujourd'hui » pour un jour fermé.
class FenetreDuJourBanner extends StatelessWidget {
  const FenetreDuJourBanner({super.key, required this.fenetre});

  final FenetreDuJour fenetre;

  @override
  Widget build(BuildContext context) {
    if (!fenetre.afficherBandeau) return const SizedBox.shrink();

    final ferme = fenetre.ferme;
    final principal = ferme ? 'Site fermé aujourd’hui' : fenetre.resume;
    if (principal == null) return const SizedBox.shrink();
    final heuresSup = ferme ? null : fenetre.resumeHeuresSup;
    final couleur = ferme ? AppTheme.warning : AppTheme.primaryDark;

    return Container(
      key: const Key('fenetre_du_jour_banner'),
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: ferme ? AppTheme.warningSurface : AppTheme.primarySurface,
        borderRadius: BorderRadius.circular(AppTheme.radius),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(ferme ? Icons.event_busy_rounded : Icons.schedule_rounded, color: couleur, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  principal,
                  style: TextStyle(color: couleur, fontWeight: FontWeight.w600, fontSize: 13),
                ),
                if (heuresSup != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    heuresSup,
                    style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

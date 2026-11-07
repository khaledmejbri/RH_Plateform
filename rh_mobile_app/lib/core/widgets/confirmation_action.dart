import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Boîte de dialogue unique « Êtes-vous sûr ? » avant toute action à effet (annuler, valider,
/// refuser, clôturer, supprimer). Nomme l'élément concerné (« Congé annuel — DEM-202611-0003 »).
///
/// Renvoie `true` seulement si l'utilisateur confirme.
Future<bool> confirmerAction(
  BuildContext context, {
  required String action,
  required String element,
  String? detail,
  String libelleConfirmer = 'Confirmer',
  String libelleAnnuler = 'Non, revenir',
  bool destructive = false,
}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: const Text('Êtes-vous sûr ?'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(action),
          const SizedBox(height: 8),
          Text(element, style: const TextStyle(fontWeight: FontWeight.w700)),
          if (detail != null && detail.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(detail, style: const TextStyle(color: AppTheme.textSecondary)),
          ],
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(libelleAnnuler)),
        FilledButton(
          style: destructive ? FilledButton.styleFrom(backgroundColor: AppTheme.error) : null,
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(libelleConfirmer),
        ),
      ],
    ),
  );
  return ok == true;
}

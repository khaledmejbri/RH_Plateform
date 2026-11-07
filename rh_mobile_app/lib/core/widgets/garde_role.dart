import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/providers/collaborateur_notifier.dart';
import 'rh_ui.dart';

/// Garde de route par rôle (T-04) : affiche [enfant] si [autorise] est vrai pour le compte connecté,
/// sinon l'écran « Accès refusé » (sans « Réessayer ») avec retour à l'accueil.
/// Le serveur reste l'autorité (403) : la garde évite seulement d'ouvrir un écran inutile.
class GardeRole extends ConsumerWidget {
  const GardeRole({super.key, required this.autorise, required this.enfant});

  final bool Function(CollaborateurInfo info) autorise;
  final Widget enfant;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(collaborateurNotifierProvider);
    final info = async.valueOrNull;
    if (info == null) {
      if (async.hasError) {
        return Scaffold(
          appBar: AppBar(),
          body: RhErrorState.depuis(
            async.error,
            onRetry: () => ref.read(collaborateurNotifierProvider.notifier).fetchMoi(),
          ),
        );
      }
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (autorise(info)) return enfant;
    return Scaffold(
      appBar: AppBar(title: const Text('Accès refusé')),
      body: RhEmptyState(
        icon: Icons.lock_outline_rounded,
        title: 'Accès refusé',
        message: 'Vous n’avez pas accès à cet écran.',
        action: FilledButton(
          onPressed: () => context.go('/home'),
          child: const Text('Retour à l’accueil'),
        ),
      ),
    );
  }
}

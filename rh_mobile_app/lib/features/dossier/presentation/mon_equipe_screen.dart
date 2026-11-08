import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../providers/dossier_providers.dart';
import 'dossier_widgets.dart';

/// « Mon équipe » (A9) : les collaborateurs dont je suis le valideur ; chacun ouvre sa fiche d'équipe.
class MonEquipeScreen extends ConsumerWidget {
  const MonEquipeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(monEquipeProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Mon équipe')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(monEquipeProvider)),
        data: (membres) => membres.isEmpty
            ? const RhEmptyState(
                icon: Icons.groups_outlined,
                title: 'Aucun collaborateur',
                message: 'Vous n’êtes le valideur d’aucun collaborateur.',
              )
            : RefreshIndicator(
                onRefresh: () async => ref.invalidate(monEquipeProvider),
                child: ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  children: [
                    SectionDossier(
                      titre: '${membres.length} collaborateur${membres.length > 1 ? 's' : ''}',
                      children: [
                        for (final p in membres)
                          LignePersonne(
                            nom: p.nomComplet,
                            sousTitre: p.sousTitre,
                            onTap: p.id.isEmpty ? null : () => context.push('/equipe/${p.id}'),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}

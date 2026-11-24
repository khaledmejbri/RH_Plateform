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
                    _PointagesEquipeCard(
                      onTap: () => context.push('/equipe/pointages'),
                    ),
                    const SizedBox(height: 12),
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

class _PointagesEquipeCard extends StatelessWidget {
  const _PointagesEquipeCard({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Semantics(
      button: true,
      label: "Pointages de l'équipe",
      child: Material(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          key: const Key('mon-equipe-pointages-btn'),
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppTheme.primary, width: 1),
            ),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: AppTheme.primarySurface,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.access_time_filled_rounded,
                    color: AppTheme.primaryDark,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        "Pointages de l'équipe",
                        style: text.titleSmall?.copyWith(color: AppTheme.primaryDark),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Suivi des entrées, sorties et recherches par période',
                        style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded, color: AppTheme.primaryDark),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

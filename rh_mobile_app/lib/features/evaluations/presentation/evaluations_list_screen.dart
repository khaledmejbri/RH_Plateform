import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_theme.dart';
import '../data/evaluation_models.dart';
import '../data/evaluation_repository.dart';

final evaluationsListProvider = FutureProvider.autoDispose<List<EvaluationItem>>((ref) async {
  return ref.watch(evaluationRepositoryProvider).mesEvaluations();
});

class EvaluationsListScreen extends ConsumerWidget {
  const EvaluationsListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(evaluationsListProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes Évaluations'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.invalidate(evaluationsListProvider),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.wifi_off_rounded,
                  size: 48, color: AppTheme.textSecondary),
              const SizedBox(height: 12),
              Text('Erreur: $e',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppTheme.textSecondary)),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.invalidate(evaluationsListProvider),
                child: const Text('Réessayer'),
              ),
            ],
          ),
        ),
        data: (evaluations) {
          if (evaluations.isEmpty) {
            return const _EmptyView();
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(evaluationsListProvider),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: evaluations.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (ctx, i) => _EvaluationCard(item: evaluations[i]),
            ),
          );
        },
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.assignment_outlined,
              size: 64, color: AppTheme.textSecondary.withValues(alpha: 0.5)),
          const SizedBox(height: 16),
          const Text(
            'Aucune évaluation disponible',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          const Text(
            'Les évaluations sont disponibles en juin et décembre',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppTheme.textSecondary),
          ),
        ],
      ),
    );
  }
}

class _EvaluationCard extends StatelessWidget {
  const _EvaluationCard({required this.item});

  final EvaluationItem item;

  @override
  Widget build(BuildContext context) {
    final (label, bgColor, fgColor) = _statusMeta(item.statut);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      color: Colors.white,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () {
          context.push('/evaluations/${item.id}');
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: bgColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(label,
                        style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: fgColor)),
                  ),
                  const Spacer(),
                  Text(
                    DateFormat('dd MMM yyyy', 'fr_FR').format(item.creeLe),
                    style: const TextStyle(
                        fontSize: 12, color: AppTheme.textSecondary),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                item.campaignNom,
                style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.textPrimary),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(Icons.person_outline_rounded,
                      size: 16, color: AppTheme.textSecondary),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      'Évaluateur: ${item.superieurNom}',
                      style: const TextStyle(
                          fontSize: 13, color: AppTheme.textSecondary),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (item.scoreSur20 != null)
                Row(
                  children: [
                    const Icon(Icons.star_rounded,
                        size: 16, color: AppTheme.primary),
                    const SizedBox(width: 4),
                    Text(
                      'Score: ${item.scoreSur20}/20',
                      style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.primary),
                    ),
                  ],
                ),
              const SizedBox(height: 8),
              if (item.etapeActuelle != null)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.primarySurface,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    _etapeLabel(item.etapeActuelle!),
                    style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.primary),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  (String, Color, Color) _statusMeta(String statut) {
    return switch (statut) {
      'VALIDEE' => ('Validée', const Color(0xFFDCFCE7), const Color(0xFF166534)),
      'VALIDEE_COLLABORATEUR' =>
        ('Validée (Vous)', const Color(0xFFDBEAFE), const Color(0xFF1E40AF)),
      'VALIDEE_SUPERIEUR' =>
        ('Validée (Manager)', const Color(0xFFFEF3C7), const Color(0xFF92400E)),
      _ => ('En attente', const Color(0xFFF1F5F9), const Color(0xFF475569)),
    };
  }

  String _etapeLabel(String etape) {
    return switch (etape) {
      'EVALUATION_GENERALE' => 'Étape 1: Générale',
      'EVALUATION_TECHNIQUE' => 'Étape 2: Technique',
      _ => etape,
    };
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_theme.dart';
import '../data/evaluation_models.dart';
import '../data/evaluation_repository.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

final evaluationsListProvider =
    FutureProvider.autoDispose<List<EvaluationItem>>((ref) async {
  return ref.watch(evaluationRepositoryProvider).mesEvaluations(ensure: true);
});

final managerEvaluationsProvider =
    FutureProvider.autoDispose<List<EvaluationItem>>((ref) async {
  return ref.watch(evaluationRepositoryProvider).managerPending();
});

class EvaluationsListScreen extends ConsumerStatefulWidget {
  const EvaluationsListScreen({super.key});

  @override
  ConsumerState<EvaluationsListScreen> createState() =>
      _EvaluationsListScreenState();
}

class _EvaluationsListScreenState extends ConsumerState<EvaluationsListScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  bool _hasManagerTab = false;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final mineAsync = ref.watch(evaluationsListProvider);
    final managerAsync = ref.watch(managerEvaluationsProvider);

    final managerItems = managerAsync.maybeWhen(
      data: (list) => list,
      orElse: () => const <EvaluationItem>[],
    );

    // Afficher l’onglet manager dès qu’il y a au moins une éval à noter.
    final enableManagerTab = managerItems.isNotEmpty;
    if (enableManagerTab != _hasManagerTab) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() => _hasManagerTab = enableManagerTab);
      });
    }

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes évaluations'),
        actions: [
          IconButton(
            tooltip: 'Actualiser',
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () {
              ref.invalidate(evaluationsListProvider);
              ref.invalidate(managerEvaluationsProvider);
              ref.invalidate(evaluationsBadgeCountProvider);
            },
          ),
        ],
        bottom: enableManagerTab
            ? TabBar(
                controller: _tabs,
                labelColor: AppTheme.primary,
                unselectedLabelColor: AppTheme.textSecondary,
                indicatorColor: AppTheme.primary,
                tabs: [
                  const Tab(text: 'Mes évaluations'),
                  Tab(
                    text: managerItems.isEmpty
                        ? 'À noter'
                        : 'À noter (${managerItems.length})',
                  ),
                ],
              )
            : null,
      ),
      body: enableManagerTab
          ? TabBarView(
              controller: _tabs,
              children: [
                _CollabListBody(async: mineAsync),
                _ManagerListBody(async: managerAsync),
              ],
            )
          : _CollabListBody(async: mineAsync),
    );
  }
}

class _CollabListBody extends ConsumerWidget {
  const _CollabListBody({required this.async});

  final AsyncValue<List<EvaluationItem>> async;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorView(
        message: rhErrorMessage(e),
        onRetry: () => ref.invalidate(evaluationsListProvider),
      ),
      data: (evaluations) {
        if (evaluations.isEmpty) {
          return const _HorsCampagneView();
        }
        final aFaire =
            evaluations.where((e) => e.actionCollaborateurRequise).toList();
        final terminees =
            evaluations.where((e) => !e.actionCollaborateurRequise).toList();

        return DefaultTabController(
          length: 2,
          child: Column(
            children: [
              const Material(
                color: AppTheme.surface,
                child: TabBar(
                  labelColor: AppTheme.primary,
                  unselectedLabelColor: AppTheme.textSecondary,
                  indicatorColor: AppTheme.primary,
                  tabs: [
                    Tab(text: 'À faire'),
                    Tab(text: 'Terminées'),
                  ],
                ),
              ),
              Expanded(
                child: TabBarView(
                  children: [
                    _EvalList(
                      items: aFaire,
                      emptyLabel:
                          'Aucune auto-évaluation en attente pour le moment.',
                      onRefresh: () async {
                        ref.invalidate(evaluationsListProvider);
                        ref.invalidate(evaluationsBadgeCountProvider);
                      },
                      asManager: false,
                    ),
                    _EvalList(
                      items: terminees,
                      emptyLabel: 'Aucune évaluation terminée pour l’instant.',
                      onRefresh: () async {
                        ref.invalidate(evaluationsListProvider);
                      },
                      asManager: false,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _ManagerListBody extends ConsumerWidget {
  const _ManagerListBody({required this.async});

  final AsyncValue<List<EvaluationItem>> async;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorView(
        message: rhErrorMessage(e),
        onRetry: () => ref.invalidate(managerEvaluationsProvider),
      ),
      data: (items) {
        final aNoter = items.where((e) => e.actionManagerRequise).toList();
        final suivi = items.where((e) => !e.actionManagerRequise).toList();
        return DefaultTabController(
          length: 2,
          child: Column(
            children: [
              const Material(
                color: AppTheme.surface,
                child: TabBar(
                  labelColor: AppTheme.primary,
                  unselectedLabelColor: AppTheme.textSecondary,
                  indicatorColor: AppTheme.primary,
                  tabs: [
                    Tab(text: 'À noter'),
                    Tab(text: 'Suivi équipe'),
                  ],
                ),
              ),
              Expanded(
                child: TabBarView(
                  children: [
                    _EvalList(
                      items: aNoter,
                      emptyLabel:
                          'Aucune évaluation d’équipe à noter pour le moment.',
                      onRefresh: () async {
                        ref.invalidate(managerEvaluationsProvider);
                        ref.invalidate(evaluationsBadgeCountProvider);
                      },
                      asManager: true,
                    ),
                    _EvalList(
                      items: suivi,
                      emptyLabel: 'Aucun suivi d’équipe pour l’instant.',
                      onRefresh: () async {
                        ref.invalidate(managerEvaluationsProvider);
                      },
                      asManager: true,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _EvalList extends StatelessWidget {
  const _EvalList({
    required this.items,
    required this.emptyLabel,
    required this.onRefresh,
    required this.asManager,
  });

  final List<EvaluationItem> items;
  final String emptyLabel;
  final Future<void> Function() onRefresh;
  final bool asManager;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            Icon(
              Icons.assignment_turned_in_outlined,
              size: 48,
              color: AppTheme.textSecondary.withValues(alpha: 0.5),
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Text(
                emptyLabel,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppTheme.textSecondary),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (ctx, i) => _EvaluationCard(
          item: items[i],
          asManager: asManager,
        ),
      ),
    );
  }
}

class _HorsCampagneView extends StatelessWidget {
  const _HorsCampagneView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.assignment_outlined,
              size: 64,
              color: AppTheme.textSecondary.withValues(alpha: 0.45),
            ),
            const SizedBox(height: 16),
            const Text(
              'Aucune évaluation ne vous est attribuée pour le moment',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            const Text(
              'Vous serez notifié dès qu’une auto-évaluation vous sera confiée '
              'lors d’une campagne (semestrielle ou annuelle).',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppTheme.textSecondary, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final is403 = message.toLowerCase().contains('accès refusé') ||
        message.contains('403');
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              is403 ? Icons.lock_outline_rounded : Icons.wifi_off_rounded,
              size: 48,
              color: AppTheme.textSecondary,
            ),
            const SizedBox(height: 12),
            Text(
              is403
                  ? 'Accès refusé. Cette évaluation ne vous est pas destinée.'
                  : message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppTheme.textSecondary),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onRetry,
              child: const Text('Réessayer'),
            ),
          ],
        ),
      ),
    );
  }
}

class _EvaluationCard extends StatelessWidget {
  const _EvaluationCard({required this.item, required this.asManager});

  final EvaluationItem item;
  final bool asManager;

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
          final q = asManager ? '?mode=manager' : '';
          context.push('/evaluations/${item.id}$q');
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: bgColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      label,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: fgColor,
                      ),
                    ),
                  ),
                  const Spacer(),
                  Text(
                    DateFormat('dd MMM yyyy', 'fr_FR').format(item.creeLe),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.textSecondary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                item.campaignNom,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.textPrimary,
                ),
              ),
              if (item.profilSnapshotLabel != null) ...[
                const SizedBox(height: 6),
                Text(
                  item.profilSnapshotLabel!,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppTheme.textSecondary,
                  ),
                ),
              ],
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(
                    Icons.person_outline_rounded,
                    size: 16,
                    color: AppTheme.textSecondary,
                  ),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      asManager
                          ? (item.collaborateurNom?.isNotEmpty == true
                              ? 'Collaborateur : ${item.collaborateurNom}'
                              : 'Notation manager')
                          : 'Manager : ${item.superieurNom}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                  ),
                ],
              ),
              if (item.etapeActuelle != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.primarySurface,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    EvaluationLabels.etapeCourt(item.etapeActuelle),
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.primary,
                    ),
                  ),
                ),
              ],
              if (item.scoreSur20 != null) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(
                      Icons.star_rounded,
                      size: 16,
                      color: AppTheme.primary,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '${item.scoreSur20}/20'
                      '${EvaluationLabels.appreciation(item.scoreSur20).isNotEmpty ? ' · ${EvaluationLabels.appreciation(item.scoreSur20)}' : ''}',
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.primary,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  (String, Color, Color) _statusMeta(String statut) {
    return switch (statut) {
      'VALIDEE' => (
          EvaluationLabels.statut(statut),
          AppTheme.successSurface,
          AppTheme.success,
        ),
      'VALIDEE_COLLABORATEUR' => (
          EvaluationLabels.statut(statut),
          AppTheme.primarySurface,
          AppTheme.primaryDark,
        ),
      'VALIDEE_SUPERIEUR' => (
          EvaluationLabels.statut(statut),
          AppTheme.warningSurface,
          AppTheme.warning,
        ),
      _ => (
          EvaluationLabels.statut(statut),
          AppTheme.surfaceMuted,
          AppTheme.textSecondary,
        ),
    };
  }
}

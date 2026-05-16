import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/formation_models.dart';
import '../data/formation_repository.dart';
import 'formation_detail_screen.dart';

enum FormationTab { mesFormations, creeesParMoi, ouJeSuisInvite }

final formationsListProvider = FutureProvider.autoDispose<List<FormationItem>>((ref) async {
  return ref.watch(formationRepositoryProvider).mesDemandes();
});

final formationsRoListProvider = FutureProvider.autoDispose<List<FormationItem>>((ref) async {
  return ref.watch(formationRepositoryProvider).mesDemandesEnTantQueRo();
});

final formationsCibleListProvider = FutureProvider.autoDispose<List<FormationItem>>((ref) async {
  return ref.watch(formationRepositoryProvider).formationsOuJeSuisCible();
});

class FormationsListScreen extends ConsumerStatefulWidget {
  const FormationsListScreen({super.key});

  @override
  ConsumerState<FormationsListScreen> createState() => _FormationsListScreenState();
}

class _FormationsListScreenState extends ConsumerState<FormationsListScreen>
    with SingleTickerProviderStateMixin {
  FormationTab _selectedTab = FormationTab.mesFormations;
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging) {
        setState(() {
          _selectedTab = FormationTab.values[_tabController.index];
        });
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(collaborateurNotifierProvider).valueOrNull;
    final isRo = user?.isRo == true;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Formations'),
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textSecondary,
          indicatorColor: AppTheme.primary,
          tabs: [
            const Tab(text: 'Mes formations'),
            if (isRo) const Tab(text: 'Créées par moi'),
            const Tab(text: 'Invitations'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () {
              switch (_selectedTab) {
                case FormationTab.mesFormations:
                  ref.invalidate(formationsListProvider);
                case FormationTab.creeesParMoi:
                  ref.invalidate(formationsRoListProvider);
                case FormationTab.ouJeSuisInvite:
                  ref.invalidate(formationsCibleListProvider);
              }
            },
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.pushNamed(context, '/formations/nouveau'),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Demander'),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildTabContent(formationsListProvider, 'Aucune demande de formation'),
          if (isRo)
            _buildTabContent(formationsRoListProvider, 'Aucune formation créée'),
          _buildTabContent(formationsCibleListProvider, 'Aucune invitation'),
        ],
      ),
    );
  }

  Widget _buildTabContent(
    AutoDisposeFutureProvider<List<FormationItem>> provider,
    String emptyMessage,
  ) {
    return Consumer(
      builder: (context, ref, _) {
        final async = ref.watch(provider);

        return async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => _ErrorState(
            message: e.toString(),
            onRetry: () => ref.invalidate(provider),
          ),
          data: (items) => items.isEmpty
              ? _EmptyState(message: emptyMessage)
              : RefreshIndicator(
                  onRefresh: () async => ref.invalidate(provider),
                  child: ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
                    itemBuilder: (_, i) => _FormationCard(item: items[i]),
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemCount: items.length,
                  ),
                ),
        );
      },
    );
  }
}

class _FormationCard extends StatelessWidget {
  const _FormationCard({required this.item});

  final FormationItem item;

  @override
  Widget build(BuildContext context) {
    final status = _statusMeta(item.statut);

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => FormationDetailScreen(formationId: item.id),
            ),
          );
        },
        borderRadius: BorderRadius.circular(18),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppTheme.border, width: 0.5),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFF0F172A).withValues(alpha: 0.04),
                blurRadius: 18,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(
                      Icons.school_rounded,
                      color: Color(0xFF2563EB),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.typeFormation,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: AppTheme.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${item.organisme} - ${item.dureeHeures} h',
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppTheme.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: status.$2,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      status.$1,
                      style: TextStyle(
                        color: status.$3,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ),
              if (item.justification != null && item.justification!.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  item.justification!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppTheme.textPrimary,
                  ),
                ),
              ],
              if (item.commentaireRh != null && item.commentaireRh!.isNotEmpty) ...[
                const SizedBox(height: 10),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    item.commentaireRh!,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.textSecondary,
                    ),
                  ),
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
      'INTEGREE_PLAN' => ('Planifiée', const Color(0xFFDCFCE7), const Color(0xFF166534)),
      'REFUSEE' => ('Refusée', const Color(0xFFFEE2E2), const Color(0xFF991B1B)),
      'ANNULEE' => ('Annulée', const Color(0xFFF1F5F9), const Color(0xFF475569)),
      _ => ('En attente RH', const Color(0xFFDBEAFE), const Color(0xFF1E40AF)),
    };
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppTheme.primarySurface,
                borderRadius: BorderRadius.circular(22),
              ),
              child: const Icon(
                Icons.school_rounded,
                color: AppTheme.primary,
                size: 34,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: AppTheme.textPrimary,
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.error_outline_rounded,
              color: AppTheme.error,
              size: 42,
            ),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Réessayer'),
            ),
          ],
        ),
      ),
    );
  }
}

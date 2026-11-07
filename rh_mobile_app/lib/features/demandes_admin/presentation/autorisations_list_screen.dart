import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../data/demande_admin_repository.dart';
import '../data/demande_admin_models.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

final autorisationsListProvider =
    FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).mesAutorisations();
});

class AutorisationsListScreen extends ConsumerStatefulWidget {
  const AutorisationsListScreen({super.key});

  @override
  ConsumerState<AutorisationsListScreen> createState() =>
      _AutorisationsListScreenState();
}

class _AutorisationsListScreenState
    extends ConsumerState<AutorisationsListScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController =
      TabController(length: 4, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(autorisationsListProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes autorisations'),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppTheme.primary,
          indicatorWeight: 2,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textSecondary,
          labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: const [
            Tab(text: 'En cours'),
            Tab(text: 'Approuvées'),
            Tab(text: 'Refusées'),
            Tab(text: 'Annulées'),
          ],
        ),
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(autorisationsListProvider)),
        data: (items) {
          final enCours = items
              .where((i) =>
                  i.statut == 'EN_VALIDATION_SUPERIEUR' ||
                  i.statut == 'EN_VALIDATION_RRH' ||
                  i.statut == 'SOUMISE')
              .toList();
          final approuvees = items
              .where((i) => i.statut == 'APPROUVEE')
              .toList();
          final refusees = items
              .where((i) => i.statut == 'REFUSEE')
              .toList();
          final annulees = items
              .where((i) => i.statut == 'ANNULEE')
              .toList();

          return TabBarView(
            controller: _tabController,
            children: [
              _AutorisationList(items: enCours, onRefresh: () async {
                ref.invalidate(autorisationsListProvider);
              }),
              _AutorisationList(items: approuvees, onRefresh: () async {
                ref.invalidate(autorisationsListProvider);
              }),
              _AutorisationList(items: refusees, onRefresh: () async {
                ref.invalidate(autorisationsListProvider);
              }),
              _AutorisationList(items: annulees, onRefresh: () async {
                ref.invalidate(autorisationsListProvider);
              }),
            ],
          );
        },
      ),
    );
  }
}

class _AutorisationList extends StatelessWidget {
  final List<DemandeAdminItem> items;
  final Future<void> Function() onRefresh;

  const _AutorisationList({required this.items, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const RhEmptyState(
        icon: Icons.access_time_rounded,
        title: 'Aucune autorisation',
        message: 'Vos autorisations de sortie apparaîtront ici.',
      );
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final item = items[index];
          return _AutorisationCard(item: item);
        },
      ),
    );
  }
}

class _AutorisationCard extends StatelessWidget {
  final DemandeAdminItem item;

  const _AutorisationCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final couleurStatut = _getCouleurStatut(item.statut);
    final libelleStatut = item.statutLibelle;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: AppTheme.border, width: 0.5),
      ),
      child: InkWell(
        onTap: () => context.push('/demandes-admin/${item.id}'),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      item.referenceAffichee == null
                          ? 'Autorisation de sortie'
                          : 'Autorisation de sortie · ${item.referenceAffichee}',
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                      color: couleurStatut.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      libelleStatut,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: couleurStatut,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  const Icon(Icons.calendar_today_rounded,
                      size: 16, color: AppTheme.textSecondary),
                  const SizedBox(width: 6),
                  Text(
                    formatDateIso(item.contenu?['date_jour'] ?? item.periodeDebut),
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppTheme.textSecondary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(Icons.access_time,
                      size: 16, color: AppTheme.textSecondary),
                  const SizedBox(width: 6),
                  Text(
                    '${item.contenu?['heure_debut'] ?? '-'} - ${item.contenu?['heure_fin'] ?? '-'}',
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppTheme.textSecondary,
                    ),
                  ),
                ],
              ),
              if (item.contenu?['motif'] != null &&
                  item.contenu!['motif'].toString().isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  item.contenu!['motif'],
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppTheme.textPrimary,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Color _getCouleurStatut(String statut) {
    switch (statut) {
      case 'APPROUVEE':
        return AppTheme.success;
      case 'REFUSEE':
      case 'ANNULEE':
        return AppTheme.error;
      case 'EN_VALIDATION_SUPERIEUR':
      case 'EN_VALIDATION_RRH':
      case 'SOUMISE':
        return AppTheme.warning;
      default:
        return AppTheme.textSecondary;
    }
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../data/demande_admin_repository.dart';
import '../data/demande_admin_models.dart';

const int _pageSize = 10;

final demandesAdminListProvider =
    FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).mesDemandes();
});

class DemandesAdminListScreen extends ConsumerStatefulWidget {
  const DemandesAdminListScreen({super.key});

  @override
  ConsumerState<DemandesAdminListScreen> createState() =>
      _DemandesAdminListScreenState();
}

class _DemandesAdminListScreenState
    extends ConsumerState<DemandesAdminListScreen>
    with SingleTickerProviderStateMixin {
  int _currentPage = 0;
  late final TabController _tabController =
      TabController(length: 3, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(demandesAdminListProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes Demandes'),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppTheme.primary,
          indicatorWeight: 2,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textSecondary,
          labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
          tabs: const [
            Tab(text: 'En cours'),
            Tab(text: 'Approuvées'),
            Tab(text: 'Refusées'),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showNouvelleDemandeDialog(context),
        child: const Icon(Icons.add_rounded),
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
                onPressed: () {
                  setState(() => _currentPage = 0);
                  ref.invalidate(demandesAdminListProvider);
                },
                child: const Text('Réessayer'),
              ),
            ],
          ),
        ),
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
              .where((i) => i.statut == 'REFUSEE' || i.statut == 'ANNULEE')
              .toList();

          return TabBarView(
            controller: _tabController,
            children: [
              _DemandeList(items: enCours, onRefresh: () async {
                setState(() => _currentPage = 0);
                ref.invalidate(demandesAdminListProvider);
              }),
              _DemandeList(items: approuvees, onRefresh: () async {
                ref.invalidate(demandesAdminListProvider);
              }),
              _DemandeList(items: refusees, onRefresh: () async {
                ref.invalidate(demandesAdminListProvider);
              }),
            ],
          );
        },
      ),
    );
  }

  void _showNouvelleDemandeDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Nouvelle demande',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFF7ED),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.calendar_month_rounded, color: Color(0xFFEA580C)),
                ),
                title: const Text('Congé', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Absence sur plusieurs jours'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/demandes-admin/conge/nouveau');
                },
              ),
              const Divider(height: 8),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.access_time_rounded, color: Color(0xFF3B82F6)),
                ),
                title: const Text('Autorisation de sortie', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Sortie courte (max 4h)'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/demandes-admin/autorisation/nouveau');
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}

class _DemandeList extends StatelessWidget {
  final List<DemandeAdminItem> items;
  final Future<void> Function() onRefresh;

  const _DemandeList({required this.items, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppTheme.primarySurface,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Icon(Icons.assignment_outlined,
                  size: 36, color: AppTheme.primary),
            ),
            const SizedBox(height: 16),
            const Text('Aucune demande',
                style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: AppTheme.textPrimary,
                    fontSize: 16)),
            const SizedBox(height: 4),
            const Text('Vos demandes apparaîtront ici.',
                style: TextStyle(color: AppTheme.textSecondary, fontSize: 13)),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      color: AppTheme.primary,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (_, i) => _DemandeCard(d: items[i]),
      ),
    );
  }
}

class _DemandeCard extends StatelessWidget {
  final DemandeAdminItem d;
  const _DemandeCard({required this.d});

  @override
  Widget build(BuildContext context) {
    final (statusLabel, statusBg, statusFg, iconBg, iconColor, icon) =
        _statusMeta(d.statut);

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: () => context.push('/demandes-admin/${d.id}'),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border, width: 0.5),
          ),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: iconBg,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(icon, color: iconColor, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          d.isAutorisationSortie
                              ? 'Autorisation de sortie'
                              : d.typeDemande == 'CONGE'
                                  ? 'Congé'
                                  : d.typeDemande,
                          style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                              color: AppTheme.textPrimary),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          d.isAutorisationSortie
                            ? '${d.periodeDebut ?? "N/A"} · ${d.heureDebut ?? "--:--"} → ${d.heureFin ?? "--:--"}'
                            : '${d.periodeDebut ?? "N/A"} → ${d.periodeFin ?? "N/A"}',
                          style: const TextStyle(
                              fontSize: 12, color: AppTheme.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusBg,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(statusLabel,
                        style: TextStyle(
                            color: statusFg,
                            fontSize: 11,
                            fontWeight: FontWeight.w600)),
                  ),
                ],
              ),
              if (d.motifRefus != null && d.motifRefus!.isNotEmpty) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFF1F2),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline_rounded,
                          size: 14, color: AppTheme.error),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          d.motifRefus!,
                          style: const TextStyle(
                              color: AppTheme.error,
                              fontSize: 12,
                              fontStyle: FontStyle.italic),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  (String, Color, Color, Color, Color, IconData) _statusMeta(String statut) {
    if (statut == 'EN_VALIDATION_SUPERIEUR') {
      return (
        'En attente RO',
        const Color(0xFFFEF9C3),
        const Color(0xFFA16207),
        const Color(0xFFFFF7ED),
        const Color(0xFFEA580C),
        Icons.supervisor_account_rounded,
      );
    } else if (statut == 'EN_VALIDATION_RRH' || statut == 'SOUMISE') {
      return (
        'En attente RRH',
        const Color(0xFFDBEAFE),
        const Color(0xFF1E40AF),
        const Color(0xFFEFF6FF),
        const Color(0xFF2563EB),
        Icons.hourglass_top_rounded,
      );
    } else if (statut == 'APPROUVEE') {
      return (
        'Approuvée',
        const Color(0xFFDCFCE7),
        const Color(0xFF166534),
        const Color(0xFFF0FDF4),
        const Color(0xFF16A34A),
        Icons.check_circle_outline_rounded,
      );
    } else if (statut == 'ANNULEE') {
      return (
        'Annulée',
        const Color(0xFFF1F5F9),
        const Color(0xFF475569),
        const Color(0xFFF8FAFC),
        const Color(0xFF94A3B8),
        Icons.cancel_outlined,
      );
    } else {
      return (
        'Refusée',
        const Color(0xFFFEE2E2),
        const Color(0xFF991B1B),
        const Color(0xFFFFF1F2),
        const Color(0xFFE11D48),
        Icons.cancel_outlined,
      );
    }
  }
}

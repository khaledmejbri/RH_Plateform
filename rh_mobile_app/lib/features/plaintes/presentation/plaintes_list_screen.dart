import '../../../core/libelles/libelles.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/widgets/pressable_scale.dart';
import '../data/plainte_models.dart';
import '../data/plainte_repository.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

const int _pageSize = 10;

final plaintesListProvider = FutureProvider.autoDispose<List<PlainteItem>>((ref) async {
  return ref.watch(plainteRepositoryProvider).mesPlaintes();
});

class PlaintesListScreen extends ConsumerStatefulWidget {
  const PlaintesListScreen({super.key});

  @override
  ConsumerState<PlaintesListScreen> createState() => _PlaintesListScreenState();
}

class _PlaintesListScreenState extends ConsumerState<PlaintesListScreen> {
  int _currentPage = 0;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(plaintesListProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Mes plaintes', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 20)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: 'Actualiser la liste',
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () {
              setState(() => _currentPage = 0);
              ref.invalidate(plaintesListProvider);
            },
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(plaintesListProvider)),
        data: (items) {
          if (items.isEmpty) {
            return RhEmptyState(
              icon: Icons.inbox_rounded,
              title: 'Aucune plainte pour le moment',
              message: 'Signalez un problème : vous suivrez son traitement ici.',
              action: FilledButton.icon(
                onPressed: () => context.push('/plaintes/nouveau'),
                icon: const Icon(Icons.add_rounded, size: 18),
                label: const Text('Nouvelle plainte'),
                style: FilledButton.styleFrom(minimumSize: const Size(200, AppTheme.minTouch)),
              ),
            );
          }

          final totalPages = (items.length / _pageSize).ceil();
          final startIndex = _currentPage * _pageSize;
          final endIndex = (startIndex + _pageSize).clamp(0, items.length);
          final currentPageItems = items.sublist(startIndex, endIndex);

          return RefreshIndicator(
            onRefresh: () async {
              setState(() => _currentPage = 0);
              ref.invalidate(plaintesListProvider);
            },
            child: Column(
              children: [
                Expanded(
                  child: ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: currentPageItems.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, i) {
                      final p = currentPageItems[i];
                      return PressableScale(
                        onPressed: () => context.push('/plaintes/${p.id}'),
                        child: _PlainteCard(plainte: p),
                      );
                    },
                  ),
                ),
                if (totalPages > 1)
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      boxShadow: [
                        BoxShadow(
                          color: AppTheme.textPrimary.withValues(alpha: 0.05),
                          blurRadius: 10,
                          offset: const Offset(0, -2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          tooltip: 'Page précédente',
                          icon: const Icon(Icons.chevron_left, size: 20),
                          onPressed: _currentPage > 0 ? () => setState(() => _currentPage--) : null,
                          color: _currentPage > 0 ? AppTheme.primaryDark : AppTheme.border,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Page ${_currentPage + 1} / $totalPages',
                          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          tooltip: 'Page suivante',
                          icon: const Icon(Icons.chevron_right, size: 20),
                          onPressed: _currentPage < totalPages - 1 ? () => setState(() => _currentPage++) : null,
                          color: _currentPage < totalPages - 1 ? AppTheme.primaryDark : AppTheme.border,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/plaintes/nouveau'),
        backgroundColor: AppTheme.primaryDark,
        foregroundColor: Colors.white,
        elevation: 4,
        icon: const Icon(Icons.add),
        label: const Text('Nouvelle plainte'),
      ),
    );
  }
}

class _PlainteCard extends StatelessWidget {
  final PlainteItem plainte;

  const _PlainteCard({required this.plainte});

  String _formatStatut(String statut) => Libelles.statutPlainte(statut);

  Color _getStatutColor(String statut) {
    switch (statut.toUpperCase()) {
      case 'NOUVEAU':
      case 'EN_ANALYSE':
      case 'EN_TRAITEMENT':
      case 'EN_COURS':
      case 'EN_ATTENTE':
        return AppTheme.warning;
      case 'RESOLU':
      case 'FERME':
      case 'RESOLUE':
      case 'TRAITEE':
        return AppTheme.success;
      case 'REJETEE':
      case 'REFUSEE':
        return AppTheme.error;
      default:
        return AppTheme.textTertiary;
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _getStatutColor(plainte.statut);
    final statusLabel = _formatStatut(plainte.statut);

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.surfaceMuted, width: 1),
        boxShadow: [
          BoxShadow(
            color: AppTheme.textPrimary.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    Icons.report_problem_rounded,
                    color: statusColor,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        plainte.titre,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                          color: AppTheme.textPrimary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          Libelles.typePlainte(plainte.typePlainte),
                          style: TextStyle(color: AppTheme.textTertiary, fontSize: 12),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    statusLabel,
                    style: TextStyle(
                      color: statusColor,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

}


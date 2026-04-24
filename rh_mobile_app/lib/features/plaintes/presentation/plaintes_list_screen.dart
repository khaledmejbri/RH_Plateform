import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/widgets/pressable_scale.dart';
import '../data/plainte_models.dart';
import '../data/plainte_repository.dart';

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
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Mes plaintes', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 20)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
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
        error: (e, _) => _ErrorPlaintes(
          message: e is DioException ? _dioMsg(e) : '$e',
          onRetry: () => ref.invalidate(plaintesListProvider),
        ),
        data: (items) {
          if (items.isEmpty) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.inbox_rounded, size: 64, color: Theme.of(context).colorScheme.outline.withOpacity(0.3)),
                  const SizedBox(height: 12),
                  const Text('Aucune plainte pour le moment', style: TextStyle(fontSize: 16, color: Colors.grey)),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: () => context.push('/plaintes/nouveau'),
                    icon: const Icon(Icons.add_rounded, size: 18),
                    label: const Text('Nouvelle plainte'),
                  ),
                ],
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
                          color: Colors.black.withOpacity(0.05),
                          blurRadius: 10,
                          offset: const Offset(0, -2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.chevron_left, size: 20),
                          onPressed: _currentPage > 0 ? () => setState(() => _currentPage--) : null,
                          color: _currentPage > 0 ? const Color(0xFF2563EB) : Colors.grey.shade300,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Page ${_currentPage + 1} / $totalPages',
                          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          icon: const Icon(Icons.chevron_right, size: 20),
                          onPressed: _currentPage < totalPages - 1 ? () => setState(() => _currentPage++) : null,
                          color: _currentPage < totalPages - 1 ? const Color(0xFF2563EB) : Colors.grey.shade300,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/plaintes/nouveau'),
        backgroundColor: const Color(0xFF2563EB),
        elevation: 4,
        child: const Icon(Icons.add, color: Colors.white, size: 28),
      ),
    );
  }
}

String _dioMsg(DioException e) {
  if (e.response?.statusCode == 401) return 'Session invalide ou expirée.';
  if (e.type == DioExceptionType.connectionError) return 'Serveur injoignable.';
  return e.message ?? 'Erreur';
}

class _PlainteCard extends StatelessWidget {
  final PlainteItem plainte;

  const _PlainteCard({required this.plainte});

  String _formatStatut(String statut) {
    switch (statut.toUpperCase()) {
      case 'EN_COURS':
      case 'EN_ATTENTE':
        return 'En cours';
      case 'RESOLUE':
      case 'TRAITEE':
        return 'Résolue';
      case 'REJETEE':
      case 'REFUSEE':
        return 'Rejetée';
      default:
        return statut;
    }
  }

  Color _getStatutColor(String statut) {
    switch (statut.toUpperCase()) {
      case 'EN_COURS':
      case 'EN_ATTENTE':
        return const Color(0xFFF59E0B);
      case 'RESOLUE':
      case 'TRAITEE':
        return const Color(0xFF10B981);
      case 'REJETEE':
      case 'REFUSEE':
        return const Color(0xFFEF4444);
      default:
        return Colors.grey;
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
        border: Border.all(color: Colors.grey.shade100, width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.03),
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
                    color: statusColor.withOpacity(0.08),
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
                          color: Color(0xFF1E293B),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          _formatTypePlainte(plainte.typePlainte),
                          style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withOpacity(0.1),
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

  String _formatTypePlainte(String type) {
    return type.replaceAll('_', ' ').toLowerCase().split(' ').map((word) {
      if (word.isEmpty) return word;
      return word[0].toUpperCase() + word.substring(1);
    }).join(' ');
  }
}

class _ErrorPlaintes extends StatelessWidget {
  const _ErrorPlaintes({required this.message, required this.onRetry});

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
            Icon(Icons.cloud_off_rounded, size: 56, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 20),
            FilledButton(onPressed: onRetry, child: const Text('Réessayer')),
          ],
        ),
      ),
    );
  }
}

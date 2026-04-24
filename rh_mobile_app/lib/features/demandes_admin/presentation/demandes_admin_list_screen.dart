import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../data/demande_admin_repository.dart';
import '../data/demande_admin_models.dart';

const int _pageSize = 10;

final demandesAdminListProvider = FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).mesDemandes();
});

class DemandesAdminListScreen extends ConsumerStatefulWidget {
  const DemandesAdminListScreen({super.key});

  @override
  ConsumerState<DemandesAdminListScreen> createState() => _DemandesAdminListScreenState();
}

class _DemandesAdminListScreenState extends ConsumerState<DemandesAdminListScreen> {
  int _currentPage = 0;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(demandesAdminListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Mes congés', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 20)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () {
              setState(() => _currentPage = 0);
              ref.invalidate(demandesAdminListProvider);
            },
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Erreur: $e')),
        data: (items) {
          if (items.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.assignment_outlined, size: 70, color: Colors.grey.withOpacity(0.3)),
                  const SizedBox(height: 16),
                  const Text('Aucune demande pour le moment', style: TextStyle(color: Colors.grey, fontSize: 16)),
                ],
              ),
            );
          }

          final totalPages = (items.length / _pageSize).ceil();
          final startIndex = _currentPage * _pageSize;
          final endIndex = (startIndex + _pageSize).clamp(0, items.length);
          final currentPageItems = items.sublist(startIndex, endIndex);

          final enAttente = currentPageItems.where((i) => i.statut == 'EN_ATTENTE_VALIDATION' || i.statut == 'EN_COURS').toList();
          final approuvees = currentPageItems.where((i) => i.statut == 'APPROUVEE' || i.statut == 'VALIDE').toList();
          final refusees = currentPageItems.where((i) => i.statut == 'REJETEE' || i.statut == 'REFUSEE').toList();

          return RefreshIndicator(
            onRefresh: () async {
              setState(() => _currentPage = 0);
              ref.invalidate(demandesAdminListProvider);
            },
            child: Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    children: [
                      if (enAttente.isNotEmpty)
                        _StatusSection(
                          title: 'En attente',
                          color: const Color(0xFFF59E0B),
                          items: enAttente.map((d) => _RequestItem(d: d)).toList(),
                        ),
                      if (approuvees.isNotEmpty)
                        _StatusSection(
                          title: 'Approuvées',
                          color: const Color(0xFF10B981),
                          items: approuvees.map((d) => _RequestItem(d: d)).toList(),
                        ),
                      if (refusees.isNotEmpty)
                        _StatusSection(
                          title: 'Refusées',
                          color: const Color(0xFFEF4444),
                          items: refusees.map((d) => _RequestItem(d: d)).toList(),
                        ),
                    ],
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
        onPressed: () => context.push('/demandes-admin/conge/nouveau'),
        backgroundColor: const Color(0xFF2563EB),
        elevation: 4,
        child: const Icon(Icons.add, color: Colors.white, size: 28),
      ),
    );
  }
}

class _StatusSection extends StatelessWidget {
  final String title;
  final Color color;
  final List<Widget> items;

  const _StatusSection({required this.title, required this.color, required this.items});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
          child: Row(
            children: [
              Container(width: 4, height: 16, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(2))),
              const SizedBox(width: 8),
              Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.grey)),
            ],
          ),
        ),
        ...items,
        const SizedBox(height: 12),
      ],
    );
  }
}

class _RequestItem extends StatelessWidget {
  final DemandeAdminItem d;

  const _RequestItem({required this.d});

  @override
  Widget build(BuildContext context) {
    Color statusColor = Colors.grey;
    String statusLabel = d.statut;

    if (d.statut == 'EN_ATTENTE_VALIDATION' || d.statut == 'EN_COURS') {
      statusColor = Colors.orange;
      statusLabel = 'En attente';
    } else if (d.statut == 'APPROUVEE' || d.statut == 'VALIDE') {
      statusColor = Colors.green;
      statusLabel = 'Approuvée';
    } else if (d.statut == 'REJETEE' || d.statut == 'REFUSEE') {
      statusColor = Colors.red;
      statusLabel = 'Refusée';
    }

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => context.push('/demandes-admin/${d.id}'),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        d.typeDemande == 'CONGE' ? 'Congé' : d.typeDemande,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                        overflow: TextOverflow.ellipsis,
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
                        style: TextStyle(color: statusColor, fontSize: 10, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    const Icon(Icons.calendar_today_outlined, size: 13, color: Colors.grey),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '${d.periodeDebut ?? "N/A"} → ${d.periodeFin ?? "N/A"}',
                        style: const TextStyle(color: Colors.grey, fontSize: 12),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                if (d.motifRefus != null && d.motifRefus!.isNotEmpty) ...[
                  const Divider(height: 20),
                  Row(
                    children: [
                      const Icon(Icons.info_outline, size: 13, color: Colors.red),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          d.motifRefus!,
                          style: const TextStyle(color: Colors.red, fontSize: 11, fontStyle: FontStyle.italic),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

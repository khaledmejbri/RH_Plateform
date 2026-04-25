import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/document_models.dart';
import '../data/document_repository.dart';

const int _pageSize = 10;

final documentsListProvider = FutureProvider.autoDispose<List<DocumentDemandeItem>>((ref) async {
  return ref.watch(documentRepositoryProvider).mesDemandes();
});

class DocumentsListScreen extends ConsumerStatefulWidget {
  const DocumentsListScreen({super.key});

  @override
  ConsumerState<DocumentsListScreen> createState() => _DocumentsListScreenState();
}

class _DocumentsListScreenState extends ConsumerState<DocumentsListScreen> {
  int _currentPage = 0;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(documentsListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: const Color(0xFFF0F4FF),
      appBar: AppBar(
        title: const Text('Mes documents', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 20)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () {
              setState(() => _currentPage = 0);
              ref.invalidate(documentsListProvider);
            },
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 48, color: colorScheme.error.withOpacity(0.5)),
              const SizedBox(height: 16),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 32),
                child: Text(e is DioException ? '${e.message}' : '$e', textAlign: TextAlign.center, style: const TextStyle(color: Colors.grey)),
              ),
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: () => ref.invalidate(documentsListProvider), child: const Text('Réessayer'))
            ],
          ),
        ),
        data: (items) {
          if (items.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.description_outlined, size: 80, color: colorScheme.outline.withOpacity(0.3)),
                  const SizedBox(height: 16),
                  const Text('Aucun document demandé', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.grey)),
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: () => context.push('/documents/nouveau'),
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Nouvelle demande'),
                    style: FilledButton.styleFrom(backgroundColor: const Color(0xFF1E40AF)),
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
              ref.invalidate(documentsListProvider);
            },
            child: Column(
              children: [
                Expanded(
                  child: ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: currentPageItems.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, i) {
                      return _DocumentCard(document: currentPageItems[i]);
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
                          color: _currentPage > 0 ? const Color(0xFF1E40AF) : Colors.grey.shade300,
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
                          color: _currentPage < totalPages - 1 ? const Color(0xFF1E40AF) : Colors.grey.shade300,
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
        onPressed: () => context.push('/documents/nouveau'),
        backgroundColor: const Color(0xFF1E40AF),
        elevation: 4,
        child: const Icon(Icons.add, color: Colors.white, size: 28),
      ),
    );
  }
}

class _DocumentCard extends StatelessWidget {
  final DocumentDemandeItem document;

  const _DocumentCard({required this.document});

  String _formatTypeDocument(String type) {
    return type.replaceAll('_', ' ').toLowerCase().split(' ').map((word) {
      if (word.isEmpty) return word;
      return word[0].toUpperCase() + word.substring(1);
    }).join(' ');
  }

  @override
  Widget build(BuildContext context) {
    String statusLabel;
    Color statusColor;
    IconData statusIcon;

    switch (document.statut) {
      case 'DISPONIBLE':
        statusLabel = 'Prêt';
        statusColor = const Color(0xFF10B981);
        statusIcon = Icons.check_circle_rounded;
        break;
      case 'REJETEE':
        statusLabel = 'Refusé';
        statusColor = const Color(0xFFEF4444);
        statusIcon = Icons.cancel_rounded;
        break;
      case 'EN_TRAITEMENT_RH':
      case 'EN_ATTENTE_FILE':
        statusLabel = 'En cours';
        statusColor = const Color(0xFFF59E0B);
        statusIcon = Icons.access_time_filled_rounded;
        break;
      default:
        statusLabel = 'Envoyé';
        statusColor = const Color(0xFF3B82F6);
        statusIcon = Icons.send_rounded;
    }

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
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => context.push('/documents/${document.id}'),
          borderRadius: BorderRadius.circular(16),
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
                        document.statut == 'DISPONIBLE' ? Icons.description_rounded : Icons.history_edu_rounded,
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
                            _formatTypeDocument(document.typeDocument),
                            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: Color(0xFF1E293B)),
                          ),
                          if (document.dateCreation != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                DateFormat('dd MMM yyyy', 'fr_FR').format(document.dateCreation!),
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
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(statusIcon, size: 12, color: statusColor),
                          const SizedBox(width: 3),
                          Text(
                            statusLabel,
                            style: TextStyle(color: statusColor, fontSize: 10, fontWeight: FontWeight.w700),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (document.enRetard == true || document.statut == 'REJETEE') ...[
                  const Padding(
                    padding: EdgeInsets.only(top: 12, bottom: 4),
                    child: Divider(height: 1),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Icon(
                          document.statut == 'REJETEE' ? Icons.info_outline : Icons.warning_amber_rounded,
                          size: 14,
                          color: statusColor,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            document.statut == 'REJETEE'
                                ? (document.motifRejet ?? 'Veuillez contacter les RH')
                                : 'Traitement en retard',
                            style: TextStyle(
                              color: statusColor,
                              fontSize: 11,
                              fontStyle: document.statut == 'REJETEE' ? FontStyle.italic : FontStyle.normal,
                            ),
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
      ),
    );
  }
}

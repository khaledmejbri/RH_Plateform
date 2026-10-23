import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/document_models.dart';
import '../data/document_repository.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

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

    return Scaffold(
      backgroundColor: AppTheme.background,
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
        error: (e, _) => RhErrorState(
          message: rhErrorMessage(e),
          onRetry: () => ref.invalidate(documentsListProvider),
        ),
        data: (items) {
          if (items.isEmpty) {
            return RhEmptyState(
              icon: Icons.description_outlined,
              title: 'Aucun document demandé',
              message: 'Demandez une attestation ou un bulletin en quelques secondes.',
              action: FilledButton.icon(
                onPressed: () => context.push('/documents/nouveau'),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Nouvelle demande'),
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
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/documents/nouveau'),
        backgroundColor: AppTheme.primaryDark,
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
        statusColor = AppTheme.success;
        statusIcon = Icons.check_circle_rounded;
        break;
      case 'REJETEE':
        statusLabel = 'Refusé';
        statusColor = AppTheme.error;
        statusIcon = Icons.cancel_rounded;
        break;
      case 'EN_TRAITEMENT_RH':
      case 'EN_ATTENTE_FILE':
        statusLabel = 'En cours';
        statusColor = AppTheme.warning;
        statusIcon = Icons.access_time_filled_rounded;
        break;
      default:
        statusLabel = 'Envoyé';
        statusColor = AppTheme.primaryLight;
        statusIcon = Icons.send_rounded;
    }

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
                        color: statusColor.withValues(alpha: 0.08),
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
                            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: AppTheme.textPrimary),
                          ),
                          if (document.dateCreation != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                DateFormat('dd MMM yyyy', 'fr_FR').format(document.dateCreation!),
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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_theme.dart';
import '../data/formation_models.dart';
import '../data/formation_repository.dart';

final formationDetailProvider = FutureProvider.family<FormationItem, String>((ref, id) async {
  final repo = ref.watch(formationRepositoryProvider);
  final formations = await repo.mesDemandes();
  final formation = formations.firstWhere((f) => f.id == id);
  return formation;
});

final workflowHistoryProvider = FutureProvider.family<List<WorkflowHistoryItem>, String>((ref, id) async {
  return ref.watch(formationRepositoryProvider).obtenirHistorique(id);
});

class FormationDetailScreen extends ConsumerWidget {
  const FormationDetailScreen({super.key, required this.formationId});

  final String formationId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final formationAsync = ref.watch(formationDetailProvider(formationId));
    final historyAsync = ref.watch(workflowHistoryProvider(formationId));

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Détails de la formation'),
      ),
      body: formationAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Erreur: $e')),
        data: (formation) {
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeaderCard(formation),
                const SizedBox(height: 16),
                _buildInfoCard(formation),
                const SizedBox(height: 16),
                _buildWorkflowTimeline(historyAsync),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildHeaderCard(FormationItem formation) {
    final status = _statusMeta(formation.statut);

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.border, width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(Icons.school_rounded, color: Color(0xFF2563EB), size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        formation.typeFormation,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          color: AppTheme.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        formation.organisme,
                        style: const TextStyle(
                          fontSize: 14,
                          color: AppTheme.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: status.$2,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                status.$1,
                style: TextStyle(
                  color: status.$3,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoCard(FormationItem formation) {
    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.border, width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Informations',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            _buildInfoRow(Icons.access_time, 'Durée', '${formation.dureeHeures} heures'),
            if (formation.coutEstime != null)
              _buildInfoRow(Icons.payments, 'Coût estimé', '${formation.coutEstime} TND'),
            if (formation.justification != null && formation.justification!.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text(
                'Justification',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              Text(
                formation.justification!,
                style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
              ),
            ],
            if (formation.commentaireRh != null && formation.commentaireRh!.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text(
                'Commentaire RH',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  formation.commentaireRh!,
                  style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 18, color: AppTheme.textSecondary),
          const SizedBox(width: 8),
          Text(
            '$label: ',
            style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWorkflowTimeline(AsyncValue<List<WorkflowHistoryItem>> historyAsync) {
    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.border, width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Historique du workflow',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 16),
            historyAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Text('Erreur: $e'),
              data: (items) {
                if (items.isEmpty) {
                  return const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24),
                      child: Text('Aucun historique disponible'),
                    ),
                  );
                }
                return ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 16),
                  itemBuilder: (_, index) {
                    return _buildTimelineItem(items[index], isLast: index == items.length - 1);
                  },
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTimelineItem(WorkflowHistoryItem item, {required bool isLast}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Container(
              width: 12,
              height: 12,
              decoration: BoxDecoration(
                color: AppTheme.primary,
                shape: BoxShape.circle,
                border: Border.all(color: AppTheme.surface, width: 2),
              ),
            ),
            if (!isLast)
              Container(
                width: 2,
                height: 40,
                color: AppTheme.border,
              ),
          ],
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                item.actionLabel,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.textPrimary,
                ),
              ),
              if (item.acteurNom != null) ...[
                const SizedBox(height: 2),
                Text(
                  'Par: ${item.acteurNom}',
                  style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                ),
              ],
              if (item.commentaire != null && item.commentaire!.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  item.commentaire!,
                  style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                ),
              ],
              const SizedBox(height: 2),
              Text(
                _formatDate(item.dateAction),
                style: const TextStyle(fontSize: 11, color: AppTheme.textSecondary),
              ),
            ],
          ),
        ),
      ],
    );
  }

  String _formatDate(String dateString) {
    try {
      final date = DateTime.parse(dateString);
      return DateFormat('dd/MM/yyyy à HH:mm').format(date);
    } catch (e) {
      return dateString;
    }
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

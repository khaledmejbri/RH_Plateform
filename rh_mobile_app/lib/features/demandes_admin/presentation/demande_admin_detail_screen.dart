import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/format/formats.dart';
import '../../../core/libelles/libelles.dart';
import '../../../core/network/api_client.dart';
import '../data/demande_admin_models.dart';
import '../data/demande_admin_repository.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

final demandeAdminDetailProvider =
    FutureProvider.family.autoDispose<Map<String, dynamic>, String>((ref, id) async {
  final dio = ref.watch(dioProvider);
  final res = await dio.get<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id');
  return res.data!;
});

final demandeAdminSuiviProvider = FutureProvider.family.autoDispose<DemandeAdminSuivi, String>((ref, id) async {
  final dio = ref.watch(dioProvider);
  final res = await dio.get<Map<String, dynamic>>('${ApiConstants.demandesAdmin}/$id/suivi');
  return DemandeAdminSuivi.fromJson(res.data!);
});

final demandeAdminHistoriqueProvider = FutureProvider.family.autoDispose<List<WorkflowHistoryItem>, String>((ref, id) async {
  final repo = ref.watch(demandeAdminRepositoryProvider);
  return repo.obtenirHistorique(id);
});

class DemandeAdminDetailScreen extends ConsumerWidget {
  const DemandeAdminDetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(demandeAdminDetailProvider(id));
    final suivi = ref.watch(demandeAdminSuiviProvider(id));

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        backgroundColor: AppTheme.background,
        appBar: AppBar(
          title: const Text('Détail de la demande', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
          backgroundColor: Colors.white,
          elevation: 0,
          centerTitle: true,
          bottom: TabBar(
            labelColor: AppTheme.primary,
            unselectedLabelColor: AppTheme.textSecondary,
            indicatorColor: AppTheme.primary,
            tabs: const [
              Tab(text: 'Résumé'),
              Tab(text: 'Suivi'),
              Tab(text: 'Historique'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            detail.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text(rhErrorMessage(e))),
              data: (j) {
                final statut = j['statut']?.toString();
                final statusLabel = Libelles.statutDemande(statut);
                final bool refusee = statut == 'REFUSEE';
                final bool approuvee = statut == 'APPROUVEE';
                final bool enAttente = statut == 'SOUMISE' ||
                    statut == 'EN_VALIDATION_SUPERIEUR' ||
                    statut == 'EN_VALIDATION_RRH';
                final valideur = (j['valideur_attendu_nom_complet']?.toString() ?? '').trim();
                String statusDescription = 'Votre demande a été enregistrée.';
                if (enAttente) {
                  statusDescription = statut == 'EN_VALIDATION_SUPERIEUR' && valideur.isNotEmpty
                      ? 'Votre demande est en attente de validation par $valideur.'
                      : 'Votre demande est en cours de validation.';
                } else if (approuvee) {
                  statusDescription = 'Votre demande a été approuvée.';
                } else if (refusee) {
                  statusDescription = 'Votre demande a été refusée. Consultez le motif ci-dessous.';
                } else if (statut == 'ANNULEE') {
                  statusDescription = 'Cette demande a été annulée.';
                }

                Color statusColor = AppTheme.textTertiary;
                if (enAttente) {
                  statusColor = AppTheme.warning;
                } else if (approuvee) {
                  statusColor = AppTheme.success;
                } else if (refusee) {
                  statusColor = AppTheme.error;
                }
                final contenu = j['contenu'] is Map ? j['contenu'] as Map : const {};
                final titre = Libelles.titreDemande(
                  j['type_demande']?.toString(),
                  typeCongeCode: contenu['type_conge']?.toString(),
                );
                final dateDepot = j['cree_le'] ?? j['date_creation'];
                final numero = (j['numero']?.toString() ?? '').trim();

                return ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: AppTheme.surfaceMuted),
                        boxShadow: [
                          BoxShadow(
                            color: AppTheme.textPrimary.withValues(alpha: 0.03),
                            blurRadius: 8,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: statusColor.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Icon(
                                  approuvee ? Icons.check_circle : Icons.calendar_today_outlined,
                                  color: statusColor,
                                  size: 24,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      titre,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 18,
                                        color: AppTheme.textPrimary,
                                      ),
                                    ),
                                    if (dateDepot != null || numero.isNotEmpty)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 4),
                                        child: Text(
                                          [
                                            if (numero.isNotEmpty) numero,
                                            if (dateDepot != null) 'déposée le ${formatDateIso(dateDepot)}',
                                          ].join(' · '),
                                          style: TextStyle(color: AppTheme.textTertiary, fontSize: 13),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: statusColor.withValues(alpha: 0.08),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: statusColor.withValues(alpha: 0.2)),
                            ),
                            child: Row(
                              children: [
                                Icon(Icons.info_outline, size: 18, color: statusColor),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    statusLabel,
                                    style: TextStyle(
                                      color: statusColor,
                                      fontSize: 14,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            statusDescription,
                            style: const TextStyle(
                              fontSize: 14,
                              color: AppTheme.textTertiary,
                              height: 1.5,
                            ),
                          ),
                          if (j['periode_debut'] != null && j['periode_fin'] != null) ...[
                            const SizedBox(height: 16),
                            const Divider(),
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                Icon(Icons.date_range_rounded, size: 18, color: AppTheme.textSecondary),
                                const SizedBox(width: 8),
                                Text(
                                  'Période : ',
                                  style: TextStyle(color: AppTheme.textSecondary, fontSize: 13),
                                ),
                                Text(
                                  formatPeriode(j['periode_debut'], j['periode_fin']),
                                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: AppTheme.textPrimary),
                                ),
                              ],
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (refusee && j['motif_refus'] != null) ...[
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.error.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.error.withValues(alpha: 0.2)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.warning_amber_rounded, color: AppTheme.error, size: 20),
                                const SizedBox(width: 8),
                                const Text(
                                  'Motif du refus',
                                  style: TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 14,
                                    color: AppTheme.error,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              j['motif_refus'],
                              style: const TextStyle(fontSize: 13, color: AppTheme.textPrimary, height: 1.5),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ],
                );
              },
            ),
            suivi.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text(rhErrorMessage(e))),
              data: (s) => ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: s.etapes.length,
                itemBuilder: (context, i) {
                  final e = s.etapes[i];
                  final scheme = Theme.of(context).colorScheme;
                  final icon = e.terminee
                      ? Icons.check_circle_rounded
                      : e.enCours
                          ? Icons.radio_button_checked_rounded
                          : Icons.radio_button_off_rounded;
                  final color = e.terminee
                      ? scheme.tertiary
                      : e.enCours
                          ? scheme.primary
                          : scheme.outline;
                  return ListTile(
                    leading: Icon(icon, color: color),
                    title: Text(e.libelleAffiche),
                  );
                },
              ),
            ),
            // Historique tab
            Consumer(
              builder: (context, ref, _) {
                final historique = ref.watch(demandeAdminHistoriqueProvider(id));
                return historique.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Center(child: Text(rhErrorMessage(e))),
                  data: (items) {
                    if (items.isEmpty) {
                      return const Center(
                        child: Padding(
                          padding: EdgeInsets.all(32),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.history_outlined, size: 48, color: AppTheme.textTertiary),
                              SizedBox(height: 16),
                              Text('Aucun historique disponible', style: TextStyle(color: AppTheme.textTertiary)),
                            ],
                          ),
                        ),
                      );
                    }
                    return ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: items.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 16),
                      itemBuilder: (_, index) {
                        return _buildTimelineItem(items[index], isLast: index == items.length - 1);
                      },
                    );
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
        // Timeline line and dot
        Column(
          children: [
            Container(
              width: 2,
              height: 24,
              color: AppTheme.border,
            ),
            Container(
              width: 12,
              height: 12,
              decoration: BoxDecoration(
                color: AppTheme.primary,
                shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 2),
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
        // Content
        Expanded(
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        item.actionLabel,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: AppTheme.textPrimary,
                        ),
                      ),
                    ),
                    Text(
                      formatDateHeureIso(item.dateAction),
                      style: TextStyle(
                        fontSize: 11,
                        color: AppTheme.textTertiary,
                      ),
                    ),
                  ],
                ),
                if (item.acteurNom != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    'Par ${item.acteurNom}',
                    style: TextStyle(
                      fontSize: 12,
                      color: AppTheme.textSecondary,
                    ),
                  ),
                ],
                if (item.commentaireAffiche != null) ...[
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppTheme.background,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      item.commentaireAffiche!,
                      style: TextStyle(
                        fontSize: 12,
                        color: AppTheme.textSecondary,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}

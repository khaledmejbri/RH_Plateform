import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../data/document_models.dart';

final documentDetailProvider = FutureProvider.family.autoDispose<Map<String, dynamic>, String>((ref, id) async {
  final dio = ref.watch(dioProvider);
  final res = await dio.get<Map<String, dynamic>>('${ApiConstants.demandesDocuments}/$id');
  return res.data!;
});

final documentSuiviProvider = FutureProvider.family.autoDispose<DocumentSuivi, String>((ref, id) async {
  final dio = ref.watch(dioProvider);
  final res = await dio.get<Map<String, dynamic>>('${ApiConstants.demandesDocuments}/$id/suivi');
  return DocumentSuivi.fromJson(res.data!);
});

class DocumentDetailScreen extends ConsumerWidget {
  const DocumentDetailScreen({super.key, required this.id});

  final String id;

  String _formatTypeDocument(String type) {
    return type.replaceAll('_', ' ').toLowerCase().split(' ').map((word) {
      if (word.isEmpty) return word;
      return word[0].toUpperCase() + word.substring(1);
    }).join(' ');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(documentDetailProvider(id));
    final suivi = ref.watch(documentSuiviProvider(id));
    final scheme = Theme.of(context).colorScheme;

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        appBar: AppBar(
          title: const Text('Détail du document', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
          backgroundColor: Colors.white,
          elevation: 0,
          centerTitle: true,
          bottom: TabBar(
            labelColor: const Color(0xFF2563EB),
            unselectedLabelColor: Colors.grey.shade600,
            indicatorColor: const Color(0xFF2563EB),
            tabs: const [
              Tab(text: 'Résumé'),
              Tab(text: 'Suivi'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            detail.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text('$e')),
              data: (j) {
                String statusLabel = 'Envoyé';
                String statusDescription = 'Votre demande a été envoyée et sera traitée prochainement.';
                if (j['statut'] == 'EN_ATTENTE_FILE') {
                  final rang = j['rang_dans_file'];
                  final nombreDevant = j['nombre_en_attente_devant'];
                  if (rang != null && rang <= 10) {
                    statusLabel = 'En cours de validation';
                    statusDescription = 'Votre demande est en position $rang dans la file d\'attente.';
                    if (nombreDevant != null && nombreDevant > 0) {
                      statusDescription += ' Il y a $nombreDevant demande(s) avant la vôtre.';
                    }
                    statusDescription += ' Délai estimé : 2-3 jours ouvrables.';
                  } else {
                    statusLabel = 'En attente';
                    statusDescription = 'Votre demande est dans la file d\'attente. Délai estimé : 5-7 jours ouvrables.';
                  }
                } else if (j['statut'] == 'EN_TRAITEMENT_RH') {
                  statusLabel = 'En cours de traitement';
                  statusDescription = 'Les RH traitent actuellement votre demande. Délai restant : 1-2 jours.';
                } else if (j['statut'] == 'DISPONIBLE') {
                  statusLabel = 'Validée';
                  statusDescription = 'Votre document est prêt ! Vous pouvez le télécharger.';
                } else if (j['statut'] == 'REJETEE') {
                  statusLabel = 'Refusée';
                  statusDescription = 'Votre demande a été refusée. Veuillez consulter le motif ci-dessous.';
                }
                
                Color statusColor = const Color(0xFF64748B);
                if (statusLabel.contains('validation') || statusLabel.contains('traitement')) {
                  statusColor = const Color(0xFFF59E0B);
                } else if (statusLabel == 'Validée') {
                  statusColor = const Color(0xFF10B981);
                } else if (statusLabel == 'Refusée') {
                  statusColor = const Color(0xFFEF4444);
                }

                return ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: Colors.grey.shade100),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.03),
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
                                  color: statusColor.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Icon(
                                  statusLabel == 'Validée' ? Icons.check_circle : Icons.description_outlined,
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
                                      _formatTypeDocument(j['type_document'] ?? ''),
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 18,
                                        color: Color(0xFF1E293B),
                                      ),
                                    ),
                                    if (j['date_creation'] != null)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 4),
                                        child: Text(
                                          'Demandé le ${DateFormat('dd MMM yyyy', 'fr_FR').format(DateTime.parse(j['date_creation']))}',
                                          style: TextStyle(color: Colors.grey.shade500, fontSize: 13),
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
                              color: statusColor.withOpacity(0.08),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: statusColor.withOpacity(0.2)),
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
                              color: Color(0xFF64748B),
                              height: 1.5,
                            ),
                          ),
                          if (j['rang_dans_file'] != null) ...[
                            const SizedBox(height: 16),
                            const Divider(),
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                Icon(Icons.queue_rounded, size: 18, color: Colors.grey.shade600),
                                const SizedBox(width: 8),
                                Text(
                                  'Position dans la file : ',
                                  style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
                                ),
                                Text(
                                  '${j['rang_dans_file']}',
                                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Color(0xFF1E293B)),
                                ),
                              ],
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (j['statut'] == 'REJETEE' && j['motif_rejet'] != null) ...[
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: const Color(0xFFEF4444).withOpacity(0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFFEF4444).withOpacity(0.2)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.warning_amber_rounded, color: Color(0xFFEF4444), size: 20),
                                const SizedBox(width: 8),
                                const Text(
                                  'Motif du refus',
                                  style: TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 14,
                                    color: Color(0xFFEF4444),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              j['motif_rejet'],
                              style: const TextStyle(fontSize: 13, color: Color(0xFF1E293B), height: 1.5),
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
              error: (e, _) => Center(child: Text('$e')),
              data: (s) => ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Container(
                    padding: const EdgeInsets.all(16),
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.grey.shade100),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.03),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'File d\'attente',
                          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: Color(0xFF1E293B)),
                        ),
                        const SizedBox(height: 12),
                        if (s.rangDansFile != null)
                          _InfoRow(
                            icon: Icons.queue_rounded,
                            label: 'Votre position',
                            value: '${s.rangDansFile}',
                            color: const Color(0xFF2563EB),
                          ),
                        if (s.nombreDevant != null)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: _InfoRow(
                              icon: Icons.people_outline,
                              label: 'Demandes avant vous',
                              value: '${s.nombreDevant}',
                              color: const Color(0xFF64748B),
                            ),
                          ),
                        if (s.enRetard)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: const Color(0xFFEF4444).withOpacity(0.08),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.warning_amber_rounded, color: Color(0xFFEF4444), size: 16),
                                  const SizedBox(width: 6),
                                  const Text(
                                    'Hors délai SLA',
                                    style: TextStyle(
                                      color: Color(0xFFEF4444),
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const Text(
                    'Étapes de traitement',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: Color(0xFF1E293B)),
                  ),
                  const SizedBox(height: 12),
                  ...s.etapes.asMap().entries.map((entry) {
                    final index = entry.key;
                    final e = entry.value;
                    final term = e['terminee'] == true;
                    final cours = e['en_cours'] == true;
                    final isLast = index == s.etapes.length - 1;
                    
                    return Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Column(
                          children: [
                            Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(
                                color: term
                                    ? const Color(0xFF10B981)
                                    : cours
                                        ? const Color(0xFF2563EB)
                                        : Colors.grey.shade200,
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                term
                                    ? Icons.check_rounded
                                    : cours
                                        ? Icons.circle_rounded
                                        : Icons.circle_outlined,
                                color: term || cours ? Colors.white : Colors.grey.shade400,
                                size: 18,
                              ),
                            ),
                            if (!isLast)
                              Container(
                                width: 2,
                                height: 40,
                                color: term ? const Color(0xFF10B981) : Colors.grey.shade200,
                              ),
                          ],
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '${e['libelle']}',
                                  style: TextStyle(
                                    fontWeight: term || cours ? FontWeight.w600 : FontWeight.w500,
                                    fontSize: 14,
                                    color: term || cours ? const Color(0xFF1E293B) : Colors.grey.shade600,
                                  ),
                                ),
                                Text(
                                  '${e['code']}',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: Colors.grey.shade500,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    );
                  }),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 8),
        Text(
          '$label : ',
          style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
        ),
        Text(
          value,
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: color),
        ),
      ],
    );
  }
}

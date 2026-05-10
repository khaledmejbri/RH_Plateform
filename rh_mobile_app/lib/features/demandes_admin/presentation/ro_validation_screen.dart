import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../data/demande_admin_models.dart';
import '../data/demande_admin_repository.dart';

// ─── Provider ────────────────────────────────────────────────────────────────

final roDemandesEnAttenteProvider =
    FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).demandesEnAttenteRo();
});

// ─── Screen ──────────────────────────────────────────────────────────────────

/// Écran RO — liste des demandes de l'unité en attente de validation.
/// Accessible uniquement si profil_acces = RO ou RESPONSABLE.
class RoValidationScreen extends ConsumerWidget {
  const RoValidationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(roDemandesEnAttenteProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        title: const Text(
          'Demandes à valider',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.invalidate(roDemandesEnAttenteProvider),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorView(
          message: e.toString(),
          onRetry: () => ref.invalidate(roDemandesEnAttenteProvider),
        ),
        data: (items) => items.isEmpty
            ? const _EmptyView()
            : RefreshIndicator(
                onRefresh: () async =>
                    ref.invalidate(roDemandesEnAttenteProvider),
                child: ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  itemBuilder: (ctx, i) => _DemandeCard(
                    item: items[i],
                    onAction: () => ref.invalidate(roDemandesEnAttenteProvider),
                  ),
                ),
              ),
      ),
    );
  }
}

// ─── Card demande ─────────────────────────────────────────────────────────────

class _DemandeCard extends ConsumerWidget {
  final DemandeAdminItem item;
  final VoidCallback onAction;
  const _DemandeCard({required this.item, required this.onAction});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final typeInfo = _typeInfo(item.typeDemande);

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: typeInfo.color.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(typeInfo.icon,
                          color: typeInfo.color, size: 20),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            typeInfo.label,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                              color: Color(0xFF1E293B),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Réf. ${item.id.substring(0, 8).toUpperCase()}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: Color(0xFF94A3B8),
                              fontFamily: 'monospace',
                            ),
                          ),
                        ],
                      ),
                    ),
                    _StatusPill(statut: item.statut),
                  ],
                ),
                const SizedBox(height: 14),
                // Period / heure info
                _PeriodRow(item: item),
                // Motif for autorisation
                if (item.isAutorisationSortie &&
                    item.contenu?['motif'] != null) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.notes_rounded,
                          size: 14, color: Color(0xFF94A3B8)),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          item.contenu!['motif'] as String,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Color(0xFF475569),
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
                if (!item.isAutorisationSortie &&
                    item.contenu?['motif'] != null) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.notes_rounded,
                          size: 14, color: Color(0xFF94A3B8)),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          item.contenu!['motif'] as String,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Color(0xFF475569),
                          ),
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

          // Action buttons
          const Divider(height: 1, color: Color(0xFFE2E8F0)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Expanded(
                  child: _ActionButton(
                    label: 'Refuser',
                    icon: Icons.close_rounded,
                    color: const Color(0xFFDC2626),
                    onTap: () => _showRefusDialog(context, ref),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _ActionButton(
                    label: 'Valider',
                    icon: Icons.check_rounded,
                    color: const Color(0xFF16A34A),
                    filled: true,
                    onTap: () => _confirmerValidation(context, ref),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmerValidation(
      BuildContext context, WidgetRef ref) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Confirmer la validation'),
        content: Text(
          'Valider la demande ${_typeInfo(item.typeDemande).label.toLowerCase()} ? '
          'Elle sera transmise au RRH pour approbation finale.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Annuler'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF16A34A),
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Valider'),
          ),
        ],
      ),
    );
    if (confirm != true || !context.mounted) return;

    try {
      await ref
          .read(demandeAdminRepositoryProvider)
          .validerSuperieur(item.id);
      onAction();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Demande validée — transmise au RRH ✓'),
            backgroundColor: Color(0xFF16A34A),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur : $e'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  Future<void> _showRefusDialog(BuildContext context, WidgetRef ref) async {
    final motifController = TextEditingController();
    final result = await showDialog<String?>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Refuser la demande'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Veuillez indiquer le motif de refus. '
              'Il sera communiqué au collaborateur.',
              style: TextStyle(fontSize: 13, color: Color(0xFF64748B)),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: motifController,
              maxLines: 3,
              autofocus: true,
              decoration: InputDecoration(
                hintText: 'Motif du refus…',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                contentPadding: const EdgeInsets.all(12),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, null),
            child: const Text('Annuler'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFDC2626),
            ),
            onPressed: () {
              final motif = motifController.text.trim();
              if (motif.isEmpty) return;
              Navigator.pop(ctx, motif);
            },
            child: const Text('Confirmer le refus'),
          ),
        ],
      ),
    );

    if (result == null || !context.mounted) return;

    try {
      await ref
          .read(demandeAdminRepositoryProvider)
          .refuserSuperieur(item.id, result);
      onAction();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Demande refusée — le collaborateur est notifié'),
            backgroundColor: Color(0xFFDC2626),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur : $e'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }
}

// ─── Period row ───────────────────────────────────────────────────────────────

class _PeriodRow extends StatelessWidget {
  final DemandeAdminItem item;
  const _PeriodRow({required this.item});

  @override
  Widget build(BuildContext context) {
    String label;
    IconData icon;

    if (item.isAutorisationSortie) {
      final date = item.contenu?['date_jour'] as String? ?? '—';
      final debut = item.heureDebut ?? '--:--';
      final fin = item.heureFin ?? '--:--';
      label = '$date  ·  $debut → $fin';
      icon = Icons.access_time_rounded;
    } else if (item.typeDemande == 'ORDRE_MISSION') {
      final lieu = item.contenu?['lieu'] as String? ?? '';
      final debut = item.periodeDebut ?? '—';
      final fin = item.periodeFin ?? '—';
      label = lieu.isNotEmpty ? '$lieu  ·  $debut → $fin' : '$debut → $fin';
      icon = Icons.business_center_rounded;
    } else {
      // CONGE
      final debut = item.periodeDebut ?? '—';
      final fin = item.periodeFin ?? '—';
      final type = item.contenu?['type_conge'] as String? ?? '';
      label = type.isNotEmpty ? '$type  ·  $debut → $fin' : '$debut → $fin';
      icon = Icons.calendar_month_rounded;
    }

    return Row(
      children: [
        Icon(icon, size: 14, color: const Color(0xFF94A3B8)),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              color: Color(0xFF475569),
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }
}

// ─── Status pill ─────────────────────────────────────────────────────────────

class _StatusPill extends StatelessWidget {
  final String statut;
  const _StatusPill({required this.statut});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: const Color(0xFFFEF9C3),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        'En attente RO',
        style: const TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: Color(0xFFA16207),
        ),
      ),
    );
  }
}

// ─── Action button ────────────────────────────────────────────────────────────

class _ActionButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  final bool filled;
  final VoidCallback onTap;

  const _ActionButton({
    required this.label,
    required this.icon,
    required this.color,
    this.filled = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: filled ? color : color.withOpacity(0.08),
          borderRadius: BorderRadius.circular(12),
          border: filled ? null : Border.all(color: color.withOpacity(0.3)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon,
                size: 16, color: filled ? Colors.white : color),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 14,
                color: filled ? Colors.white : color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Type info ────────────────────────────────────────────────────────────────

class _TypeInfo {
  final String label;
  final IconData icon;
  final Color color;
  const _TypeInfo(this.label, this.icon, this.color);
}

_TypeInfo _typeInfo(String type) {
  return switch (type) {
    'CONGE' => const _TypeInfo(
        'Demande de congé', Icons.calendar_month_rounded, Color(0xFFEA580C)),
    'AUTORISATION_SORTIE' => const _TypeInfo(
        'Autorisation de sortie', Icons.access_time_rounded, Color(0xFF0D9488)),
    'ORDRE_MISSION' => const _TypeInfo(
        'Ordre de mission', Icons.business_center_rounded, Color(0xFF7C3AED)),
    _ => const _TypeInfo(
        'Demande', Icons.assignment_rounded, Color(0xFF2563EB)),
  };
}

// ─── Empty / Error views ──────────────────────────────────────────────────────

class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: const Color(0xFF16A34A).withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.check_circle_outline_rounded,
                  size: 48, color: Color(0xFF16A34A)),
            ),
            const SizedBox(height: 20),
            const Text(
              'Aucune demande en attente',
              style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF1E293B)),
            ),
            const SizedBox(height: 8),
            const Text(
              'Toutes les demandes de votre unité ont été traitées.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 14, color: Color(0xFF64748B)),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _ErrorView({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline_rounded,
                size: 48, color: Color(0xFFDC2626)),
            const SizedBox(height: 16),
            const Text('Erreur de chargement',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    fontSize: 12, color: Color(0xFF64748B))),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Réessayer'),
            ),
          ],
        ),
      ),
    );
  }
}

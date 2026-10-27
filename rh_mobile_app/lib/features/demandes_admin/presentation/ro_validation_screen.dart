import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../data/demande_admin_models.dart';
import '../data/demande_admin_repository.dart';
import '../data/demande_admin_errors.dart';
import '../data/type_conge.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

// ─── Provider ────────────────────────────────────────────────────────────────

final roDemandesEnAttenteProvider =
    FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).demandesEnAttenteRo();
});

// ─── Screen ──────────────────────────────────────────────────────────────────

/// File M01 — demandes dont le connecté est le manager ACTIF du nœud
/// (`valideur_attendu`). Accessible à tout USER ; le JWT RO n’est qu’un indice UI.
class RoValidationScreen extends ConsumerWidget {
  const RoValidationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(roDemandesEnAttenteProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
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
          message: messageErreurDemandeAdmin(e, fallback: rhErrorMessage(e)),
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
        border: Border.all(color: AppTheme.border),
        boxShadow: [
          BoxShadow(
            color: AppTheme.textPrimary.withValues(alpha: 0.04),
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
                        color: typeInfo.color.withValues(alpha: 0.1),
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
                              color: AppTheme.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Réf. ${item.id.substring(0, 8).toUpperCase()}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppTheme.textTertiary,
                              fontFamily: 'monospace',
                            ),
                          ),
                        ],
                      ),
                    ),
                    _StatusPill(statut: item.statut),
                  ],
                ),
                const SizedBox(height: 12),
                // SCRUM-18 : le RO doit savoir qui demande (§ 20 RO, § 6.5).
                Row(
                  children: [
                    const Icon(Icons.person_outline_rounded,
                        size: 16, color: AppTheme.textTertiary),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        [
                          item.demandeurNom ?? 'Demandeur inconnu',
                          if (item.demandeurMatricule != null) item.demandeurMatricule!,
                        ].join(' · '),
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textPrimary,
                        ),
                      ),
                    ),
                  ],
                ),
                if (item.demandeurUnite != null) ...[
                  const SizedBox(height: 2),
                  Padding(
                    padding: const EdgeInsets.only(left: 22),
                    child: Text(
                      item.demandeurUnite!,
                      style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                // Period / heure info
                _PeriodRow(item: item),
                if (item.certificat != null) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.attach_file_rounded,
                          size: 14, color: AppTheme.textTertiary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'Justificatif joint : ${item.certificat}',
                          style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
                // Motif for autorisation
                if (item.isAutorisationSortie &&
                    item.contenu?['motif'] != null) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.notes_rounded,
                          size: 14, color: AppTheme.textTertiary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          item.contenu!['motif'] as String,
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppTheme.textSecondary,
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
                          size: 14, color: AppTheme.textTertiary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          item.contenu!['motif'] as String,
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppTheme.textSecondary,
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
          const Divider(height: 1, color: AppTheme.border),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Expanded(
                  child: _ActionButton(
                    label: 'Refuser',
                    icon: Icons.close_rounded,
                    color: AppTheme.error,
                    onTap: () => _showRefusDialog(context, ref),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _ActionButton(
                    label: 'Valider',
                    icon: Icons.check_rounded,
                    color: AppTheme.success,
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
              backgroundColor: AppTheme.success,
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
            backgroundColor: AppTheme.success,
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(messageErreurDemandeAdmin(e)),
            backgroundColor: AppTheme.error,
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
              style: TextStyle(fontSize: 13, color: AppTheme.textTertiary),
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
              backgroundColor: AppTheme.error,
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
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(messageErreurDemandeAdmin(e)),
            backgroundColor: AppTheme.error,
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
      final typeCode = item.contenu?['type_conge'] as String? ?? '';
      final typeLabel = libelleTypeConge(typeCode);
      label = typeLabel.isNotEmpty ? '$typeLabel  ·  $debut → $fin' : '$debut → $fin';
      icon = Icons.calendar_month_rounded;
    }

    return Row(
      children: [
        Icon(icon, size: 14, color: AppTheme.textTertiary),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              color: AppTheme.textSecondary,
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
    return const RhStatusChip(label: 'En attente de validation', tone: RhTone.warning);
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
          color: filled ? color : color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
          border: filled ? null : Border.all(color: color.withValues(alpha: 0.3)),
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
        'Demande de congé', Icons.calendar_month_rounded, AppTheme.accent),
    'AUTORISATION_SORTIE' => const _TypeInfo(
        'Autorisation de sortie', Icons.access_time_rounded, Color(0xFF0D9488)),
    'ORDRE_MISSION' => const _TypeInfo(
        'Ordre de mission', Icons.business_center_rounded, Color(0xFF7C3AED)),
    _ => const _TypeInfo(
        'Demande', Icons.assignment_rounded, AppTheme.primary),
  };
}

// ─── Empty / Error views ──────────────────────────────────────────────────────

class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const RhEmptyState(
      icon: Icons.check_circle_outline_rounded,
      title: 'Aucune demande en attente',
      message: 'Toutes les demandes assignées à votre nœud ont été traitées.',
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
                size: 48, color: AppTheme.error),
            const SizedBox(height: 16),
            const Text('Erreur de chargement',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    fontSize: 12, color: AppTheme.textTertiary)),
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

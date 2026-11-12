import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../a_valider/providers/a_valider_providers.dart';
import '../data/demande_manuelle_textes.dart';
import '../data/journee_models.dart';
import '../data/presence_errors.dart';
import '../data/presence_models.dart';
import '../data/presence_repository.dart';
import 'motif_refus_dialog.dart';
import 'validation_manuelle_dialog.dart';

final demandeManuelleDetailProvider =
    FutureProvider.autoDispose.family<DemandeManuelleDetail, String>((ref, id) {
  return ref.watch(presenceRepositoryProvider).detailDemandeManuelle(id);
});

/// Détail d'un pointage manuel (C5) : demandeur ou valideur. Le valideur peut valider ou refuser
/// (confirmation « Êtes-vous sûr ? » avant tout appel).
class PointageManuelDetailScreen extends ConsumerStatefulWidget {
  const PointageManuelDetailScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<PointageManuelDetailScreen> createState() => _PointageManuelDetailScreenState();
}

class _PointageManuelDetailScreenState extends ConsumerState<PointageManuelDetailScreen> {
  bool _busy = false;

  static String _element(DemandeManuelle d) =>
      [d.typeLibelle, d.collaborateurNom ?? ''].where((e) => e.isNotEmpty).join(' — ');

  Future<void> _valider(DemandeManuelle d) async {
    final commentaire = await showDialog<String>(
      context: context,
      builder: (_) => ValidationManuelleDialog(
        element: _element(d),
        avecSignal: d.avecSignal,
        signaux: d.signaux,
      ),
    );
    if (commentaire == null || !mounted) return;
    await _decider(
        () => ref.read(presenceRepositoryProvider).validerDemandeManuelle(d.id, commentaire: commentaire),
        'Pointage manuel validé.');
  }

  Future<void> _refuser(DemandeManuelle d) async {
    final motif = await showDialog<String>(
      context: context,
      builder: (_) => MotifRefusDialog(element: _element(d)),
    );
    if (motif == null || !mounted) return;
    await _decider(() => ref.read(presenceRepositoryProvider).refuserDemandeManuelle(d.id, motif),
        'Pointage manuel refusé.');
  }

  Future<void> _decider(Future<DemandeManuelle> Function() action, String succes) async {
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await action();
      ref.invalidate(demandeManuelleDetailProvider(widget.id));
      ref.invalidate(aValiderProvider);
      messenger.showSnackBar(SnackBar(content: Text(succes)));
    } catch (e) {
      // 409 DEMANDE_EXPIREE : la demande a changé de statut, on recharge le détail et les listes.
      if (codeErreurPresence(e) == 'DEMANDE_EXPIREE') {
        ref.invalidate(demandeManuelleDetailProvider(widget.id));
        ref.invalidate(aValiderProvider);
      }
      messenger.showSnackBar(
        SnackBar(content: Text(messageErreurPresence(e)), backgroundColor: AppTheme.error),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(demandeManuelleDetailProvider(widget.id));
    final aValider = ref.watch(aValiderProvider).valueOrNull;
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Pointage manuel')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(
          e,
          onRetry: () => ref.invalidate(demandeManuelleDetailProvider(widget.id)),
        ),
        data: (detail) {
          final d = detail.demande;
          final estValideur = aValider?.pointagesManuels.any((x) => x.id == d.id) ?? false;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppTheme.border, width: 0.5),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            d.collaborateurNom ?? 'Pointage manuel',
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                          ),
                        ),
                        RhStatusChip(label: d.statutLibelle, statut: d.statut),
                      ],
                    ),
                    const SizedBox(height: 10),
                    _Ligne('Pointage', '${d.typeLibelle} du ${d.heureDeclaree == null ? kValeurAbsente : formatDateHeure(d.heureDeclaree!.toLocal())}'),
                    if (d.avecSignal) ...[
                      const Align(alignment: Alignment.centerLeft, child: PastilleVigilance()),
                      const SizedBox(height: 8),
                      BandeauSignaux(signaux: d.signaux),
                      const SizedBox(height: 8),
                    ],
                    _Ligne('Site', detail.siteLibelle),
                    _Ligne('Unité', detail.uniteLibelle),
                    _Ligne('Justification', d.justification),
                    _Ligne('Refus automatique', detail.motifRejetAutomatique ?? d.motifRejetInitial),
                    _Ligne('Valideur', detail.valideurAffiche),
                    _Ligne('Demande déposée', d.creeLe == null ? null : formatDateHeure(d.creeLe!.toLocal())),
                    if (libelleEcheance(d) != null) _Ligne('Échéance', libelleEcheance(d)),
                    if (estValideur)
                      for (final (libelle, valeur) in lignesInfosValideur(d)) _Ligne(libelle, valeur),
                    if (d.motifRefus != null) _Ligne('Motif du refus', d.motifRefus),
                    if (d.commentaireValideur != null) _Ligne('Commentaire', d.commentaireValideur),
                    if (libelleDelaiDecision(d) != null) _Ligne('Décision', libelleDelaiDecision(d)),
                  ],
                ),
              ),
              if (estValideur && d.enAttente) ...[
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _busy ? null : () => _refuser(d),
                        child: const Text('Refuser'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        onPressed: _busy ? null : () => _valider(d),
                        child: const Text('Valider'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _Ligne extends StatelessWidget {
  const _Ligne(this.libelle, this.valeur);

  final String libelle;
  final String? valeur;

  @override
  Widget build(BuildContext context) {
    final v = valeur;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
          ),
          Expanded(child: Text((v == null || v.isEmpty) ? kValeurAbsente : v)),
        ],
      ),
    );
  }
}

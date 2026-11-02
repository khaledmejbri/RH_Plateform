import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../pointage/data/presence_errors.dart';
import '../data/heures_sup_models.dart';
import '../data/heures_sup_repository.dart';
import '../providers/heures_sup_providers.dart';
import 'heures_sup_widgets.dart';

/// « Mes demandes » du RO / chef de département : statuts, annulation si EN_ATTENTE,
/// heures comptées de ses bénéficiaires (décision 4).
class MesDemandesHeuresSupScreen extends ConsumerStatefulWidget {
  const MesDemandesHeuresSupScreen({super.key});

  @override
  ConsumerState<MesDemandesHeuresSupScreen> createState() => _MesDemandesHeuresSupScreenState();
}

class _MesDemandesHeuresSupScreenState extends ConsumerState<MesDemandesHeuresSupScreen> {
  String? _annulationEnCours;

  Future<void> _rafraichir() async {
    ref.invalidate(mesDemandesHeuresSupProvider);
    ref.invalidate(heuresComptesMesDemandesProvider);
    try {
      await ref.read(mesDemandesHeuresSupProvider.future);
    } catch (_) {
      // L'erreur est affichée par la vue.
    }
  }

  Future<void> _annuler(DemandeHeuresSup d) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Annuler la demande ?'),
        content: Text('La demande ${d.reference} sera annulée.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Non')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Oui, annuler')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() => _annulationEnCours = d.id);
    try {
      await ref.read(heuresSupRepositoryProvider).annulerDemande(d.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Demande annulée.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(messageErreurPresence(e, fallback: 'Annulation impossible.')),
          backgroundColor: AppTheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _annulationEnCours = null);
    }
    await _rafraichir();
  }

  @override
  Widget build(BuildContext context) {
    final demandes = ref.watch(mesDemandesHeuresSupProvider);
    final comptees = ref.watch(heuresComptesMesDemandesProvider).valueOrNull ?? const <HeuresComptees>[];

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Demandes d’heures sup')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/heures-sup/nouveau');
          if (mounted) _rafraichir();
        },
        icon: const Icon(Icons.add_rounded),
        label: const Text('Nouvelle demande'),
      ),
      body: HeuresSupAsyncView<List<DemandeHeuresSup>>(
        value: demandes,
        onRefresh: _rafraichir,
        estVide: (l) => l.isEmpty,
        messageVide: 'Aucune demande d’heures sup pour le moment.',
        builder: (context, liste) => ListView.separated(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          itemCount: liste.length,
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemBuilder: (_, i) {
            final d = liste[i];
            final lignes = comptees.where((c) => c.referenceDemande == d.reference).toList();
            return _CarteDemande(
              demande: d,
              comptees: lignes,
              annulationEnCours: _annulationEnCours == d.id,
              onAnnuler: d.enAttente ? () => _annuler(d) : null,
            );
          },
        ),
      ),
    );
  }
}

class _CarteDemande extends StatelessWidget {
  const _CarteDemande({
    required this.demande,
    required this.comptees,
    required this.annulationEnCours,
    required this.onAnnuler,
  });

  final DemandeHeuresSup demande;
  final List<HeuresComptees> comptees;
  final bool annulationEnCours;
  final VoidCallback? onAnnuler;

  @override
  Widget build(BuildContext context) {
    final d = demande;
    final noms = d.beneficiaires.map((b) => b.nom).where((n) => n.isNotEmpty).join(', ');
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    d.reference.isEmpty ? 'Demande' : d.reference,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                StatutPastille(statut: d.statut),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              [
                if (d.periodeLibelle.isNotEmpty) d.periodeLibelle,
                if (d.siteLibelle != null && d.siteLibelle!.isNotEmpty) d.siteLibelle!,
              ].join(' · '),
            ),
            if (d.ouverturesLibelle.isNotEmpty)
              Text('Ouverture : ${d.ouverturesLibelle}', style: const TextStyle(color: AppTheme.textSecondary)),
            if (noms.isNotEmpty)
              Text('Pour : $noms', style: const TextStyle(color: AppTheme.textSecondary)),
            if (d.motif.isNotEmpty)
              Text('Motif : ${d.motif}', style: const TextStyle(color: AppTheme.textSecondary)),
            if (d.statut == StatutHeuresSup.refusee && (d.motifRefus ?? '').isNotEmpty)
              Text('Motif du refus : ${d.motifRefus}', style: const TextStyle(color: AppTheme.errorDark)),
            if (d.statut == StatutHeuresSup.annulee && (d.motifAnnulation ?? '').isNotEmpty)
              Text('Annulation : ${d.motifAnnulation}', style: const TextStyle(color: AppTheme.textSecondary)),
            if (comptees.isNotEmpty) ...[
              const Divider(height: 20),
              const Text('Heures comptées', style: TextStyle(fontWeight: FontWeight.w600)),
              for (final c in comptees) LigneHeuresComptees(ligne: c),
            ],
            if (onAnnuler != null) ...[
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton.icon(
                  onPressed: annulationEnCours ? null : onAnnuler,
                  icon: annulationEnCours
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.cancel_outlined),
                  label: const Text('Annuler la demande'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

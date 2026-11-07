import '../../../core/widgets/confirmation_action.dart';
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
    final ok = await confirmerAction(
      context,
      action: 'Annuler cette demande d’heures supplémentaires ?',
      element: d.numeroAffiche.isEmpty ? 'Demande d’heures supplémentaires' : d.numeroAffiche,
      libelleConfirmer: 'Oui, annuler',
      destructive: true,
    );
    if (!ok || !mounted) return;
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
    final compteesAsync = ref.watch(heuresComptesMesDemandesProvider);
    final comptees = compteesAsync.valueOrNull ?? const <HeuresComptees>[];
    // D-T02.e : une erreur sur les heures comptées n'est jamais présentée comme « aucune heure ».
    final compteesEnErreur = compteesAsync.hasError;

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
          itemCount: liste.length + (compteesEnErreur ? 1 : 0),
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemBuilder: (_, i) {
            if (compteesEnErreur) {
              if (i == 0) {
                return _BandeauErreurComptees(
                  erreur: compteesAsync.error!,
                  onRetry: () => ref.invalidate(heuresComptesMesDemandesProvider),
                );
              }
              i--;
            }
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

class _BandeauErreurComptees extends StatelessWidget {
  const _BandeauErreurComptees({required this.erreur, required this.onRetry});

  final Object erreur;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final message = estHorsLigne(erreur)
        ? 'Hors ligne : les heures comptées ne sont pas disponibles.'
        : messageErreurPresence(erreur, fallback: 'Heures comptées indisponibles.');
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.errorSurface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline_rounded, color: AppTheme.errorDark),
          const SizedBox(width: 10),
          Expanded(child: Text(message, style: const TextStyle(color: AppTheme.errorDark, fontSize: 13))),
          TextButton(onPressed: onRetry, child: const Text('Réessayer')),
        ],
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
                    d.numeroAffiche.isEmpty ? 'Demande' : d.numeroAffiche,
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

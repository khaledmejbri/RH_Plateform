import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../data/journee_models.dart';
import '../data/presence_errors.dart';
import '../data/presence_repository.dart';
import 'justification_dialog.dart';

/// Données de « Mes pointages » : totaux et journées des 31 derniers jours.
class MesPointagesData {
  const MesPointagesData({required this.synthese, required this.journees});

  final SynthesePresence synthese;
  final List<JourneePresence> journees;
}

final mesPointagesProvider = FutureProvider.autoDispose<MesPointagesData>((ref) async {
  final repo = ref.watch(presenceRepositoryProvider);
  final resultats = await Future.wait([repo.maSynthese(), repo.mesJournees()]);
  return MesPointagesData(
    synthese: resultats[0] as SynthesePresence,
    journees: resultats[1] as List<JourneePresence>,
  );
});

/// « Mes pointages » : temps du jour, retards et état de chaque journée ; demande de pointage manuel
/// proposée seulement quand le serveur déclare le refus éligible (D-P04, D-P12.a).
class MesPointagesScreen extends ConsumerWidget {
  const MesPointagesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(mesPointagesProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes pointages'),
        actions: [
          IconButton(
            tooltip: 'Actualiser',
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.invalidate(mesPointagesProvider),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(mesPointagesProvider)),
        data: (data) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(mesPointagesProvider),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              SyntheseCard(synthese: data.synthese, titre: '31 derniers jours'),
              const SizedBox(height: 12),
              if (data.journees.isEmpty)
                const Padding(
                  padding: EdgeInsets.only(top: 24),
                  child: RhEmptyState(
                    icon: Icons.event_busy_outlined,
                    title: 'Aucun pointage sur la période',
                    message: 'Vos pointages des 31 derniers jours apparaîtront ici.',
                  ),
                )
              else
                for (final j in data.journees) ...[
                  JourneeCard(
                    journee: j,
                    onDemanderManuel: (p) => _demanderManuel(context, ref, p),
                  ),
                  const SizedBox(height: 10),
                ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _demanderManuel(BuildContext context, WidgetRef ref, PointageJournee p) async {
    final justification = await showDialog<String>(
      context: context,
      builder: (_) => const JustificationPointageDialog(),
    );
    if (justification == null || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final demande = await ref.read(presenceRepositoryProvider).demanderPointageManuel(
            pointageRejeteId: p.id,
            justification: justification,
          );
      messenger.showSnackBar(
        SnackBar(content: Text('Demande envoyée à ${demande.valideurLibelle} pour validation.')),
      );
      ref.invalidate(mesPointagesProvider);
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(
          content: Text(e is DioException
              ? messageErreurPresence(e, fallback: 'Envoi de la demande impossible.')
              : 'Envoi de la demande impossible.'),
          backgroundColor: AppTheme.error,
        ),
      );
    }
  }
}

/// Totaux d'une période : jours pointés, temps, retards (réutilisée par la fiche d'équipe).
class SyntheseCard extends StatelessWidget {
  const SyntheseCard({super.key, required this.synthese, required this.titre});

  final SynthesePresence synthese;
  final String titre;

  @override
  Widget build(BuildContext context) {
    final s = synthese;
    final retards = s.retardsNombre == 0
        ? 'Aucun retard'
        : '${s.retardsNombre} retard${s.retardsNombre > 1 ? 's' : ''} · ${formatDuree(s.retardsCumulMinutes)}';
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titre, style: const TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          Row(
            children: [
              _Chiffre(valeur: '${s.journeesPointees}', libelle: 'Jours pointés'),
              _Chiffre(valeur: formatDuree(s.tempsTotalMinutes), libelle: 'Temps total'),
              _Chiffre(valeur: '${s.retardsNombre}', libelle: 'Retards'),
            ],
          ),
          const SizedBox(height: 10),
          Text(retards, style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary)),
          if (s.journeesIncompletes > 0)
            Text(
              '${s.journeesIncompletes} journée${s.journeesIncompletes > 1 ? 's' : ''} incomplète${s.journeesIncompletes > 1 ? 's' : ''}',
              style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
            ),
          if (s.pointagesManuelsEnAttente > 0)
            Text(
              '${s.pointagesManuelsEnAttente} pointage${s.pointagesManuelsEnAttente > 1 ? 's' : ''} manuel${s.pointagesManuelsEnAttente > 1 ? 's' : ''} en attente',
              style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
            ),
        ],
      ),
    );
  }
}

class _Chiffre extends StatelessWidget {
  const _Chiffre({required this.valeur, required this.libelle});

  final String valeur;
  final String libelle;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(valeur, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
          Text(libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}

/// Carte d'une journée : date, site, temps, retard, état et pointages. Le bouton « Demander un pointage
/// manuel » n'apparaît que si [onDemanderManuel] est fourni et que le serveur déclare le refus éligible.
class JourneeCard extends StatelessWidget {
  const JourneeCard({super.key, required this.journee, this.onDemanderManuel});

  final JourneePresence journee;
  final void Function(PointageJournee)? onDemanderManuel;

  @override
  Widget build(BuildContext context) {
    final j = journee;
    return Container(
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
                  j.journee == null ? kValeurAbsente : formatDate(j.journee!),
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                ),
              ),
              RhStatusChip(label: j.etatAffiche, statut: j.etat),
            ],
          ),
          if (j.siteLibelle != null) ...[
            const SizedBox(height: 2),
            Text(j.siteLibelle!, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
          ],
          const SizedBox(height: 8),
          Text('Temps de travail : ${formatDuree(j.tempsMinutes)}'),
          Text('Retard : ${formatRetard(j.retardMinutes)}'),
          if (j.pointages.isNotEmpty) const Divider(height: 20),
          for (final p in j.pointages) ...[
            _LignePointage(pointage: p, onDemanderManuel: onDemanderManuel),
          ],
        ],
      ),
    );
  }
}

class _LignePointage extends StatelessWidget {
  const _LignePointage({required this.pointage, this.onDemanderManuel});

  final PointageJournee pointage;
  final void Function(PointageJournee)? onDemanderManuel;

  @override
  Widget build(BuildContext context) {
    final p = pointage;
    final motif = p.motifAffiche;
    final heure = p.serverTs == null ? kValeurAbsente : formatHeure(p.serverTs!);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '${p.typeAffiche} à $heure${p.manuel ? ' (manuel)' : ''}',
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
              Text(p.statutAffiche, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
            ],
          ),
          if (motif != null)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(motif, style: const TextStyle(fontSize: 12, color: AppTheme.errorDark)),
            ),
          if (!p.manuelPossible && p.estRejet && p.manuelImpossibleMotif != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                'Pointage manuel impossible : ${p.manuelImpossibleMotif}.',
                key: const Key('manuel_impossible_motif'),
                style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
              ),
            ),
          if (p.manuelPossible && onDemanderManuel != null)
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => onDemanderManuel!(p),
                icon: const Icon(Icons.edit_calendar_outlined, size: 18),
                label: const Text('Demander un pointage manuel'),
              ),
            ),
        ],
      ),
    );
  }
}

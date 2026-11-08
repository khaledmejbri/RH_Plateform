import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../demandes_admin/data/demande_admin_models.dart';
import '../../pointage/data/presence_models.dart';
import '../providers/a_valider_providers.dart';

/// Hub « À valider » : demandes M01 et pointages manuels en attente de mon accord. Chaque élément
/// ouvre son détail ; valider ou refuser demande toujours la confirmation « Êtes-vous sûr ? ».
class AValiderScreen extends ConsumerWidget {
  const AValiderScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(aValiderProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: Text(async.valueOrNull == null ? 'À valider' : 'À valider (${async.value!.total})'),
        actions: [
          IconButton(
            tooltip: 'Actualiser',
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => ref.invalidate(aValiderProvider),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(aValiderProvider)),
        data: (a) {
          if (a.total == 0) {
            return RefreshIndicator(
              onRefresh: () async => ref.invalidate(aValiderProvider),
              child: ListView(
                children: const [
                  SizedBox(height: 80),
                  RhEmptyState(
                    icon: Icons.check_circle_outline_rounded,
                    title: 'Rien à valider',
                    message: 'Aucune demande ni aucun pointage manuel en attente (0).',
                  ),
                ],
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(aValiderProvider),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                if (a.demandes.isNotEmpty) ...[
                  _Titre('Demandes (${a.demandes.length})'),
                  for (final d in a.demandes) _CarteDemande(demande: d),
                  const SizedBox(height: 4),
                  OutlinedButton.icon(
                    onPressed: () => context.push('/demandes-admin/ro/validation'),
                    icon: const Icon(Icons.fact_check_outlined),
                    label: const Text('Valider ou refuser les demandes'),
                    style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
                  ),
                  const SizedBox(height: 16),
                ],
                if (a.pointagesManuels.isNotEmpty) ...[
                  _Titre('Pointages manuels (${a.pointagesManuels.length})'),
                  for (final p in a.pointagesManuels) _CartePointage(demande: p),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _Titre extends StatelessWidget {
  const _Titre(this.texte);

  final String texte;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(texte, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
      );
}

class _CarteDemande extends StatelessWidget {
  const _CarteDemande({required this.demande});

  final DemandeAdminItem demande;

  @override
  Widget build(BuildContext context) {
    final d = demande;
    final sousTitre = [
      if (d.referenceAffichee != null) d.referenceAffichee!,
      formatPeriode(d.periodeDebut, d.periodeFin ?? d.periodeDebut),
    ].join(' · ');
    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        title: Text(
          [d.titre, d.demandeurNom ?? ''].where((e) => e.isNotEmpty).join(' — '),
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(sousTitre),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: d.id.isEmpty ? null : () => context.push('/demandes-admin/${d.id}'),
      ),
    );
  }
}

class _CartePointage extends StatelessWidget {
  const _CartePointage({required this.demande});

  final DemandeManuelle demande;

  @override
  Widget build(BuildContext context) {
    final d = demande;
    final heure = d.heureDeclaree == null ? kValeurAbsente : formatDateHeure(d.heureDeclaree!.toLocal());
    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        title: Text(
          [d.collaborateurNom ?? '', d.typeLibelle].where((e) => e.isNotEmpty).join(' — '),
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(heure),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: d.id.isEmpty ? null : () => context.push('/pointage/manuels/${d.id}'),
      ),
    );
  }
}

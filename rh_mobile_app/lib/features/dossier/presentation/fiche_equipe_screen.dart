import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../pointage/presentation/mes_pointages_screen.dart';
import '../data/dossier_models.dart';
import '../providers/dossier_providers.dart';
import 'dossier_widgets.dart';

/// Fiche d'un membre de mon équipe (A10 + C7) : annuaire, présence et retards sur 31 jours, demandes.
/// Si la présence est refusée (403), seule la fiche annuaire est affichée ; si elle est indisponible
/// (503, réseau), la section propose « Réessayer ».
class FicheEquipeScreen extends ConsumerWidget {
  const FicheEquipeScreen({super.key, required this.collaborateurId});

  final String collaborateurId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fiche = ref.watch(ficheEquipeProvider(collaborateurId));
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: Text(fiche.valueOrNull?.annuaire.nomComplet ?? 'Fiche d’équipe'),
      ),
      body: fiche.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => etatErreurFiche(e, () => ref.invalidate(ficheEquipeProvider(collaborateurId))),
        data: (f) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(ficheEquipeProvider(collaborateurId));
            ref.invalidate(presenceEquipeProvider(collaborateurId));
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              SectionDossier(
                titre: f.annuaire.nomComplet,
                children: [
                  LigneInfo(libelle: 'Poste', valeur: f.annuaire.poste),
                  LigneInfo(libelle: 'Rôle', valeur: f.annuaire.intituleRole),
                  LigneInfo(libelle: 'Unité', valeur: f.annuaire.uniteLibelle),
                  LigneInfo(libelle: 'Courriel', valeur: f.annuaire.courriel),
                ],
              ),
              const SizedBox(height: 12),
              _PresenceSection(collaborateurId: collaborateurId),
              const SizedBox(height: 12),
              SectionDossier(
                titre: 'Demandes',
                children: [
                  if (f.demandes.isEmpty)
                    const Text('Aucune demande à votre connaissance.')
                  else
                    for (final d in f.demandes) _LigneDemande(demande: d),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PresenceSection extends ConsumerWidget {
  const _PresenceSection({required this.collaborateurId});

  final String collaborateurId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(presenceEquipeProvider(collaborateurId));
    return async.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) {
        final code = e is DioException ? e.response?.statusCode : null;
        // Hors périmètre de présence : la fiche annuaire seule reste affichée.
        if (code == 403) return const SizedBox.shrink();
        return SectionDossier(
          titre: 'Présence',
          children: [
            Text(rhErrorMessage(e)),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => ref.invalidate(presenceEquipeProvider(collaborateurId)),
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: const Text('Réessayer'),
            ),
          ],
        );
      },
      data: (p) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SyntheseCard(synthese: p.synthese, titre: 'Présence · 31 derniers jours'),
          const SizedBox(height: 10),
          if (p.journees.isEmpty)
            const Text('Aucun pointage sur la période.')
          else
            for (final j in p.journees) ...[
              JourneeCard(journee: j),
              const SizedBox(height: 8),
            ],
        ],
      ),
    );
  }
}

class _LigneDemande extends StatelessWidget {
  const _LigneDemande({required this.demande});

  final DemandeEquipe demande;

  @override
  Widget build(BuildContext context) {
    final d = demande;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      title: Text(
        [d.typeLibelle, d.statutLibelle].where((e) => e.isNotEmpty).join(' — '),
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        [
          if (d.numero != null) d.numero!,
          if (d.periodeDebut != null) formatPeriode(d.periodeDebut, d.periodeFin ?? d.periodeDebut),
        ].join(' · '),
      ),
      trailing: d.id.isEmpty ? null : const Icon(Icons.chevron_right_rounded, size: 20),
      onTap: d.id.isEmpty ? null : () => context.push('/demandes-admin/${d.id}'),
    );
  }
}

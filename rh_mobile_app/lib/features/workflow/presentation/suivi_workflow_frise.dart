import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../data/workflow_errors.dart';
import '../data/workflow_labels.dart';
import '../data/workflow_models.dart';
import '../providers/workflow_providers.dart';

/// Frise chronologique des étapes d'un dossier workflow (D-W8-04, CA-W8-02).
///
/// N'expose jamais les noms individuels des membres d'un pool (`candidats`),
/// uniquement l'intitulé de rôle/règle ou le décideur effectif.
class SuiviWorkflowFrise extends StatelessWidget {
  const SuiviWorkflowFrise({
    super.key,
    required this.suivi,
    this.titre = 'Circuit de validation',
  });

  final SuiviInstance suivi;
  final String titre;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final courante = suivi.etapeCourante;

    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceMd),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(titre, style: text.titleSmall)),
              const SizedBox(width: 8),
              RhStatusChip(
                label: libelleStatutInstance(suivi.statut),
                tone: _toneInstance(suivi.statut),
              ),
            ],
          ),
          if ((suivi.statut == 'EN_COURS' || suivi.statut == 'BLOQUEE') &&
              courante != null &&
              courante.libelle.isNotEmpty) ...[
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppTheme.primarySurface,
                borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Étape en cours : ${courante.libelle}',
                    style: text.labelMedium?.copyWith(color: AppTheme.primaryDark),
                  ),
                  if (courante.intituleIntervenants != null &&
                      courante.intituleIntervenants!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      courante.intituleIntervenants!,
                      style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                    ),
                  ],
                  if (courante.echeanceLe != null && courante.echeanceLe!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      'Échéance : ${formatDateHeureTunisIso(courante.echeanceLe)}',
                      style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                    ),
                  ],
                ],
              ),
            ),
          ],
          if (suivi.etapes.isNotEmpty) ...[
            const SizedBox(height: 14),
            for (var i = 0; i < suivi.etapes.length; i++)
              _EtapeLigne(
                etape: suivi.etapes[i],
                derniere: i == suivi.etapes.length - 1,
              ),
          ],
        ],
      ),
    );
  }

  RhTone _toneInstance(String statut) {
    switch (statut) {
      case 'APPROUVEE':
        return RhTone.success;
      case 'REFUSEE':
      case 'BLOQUEE':
      case 'EXPIREE':
      case 'ERREUR':
        return RhTone.danger;
      case 'EN_COURS':
        return RhTone.info;
      default:
        return RhTone.neutral;
    }
  }
}

class _EtapeLigne extends StatelessWidget {
  const _EtapeLigne({required this.etape, required this.derniere});

  final EtapeSuivi etape;
  final bool derniere;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final style = _styleEtape(etape);

    String? detailIntervenant;
    if (etape.decideurNom != null && etape.decideurNom!.isNotEmpty) {
      detailIntervenant = etape.pourNom != null && etape.pourNom!.isNotEmpty
          ? 'Par ${etape.decideurNom} (pour le compte de ${etape.pourNom})'
          : 'Par ${etape.decideurNom}';
    } else if (etape.intituleIntervenants != null &&
        etape.intituleIntervenants!.isNotEmpty) {
      detailIntervenant = etape.intituleIntervenants;
    }

    final resultatTexte = (etape.resultat != null && etape.resultat!.isNotEmpty)
        ? libelleResultatEtape(etape.resultat)
        : libelleStatutEtapeSuivi(etape.statut);

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 24,
            child: Column(
              children: [
                Container(
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    color: style.fond,
                    shape: BoxShape.circle,
                    border: Border.all(color: style.bordure, width: 1.5),
                  ),
                  child: Icon(style.icone, size: 13, color: style.couleurIcone),
                ),
                if (!derniere)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 2),
                      color: AppTheme.border,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: derniere ? 0 : 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          etape.libelle,
                          style: text.labelMedium?.copyWith(color: AppTheme.textPrimary),
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        resultatTexte,
                        style: text.labelSmall?.copyWith(color: style.couleurTexte),
                      ),
                    ],
                  ),
                  if (etape.raison != null && etape.raison!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      libelleRaisonSaut(etape.raison),
                      style: text.bodySmall,
                    ),
                  ],
                  if (detailIntervenant != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      detailIntervenant,
                      style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                    ),
                  ],
                  if (etape.dateDecision != null && etape.dateDecision!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      formatDateHeureTunisIso(etape.dateDecision),
                      style: text.bodySmall,
                    ),
                  ] else if (etape.statut == 'ACTIVE' &&
                      etape.echeanceLe != null &&
                      etape.echeanceLe!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      'Échéance : ${formatDateHeureTunisIso(etape.echeanceLe)}',
                      style: text.bodySmall,
                    ),
                  ],
                  if (etape.motif != null && etape.motif!.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTheme.surfaceMuted,
                        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                      ),
                      child: Text(
                        etape.motif!,
                        style: text.bodySmall?.copyWith(color: AppTheme.textPrimary),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  _EtapeStyle _styleEtape(EtapeSuivi e) {
    if (e.statut == 'TERMINEE') {
      if (e.resultat == 'REFUSEE' || e.resultat == 'EXPIREE') {
        return const _EtapeStyle(
          fond: AppTheme.errorSurface,
          bordure: AppTheme.error,
          couleurIcone: AppTheme.errorDark,
          couleurTexte: AppTheme.errorDark,
          icone: Icons.close_rounded,
        );
      }
      if (e.resultat == 'RENVOYEE') {
        return const _EtapeStyle(
          fond: AppTheme.warningSurface,
          bordure: AppTheme.warning,
          couleurIcone: AppTheme.warning,
          couleurTexte: AppTheme.warning,
          icone: Icons.undo_rounded,
        );
      }
      return const _EtapeStyle(
        fond: AppTheme.successSurface,
        bordure: AppTheme.success,
        couleurIcone: AppTheme.success,
        couleurTexte: AppTheme.success,
        icone: Icons.check_rounded,
      );
    }
    if (e.statut == 'ACTIVE') {
      return const _EtapeStyle(
        fond: AppTheme.primarySurface,
        bordure: AppTheme.primary,
        couleurIcone: AppTheme.primaryDark,
        couleurTexte: AppTheme.primaryDark,
        icone: Icons.schedule_rounded,
      );
    }
    if (e.statut == 'SAUTEE') {
      return const _EtapeStyle(
        fond: AppTheme.surfaceMuted,
        bordure: AppTheme.border,
        couleurIcone: AppTheme.textTertiary,
        couleurTexte: AppTheme.textTertiary,
        icone: Icons.remove_rounded,
      );
    }
    if (e.statut == 'ANNULEE') {
      return const _EtapeStyle(
        fond: AppTheme.surfaceMuted,
        bordure: AppTheme.border,
        couleurIcone: AppTheme.textTertiary,
        couleurTexte: AppTheme.textTertiary,
        icone: Icons.block_rounded,
      );
    }
    return const _EtapeStyle(
      fond: AppTheme.surface,
      bordure: AppTheme.border,
      couleurIcone: AppTheme.textTertiary,
      couleurTexte: AppTheme.textTertiary,
      icone: Icons.circle_outlined,
    );
  }
}

class _EtapeStyle {
  const _EtapeStyle({
    required this.fond,
    required this.bordure,
    required this.couleurIcone,
    required this.couleurTexte,
    required this.icone,
  });

  final Color fond;
  final Color bordure;
  final Color couleurIcone;
  final Color couleurTexte;
  final IconData icone;
}

/// Charge et affiche [SuiviWorkflowFrise] pour un objet métier en mode `MOTEUR`.
class SuiviWorkflowObjetSection extends ConsumerWidget {
  const SuiviWorkflowObjetSection({
    super.key,
    required this.objetType,
    required this.objetId,
    this.titre = 'Circuit de validation',
  });

  final String objetType;
  final String objetId;
  final String titre;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(suiviObjetProvider((objetType: objetType, objetId: objetId)));
    return async.when(
      data: (suivi) => SuiviWorkflowFrise(suivi: suivi, titre: titre),
      loading: () => Container(
        padding: const EdgeInsets.all(AppTheme.spaceMd),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radius),
          border: Border.all(color: AppTheme.border),
        ),
        child: const Row(
          children: [
            SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            SizedBox(width: 12),
            Text('Chargement du circuit de validation…'),
          ],
        ),
      ),
      error: (err, _) => Container(
        padding: const EdgeInsets.all(AppTheme.spaceMd),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radius),
          border: Border.all(color: AppTheme.border),
        ),
        child: Row(
          children: [
            const Icon(Icons.info_outline_rounded, color: AppTheme.textSecondary, size: 20),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                messageErreurWorkflowGenerique(
                  err,
                  fallback: 'Suivi du circuit indisponible.',
                ),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
            TextButton(
              onPressed: () => ref.invalidate(
                suiviObjetProvider((objetType: objetType, objetId: objetId)),
              ),
              child: const Text('Réessayer'),
            ),
          ],
        ),
      ),
    );
  }
}

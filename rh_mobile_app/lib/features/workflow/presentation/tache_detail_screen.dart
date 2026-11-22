import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../a_valider/providers/a_valider_providers.dart';
import '../data/uuid_v4.dart';
import '../data/workflow_errors.dart';
import '../data/workflow_labels.dart';
import '../data/workflow_models.dart';
import '../data/workflow_repository.dart';
import '../providers/workflow_providers.dart';
import 'suivi_workflow_frise.dart';

/// Détail d'une tâche workflow (`/taches/:id`).
///
/// Respecte strictement D-W8-04 :
/// - barre d'actions fixe (`bottomNavigationBar` + `SafeArea`), lisible sur `360x560`,
/// - affichée uniquement si `detail.statut == 'A_TRAITER'`,
/// - mode `COMPLEMENT` avec consigne, lien « Voir la demande » et « J'ai complété »,
/// - clé d'idempotence UUID v4 générée à l'ouverture de la feuille et réutilisée au rejeu.
class TacheDetailScreen extends ConsumerStatefulWidget {
  const TacheDetailScreen({
    super.key,
    required this.tacheId,
    this.itemListe,
  });

  final String tacheId;
  final TacheListeItem? itemListe;

  @override
  ConsumerState<TacheDetailScreen> createState() => _TacheDetailScreenState();
}

class _TacheDetailScreenState extends ConsumerState<TacheDetailScreen> {
  Future<void> _ouvrirDecision(TacheDetail detail, String action) async {
    final res = await showModalBottomSheet<_ResultatFeuille>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _FeuilleDecisionSheet(
        detail: detail,
        action: action,
      ),
    );
    if (!mounted || res == null) return;
    await _apresActionFeuille(res);
  }

  Future<void> _ouvrirComplement(TacheDetail detail) async {
    final res = await showModalBottomSheet<_ResultatFeuille>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _FeuilleComplementSheet(detail: detail),
    );
    if (!mounted || res == null) return;
    await _apresActionFeuille(res);
  }

  Future<void> _apresActionFeuille(_ResultatFeuille res) async {
    ref.invalidate(compteurTachesProvider);
    ref.invalidate(aValiderProvider);
    ref.invalidate(tacheDetailProvider(widget.tacheId));

    if (!mounted) return;
    if (res.message != null && res.message!.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(res.message!)),
      );
    }
    if (res.fermerEcran) {
      await Navigator.of(context).maybePop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final asyncDetail = ref.watch(tacheDetailProvider(widget.tacheId));
    final titreAppBar = asyncDetail.valueOrNull?.objetLibelle.isNotEmpty == true
        ? asyncDetail.valueOrNull!.objetLibelle
        : (widget.itemListe?.objetLibelle ?? 'Détail de la tâche');

    final detailBrut = asyncDetail.valueOrNull;
    final detail = detailBrut?.avecMetaListe(widget.itemListe);
    final afficherBarreActions = detail != null &&
        detail.statut == 'A_TRAITER' &&
        (detail.estComplementATraiter || detail.actionsPermises.isNotEmpty);

    return Scaffold(
      appBar: AppBar(title: Text(titreAppBar)),
      body: asyncDetail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => RhErrorState(
          message: messageErreurWorkflowGenerique(
            err,
            fallback: 'Impossible de charger cette tâche.',
          ),
          accesRefuse: statutHttpWorkflow(err) == 403,
          onRetry: statutHttpWorkflow(err) == 403 || statutHttpWorkflow(err) == 404
              ? null
              : () => ref.invalidate(tacheDetailProvider(widget.tacheId)),
        ),
        data: (_) {
          final d = detail!;
          final entreesResume = entreesResumeAffichables(d.resume);
          final motifComplement = d.suivi?.motifRenvoiOuMotif;
          final routeObjet = routePourObjetWorkflow(d.objetType, d.objetId);

          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(tacheDetailProvider(widget.tacheId));
              await ref.read(tacheDetailProvider(widget.tacheId).future);
            },
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(AppTheme.spaceMd),
              children: [
                _EnteteTacheCard(detail: d),
                if (d.estComplementATraiter) ...[
                  const SizedBox(height: 12),
                  _ComplementConsigneCard(
                    motif: motifComplement,
                    onVoirDemande: routeObjet == null
                        ? null
                        : () => context.push(routeObjet),
                  ),
                ] else if (routeObjet != null) ...[
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () => context.push(routeObjet),
                      icon: const Icon(Icons.open_in_new_rounded, size: 18),
                      label: const Text('Voir la demande associée'),
                    ),
                  ),
                ],
                if (entreesResume.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  _ResumeTacheCard(entrees: entreesResume),
                ],
                if (!d.estATraiter &&
                    (d.decision != null || d.commentaire != null || d.decideLe != null)) ...[
                  const SizedBox(height: 12),
                  _DecisionHistoriqueCard(detail: d),
                ],
                if (d.suivi != null) ...[
                  const SizedBox(height: 12),
                  SuiviWorkflowFrise(suivi: d.suivi!),
                ],
                const SizedBox(height: 16),
              ],
            ),
          );
        },
      ),
      bottomNavigationBar: afficherBarreActions
          ? _BarreActionsTache(
              detail: detail,
              onComplement: () => _ouvrirComplement(detail),
              onAction: (action) => _ouvrirDecision(detail, action),
            )
          : null,
    );
  }
}

class _EnteteTacheCard extends StatelessWidget {
  const _EnteteTacheCard({required this.detail});

  final TacheDetail detail;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceMd),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        border: Border.all(
          color: detail.enRetard && detail.estATraiter
              ? AppTheme.error.withValues(alpha: 0.45)
              : AppTheme.border,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 6,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              RhStatusChip(
                label: libelleStatutTache(detail.statut),
                tone: detail.estATraiter ? RhTone.info : RhTone.neutral,
              ),
              if (detail.enRetard && detail.estATraiter)
                const RhStatusChip(
                  label: 'En retard',
                  tone: RhTone.danger,
                ),
              if (detail.objetNumero != null && detail.objetNumero!.isNotEmpty)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppTheme.surfaceMuted,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(detail.objetNumero!, style: text.labelSmall),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Text(detail.libelle, style: text.titleMedium),
          if (detail.demandeurNom != null && detail.demandeurNom!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text('Demandeur : ${detail.demandeurNom}', style: text.bodyMedium),
          ],
          if (detail.pourNom != null && detail.pourNom!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: AppTheme.accentSurface,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                'Pour le compte de ${detail.pourNom}',
                style: text.labelSmall?.copyWith(color: AppTheme.accent),
              ),
            ),
          ] else if (detail.origine != null &&
              detail.origine!.isNotEmpty &&
              detail.origine != 'NOMINATIF') ...[
            const SizedBox(height: 4),
            Text(
              'Origine : ${libelleOrigineTache(detail.origine)}',
              style: text.bodySmall?.copyWith(color: AppTheme.primaryDark),
            ),
          ],
          if (detail.echeanceLe != null && detail.echeanceLe!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Row(
              children: [
                Icon(
                  Icons.schedule_rounded,
                  size: 16,
                  color: detail.enRetard && detail.estATraiter
                      ? AppTheme.errorDark
                      : AppTheme.textSecondary,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Échéance : ${formatDateHeureTunisIso(detail.echeanceLe)}',
                    style: text.bodySmall?.copyWith(
                      color: detail.enRetard && detail.estATraiter
                          ? AppTheme.errorDark
                          : AppTheme.textSecondary,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _ComplementConsigneCard extends StatelessWidget {
  const _ComplementConsigneCard({
    this.motif,
    this.onVoirDemande,
  });

  final String? motif;
  final VoidCallback? onVoirDemande;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceMd),
      decoration: BoxDecoration(
        color: AppTheme.warningSurface,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        border: Border.all(color: AppTheme.warning.withValues(alpha: 0.45)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.edit_note_rounded, color: AppTheme.warning, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Complément demandé',
                  style: text.titleSmall?.copyWith(color: AppTheme.warning),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            motif != null && motif!.isNotEmpty
                ? motif!
                : 'Un valideur vous invite à préciser ou compléter votre demande avant réexamen.',
            style: text.bodyMedium?.copyWith(color: AppTheme.textPrimary),
          ),
          if (onVoirDemande != null) ...[
            const SizedBox(height: 6),
            TextButton.icon(
              onPressed: onVoirDemande,
              icon: const Icon(Icons.open_in_new_rounded, size: 18),
              label: const Text('Voir la demande'),
            ),
          ],
        ],
      ),
    );
  }
}

class _ResumeTacheCard extends StatelessWidget {
  const _ResumeTacheCard({required this.entrees});

  final List<MapEntry<String, String>> entrees;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
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
          Text('Résumé de la demande', style: text.titleSmall),
          const SizedBox(height: 10),
          for (var i = 0; i < entrees.length; i++) ...[
            if (i > 0) const Divider(height: 14),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 4,
                  child: Text(entrees[i].key, style: text.bodySmall),
                ),
                const SizedBox(width: 8),
                Expanded(
                  flex: 6,
                  child: Text(
                    entrees[i].value,
                    style: text.bodyMedium?.copyWith(color: AppTheme.textPrimary),
                    textAlign: TextAlign.right,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _DecisionHistoriqueCard extends StatelessWidget {
  const _DecisionHistoriqueCard({required this.detail});

  final TacheDetail detail;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceMd),
      decoration: BoxDecoration(
        color: AppTheme.surfaceMuted,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Décision enregistrée', style: text.titleSmall),
          if (detail.decision != null) ...[
            const SizedBox(height: 6),
            Text(
              'Action : ${libelleActionDecision(detail.decision)}',
              style: text.bodyMedium?.copyWith(color: AppTheme.textPrimary),
            ),
          ],
          if (detail.decideLe != null) ...[
            const SizedBox(height: 4),
            Text(
              'Le ${formatDateHeureTunisIso(detail.decideLe)}',
              style: text.bodySmall,
            ),
          ],
          if (detail.commentaire != null && detail.commentaire!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(detail.commentaire!, style: text.bodyMedium),
          ],
        ],
      ),
    );
  }
}

/// Barre d'actions fixe en bas, enveloppée dans [SafeArea] et adaptée à `360x560`.
class _BarreActionsTache extends StatelessWidget {
  const _BarreActionsTache({
    required this.detail,
    required this.onComplement,
    required this.onAction,
  });

  final TacheDetail detail;
  final VoidCallback onComplement;
  final ValueChanged<String> onAction;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.border)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: detail.estComplementATraiter
              ? SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: onComplement,
                    icon: const Icon(Icons.send_rounded),
                    label: const Text(
                      'J’ai complété',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                )
              : _buildBoutonsDecision(),
        ),
      ),
    );
  }

  Widget _buildBoutonsDecision() {
    final actions = detail.actionsPermises;
    if (actions.length == 1) {
      final a = actions.first;
      return SizedBox(
        width: double.infinity,
        child: _boutonPour(a),
      );
    }
    if (actions.length == 2) {
      return Row(
        children: [
          Expanded(child: _boutonPour(actions[1])),
          const SizedBox(width: 8),
          Expanded(child: _boutonPour(actions[0])),
        ],
      );
    }

    final primaire = actions.contains('APPROUVER')
        ? 'APPROUVER'
        : (actions.contains('FAIT') ? 'FAIT' : actions.first);
    final secondaires = actions.where((a) => a != primaire).toList();

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: double.infinity,
          child: _boutonPour(primaire),
        ),
        if (secondaires.isNotEmpty) ...[
          const SizedBox(height: 8),
          Row(
            children: [
              for (var i = 0; i < secondaires.length; i++) ...[
                if (i > 0) const SizedBox(width: 8),
                Expanded(child: _boutonPour(secondaires[i])),
              ],
            ],
          ),
        ],
      ],
    );
  }

  Widget _boutonPour(String action) {
    final label = libelleActionDecision(action);
    final styleCompact = OutlinedButton.styleFrom(
      minimumSize: const Size(0, 44),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
    );

    if (action == 'REFUSER') {
      return OutlinedButton(
        style: styleCompact.copyWith(
          foregroundColor: const WidgetStatePropertyAll(AppTheme.errorDark),
          side: WidgetStatePropertyAll(
            BorderSide(color: AppTheme.error.withValues(alpha: 0.5)),
          ),
        ),
        onPressed: () => onAction(action),
        child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      );
    }
    if (action == 'RENVOYER') {
      return OutlinedButton(
        style: styleCompact.copyWith(
          foregroundColor: const WidgetStatePropertyAll(AppTheme.warning),
          side: WidgetStatePropertyAll(
            BorderSide(color: AppTheme.warning.withValues(alpha: 0.5)),
          ),
        ),
        onPressed: () => onAction(action),
        child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      );
    }
    return FilledButton(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 44),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
      onPressed: () => onAction(action),
      child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class _ResultatFeuille {
  const _ResultatFeuille({required this.fermerEcran, this.message});

  final bool fermerEcran;
  final String? message;
}

class _FeuilleDecisionSheet extends ConsumerStatefulWidget {
  const _FeuilleDecisionSheet({required this.detail, required this.action});

  final TacheDetail detail;
  final String action;

  @override
  ConsumerState<_FeuilleDecisionSheet> createState() => _FeuilleDecisionSheetState();
}

class _FeuilleDecisionSheetState extends ConsumerState<_FeuilleDecisionSheet> {
  final _ctrl = TextEditingController();
  late final String _cleIdempotence = genererUuidV4();
  bool _envoi = false;
  String? _erreur;

  RegleCommentaireDecision get _regle => regleCommentairePourAction(
        widget.action,
        commentaireObligatoireSi: widget.detail.commentaireObligatoire,
      );

  bool get _saisieValide {
    final len = _ctrl.text.trim().length;
    if (_regle.obligatoire && len < _regle.minLongueur) return false;
    return len <= _regle.maxLongueur;
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _soumettre() async {
    if (_envoi || !_saisieValide) return;
    setState(() {
      _envoi = true;
      _erreur = null;
    });

    try {
      final res = await ref.read(workflowRepositoryProvider).deciderTache(
            tacheId: widget.detail.tacheId,
            action: widget.action,
            commentaire: _ctrl.text,
            version: widget.detail.version,
            cleIdempotence: _cleIdempotence,
          );
      if (!mounted) return;
      Navigator.of(context).pop(
        _ResultatFeuille(
          fermerEcran: true,
          message: res.enCours
              ? 'Décision en cours d’enregistrement'
              : 'Décision enregistrée.',
        ),
      );
    } catch (e) {
      if (!mounted) return;
      final http = statutHttpWorkflow(e);
      if (http == 409) {
        Navigator.of(context).pop(
          const _ResultatFeuille(
            fermerEcran: false,
            message: 'Déjà traitée par quelqu’un d’autre.',
          ),
        );
        return;
      }
      if (http == 403 || http == 404) {
        Navigator.of(context).pop(
          _ResultatFeuille(
            fermerEcran: http == 404,
            message: messageErreurDecision(e),
          ),
        );
        return;
      }
      setState(() {
        _envoi = false;
        _erreur = http == 422
            ? (messageServeurWorkflow(e) ?? messageErreurDecision(e))
            : messageErreurDecision(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final len = _ctrl.text.trim().length;
    final estDestructive = widget.action == 'REFUSER';

    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${libelleActionDecision(widget.action)} — ${widget.detail.libelle}',
              style: text.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              _regle.obligatoire
                  ? 'Commentaire obligatoire (${_regle.minLongueur} à ${_regle.maxLongueur} caractères).'
                  : 'Commentaire facultatif (${_regle.maxLongueur} caractères maximum).',
              style: text.bodySmall,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _ctrl,
              maxLines: 4,
              maxLength: _regle.maxLongueur,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: _regle.obligatoire ? 'Motif / commentaire *' : 'Commentaire (facultatif)',
                hintText: _regle.obligatoire
                    ? 'Précisez votre décision…'
                    : 'Ajouter une précision si utile…',
                border: const OutlineInputBorder(),
                counterText: '$len / ${_regle.maxLongueur} caractères',
              ),
            ),
            if (_erreur != null) ...[
              const SizedBox(height: 10),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.errorSurface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                ),
                child: Text(
                  _erreur!,
                  style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
                ),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _envoi ? null : () => Navigator.of(context).pop(),
                    child: const Text('Annuler'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    style: estDestructive
                        ? FilledButton.styleFrom(backgroundColor: AppTheme.error)
                        : null,
                    onPressed: (_envoi || !_saisieValide) ? null : _soumettre,
                    child: _envoi
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Text(libelleActionDecision(widget.action)),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _FeuilleComplementSheet extends ConsumerStatefulWidget {
  const _FeuilleComplementSheet({required this.detail});

  final TacheDetail detail;

  @override
  ConsumerState<_FeuilleComplementSheet> createState() => _FeuilleComplementSheetState();
}

class _FeuilleComplementSheetState extends ConsumerState<_FeuilleComplementSheet> {
  static const int _min = 5;
  static const int _max = 1000;

  final _ctrl = TextEditingController();
  late final String _cleIdempotence = genererUuidV4();
  bool _envoi = false;
  String? _erreur;

  bool get _saisieValide {
    final len = _ctrl.text.trim().length;
    return len >= _min && len <= _max;
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _soumettre() async {
    if (_envoi || !_saisieValide) return;
    setState(() {
      _envoi = true;
      _erreur = null;
    });

    try {
      await ref.read(workflowRepositoryProvider).envoyerComplement(
            objetType: widget.detail.objetType,
            objetId: widget.detail.objetId,
            tacheId: widget.detail.tacheId,
            commentaire: _ctrl.text,
            version: widget.detail.version,
            cleIdempotence: _cleIdempotence,
          );
      if (!mounted) return;
      Navigator.of(context).pop(
        const _ResultatFeuille(
          fermerEcran: true,
          message: 'Complément envoyé.',
        ),
      );
    } catch (e) {
      if (!mounted) return;
      final http = statutHttpWorkflow(e);
      if (http == 409) {
        Navigator.of(context).pop(
          _ResultatFeuille(
            fermerEcran: false,
            message: messageErreurComplement(e),
          ),
        );
        return;
      }
      if (http == 403 || http == 404) {
        Navigator.of(context).pop(
          _ResultatFeuille(
            fermerEcran: http == 404,
            message: messageErreurComplement(e),
          ),
        );
        return;
      }
      setState(() {
        _envoi = false;
        _erreur = http == 422
            ? (messageServeurWorkflow(e) ?? messageErreurComplement(e))
            : messageErreurComplement(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final len = _ctrl.text.trim().length;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Envoyer le complément', style: text.titleMedium),
            const SizedBox(height: 6),
            Text(
              'Précisez les informations demandées ($_min à $_max caractères).',
              style: text.bodySmall,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _ctrl,
              maxLines: 5,
              maxLength: _max,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: 'Votre réponse *',
                hintText: 'Saisissez les précisions demandées…',
                border: const OutlineInputBorder(),
                counterText: '$len / $_max caractères',
              ),
            ),
            if (_erreur != null) ...[
              const SizedBox(height: 10),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.errorSurface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                ),
                child: Text(
                  _erreur!,
                  style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
                ),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _envoi ? null : () => Navigator.of(context).pop(),
                    child: const Text('Annuler'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    onPressed: (_envoi || !_saisieValide) ? null : _soumettre,
                    child: _envoi
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('Envoyer'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

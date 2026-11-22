import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../a_valider/providers/a_valider_providers.dart';
import '../data/workflow_errors.dart';
import '../data/workflow_labels.dart';
import '../data/workflow_models.dart';
import '../data/workflow_repository.dart';
import '../providers/workflow_providers.dart';

/// Écran « Mes tâches » (`/taches`) : tâches à traiter et historique des 90 derniers jours.
class MesTachesScreen extends ConsumerStatefulWidget {
  const MesTachesScreen({super.key});

  @override
  ConsumerState<MesTachesScreen> createState() => _MesTachesScreenState();
}

class _MesTachesScreenState extends ConsumerState<MesTachesScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _rafraichirGlobal() {
    ref.invalidate(compteurTachesProvider);
    ref.invalidate(aValiderProvider);
  }

  @override
  Widget build(BuildContext context) {
    final compteurAsync = ref.watch(compteurTachesProvider);
    final aTraiterCount = compteurAsync.valueOrNull?.aTraiter;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mes tâches'),
        bottom: TabBar(
          controller: _tabController,
          tabs: [
            Tab(text: aTraiterCount != null && aTraiterCount > 0 ? 'À traiter ($aTraiterCount)' : 'À traiter'),
            const Tab(text: 'Traitées (90 j)'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _OngletTaches(
            statut: 'A_TRAITER',
            onTacheModifiee: _rafraichirGlobal,
          ),
          _OngletTaches(
            statut: 'TRAITEE',
            onTacheModifiee: _rafraichirGlobal,
          ),
        ],
      ),
    );
  }
}

class _OngletTaches extends ConsumerStatefulWidget {
  const _OngletTaches({
    required this.statut,
    required this.onTacheModifiee,
  });

  final String statut;
  final VoidCallback onTacheModifiee;

  @override
  ConsumerState<_OngletTaches> createState() => _OngletTachesState();
}

class _OngletTachesState extends ConsumerState<_OngletTaches>
    with AutomaticKeepAliveClientMixin {
  static const int _taillePage = 20;

  final List<TacheListeItem> _items = [];
  int _pageCourante = 0;
  int _total = 0;
  bool _chargementInitial = true;
  bool _chargementSuite = false;
  Object? _erreur;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _charger(reinitialiser: true);
  }

  Future<void> _charger({bool reinitialiser = false}) async {
    if (reinitialiser) {
      setState(() {
        _chargementInitial = _items.isEmpty;
        _erreur = null;
      });
    } else {
      if (_chargementSuite) return;
      setState(() {
        _chargementSuite = true;
      });
    }

    final ciblePage = reinitialiser ? 0 : _pageCourante + 1;
    try {
      final page = await ref.read(workflowRepositoryProvider).mesTaches(
            statut: widget.statut,
            page: ciblePage,
            taille: _taillePage,
          );
      if (!mounted) return;
      setState(() {
        if (reinitialiser) {
          _items
            ..clear()
            ..addAll(page.elements);
        } else {
          _items.addAll(page.elements);
        }
        _pageCourante = page.page;
        _total = page.total;
        _chargementInitial = false;
        _chargementSuite = false;
        _erreur = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _chargementInitial = false;
        _chargementSuite = false;
        _erreur = e;
      });
      if (!reinitialiser) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(messageErreurWorkflowGenerique(e))),
        );
      }
    }
  }

  bool get _aPlus => (_pageCourante + 1) * _taillePage < _total;

  Future<void> _ouvrirDetail(TacheListeItem tache) async {
    final modifie = await context.push<bool>(
      '/taches/${tache.tacheId}',
      extra: tache,
    );
    if (modifie == true && mounted) {
      widget.onTacheModifiee();
      await _charger(reinitialiser: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    if (_chargementInitial) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_erreur != null && _items.isEmpty) {
      return RhErrorState(
        message: messageErreurWorkflowGenerique(
          _erreur!,
          fallback: 'Impossible de charger vos tâches.',
        ),
        accesRefuse: statutHttpWorkflow(_erreur!) == 403,
        onRetry: statutHttpWorkflow(_erreur!) == 403
            ? null
            : () => _charger(reinitialiser: true),
      );
    }

    if (_items.isEmpty) {
      final estATraiter = widget.statut == 'A_TRAITER';
      return RefreshIndicator(
        onRefresh: () => _charger(reinitialiser: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.55,
              child: RhEmptyState(
                icon: estATraiter
                    ? Icons.task_alt_rounded
                    : Icons.history_rounded,
                title: estATraiter
                    ? 'Aucune tâche à traiter'
                    : 'Aucune tâche traitée récemment',
                message: estATraiter
                    ? 'Vous êtes à jour sur toutes vos validations et actions.'
                    : 'Vos décisions des 90 derniers jours apparaîtront ici.',
              ),
            ),
          ],
        ),
      );
    }

    final aBoutonSuite = _aPlus || _chargementSuite;
    return RefreshIndicator(
      onRefresh: () => _charger(reinitialiser: true),
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(AppTheme.spaceMd),
        itemCount: _items.length + (aBoutonSuite ? 1 : 0),
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          if (index >= _items.length) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Center(
                child: _chargementSuite
                    ? const CircularProgressIndicator()
                    : OutlinedButton.icon(
                        onPressed: () => _charger(reinitialiser: false),
                        icon: const Icon(Icons.expand_more_rounded),
                        label: Text(
                          'Charger plus (${_items.length} / $_total)',
                        ),
                      ),
              ),
            );
          }
          final item = _items[index];
          return _TacheCard(
            item: item,
            onTap: () => _ouvrirDetail(item),
          );
        },
      ),
    );
  }
}

class _TacheCard extends StatelessWidget {
  const _TacheCard({required this.item, required this.onTap});

  final TacheListeItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final entreesResume = entreesResumeAffichables(item.resume).take(2).toList();

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(AppTheme.radius),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        child: Container(
          padding: const EdgeInsets.all(AppTheme.spaceMd),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTheme.radius),
            border: Border.all(
              color: item.enRetard ? AppTheme.error.withValues(alpha: 0.45) : AppTheme.border,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppTheme.primarySurface,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            item.objetLibelle.isNotEmpty
                                ? item.objetLibelle
                                : libelleObjetType(item.objetType),
                            style: text.labelSmall?.copyWith(color: AppTheme.primaryDark),
                          ),
                        ),
                        if (item.objetNumero != null && item.objetNumero!.isNotEmpty)
                          Text(
                            item.objetNumero!,
                            style: text.labelSmall?.copyWith(color: AppTheme.textSecondary),
                          ),
                        if (item.enRetard)
                          const RhStatusChip(
                            label: 'En retard',
                            tone: RhTone.danger,
                          ),
                        if (item.estComplement)
                          const RhStatusChip(
                            label: 'Complément demandé',
                            tone: RhTone.warning,
                          ),
                      ],
                    ),
                  ),
                  const Icon(
                    Icons.chevron_right_rounded,
                    color: AppTheme.textTertiary,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                item.libelleEtape,
                style: text.titleSmall,
              ),
              if (item.demandeurNom != null && item.demandeurNom!.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  'Demandeur : ${item.demandeurNom}',
                  style: text.bodyMedium,
                ),
              ],
              if (item.pourNom != null && item.pourNom!.isNotEmpty) ...[
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.accentSurface,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.swap_horiz_rounded,
                        size: 14,
                        color: AppTheme.accent,
                      ),
                      const SizedBox(width: 4),
                      Flexible(
                        child: Text(
                          'Pour le compte de ${item.pourNom}',
                          style: text.labelSmall?.copyWith(color: AppTheme.accent),
                        ),
                      ),
                    ],
                  ),
                ),
              ] else if (item.origine != null && item.origine!.isNotEmpty && item.origine != 'TITULAIRE') ...[
                const SizedBox(height: 4),
                Text(
                  libelleOrigineCandidat(item.origine),
                  style: text.bodySmall?.copyWith(color: AppTheme.primaryDark),
                ),
              ],
              if (entreesResume.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 12,
                  runSpacing: 4,
                  children: [
                    for (final e in entreesResume)
                      Text(
                        '${e.key} : ${e.value}',
                        style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                      ),
                  ],
                ),
              ],
              if (item.echeanceLe != null && item.echeanceLe!.isNotEmpty) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(
                      Icons.schedule_rounded,
                      size: 14,
                      color: item.enRetard ? AppTheme.errorDark : AppTheme.textTertiary,
                    ),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        'Échéance : ${formatDateHeureTunisIso(item.echeanceLe)}',
                        style: text.bodySmall?.copyWith(
                          color: item.enRetard ? AppTheme.errorDark : AppTheme.textTertiary,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

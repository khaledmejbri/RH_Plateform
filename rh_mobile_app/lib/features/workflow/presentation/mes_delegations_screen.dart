import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/confirmation_action.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../auth/providers/collaborateur_notifier.dart';
import '../../organigramme/data/organigramme_repository.dart';
import '../data/workflow_errors.dart';
import '../data/workflow_labels.dart';
import '../data/workflow_models.dart';
import '../data/workflow_repository.dart';
import '../providers/workflow_providers.dart';

/// Écran « Mes délégations » (`/delegations`) : données / reçues, création et annulation.
class MesDelegationsScreen extends ConsumerStatefulWidget {
  const MesDelegationsScreen({super.key});

  @override
  ConsumerState<MesDelegationsScreen> createState() => _MesDelegationsScreenState();
}

class _MesDelegationsScreenState extends ConsumerState<MesDelegationsScreen>
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

  void _rafraichirTout() {
    ref.invalidate(delegationsMoiProvider('DELEGANT'));
    ref.invalidate(delegationsMoiProvider('DELEGUE'));
  }

  Future<void> _ouvrirCreation() async {
    final creee = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => const _FeuilleCreationDelegation(),
    );
    if (!mounted || creee != true) return;
    _rafraichirTout();
    _tabController.animateTo(0);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Délégation créée.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mes délégations'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Données'),
            Tab(text: 'Reçues'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _OngletDelegations(
            role: 'DELEGANT',
            onAnnulerEffectuee: _rafraichirTout,
            onCreer: _ouvrirCreation,
          ),
          _OngletDelegations(
            role: 'DELEGUE',
            onAnnulerEffectuee: _rafraichirTout,
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('delegation-nouvelle-fab'),
        onPressed: _ouvrirCreation,
        icon: const Icon(Icons.add_rounded),
        label: const Text('Nouvelle délégation'),
      ),
    );
  }
}

class _OngletDelegations extends ConsumerStatefulWidget {
  const _OngletDelegations({
    required this.role,
    required this.onAnnulerEffectuee,
    this.onCreer,
  });

  final String role;
  final VoidCallback onAnnulerEffectuee;
  final VoidCallback? onCreer;

  @override
  ConsumerState<_OngletDelegations> createState() => _OngletDelegationsState();
}

class _OngletDelegationsState extends ConsumerState<_OngletDelegations>
    with AutomaticKeepAliveClientMixin {
  String? _annulationEnCoursId;

  @override
  bool get wantKeepAlive => true;

  Future<void> _annuler(DelegationItem d) async {
    final estDelegant = widget.role == 'DELEGANT';
    final nom = estDelegant ? d.delegueNom : d.delegantNom;
    final ok = await confirmerAction(
      context,
      action: 'Voulez-vous annuler cette délégation ?',
      element: '$nom (${formatDateTunisIso(d.du)} au ${formatDateTunisIso(d.au)})',
      libelleConfirmer: 'Annuler la délégation',
      destructive: true,
    );
    if (!ok || !mounted) return;

    setState(() => _annulationEnCoursId = d.id);
    try {
      await ref.read(workflowRepositoryProvider).annulerDelegation(d.id);
      if (!mounted) return;
      widget.onAnnulerEffectuee();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Délégation annulée.')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(messageErreurDelegation(e))),
      );
    } finally {
      if (mounted) {
        setState(() => _annulationEnCoursId = null);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final async = ref.watch(delegationsMoiProvider(widget.role));
    final estDelegant = widget.role == 'DELEGANT';

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => RhErrorState(
        message: messageErreurDelegation(err),
        accesRefuse: statutHttpWorkflow(err) == 403,
        onRetry: statutHttpWorkflow(err) == 403
            ? null
            : () => ref.invalidate(delegationsMoiProvider(widget.role)),
      ),
      data: (items) {
        if (items.isEmpty) {
          return RefreshIndicator(
            onRefresh: () async => ref.refresh(delegationsMoiProvider(widget.role).future),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                SizedBox(
                  height: MediaQuery.of(context).size.height * 0.55,
                  child: RhEmptyState(
                    icon: Icons.swap_horiz_rounded,
                    title: estDelegant
                        ? 'Aucune délégation donnée'
                        : 'Aucune délégation reçue',
                    message: estDelegant
                        ? 'Confiez temporairement vos tâches de validation à un collègue pendant votre absence.'
                        : 'Les délégations qui vous sont confiées apparaîtront ici.',
                    action: estDelegant && widget.onCreer != null
                        ? FilledButton.icon(
                            onPressed: widget.onCreer,
                            icon: const Icon(Icons.add_rounded),
                            label: const Text('Créer une délégation'),
                          )
                        : null,
                  ),
                ),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: () async => ref.refresh(delegationsMoiProvider(widget.role).future),
          child: ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 88),
            itemCount: items.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (_, i) {
              final d = items[i];
              return _DelegationCard(
                item: d,
                estDelegant: estDelegant,
                annulationEnCours: _annulationEnCoursId == d.id,
                onAnnuler: () => _annuler(d),
              );
            },
          ),
        );
      },
    );
  }
}

class _DelegationCard extends StatelessWidget {
  const _DelegationCard({
    required this.item,
    required this.estDelegant,
    required this.annulationEnCours,
    required this.onAnnuler,
  });

  final DelegationItem item;
  final bool estDelegant;
  final bool annulationEnCours;
  final VoidCallback onAnnuler;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final statut = statutDelegation(item);
    final peutAnnuler = estDelegant &&
        (statut == StatutDelegation.active || statut == StatutDelegation.planifiee);
    final nomAffiche = estDelegant ? item.delegueNom : item.delegantNom;
    final libelleRole = estDelegant ? 'Délégué à' : 'Délégant';
    final typesTexte = item.objetTypes.isEmpty
        ? 'Tous les types de demandes'
        : item.objetTypes.map(libelleObjetType).join(', ');

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
              Expanded(
                child: Text(
                  '$libelleRole : $nomAffiche',
                  style: text.titleSmall,
                ),
              ),
              const SizedBox(width: 8),
              RhStatusChip(
                label: libelleStatutDelegation(statut),
                tone: _toneDelegation(statut),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              const Icon(Icons.date_range_rounded, size: 15, color: AppTheme.textSecondary),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  'Du ${formatDateTunisIso(item.du)} au ${formatDateTunisIso(item.au)}',
                  style: text.bodyMedium?.copyWith(color: AppTheme.textPrimary),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Périmètre : $typesTexte',
            style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
          ),
          if (item.origine != null && item.origine!.isNotEmpty) ...[
            const SizedBox(height: 2),
            Text(
              'Origine : ${libelleOrigineDelegation(item.origine)}',
              style: text.bodySmall,
            ),
          ],
          if (item.motif != null && item.motif!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppTheme.surfaceMuted,
                borderRadius: BorderRadius.circular(AppTheme.radiusSm),
              ),
              child: Text(item.motif!, style: text.bodySmall?.copyWith(color: AppTheme.textPrimary)),
            ),
          ],
          if (peutAnnuler) ...[
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                style: TextButton.styleFrom(foregroundColor: AppTheme.errorDark),
                onPressed: annulationEnCours ? null : onAnnuler,
                icon: annulationEnCours
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.cancel_outlined, size: 18),
                label: const Text('Annuler'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  RhTone _toneDelegation(StatutDelegation s) {
    switch (s) {
      case StatutDelegation.active:
        return RhTone.success;
      case StatutDelegation.planifiee:
        return RhTone.info;
      case StatutDelegation.expiree:
        return RhTone.neutral;
      case StatutDelegation.annulee:
        return RhTone.danger;
    }
  }
}

class _FeuilleCreationDelegation extends ConsumerStatefulWidget {
  const _FeuilleCreationDelegation();

  @override
  ConsumerState<_FeuilleCreationDelegation> createState() =>
      _FeuilleCreationDelegationState();
}

class _FeuilleCreationDelegationState extends ConsumerState<_FeuilleCreationDelegation> {
  final _rechercheCtrl = TextEditingController();
  final _motifCtrl = TextEditingController();

  bool _chargementMembres = true;
  Object? _erreurMembres;
  List<CollaborateurSelection> _membres = const [];

  CollaborateurSelection? _delegue;
  DateTime? _du;
  DateTime? _au;
  final Set<String> _typesSelectionnes = {};

  bool _envoi = false;
  String? _erreurSoumission;

  @override
  void initState() {
    super.initState();
    _chargerOrganigramme();
  }

  @override
  void dispose() {
    _rechercheCtrl.dispose();
    _motifCtrl.dispose();
    super.dispose();
  }

  Future<void> _chargerOrganigramme() async {
    setState(() {
      _chargementMembres = true;
      _erreurMembres = null;
    });
    try {
      final org = await ref.read(organigrammeRepositoryProvider).fetch();
      final monId = ref.read(collaborateurNotifierProvider).valueOrNull?.identifiant;
      final liste = aplatirMembresOrganigramme(org, exclureIdentifiant: monId);
      if (!mounted) return;
      setState(() {
        _membres = liste;
        _chargementMembres = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _chargementMembres = false;
        _erreurMembres = e;
      });
    }
  }

  String? get _erreurDates =>
      (_du == null && _au == null) ? null : validerDelegationDates(du: _du, au: _au);

  bool get _formulaireValide =>
      _delegue != null &&
      _du != null &&
      _au != null &&
      validerDelegationDates(du: _du, au: _au) == null &&
      _motifCtrl.text.trim().length <= 300;

  Future<void> _choisirPeriode() async {
    final aujourdhui = aujourdhuiTunis();
    final plage = await showDateRangePicker(
      context: context,
      locale: Localizations.localeOf(context).languageCode == 'fr'
          ? const Locale('fr', 'FR')
          : null,
      firstDate: aujourdhui,
      lastDate: aujourdhui.add(const Duration(days: 365)),
      initialDateRange: (_du != null && _au != null && !_au!.isBefore(_du!))
          ? DateTimeRange(start: _du!, end: _au!)
          : null,
      helpText: 'Période de délégation (90 j max)',
      saveText: 'Valider',
    );
    if (!mounted || plage == null) return;
    setState(() {
      _du = plage.start;
      _au = plage.end;
      _erreurSoumission = null;
    });
  }

  Future<void> _creer() async {
    if (_envoi || !_formulaireValide) return;
    setState(() {
      _envoi = true;
      _erreurSoumission = null;
    });
    try {
      await ref.read(workflowRepositoryProvider).creerDelegation(
            delegueId: _delegue!.identifiant,
            du: _du!,
            au: _au!,
            objetTypes: _typesSelectionnes.isEmpty ? null : _typesSelectionnes.toList(),
            motif: _motifCtrl.text,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _envoi = false;
        _erreurSoumission = messageErreurDelegation(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final requete = _rechercheCtrl.text.trim().toLowerCase();
    final filtres = _membres.where((m) {
      if (requete.isEmpty) return true;
      return m.nomComplet.toLowerCase().contains(requete) ||
          (m.matricule?.toLowerCase().contains(requete) ?? false) ||
          (m.posteLibelle?.toLowerCase().contains(requete) ?? false);
    }).toList();

    final errDates = _erreurDates;

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
            Text('Nouvelle délégation', style: text.titleLarge),
            const SizedBox(height: 12),
            Text('Collaborateur délégué *', style: text.titleSmall),
            const SizedBox(height: 6),
            if (_delegue != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: AppTheme.primarySurface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                  border: Border.all(color: AppTheme.primary.withValues(alpha: 0.35)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.person_rounded, color: AppTheme.primaryDark, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _delegue!.nomComplet,
                            style: text.labelMedium?.copyWith(color: AppTheme.primaryDark),
                          ),
                          if (_delegue!.posteLibelle != null)
                            Text(_delegue!.posteLibelle!, style: text.bodySmall),
                        ],
                      ),
                    ),
                    TextButton(
                      onPressed: () => setState(() => _delegue = null),
                      child: const Text('Changer'),
                    ),
                  ],
                ),
              )
            else if (_chargementMembres)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_erreurMembres != null)
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Impossible de charger l’organigramme.',
                      style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
                    ),
                  ),
                  TextButton(
                    onPressed: _chargerOrganigramme,
                    child: const Text('Réessayer'),
                  ),
                ],
              )
            else ...[
              TextField(
                key: const Key('delegation-recherche'),
                controller: _rechercheCtrl,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  hintText: 'Rechercher par nom, matricule ou poste…',
                  prefixIcon: Icon(Icons.search_rounded),
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                constraints: const BoxConstraints(maxHeight: 160),
                decoration: BoxDecoration(
                  border: Border.all(color: AppTheme.border),
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                ),
                child: filtres.isEmpty
                    ? const Padding(
                        padding: EdgeInsets.all(14),
                        child: Text('Aucun collaborateur correspondant.'),
                      )
                    : ListView.separated(
                        shrinkWrap: true,
                        itemCount: filtres.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (_, i) {
                          final m = filtres[i];
                          return ListTile(
                            dense: true,
                            title: Text(m.nomComplet),
                            subtitle: m.posteLibelle != null
                                ? Text(m.posteLibelle!, maxLines: 1, overflow: TextOverflow.ellipsis)
                                : null,
                            trailing: m.matricule != null ? Text(m.matricule!, style: text.bodySmall) : null,
                            onTap: () => setState(() {
                              _delegue = m;
                              _erreurSoumission = null;
                            }),
                          );
                        },
                      ),
              ),
            ],
            const SizedBox(height: 14),
            Text('Période (90 jours max) *', style: text.titleSmall),
            const SizedBox(height: 6),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                key: const Key('delegation-bouton-periode'),
                onPressed: _choisirPeriode,
                icon: const Icon(Icons.date_range_rounded),
                label: Text(
                  _du != null && _au != null
                      ? 'Du ${formatDate(_du!)} au ${formatDate(_au!)} (${nombreJoursPeriode(_du!, _au!)} j)'
                      : 'Choisir les dates de début et de fin',
                ),
              ),
            ),
            if (errDates != null) ...[
              const SizedBox(height: 6),
              Text(
                errDates,
                style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
              ),
            ],
            const SizedBox(height: 14),
            Text('Types de demandes concernés (facultatif)', style: text.titleSmall),
            const SizedBox(height: 4),
            Text(
              'Laissez vide pour déléguer tous vos types de tâches.',
              style: text.bodySmall,
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: [
                for (final code in typesObjetDelegables)
                  FilterChip(
                    label: Text(libelleObjetType(code)),
                    selected: _typesSelectionnes.contains(code),
                    onSelected: (sel) => setState(() {
                      if (sel) {
                        _typesSelectionnes.add(code);
                      } else {
                        _typesSelectionnes.remove(code);
                      }
                    }),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              key: const Key('delegation-motif'),
              controller: _motifCtrl,
              maxLength: 300,
              maxLines: 2,
              onChanged: (_) => setState(() {}),
              decoration: const InputDecoration(
                labelText: 'Motif (facultatif)',
                hintText: 'Ex. : Congés annuels, mission…',
                border: OutlineInputBorder(),
              ),
            ),
            if (_erreurSoumission != null) ...[
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.errorSurface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                ),
                child: Text(
                  _erreurSoumission!,
                  style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
                ),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _envoi ? null : () => Navigator.of(context).pop(false),
                    child: const Text('Annuler'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    key: const Key('delegation-soumettre'),
                    onPressed: (_envoi || !_formulaireValide) ? null : _creer,
                    child: _envoi
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('Créer'),
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

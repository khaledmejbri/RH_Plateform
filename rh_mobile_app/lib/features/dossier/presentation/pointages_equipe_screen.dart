import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../pointage/data/presence_errors.dart';
import '../../pointage/data/presence_models.dart';
import '../../pointage/data/presence_repository.dart';

enum _FiltrePeriode { aujourdhui, septJours, ceMois, personnalisee }

/// « Pointages de l'équipe » (`/equipe/pointages`) : suivi paginé et filtrable des pointages
/// des collaborateurs du périmètre D-H03 du responsable connecté.
class PointagesEquipeScreen extends ConsumerStatefulWidget {
  const PointagesEquipeScreen({super.key});

  @override
  ConsumerState<PointagesEquipeScreen> createState() =>
      _PointagesEquipeScreenState();
}

class _PointagesEquipeScreenState extends ConsumerState<PointagesEquipeScreen> {
  static const int _taillePage = 20;

  final TextEditingController _rechercheCtrl = TextEditingController();
  final ScrollController _scroll = ScrollController();
  Timer? _debounce;

  _FiltrePeriode _filtrePeriode = _FiltrePeriode.septJours;
  late DateTime _du;
  late DateTime _au;
  String _q = '';

  final List<PointageEquipeItem> _items = [];
  int _pageCourante = 0;
  bool _aSuite = false;
  bool _chargementInitial = true;
  bool _chargementSuite = false;
  Object? _erreur;
  int _requeteSeq = 0;

  @override
  void initState() {
    super.initState();
    final auj = aujourdhuiTunis();
    _du = auj.subtract(const Duration(days: 6));
    _au = auj;
    _scroll.addListener(_surDefilement);
    _charger(reinitialiser: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _rechercheCtrl.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _surDefilement() {
    if (!_scroll.hasClients) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 240 &&
        _aSuite &&
        !_chargementSuite &&
        !_chargementInitial) {
      _charger(reinitialiser: false);
    }
  }

  Future<void> _charger({bool reinitialiser = false}) async {
    if (reinitialiser) {
      setState(() {
        _chargementInitial = _items.isEmpty;
        _chargementSuite = false;
        _erreur = null;
      });
    } else {
      if (_chargementSuite || !_aSuite) return;
      setState(() {
        _chargementSuite = true;
      });
    }

    final seq = ++_requeteSeq;
    final ciblePage = reinitialiser ? 0 : _pageCourante + 1;
    final reqDu = formatDateApi(_du);
    final reqAu = formatDateApi(_au);
    final reqQ = _q;

    try {
      final page = await ref.read(presenceRepositoryProvider).pointagesEquipe(
            du: reqDu,
            au: reqAu,
            q: reqQ.isEmpty ? null : reqQ,
            page: ciblePage,
            size: _taillePage,
          );
      if (!mounted || seq != _requeteSeq) return;
      setState(() {
        if (reinitialiser) {
          _items
            ..clear()
            ..addAll(page.elements);
        } else {
          _items.addAll(page.elements);
        }
        _pageCourante = page.page;
        _aSuite = page.aSuite;
        _chargementInitial = false;
        _chargementSuite = false;
        _erreur = null;
      });
    } catch (e) {
      if (!mounted || seq != _requeteSeq) return;
      setState(() {
        _chargementInitial = false;
        _chargementSuite = false;
        _erreur = e;
      });
      if (!reinitialiser || _items.isNotEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(messageErreurPresence(e))),
        );
      }
    }
  }

  void _surRechercheChangee(String valeur) {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      final nettoye = valeur.trim();
      if (nettoye == _q) return;
      _q = nettoye;
      _charger(reinitialiser: true);
    });
  }

  void _effacerRecherche() {
    _debounce?.cancel();
    _rechercheCtrl.clear();
    if (_q.isEmpty) {
      setState(() {});
      return;
    }
    setState(() {
      _q = '';
    });
    _charger(reinitialiser: true);
  }

  void _choisirRaccourci(_FiltrePeriode choix) {
    final auj = aujourdhuiTunis();
    late final DateTime nouveauDu;
    final DateTime nouveauAu = auj;

    switch (choix) {
      case _FiltrePeriode.aujourdhui:
        nouveauDu = auj;
      case _FiltrePeriode.septJours:
        nouveauDu = auj.subtract(const Duration(days: 6));
      case _FiltrePeriode.ceMois:
        final debutMois = DateTime(auj.year, auj.month, 1);
        nouveauDu = nombreJoursPeriode(debutMois, auj) > 31
            ? auj.subtract(const Duration(days: 30))
            : debutMois;
      case _FiltrePeriode.personnalisee:
        _choisirPlagePersonnalisee();
        return;
    }

    setState(() {
      _filtrePeriode = choix;
      _du = nouveauDu;
      _au = nouveauAu;
    });
    _charger(reinitialiser: true);
  }

  Future<void> _choisirPlagePersonnalisee() async {
    final auj = aujourdhuiTunis();
    final plage = await showDateRangePicker(
      context: context,
      locale: Localizations.localeOf(context).languageCode == 'fr'
          ? const Locale('fr', 'FR')
          : null,
      firstDate: DateTime(2024, 1, 1),
      lastDate: auj,
      initialDateRange: DateTimeRange(start: _du, end: _au),
      helpText: 'Sélectionner une période (max 31 jours)',
      cancelText: 'Annuler',
      confirmText: 'Valider',
      saveText: 'Valider',
    );
    if (!mounted || plage == null) return;

    final debut = DateTime(plage.start.year, plage.start.month, plage.start.day);
    final fin = DateTime(plage.end.year, plage.end.month, plage.end.day);
    if (nombreJoursPeriode(debut, fin) > 31) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('La période ne peut pas dépasser 31 jours.'),
        ),
      );
      return;
    }

    setState(() {
      _filtrePeriode = _FiltrePeriode.personnalisee;
      _du = debut;
      _au = fin;
    });
    _charger(reinitialiser: true);
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text("Pointages de l'équipe"),
        actions: [
          IconButton(
            tooltip: 'Actualiser',
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => _charger(reinitialiser: true),
          ),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            color: AppTheme.surface,
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Semantics(
                  label: "Rechercher un membre de l'équipe",
                  child: TextField(
                    key: const Key('equipe-pointages-recherche'),
                    controller: _rechercheCtrl,
                    onChanged: _surRechercheChangee,
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      hintText: 'Rechercher un membre (nom, matricule)…',
                      prefixIcon: const Icon(Icons.search_rounded),
                      suffixIcon: _rechercheCtrl.text.isNotEmpty
                          ? IconButton(
                              key: const Key('equipe-pointages-effacer'),
                              tooltip: 'Effacer la recherche',
                              icon: const Icon(Icons.close_rounded),
                              onPressed: _effacerRecherche,
                            )
                          : null,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      ChoiceChip(
                        key: const Key('periode-aujourdhui'),
                        label: const Text("Aujourd'hui"),
                        selected: _filtrePeriode == _FiltrePeriode.aujourdhui,
                        onSelected: (_) =>
                            _choisirRaccourci(_FiltrePeriode.aujourdhui),
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        key: const Key('periode-7j'),
                        label: const Text('7 jours'),
                        selected: _filtrePeriode == _FiltrePeriode.septJours,
                        onSelected: (_) =>
                            _choisirRaccourci(_FiltrePeriode.septJours),
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        key: const Key('periode-mois'),
                        label: const Text('Ce mois'),
                        selected: _filtrePeriode == _FiltrePeriode.ceMois,
                        onSelected: (_) =>
                            _choisirRaccourci(_FiltrePeriode.ceMois),
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        key: const Key('periode-personnalisee'),
                        label: const Text('Personnalisée'),
                        selected:
                            _filtrePeriode == _FiltrePeriode.personnalisee,
                        onSelected: (_) =>
                            _choisirRaccourci(_FiltrePeriode.personnalisee),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'du ${formatDate(_du)} au ${formatDate(_au)}',
                  key: const Key('equipe-pointages-plage'),
                  style: text.bodySmall?.copyWith(color: AppTheme.textSecondary),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(child: _corps()),
        ],
      ),
    );
  }

  Widget _corps() {
    if (_chargementInitial) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_erreur != null && _items.isEmpty) {
      final accesRefuse = rhEstAccesRefuse(_erreur);
      return RhErrorState(
        message: messageErreurPresence(_erreur!),
        accesRefuse: accesRefuse,
        onRetry: accesRefuse ? null : () => _charger(reinitialiser: true),
      );
    }

    if (_items.isEmpty) {
      final rechercheActive = _q.isNotEmpty;
      return RefreshIndicator(
        onRefresh: () => _charger(reinitialiser: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 48),
            RhEmptyState(
              icon: rechercheActive
                  ? Icons.person_search_rounded
                  : Icons.event_busy_outlined,
              title: rechercheActive
                  ? 'Aucun pointage trouvé dans votre équipe'
                  : 'Aucun pointage sur cette période',
              message: rechercheActive
                  ? 'Aucun membre de votre équipe ne correspond à cette recherche.'
                  : 'Les pointages de votre équipe apparaîtront ici.',
            ),
          ],
        ),
      );
    }

    final afficherPied = _aSuite || _chargementSuite;
    return RefreshIndicator(
      onRefresh: () => _charger(reinitialiser: true),
      child: ListView.separated(
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        itemCount: _items.length + (afficherPied ? 1 : 0),
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
                        label: const Text('Charger plus'),
                      ),
              ),
            );
          }
          final item = _items[index];
          return _PointageEquipeCard(
            item: item,
            onTap: item.collaborateurId.isEmpty
                ? null
                : () => context.push('/equipe/${item.collaborateurId}'),
          );
        },
      ),
    );
  }
}

class _PointageEquipeCard extends StatelessWidget {
  const _PointageEquipeCard({required this.item, required this.onTap});

  final PointageEquipeItem item;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final tone = item.estValide
        ? RhTone.success
        : (item.estRejet ? RhTone.danger : RhTone.neutral);
    final siteTexte = item.siteLibelle ?? item.siteCode;
    final motif = item.estRejet
        ? messageMotifPointage(item.statut, motifServeur: item.motifRejet)
        : null;

    return Semantics(
      button: onTap != null,
      label:
          '${item.collaborateurNom}, ${item.typeAffiche}, ${item.statutAffiche}, ${item.horodatageTunis}',
      child: Material(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radius),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(AppTheme.radius),
          child: Container(
            padding: const EdgeInsets.all(AppTheme.spaceMd),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(AppTheme.radius),
              border: Border.all(color: AppTheme.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.collaborateurNom,
                            style: text.titleSmall,
                          ),
                          if (item.collaborateurMatricule != null &&
                              item.collaborateurMatricule!.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                item.collaborateurMatricule!,
                                style: text.bodySmall?.copyWith(
                                  color: AppTheme.textSecondary,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    const Icon(
                      Icons.chevron_right_rounded,
                      size: 20,
                      color: AppTheme.textTertiary,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    _BadgeTypePointage(
                      label: item.typeAffiche,
                      estSortie: item.estSortie,
                    ),
                    RhStatusChip(
                      label: item.statutAffiche,
                      tone: tone,
                    ),
                  ],
                ),
                if (motif != null && motif.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: AppTheme.errorSurface,
                      borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                    ),
                    child: Text(
                      motif,
                      style: text.bodySmall?.copyWith(color: AppTheme.errorDark),
                    ),
                  ),
                ],
                const SizedBox(height: 8),
                Wrap(
                  spacing: 12,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (siteTexte != null && siteTexte.isNotEmpty)
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.place_outlined,
                            size: 14,
                            color: AppTheme.textSecondary,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            siteTexte,
                            style: text.bodySmall?.copyWith(
                              color: AppTheme.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.schedule_rounded,
                          size: 14,
                          color: AppTheme.textSecondary,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          item.horodatageTunis,
                          style: text.bodySmall?.copyWith(
                            color: AppTheme.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BadgeTypePointage extends StatelessWidget {
  const _BadgeTypePointage({required this.label, required this.estSortie});

  final String label;
  final bool estSortie;

  @override
  Widget build(BuildContext context) {
    final bg = estSortie ? AppTheme.accentSurface : AppTheme.primarySurface;
    final fg = estSortie ? AppTheme.accent : AppTheme.primaryDark;
    final icon = estSortie ? Icons.logout_rounded : Icons.login_rounded;
    return Semantics(
      label: 'Type : $label',
      excludeSemantics: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: fg),
            const SizedBox(width: 4),
            Text(
              label,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(color: fg),
            ),
          ],
        ),
      ),
    );
  }
}

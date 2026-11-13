import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../data/demande_admin_repository.dart';
import '../data/demande_admin_models.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

const int _pageSize = 10;

final demandesAdminListProvider =
    FutureProvider.autoDispose<List<DemandeAdminItem>>((ref) async {
  return ref.watch(demandeAdminRepositoryProvider).mesDemandes();
});

class DemandesAdminListScreen extends ConsumerStatefulWidget {
  const DemandesAdminListScreen({super.key});

  @override
  ConsumerState<DemandesAdminListScreen> createState() =>
      _DemandesAdminListScreenState();
}

class _DemandesAdminListScreenState
    extends ConsumerState<DemandesAdminListScreen>
    with SingleTickerProviderStateMixin {
  int _currentPage = 0;

  /// Filtre par type (titre affiché : « Congé annuel », « Ordre de mission »…) ; null = tous.
  String? _typeFiltre;
  late final TabController _tabController =
      TabController(length: 4, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  static bool _enCours(DemandeAdminItem i) =>
      i.statut == 'EN_VALIDATION_SUPERIEUR' || i.statut == 'EN_VALIDATION_RRH' || i.statut == 'SOUMISE';
  static bool _approuvees(DemandeAdminItem i) => i.statut == 'APPROUVEE';
  static bool _refusees(DemandeAdminItem i) => i.statut == 'REFUSEE';
  static bool _annulees(DemandeAdminItem i) => i.statut == 'ANNULEE';

  /// Compteur d'onglet = nombre de cartes de l'onglet (vide tant que la liste n'est pas chargée).
  static String _compteur(AsyncValue<List<DemandeAdminItem>> async, bool Function(DemandeAdminItem) filtre) {
    final items = async.valueOrNull;
    return items == null ? '' : ' (${items.where(filtre).length})';
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(demandesAdminListProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes demandes'),
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          indicatorColor: AppTheme.primary,
          indicatorWeight: 2,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textSecondary,
          labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
          tabs: [
            Tab(text: 'En cours${_compteur(async, _enCours)}'),
            Tab(text: 'Approuvées${_compteur(async, _approuvees)}'),
            Tab(text: 'Refusées${_compteur(async, _refusees)}'),
            Tab(text: 'Annulées${_compteur(async, _annulees)}'),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => afficherChoixDemande(context),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Nouvelle demande'),
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () {
                  setState(() => _currentPage = 0);
                  ref.invalidate(demandesAdminListProvider);
                }),
        data: (items) {
          final types = items.map((i) => i.titre).toSet().toList()..sort();
          final filtre = types.contains(_typeFiltre) ? _typeFiltre : null;
          bool typeOk(DemandeAdminItem i) => filtre == null || i.titre == filtre;
          final enCours = items.where(_enCours).where(typeOk).toList();
          final approuvees = items.where(_approuvees).where(typeOk).toList();
          final refusees = items.where(_refusees).where(typeOk).toList();
          final annulees = items.where(_annulees).where(typeOk).toList();

          final onglets = TabBarView(
            controller: _tabController,
            children: [
              _DemandeList(items: enCours, onRefresh: () async {
                setState(() => _currentPage = 0);
                ref.invalidate(demandesAdminListProvider);
              }),
              _DemandeList(items: approuvees, onRefresh: () async {
                ref.invalidate(demandesAdminListProvider);
              }),
              _DemandeList(items: refusees, onRefresh: () async {
                ref.invalidate(demandesAdminListProvider);
              }),
              _DemandeList(items: annulees, onRefresh: () async {
                ref.invalidate(demandesAdminListProvider);
              }),
            ],
          );
          if (types.length < 2) return onglets;
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: DropdownButtonFormField<String?>(
                  key: const Key('filtre-type-demande'),
                  value: filtre,
                  decoration: const InputDecoration(labelText: 'Type de demande', isDense: true),
                  items: [
                    const DropdownMenuItem<String?>(value: null, child: Text('Tous les types')),
                    for (final t in types) DropdownMenuItem<String?>(value: t, child: Text(t)),
                  ],
                  onChanged: (v) => setState(() => _typeFiltre = v),
                ),
              ),
              Expanded(child: onglets),
            ],
          );
        },
      ),
    );
  }
}

/// Feuille unique de choix d'une nouvelle demande (congé, sortie, mission, document) ; réutilisée par l'accueil.
/// Avec [avecListe], la première ligne ouvre la liste de mes demandes (tuile « Mes demandes »).
void afficherChoixDemande(BuildContext context, {bool avecListe = false}) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                avecListe ? 'Mes demandes' : 'Nouvelle demande',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              if (avecListe) ...[
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppTheme.surfaceMuted,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(Icons.list_alt_rounded, color: AppTheme.textSecondary),
                  ),
                  title: const Text('Voir mes demandes', style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: const Text('Suivre l’état de mes demandes'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () {
                    Navigator.pop(ctx);
                    context.push('/demandes-admin');
                  },
                ),
                const Divider(height: 8),
              ],
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.accentSurface,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.calendar_month_rounded, color: AppTheme.accent),
                ),
                title: const Text('Congé', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Absence sur plusieurs jours'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/demandes-admin/conge/nouveau');
                },
              ),
              const Divider(height: 8),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.primarySurface,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.access_time_rounded, color: AppTheme.primaryLight),
                ),
                title: const Text('Autorisation de sortie', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Sortie courte (2 h au maximum)'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/demandes-admin/autorisation/nouveau');
                },
              ),
              const Divider(height: 8),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFF7C3AED).withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.business_center_rounded, color: Color(0xFF7C3AED)),
                ),
                title: const Text('Ordre de mission', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Déplacement professionnel'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/demandes-admin/mission/nouveau');
                },
              ),
              const Divider(height: 8),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.successSurface,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.description_rounded, color: AppTheme.success),
                ),
                title: const Text('Document administratif', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Attestation ou autre document'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(ctx);
                  context.push('/documents/nouveau');
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
}

class _DemandeList extends StatelessWidget {
  final List<DemandeAdminItem> items;
  final Future<void> Function() onRefresh;

  const _DemandeList({required this.items, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const RhEmptyState(
        icon: Icons.assignment_outlined,
        title: 'Aucune demande',
        message: 'Vos demandes apparaîtront ici.',
      );
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      color: AppTheme.primary,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (_, i) => _DemandeCard(d: items[i]),
      ),
    );
  }
}

class _DemandeCard extends StatelessWidget {
  final DemandeAdminItem d;
  const _DemandeCard({required this.d});

  @override
  Widget build(BuildContext context) {
    final (statusLabelBrut, statusBg, statusFg, iconBg, iconColor, icon) =
        _statusMeta(d.statut);
    final statusLabel = d.annuleeApresApprobation ? 'Annulée après approbation' : statusLabelBrut;

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: () => context.push('/demandes-admin/${d.id}'),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border, width: 0.5),
          ),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: iconBg,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(icon, color: iconColor, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          d.titre,
                          style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                              color: AppTheme.textPrimary),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          d.isAutorisationSortie
                              ? '${formatDateIso(d.periodeDebut)} · ${d.heureDebut ?? "--:--"} → ${d.heureFin ?? "--:--"}'
                              : formatPeriode(d.periodeDebut, d.periodeFin),
                          style: const TextStyle(
                              fontSize: 12, color: AppTheme.textSecondary),
                        ),
                        if (_sousTitre(d) != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            _sousTitre(d)!,
                            style: const TextStyle(
                                fontSize: 12, color: AppTheme.textSecondary),
                          ),
                        ],
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusBg,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(statusLabel,
                        style: TextStyle(
                            color: statusFg,
                            fontSize: 11,
                            fontWeight: FontWeight.w600)),
                  ),
                ],
              ),
              if (d.motifRefus != null && d.motifRefus!.isNotEmpty) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.errorSurface,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline_rounded,
                          size: 14, color: AppTheme.error),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          d.motifRefus!,
                          style: const TextStyle(
                              color: AppTheme.error,
                              fontSize: 12,
                              fontStyle: FontStyle.italic),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// « DEM-202611-0003 · déposée le 06/11/2026 », puis « Validation par Omar Belhadj » si connu.
  static String? _sousTitre(DemandeAdminItem d) {
    final parts = <String>[
      if (d.joursDecomptes != null) 'Jours décomptés : ${formatJours(d.joursDecomptes!)}',
      if (d.referenceAffichee != null) d.referenceAffichee!,
      if (d.creeLe != null) 'déposée le ${formatDateIso(d.creeLe)}',
    ];
    final enValidation = d.statut == 'EN_VALIDATION_SUPERIEUR' ||
        d.statut == 'EN_VALIDATION_RRH' ||
        d.statut == 'SOUMISE';
    if (enValidation && (d.valideurNom ?? '').trim().isNotEmpty) {
      parts.add('Validation par ${d.valideurNom!.trim()}');
    }
    return parts.isEmpty ? null : parts.join(' · ');
  }

  (String, Color, Color, Color, Color, IconData) _statusMeta(String statut) {
    if (statut == 'EN_VALIDATION_SUPERIEUR') {
      return (
        d.statutLibelle,
        AppTheme.warningSurface,
        AppTheme.warning,
        AppTheme.accentSurface,
        AppTheme.accent,
        Icons.supervisor_account_rounded,
      );
    } else if (statut == 'EN_VALIDATION_RRH' || statut == 'SOUMISE') {
      return (
        d.statutLibelle,
        AppTheme.primarySurface,
        AppTheme.primaryDark,
        AppTheme.primarySurface,
        AppTheme.primary,
        Icons.hourglass_top_rounded,
      );
    } else if (statut == 'APPROUVEE') {
      return (
        d.statutLibelle,
        AppTheme.successSurface,
        AppTheme.success,
        AppTheme.successSurface,
        AppTheme.success,
        Icons.check_circle_outline_rounded,
      );
    } else if (statut == 'ANNULEE') {
      return (
        d.statutLibelle,
        AppTheme.surfaceMuted,
        AppTheme.textSecondary,
        AppTheme.background,
        AppTheme.textTertiary,
        Icons.cancel_outlined,
      );
    } else {
      return (
        d.statutLibelle,
        AppTheme.errorSurface,
        AppTheme.errorDark,
        AppTheme.errorSurface,
        AppTheme.error,
        Icons.cancel_outlined,
      );
    }
  }
}

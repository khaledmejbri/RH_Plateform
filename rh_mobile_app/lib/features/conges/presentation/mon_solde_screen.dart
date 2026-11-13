import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../data/solde_models.dart';
import '../providers/solde_providers.dart';

/// « Mon solde de congé » : disponible en grand, acquis et réservé, puis l'historique paginé des mouvements.
class MonSoldeScreen extends ConsumerStatefulWidget {
  const MonSoldeScreen({super.key});

  @override
  ConsumerState<MonSoldeScreen> createState() => _MonSoldeScreenState();
}

class _MonSoldeScreenState extends ConsumerState<MonSoldeScreen> {
  final ScrollController _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 200) {
        ref.read(mouvementsProvider.notifier).chargerSuite();
      }
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _actualiser() async {
    ref.invalidate(monSoldeProvider);
    await ref.read(mouvementsProvider.notifier).recharger();
  }

  @override
  Widget build(BuildContext context) {
    final solde = ref.watch(monSoldeProvider);
    final mvts = ref.watch(mouvementsProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Mon solde de congé')),
      body: RefreshIndicator(
        onRefresh: _actualiser,
        child: ListView(
          controller: _scroll,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            solde.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 32),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(monSoldeProvider)),
              data: (s) => CarteSoldeDetail(solde: s),
            ),
            const SizedBox(height: 20),
            const Text('Mouvements', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            _FiltreType(
              type: mvts.type,
              onChanged: (t) => ref.read(mouvementsProvider.notifier).filtrer(t),
            ),
            const SizedBox(height: 8),
            ..._liste(mvts),
          ],
        ),
      ),
    );
  }

  List<Widget> _liste(MouvementsEtat e) {
    final widgets = <Widget>[
      for (final m in e.mouvements) LigneMouvement(mouvement: m),
    ];
    if (e.erreur != null) {
      widgets.add(RhErrorState.depuis(e.erreur, onRetry: () => ref.read(mouvementsProvider.notifier).chargerSuite()));
    } else if (e.chargement) {
      widgets.add(const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(child: CircularProgressIndicator()),
      ));
    } else if (e.vide) {
      widgets.add(const RhEmptyState(
        icon: Icons.history_outlined,
        title: 'Aucun mouvement',
        message: 'Les acquisitions, congés pris et ajustements apparaîtront ici.',
      ));
    }
    return widgets;
  }
}

/// Carte du solde : disponible en grand, acquis et réservé dessous.
class CarteSoldeDetail extends StatelessWidget {
  const CarteSoldeDetail({super.key, required this.solde});

  final SoldeConge solde;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Solde disponible', style: TextStyle(fontSize: 13, color: AppTheme.textSecondary)),
          const SizedBox(height: 4),
          Text(
            formatJours(solde.disponible),
            key: const Key('solde-disponible'),
            style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: AppTheme.primary),
          ),
          const Divider(height: 24),
          Row(
            children: [
              Expanded(child: _Chiffre(libelle: 'Solde acquis', valeur: formatJours(solde.acquis))),
              Expanded(child: _Chiffre(libelle: 'Réservé (demandes en cours)', valeur: formatJours(solde.reserve))),
            ],
          ),
        ],
      ),
    );
  }
}

class _Chiffre extends StatelessWidget {
  const _Chiffre({required this.libelle, required this.valeur});

  final String libelle;
  final String valeur;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
        const SizedBox(height: 2),
        Text(valeur, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
      ],
    );
  }
}

class _FiltreType extends StatelessWidget {
  const _FiltreType({required this.type, required this.onChanged});

  final String? type;
  final ValueChanged<String?> onChanged;

  static const Map<String, String> _libelles = {
    TypeMouvement.acquisition: 'Acquisitions',
    TypeMouvement.congePris: 'Congés pris',
    TypeMouvement.ajustement: 'Ajustements',
    TypeMouvement.recredit: 'Recrédits',
  };

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children: [
        ChoiceChip(label: const Text('Tous'), selected: type == null, onSelected: (_) => onChanged(null)),
        for (final t in TypeMouvement.tous)
          ChoiceChip(label: Text(_libelles[t] ?? t), selected: type == t, onSelected: (_) => onChanged(t)),
      ],
    );
  }
}

/// Une ligne de mouvement : intitulé, détails, date, quantité signée et solde après ; le numéro ouvre la demande.
class LigneMouvement extends StatelessWidget {
  const LigneMouvement({super.key, required this.mouvement});

  final MouvementSolde mouvement;

  @override
  Widget build(BuildContext context) {
    final m = mouvement;
    final positif = m.nombre > 0;
    final couleur = m.nombre == 0 ? AppTheme.textSecondary : (positif ? AppTheme.success : AppTheme.errorDark);
    final ouvrable = m.demandeId != null;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      color: AppTheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppTheme.border, width: 0.5),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: ouvrable ? () => context.push('/demandes-admin/${m.demandeId}') : null,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(m.intitule, style: const TextStyle(fontWeight: FontWeight.w600)),
                    if (m.details != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(m.details!, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                      ),
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        formatDateHeureIso(m.creeLe),
                        style: const TextStyle(fontSize: 11, color: AppTheme.textTertiary),
                      ),
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(m.nombreAffiche, style: TextStyle(fontWeight: FontWeight.w700, color: couleur)),
                  if (m.soldeApres != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        'Solde : ${formatJours(m.soldeApres!)}',
                        style: const TextStyle(fontSize: 11, color: AppTheme.textSecondary),
                      ),
                    ),
                ],
              ),
              if (ouvrable) const Icon(Icons.chevron_right_rounded, size: 18, color: AppTheme.textSecondary),
            ],
          ),
        ),
      ),
    );
  }
}

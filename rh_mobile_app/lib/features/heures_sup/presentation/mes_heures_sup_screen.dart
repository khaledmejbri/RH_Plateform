import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/heures_sup_models.dart';
import '../providers/heures_sup_providers.dart';
import 'heures_sup_widgets.dart';

/// « Mes heures sup » du collaborateur : validées à venir + heures comptées (D-P16.b).
class MesHeuresSupScreen extends ConsumerWidget {
  const MesHeuresSupScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    Future<void> rafraichir() async {
      ref.invalidate(mesHeuresSupProvider);
      try {
        await ref.read(mesHeuresSupProvider.future);
      } catch (_) {
        // Affiché par la vue.
      }
    }

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Mes heures sup')),
      body: HeuresSupAsyncView<MesHeuresSup>(
        value: ref.watch(mesHeuresSupProvider),
        onRefresh: rafraichir,
        estVide: (d) => d.aVenir.isEmpty && d.comptees.isEmpty,
        messageVide: 'Aucune heure sup validée ou comptée pour le moment.',
        builder: (context, data) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            const _Titre('Validées à venir'),
            if (data.aVenir.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Text('Aucune heure sup à venir.', style: TextStyle(color: AppTheme.textSecondary)),
              ),
            for (final d in data.aVenir) _CarteAVenir(demande: d),
            const SizedBox(height: 16),
            const _Titre('Heures comptées'),
            if (data.comptees.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Text('Aucune heure comptée.', style: TextStyle(color: AppTheme.textSecondary)),
              ),
            for (final c in data.comptees) LigneHeuresComptees(ligne: c, afficherNom: false),
          ],
        ),
      ),
    );
  }
}

class _Titre extends StatelessWidget {
  const _Titre(this.texte);
  final String texte;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(texte, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
      );
}

class _CarteAVenir extends StatelessWidget {
  const _CarteAVenir({required this.demande});
  final HeuresSupAVenir demande;

  @override
  Widget build(BuildContext context) {
    final d = demande;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: const Icon(Icons.more_time_rounded, color: AppTheme.primary),
        title: Text(
          [
            if (d.journeeLibelle.isNotEmpty) d.journeeLibelle,
            if (d.siteLibelle != null && d.siteLibelle!.isNotEmpty) d.siteLibelle!,
          ].join(' · '),
        ),
        subtitle: d.ouverturesLibelle.isEmpty ? null : Text('Ouverture : ${d.ouverturesLibelle}'),
      ),
    );
  }
}

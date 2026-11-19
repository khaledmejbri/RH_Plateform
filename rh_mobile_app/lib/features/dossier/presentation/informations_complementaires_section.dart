import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../data/champs_perso_models.dart';
import '../providers/dossier_providers.dart';
import 'dossier_widgets.dart';

/// Section « Informations complémentaires » de « Mon dossier » (T12) : lecture seule, uniquement les champs
/// que le serveur renvoie. Absente si la liste est vide ; une panne n'empêche pas le reste du dossier.
class InformationsComplementairesSection extends ConsumerWidget {
  const InformationsComplementairesSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(mesChampsPersoProvider);
    return async.when(
      loading: () => const SizedBox.shrink(),
      error: (e, _) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: SectionDossier(
          titre: 'Informations complémentaires',
          children: [
            Text(rhErrorMessage(e), style: const TextStyle(color: AppTheme.textSecondary)),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: () => ref.invalidate(mesChampsPersoProvider),
              child: const Text('Réessayer'),
            ),
          ],
        ),
      ),
      data: (champs) => champs.isEmpty
          ? const SizedBox.shrink()
          : Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: ContenuInformationsComplementaires(champs: champs),
            ),
    );
  }
}

/// Carte des champs (séparée pour être testée sans réseau).
class ContenuInformationsComplementaires extends StatelessWidget {
  const ContenuInformationsComplementaires({super.key, required this.champs});

  final List<ChampPersoAffiche> champs;

  @override
  Widget build(BuildContext context) {
    return SectionDossier(
      titre: 'Informations complémentaires',
      children: [
        for (final c in champs)
          Semantics(
            container: true,
            label: '${c.libelle} : ${c.valeurAffichee ?? 'non renseigné'}',
            excludeSemantics: true,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(c.libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  const SizedBox(height: 2),
                  Text(
                    c.valeurAffichee ?? '—',
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

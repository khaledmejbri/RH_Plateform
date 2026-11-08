import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../providers/dossier_providers.dart';
import 'dossier_widgets.dart';

/// Fiche d'un collègue (A8) : nom, poste, rôle, unité, responsable et courriel. Ni présence ni demandes.
class FicheAnnuaireScreen extends ConsumerWidget {
  const FicheAnnuaireScreen({super.key, required this.collaborateurId});

  final String collaborateurId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(annuaireProvider(collaborateurId));
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: Text(async.valueOrNull?.nomComplet ?? 'Fiche collègue'),
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => etatErreurFiche(e, () => ref.invalidate(annuaireProvider(collaborateurId))),
        data: (f) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            SectionDossier(
              titre: f.nomComplet,
              children: [
                LigneInfo(libelle: 'Poste', valeur: f.poste),
                LigneInfo(libelle: 'Rôle', valeur: f.intituleRole),
                LigneInfo(libelle: 'Unité', valeur: f.uniteLibelle),
                LigneInfo(libelle: 'Courriel', valeur: f.courriel),
                if (f.responsableNom != null) ...[
                  const SizedBox(height: 4),
                  const Text('Responsable',
                      style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  LignePersonne(
                    nom: f.responsableNom!,
                    onTap: (f.responsableId == null)
                        ? null
                        : () => ouvrirPersonne(context, ref, f.responsableId!),
                  ),
                ] else
                  const LigneInfo(libelle: 'Responsable', valeur: null),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

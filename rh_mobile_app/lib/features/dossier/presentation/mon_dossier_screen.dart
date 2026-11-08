import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/rh_ui.dart';
import '../../auth/providers/collaborateur_notifier.dart';
import '../data/dossier_models.dart';
import '../providers/dossier_providers.dart';
import 'dossier_widgets.dart';

/// « Mon dossier » : mon identité, ma place dans l'organisation, mon valideur et les personnes que j'encadre
/// (A7). Aucun compteur n'est écrit en dur : tout vient du serveur.
class MonDossierScreen extends ConsumerWidget {
  const MonDossierScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final moi = ref.watch(collaborateurNotifierProvider).valueOrNull;
    final async = ref.watch(monDossierProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Mon dossier')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(e, onRetry: () => ref.invalidate(monDossierProvider)),
        data: (h) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(monDossierProvider),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              SectionDossier(
                titre: 'Mon identité',
                children: [
                  LigneInfo(libelle: 'Nom', valeur: moi?.nomComplet ?? h.collaborateur?.nomComplet),
                  LigneInfo(libelle: 'Matricule', valeur: moi?.matricule ?? h.collaborateur?.matricule),
                  LigneInfo(libelle: 'Poste', valeur: moi?.posteLibelle ?? h.collaborateur?.fonction),
                  LigneInfo(libelle: 'Unité', valeur: moi?.uniteLibelle ?? h.collaborateur?.uniteLibelle),
                  LigneInfo(libelle: 'Courriel', valeur: moi?.email),
                ],
              ),
              const SizedBox(height: 12),
              SectionDossier(
                titre: 'Ma place dans l’organisation',
                children: [
                  if (h.chemin.isEmpty)
                    const Text('Aucun rattachement.')
                  else
                    for (var i = 0; i < h.chemin.length; i++)
                      Padding(
                        padding: EdgeInsets.only(left: i * 12.0, bottom: 4),
                        child: Text(
                          [h.chemin[i].libelle, h.chemin[i].typeLibelle]
                              .whereType<String>()
                              .join(' · '),
                        ),
                      ),
                  const Divider(height: 20),
                  _PersonneOuVide(
                    libelle: 'Responsable direct',
                    personne: h.responsableDirect,
                    vide: 'Aucun responsable direct.',
                  ),
                  const SizedBox(height: 8),
                  const Text('Mes demandes sont validées par',
                      style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  if (h.valideur == null)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(h.valideurAffiche, style: const TextStyle(fontWeight: FontWeight.w600)),
                    )
                  else
                    _LienPersonne(personne: h.valideur!),
                ],
              ),
              const SizedBox(height: 12),
              SectionDossier(
                titre: 'Personnes que j’encadre',
                children: [
                  if (h.collaborateursEncadres.isEmpty)
                    const Text('Vous n’encadrez personne.')
                  else
                    for (final p in h.collaborateursEncadres) _LienPersonne(personne: p),
                ],
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => context.push('/pointage/mes-pointages'),
                icon: const Icon(Icons.schedule_rounded),
                label: const Text('Mes pointages'),
                style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PersonneOuVide extends StatelessWidget {
  const _PersonneOuVide({required this.libelle, required this.personne, required this.vide});

  final String libelle;
  final PersonneResume? personne;
  final String vide;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
        if (personne == null)
          Padding(padding: const EdgeInsets.only(top: 4), child: Text(vide))
        else
          _LienPersonne(personne: personne!),
      ],
    );
  }
}

class _LienPersonne extends ConsumerWidget {
  const _LienPersonne({required this.personne});

  final PersonneResume personne;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return LignePersonne(
      nom: personne.nomComplet,
      sousTitre: personne.sousTitre,
      onTap: personne.id.isEmpty ? null : () => ouvrirPersonne(context, ref, personne.id),
    );
  }
}

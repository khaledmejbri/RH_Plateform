import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/workflow_models.dart';
import '../data/workflow_repository.dart';

/// Compteur temps réel de tâches à traiter / en retard sur le circuit paramétré.
final compteurTachesProvider = FutureProvider.autoDispose<CompteurTaches>((ref) {
  return ref.watch(workflowRepositoryProvider).compteurMesTaches();
});

/// Types et sous-types d'objets ouverts à la saisie (`null` = repli tous ouverts).
final typesOuvertsProvider = FutureProvider.autoDispose<List<TypeOuvert>?>((ref) {
  return ref.watch(workflowRepositoryProvider).typesOuverts();
});

/// Suivi workflow d'un objet métier (`objetType`, `objetId`).
final suiviObjetProvider = FutureProvider.autoDispose
    .family<SuiviInstance, ({String objetType, String objetId})>((ref, args) {
  return ref.watch(workflowRepositoryProvider).suiviParObjet(args.objetType, args.objetId);
});

/// Détail d'une tâche workflow par identifiant.
final tacheDetailProvider = FutureProvider.autoDispose.family<TacheDetail, String>((ref, id) {
  return ref.watch(workflowRepositoryProvider).detailTache(id);
});

/// Délégations de l'utilisateur courant par rôle (`'DELEGANT'` ou `'DELEGUE'`).
final delegationsMoiProvider =
    FutureProvider.autoDispose.family<List<DelegationItem>, String>((ref, role) {
  return ref.watch(workflowRepositoryProvider).delegationsMoi(role: role);
});

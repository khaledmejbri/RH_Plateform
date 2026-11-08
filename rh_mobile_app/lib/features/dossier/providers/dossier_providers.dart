import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/widgets/rh_ui.dart';
import '../../pointage/data/journee_models.dart';
import '../../pointage/data/presence_repository.dart';
import '../data/dossier_models.dart';
import '../data/dossier_repository.dart';

final monDossierProvider = FutureProvider.autoDispose<Hierarchie>((ref) {
  return ref.watch(dossierRepositoryProvider).maHierarchie();
});

/// Collaborateurs dont l'appelant est le valideur (A9).
final monEquipeProvider = FutureProvider.autoDispose<List<PersonneResume>>((ref) {
  return ref.watch(dossierRepositoryProvider).monEquipe();
});

final annuaireProvider = FutureProvider.autoDispose.family<FicheAnnuaire, String>((ref, id) {
  return ref.watch(dossierRepositoryProvider).annuaire(id);
});

final ficheEquipeProvider = FutureProvider.autoDispose.family<FicheEquipe, String>((ref, id) {
  return ref.watch(dossierRepositoryProvider).ficheEquipe(id);
});

final presenceEquipeProvider = FutureProvider.autoDispose.family<PresenceEquipe, String>((ref, id) {
  return ref.watch(presenceRepositoryProvider).presenceEquipe(id);
});

/// Ouvre la bonne fiche pour une personne : équipe (présence, retards) si l'appelant en est le valideur,
/// sinon annuaire. Si la liste d'équipe est indisponible, l'annuaire sert de repli.
Future<void> ouvrirPersonne(BuildContext context, WidgetRef ref, String personneId) async {
  if (personneId.isEmpty) return;
  var ids = <String>{};
  try {
    final equipe = await ref.read(monEquipeProvider.future);
    ids = equipe.map((p) => p.id).toSet();
  } catch (_) {
    // Repli annuaire.
  }
  if (!context.mounted) return;
  context.push(routePourPersonne(personneId, ids));
}

/// Message d'erreur d'une fiche : 403 « pas d'accès », 404 « introuvable », sinon message générique.
String messageErreurFiche(Object? error) {
  if (error is DioException) {
    final code = error.response?.statusCode;
    if (code == 403) return 'Vous n’avez pas accès à cette fiche.';
    if (code == 404) return 'Élément introuvable.';
  }
  return rhErrorMessage(error);
}

/// État d'erreur d'une fiche : « Réessayer » seulement pour une panne réseau ou serveur (403 / 404 : inutile).
Widget etatErreurFiche(Object? error, VoidCallback onRetry) {
  final code = error is DioException ? error.response?.statusCode : null;
  final definitif = code == 403 || code == 404;
  return RhErrorState(
    message: messageErreurFiche(error),
    accesRefuse: code == 403,
    onRetry: definitif ? null : onRetry,
  );
}

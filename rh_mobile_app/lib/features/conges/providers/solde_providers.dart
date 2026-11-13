import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/solde_models.dart';
import '../data/solde_repository.dart';

/// Mon solde (A1) : une erreur réseau reste une erreur, jamais « 0 ».
final monSoldeProvider = FutureProvider.autoDispose<SoldeConge>((ref) {
  return ref.watch(soldeRepositoryProvider).monSolde();
});

/// État de la liste paginée des mouvements.
class MouvementsEtat {
  const MouvementsEtat({
    this.mouvements = const [],
    this.page = -1,
    this.aSuite = true,
    this.chargement = false,
    this.erreur,
    this.type,
  });

  final List<MouvementSolde> mouvements;
  final int page;
  final bool aSuite;
  final bool chargement;
  final Object? erreur;
  final String? type;

  /// Première page lue, aucun mouvement.
  bool get vide => mouvements.isEmpty && !chargement && erreur == null && !aSuite;

  MouvementsEtat copie({
    List<MouvementSolde>? mouvements,
    int? page,
    bool? aSuite,
    bool? chargement,
    Object? erreur,
    bool effacerErreur = false,
  }) =>
      MouvementsEtat(
        mouvements: mouvements ?? this.mouvements,
        page: page ?? this.page,
        aSuite: aSuite ?? this.aSuite,
        chargement: chargement ?? this.chargement,
        erreur: effacerErreur ? null : (erreur ?? this.erreur),
        type: type,
      );
}

class MouvementsNotifier extends AutoDisposeNotifier<MouvementsEtat> {
  static const int taille = 20;

  @override
  MouvementsEtat build() {
    Future.microtask(chargerSuite);
    return const MouvementsEtat();
  }

  /// Charge la page suivante ; sans effet pendant un chargement ou quand tout est lu.
  Future<void> chargerSuite() async {
    final s = state;
    if (s.chargement || !s.aSuite) return;
    final type = s.type;
    state = s.copie(chargement: true, effacerErreur: true);
    try {
      final page = await ref.read(soldeRepositoryProvider).mesMouvements(page: s.page + 1, taille: taille, type: type);
      // Le filtre a changé pendant l'appel : réponse périmée, ignorée.
      if (state.type != type) return;
      state = state.copie(
        mouvements: [...s.mouvements, ...page.contenu],
        page: page.page,
        aSuite: page.aSuite,
        chargement: false,
      );
    } catch (e) {
      if (state.type != type) return;
      state = state.copie(chargement: false, erreur: e);
    }
  }

  /// Applique (ou retire) le filtre par type et recharge depuis la première page.
  Future<void> filtrer(String? type) async {
    state = MouvementsEtat(type: type);
    await chargerSuite();
  }

  Future<void> recharger() => filtrer(state.type);
}

final mouvementsProvider = NotifierProvider.autoDispose<MouvementsNotifier, MouvementsEtat>(MouvementsNotifier.new);

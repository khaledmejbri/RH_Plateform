import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/notifications/notification_model.dart';
import '../../core/notifications/notification_repository.dart';

/// Taille d'une page de « Mes notifications » (D-NOT01).
const int tailleListeNotifications = 20;

/// État de la liste paginée « Mes notifications ».
class ListeNotificationsState {
  final List<AppNotification> items;

  /// `true` : non lues seulement ; `false` : toutes.
  final bool nonLuesSeulement;
  final int page;
  final bool aSuite;
  final bool chargement;
  final bool chargementSuite;

  /// Erreur du premier chargement ; l'erreur d'une page suivante n'efface pas la liste.
  final Object? erreur;
  final Object? erreurSuite;

  const ListeNotificationsState({
    this.items = const [],
    this.nonLuesSeulement = false,
    this.page = 0,
    this.aSuite = false,
    this.chargement = true,
    this.chargementSuite = false,
    this.erreur,
    this.erreurSuite,
  });

  ListeNotificationsState copyWith({
    List<AppNotification>? items,
    bool? nonLuesSeulement,
    int? page,
    bool? aSuite,
    bool? chargement,
    bool? chargementSuite,
    Object? erreur,
    Object? erreurSuite,
    bool effacerErreur = false,
    bool effacerErreurSuite = false,
  }) {
    return ListeNotificationsState(
      items: items ?? this.items,
      nonLuesSeulement: nonLuesSeulement ?? this.nonLuesSeulement,
      page: page ?? this.page,
      aSuite: aSuite ?? this.aSuite,
      chargement: chargement ?? this.chargement,
      chargementSuite: chargementSuite ?? this.chargementSuite,
      erreur: effacerErreur ? null : (erreur ?? this.erreur),
      erreurSuite: effacerErreurSuite ? null : (erreurSuite ?? this.erreurSuite),
    );
  }
}

class ListeNotificationsNotifier extends StateNotifier<ListeNotificationsState> {
  ListeNotificationsNotifier(this._repository) : super(const ListeNotificationsState()) {
    charger();
  }

  final NotificationRepository _repository;

  /// (Re)charge la première page avec le filtre courant.
  Future<void> charger() async {
    state = state.copyWith(chargement: true, effacerErreur: true, effacerErreurSuite: true);
    try {
      final page = await _repository.lister(
        lu: state.nonLuesSeulement ? false : null,
        page: 0,
        taille: tailleListeNotifications,
      );
      if (!mounted) return;
      state = state.copyWith(
        items: page.contenu,
        page: 0,
        aSuite: page.aSuite,
        chargement: false,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(chargement: false, erreur: e, items: const []);
    }
  }

  /// Bascule entre « Toutes » et « Non lues » et recharge.
  Future<void> choisirFiltre({required bool nonLuesSeulement}) async {
    if (state.nonLuesSeulement == nonLuesSeulement) return;
    state = state.copyWith(nonLuesSeulement: nonLuesSeulement);
    await charger();
  }

  /// Charge la page suivante (20 de plus).
  Future<void> chargerSuite() async {
    if (!state.aSuite || state.chargement || state.chargementSuite) return;
    state = state.copyWith(chargementSuite: true, effacerErreurSuite: true);
    try {
      final suivante = state.page + 1;
      final page = await _repository.lister(
        lu: state.nonLuesSeulement ? false : null,
        page: suivante,
        taille: tailleListeNotifications,
      );
      if (!mounted) return;
      state = state.copyWith(
        items: fusionnerNotifications(state.items, page.contenu),
        page: suivante,
        aSuite: page.aSuite,
        chargementSuite: false,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(chargementSuite: false, erreurSuite: e);
    }
  }

  /// Ajoute en tête une notification reçue en temps réel (sans doublon).
  void inserer(AppNotification notification) {
    if (state.items.any((n) => n.id == notification.id)) return;
    state = state.copyWith(items: [notification, ...state.items]);
  }

  /// Reflète localement qu'une notification est lue (après succès côté serveur). Dans le filtre
  /// « Non lues », elle reste affichée jusqu'au prochain rechargement pour ne pas faire sauter la liste.
  void marquerLueLocalement(String id) {
    state = state.copyWith(
      items: [for (final n in state.items) n.id == id ? n.copyWith(isRead: true) : n],
    );
  }

  /// Toutes lues : dans « Non lues » la liste devient vide, sinon tout passe en lu.
  void toutesLuesLocalement() {
    state = state.copyWith(
      items: state.nonLuesSeulement
          ? const []
          : [for (final n in state.items) n.copyWith(isRead: true)],
      aSuite: state.nonLuesSeulement ? false : state.aSuite,
    );
  }
}

/// Ajoute à [existantes] les éléments de [nouvelles] absents (par identifiant), en gardant l'ordre.
List<AppNotification> fusionnerNotifications(
  List<AppNotification> existantes,
  List<AppNotification> nouvelles,
) {
  final ids = existantes.map((n) => n.id).toSet();
  return [
    ...existantes,
    for (final n in nouvelles)
      if (ids.add(n.id)) n,
  ];
}

final listeNotificationsProvider =
    StateNotifierProvider.autoDispose<ListeNotificationsNotifier, ListeNotificationsState>((ref) {
  return ListeNotificationsNotifier(ref.watch(notificationRepositoryProvider));
});

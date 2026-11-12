import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../network/api_client.dart';
import 'libelles.dart';

/// Les trois services qui servent un `GET libelles` (ADR lot 3, § 7). Un seul appel par service.
enum ServiceParametres {
  referentiel('/api/referentiel/v1/parametres/libelles'),
  evaluation('/api/rh/v1/evaluations/parametres/libelles'),
  presence('/api/rh/v1/presence/parametres/libelles');

  const ServiceParametres(this.chemin);
  final String chemin;
}

/// Charge les libellés paramétrés dans [Libelles] (D-R02.d, D-R02.j).
///
/// - Lecture des valeurs actives seulement (`actifs=true`), jamais de liste de secours.
/// - Revalidation par ETag : un `304` garde les valeurs déjà chargées.
/// - [rafraichir] propage l'erreur (les formulaires affichent « Réessayer »).
///   [rafraichirSilencieux] l'avale (démarrage, minuterie) : les libellés restent alors ceux déjà chargés.
class LibellesService {
  LibellesService(this._dio, {this.intervalle = const Duration(seconds: 60)});

  final Dio _dio;

  /// Période de revalidation en arrière-plan (D-R02.j : prise en compte en moins d'une minute).
  final Duration intervalle;

  final Map<ServiceParametres, String> _etags = {};
  final Map<ServiceParametres, Future<void>> _enCours = {};
  Timer? _minuterie;

  /// Relit les services demandés (tous par défaut). Lève l'erreur du premier échec.
  Future<void> rafraichir([Set<ServiceParametres>? services]) async {
    final cibles = services ?? ServiceParametres.values.toSet();
    await Future.wait([for (final s in cibles) _lire(s)]);
  }

  /// Comme [rafraichir] sans lever d'erreur ; retourne vrai si tout est à jour.
  Future<bool> rafraichirSilencieux([Set<ServiceParametres>? services]) async {
    try {
      await rafraichir(services);
      return true;
    } catch (e) {
      debugPrint('[LIBELLES] lecture impossible : $e');
      return false;
    }
  }

  /// Charge tout puis relit toutes les [intervalle] jusqu'à [arreter].
  Future<void> demarrer() async {
    _minuterie?.cancel();
    _minuterie = Timer.periodic(intervalle, (_) => rafraichirSilencieux());
    await rafraichirSilencieux();
  }

  /// Arrête la relecture et oublie les valeurs (déconnexion).
  void arreter() {
    _minuterie?.cancel();
    _minuterie = null;
    _etags.clear();
    Libelles.reinitialiser();
  }

  Future<void> _lire(ServiceParametres service) {
    // Une lecture à la fois par service : un second appel attend le premier.
    return _enCours[service] ??= _lireUneFois(service).whenComplete(() {
      _enCours.remove(service);
    });
  }

  Future<void> _lireUneFois(ServiceParametres service) async {
    final etag = _etags[service];
    final res = await _dio.get<Object?>(
      service.chemin,
      queryParameters: const {'actifs': true},
      options: Options(
        headers: etag == null ? null : {'If-None-Match': etag},
        validateStatus: (code) => code != null && ((code >= 200 && code < 300) || code == 304),
      ),
    );
    if (res.statusCode == 304) {
      if (etag == null) throw const FormatException('Réponse 304 sans version connue.');
      return; // valeurs inchangées
    }
    final reponse = ParametresReponse.depuisJson(res.data);
    Libelles.charger(reponse.referentiels);
    final nouvelle = res.headers.value('etag') ?? reponse.version;
    if (nouvelle != null && nouvelle.isNotEmpty) {
      _etags[service] = nouvelle;
    } else {
      _etags.remove(service);
    }
  }
}

final libellesServiceProvider = Provider<LibellesService>((ref) {
  final service = LibellesService(ref.watch(dioProvider));
  ref.onDispose(service.arreter);
  return service;
});

/// Ouverture du formulaire de congé : relit le référentiel et renvoie les types ACTIFS.
/// Échec (réseau coupé, serveur arrêté) : l'erreur est propagée, aucune valeur n'est proposée.
final typesCongeActifsProvider = FutureProvider.autoDispose<List<ParametreValeur>>((ref) async {
  await ref.read(libellesServiceProvider).rafraichir({ServiceParametres.referentiel});
  return Libelles.valeurs(Referentiel.typeConge);
});

/// Ouverture du formulaire de document : relit le référentiel et renvoie les types ACTIFS.
final typesDocumentActifsProvider = FutureProvider.autoDispose<List<ParametreValeur>>((ref) async {
  await ref.read(libellesServiceProvider).rafraichir({ServiceParametres.referentiel});
  return Libelles.valeurs(Referentiel.typeDocument);
});

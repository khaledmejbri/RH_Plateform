import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/core/libelles/libelles_service.dart';

import '../../support/libelles_test_data.dart';

/// Adaptateur Dio factice : sert les trois `GET libelles`, gère l'ETag, ou simule une panne réseau.
class _Serveur implements HttpClientAdapter {
  bool reseauCoupe = false;
  Map<String, dynamic> referentiel = libellesReferentielJson();
  final List<RequestOptions> requetes = [];

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requetes.add(options);
    if (reseauCoupe) {
      throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
    }
    final Map<String, dynamic> corps;
    if (options.path == ServiceParametres.referentiel.chemin) {
      corps = referentiel;
    } else if (options.path == ServiceParametres.evaluation.chemin) {
      corps = libellesEvaluationJson();
    } else if (options.path == ServiceParametres.presence.chemin) {
      corps = {'version': 'W/"p1"', 'referentiels': <String, dynamic>{}};
    } else {
      return ResponseBody.fromString('{}', 404);
    }
    final etag = corps['version'] as String;
    if (options.headers['If-None-Match'] == etag) {
      return ResponseBody.fromString('', 304, headers: {
        'etag': [etag],
      });
    }
    return ResponseBody.fromString(
      jsonEncode(corps),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
        'etag': [etag],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

({LibellesService service, _Serveur serveur}) creer() {
  final serveur = _Serveur();
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = serveur;
  return (service: LibellesService(dio), serveur: serveur);
}

void main() {
  tearDown(Libelles.reinitialiser);

  test('charge les trois services avec actifs=true', () async {
    final t = creer();
    await t.service.rafraichir();

    expect(t.serveur.requetes.map((r) => r.path).toSet(), {
      '/api/referentiel/v1/parametres/libelles',
      '/api/rh/v1/evaluations/parametres/libelles',
      '/api/rh/v1/presence/parametres/libelles',
    });
    expect(t.serveur.requetes.every((r) => r.queryParameters['actifs'] == true), isTrue);
    expect(Libelles.typeConge('MARIAGE'), 'Congé de mariage');
    expect(Libelles.statutEvaluation('VALIDEE'), 'Validée');
    expect(Libelles.niveauNote(5), 'Expert');
  });

  test('revalidation par ETag : un 304 garde les valeurs, un nouveau contenu les remplace', () async {
    final t = creer();
    await t.service.rafraichir({ServiceParametres.referentiel});
    t.serveur.requetes.clear();

    await t.service.rafraichir({ServiceParametres.referentiel});
    expect(t.serveur.requetes.single.headers['If-None-Match'], 'W/"v1"');
    expect(Libelles.typeConge('ANNUEL'), 'Congé annuel');

    // Un type est désactivé côté serveur : il n'est plus servi, il disparaît des valeurs actives.
    t.serveur.referentiel = libellesReferentielJson(version: 'W/"v2"');
    ((t.serveur.referentiel['referentiels'] as Map)['TYPE_CONGE'] as List)
        .removeWhere((v) => (v as Map)['code'] == 'MARIAGE');
    await t.service.rafraichir({ServiceParametres.referentiel});
    expect(Libelles.valeurs(Referentiel.typeConge).map((v) => v.code), ['ANNUEL', 'MALADIE']);
  });

  test('échec réseau : l’erreur est propagée et aucune liste de secours n’est chargée', () async {
    final t = creer();
    t.serveur.reseauCoupe = true;

    await expectLater(t.service.rafraichir({ServiceParametres.referentiel}), throwsA(isA<DioException>()));
    expect(await t.service.rafraichirSilencieux(), isFalse);
    expect(Libelles.valeurs(Referentiel.typeConge), isEmpty);
    expect(Libelles.valeurs(Referentiel.typeDocument), isEmpty);
    expect(Libelles.typeConge('ANNUEL'), 'Valeur non reconnue');
  });

  test('arrêt (déconnexion) : valeurs et versions oubliées', () async {
    final t = creer();
    await t.service.rafraichir({ServiceParametres.referentiel});
    t.service.arreter();
    expect(Libelles.valeurs(Referentiel.typeConge), isEmpty);

    t.serveur.requetes.clear();
    await t.service.rafraichir({ServiceParametres.referentiel});
    expect(t.serveur.requetes.single.headers.containsKey('If-None-Match'), isFalse);
    expect(Libelles.valeurs(Referentiel.typeConge), isNotEmpty);
  });

  group('listes des formulaires', () {
    test('types de congé : relus à l’ouverture, pièce jointe servie par l’API', () async {
      final t = creer();
      final c = ProviderContainer(overrides: [libellesServiceProvider.overrideWithValue(t.service)]);
      addTearDown(c.dispose);

      final types = await c.read(typesCongeActifsProvider.future);
      expect(types.map((x) => x.code), ['ANNUEL', 'MALADIE', 'MARIAGE']);
      expect(types.firstWhere((x) => x.code == 'MALADIE').pieceJointeObligatoire, isTrue);
      expect(types.firstWhere((x) => x.code == 'MARIAGE').pieceJointeObligatoire, isFalse);
    });

    test('serveur coupé : erreur, aucune valeur ; réseau rétabli puis relecture : les valeurs apparaissent',
        () async {
      final t = creer();
      final c = ProviderContainer(overrides: [libellesServiceProvider.overrideWithValue(t.service)]);
      addTearDown(c.dispose);
      final ecoute = c.listen(typesDocumentActifsProvider, (_, __) {});
      addTearDown(ecoute.close);

      t.serveur.reseauCoupe = true;
      await expectLater(c.read(typesDocumentActifsProvider.future), throwsA(isA<DioException>()));
      expect(c.read(typesDocumentActifsProvider).hasError, isTrue);
      expect(c.read(typesDocumentActifsProvider).valueOrNull, isNull);

      t.serveur.reseauCoupe = false;
      c.invalidate(typesDocumentActifsProvider); // « Réessayer »
      final types = await c.read(typesDocumentActifsProvider.future);
      expect(types.map((x) => x.code), ['ATTESTATION_TRAVAIL', 'BULLETIN_PAIE']);
    });

    test('tous les types de document désactivés : liste vide, pas d’erreur', () async {
      final t = creer();
      ((t.serveur.referentiel['referentiels'] as Map)['TYPE_DOCUMENT'] as List).clear();
      final c = ProviderContainer(overrides: [libellesServiceProvider.overrideWithValue(t.service)]);
      addTearDown(c.dispose);

      expect(await c.read(typesDocumentActifsProvider.future), isEmpty);
    });
  });
}

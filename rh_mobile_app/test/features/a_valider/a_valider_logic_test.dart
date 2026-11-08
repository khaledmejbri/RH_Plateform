import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/a_valider/data/a_valider_logic.dart';

DioException _erreur(int? statut) => DioException(
      requestOptions: RequestOptions(path: '/x'),
      response: statut == null
          ? null
          : Response<dynamic>(requestOptions: RequestOptions(path: '/x'), statusCode: statut),
      type: statut == null ? DioExceptionType.connectionError : DioExceptionType.badResponse,
    );

void main() {
  test('compteur = demandes M01 + pointages manuels (0 + 1 = 1)', () {
    expect(compterAValider(demandes: 0, pointagesManuels: 1), 1);
    expect(compterAValider(demandes: 2, pointagesManuels: 3), 5);
    expect(compterAValider(demandes: 0, pointagesManuels: 0), 0);
  });

  test('403 compte pour une liste vide', () async {
    final r = await listeOuVideSiRefuse<int>(() async => throw _erreur(403));
    expect(r, isEmpty);
  });

  test('réseau coupé : l’erreur est propagée (pas de liste vide trompeuse)', () async {
    expect(
      () => listeOuVideSiRefuse<int>(() async => throw _erreur(null)),
      throwsA(isA<DioException>()),
    );
  });

  test('liste normale renvoyée telle quelle', () async {
    expect(await listeOuVideSiRefuse<int>(() async => [1, 2]), [1, 2]);
  });
}

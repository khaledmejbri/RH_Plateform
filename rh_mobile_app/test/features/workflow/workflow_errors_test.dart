import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/workflow/data/workflow_errors.dart';

DioException _dioErr(int statusCode, Map<String, dynamic> body) {
  final req = RequestOptions(path: '/api/rh/v1/workflow/taches/x');
  return DioException(
    requestOptions: req,
    response: Response<dynamic>(
      requestOptions: req,
      statusCode: statusCode,
      data: body,
    ),
    type: DioExceptionType.badResponse,
  );
}

void main() {
  test('409 renvoie le message normalisé « Déjà traitée par quelqu’un d’autre. »', () {
    final err = _dioErr(409, {'code': 'TACHE_DEJA_TRAITEE', 'message': 'Conflit serveur'});
    expect(messageErreurDecision(err), 'Déjà traitée par quelqu’un d’autre.');
  });

  test('422 conserve le message exact du serveur tel quel', () {
    final err = _dioErr(422, {
      'code': 'COMMENTAIRE_OBLIGATOIRE',
      'message': 'Un commentaire de 10 caractères minimum est requis pour cette étape.',
    });
    expect(
      messageErreurDecision(err),
      'Un commentaire de 10 caractères minimum est requis pour cette étape.',
    );
  });

  test('403 et 404 renvoient des messages clairs en français', () {
    final err403 = _dioErr(403, {'code': 'AUTO_DECISION_INTERDITE', 'message': 'Auto-décision interdite.'});
    final err404 = _dioErr(404, {'code': 'TACHE_INTROUVABLE'});
    expect(messageErreurDecision(err403), 'Auto-décision interdite.');
    expect(messageErreurDecision(err404), contains('introuvable'));
  });

  test('erreurs délégation 409 chevauchement et 422 invalide', () {
    final err409 = _dioErr(409, {
      'code': 'DELEGATION_CHEVAUCHEMENT',
      'message': 'Chevauchement avec une délégation existante.',
    });
    final err422 = _dioErr(422, {
      'code': 'DELEGATION_INVALIDE',
      'message': 'La durée d’une délégation ne peut pas dépasser 90 jours.',
    });
    expect(messageErreurDelegation(err409), 'Chevauchement avec une délégation existante.');
    expect(messageErreurDelegation(err422), 'La durée d’une délégation ne peut pas dépasser 90 jours.');
  });
}

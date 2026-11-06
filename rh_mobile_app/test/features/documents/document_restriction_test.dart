import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/documents/data/document_repository.dart';

/// Adaptateur Dio factice : renvoie un statut + corps JSON, ou une erreur réseau.
class _Adaptateur implements HttpClientAdapter {
  _Adaptateur({this.statut = 200, this.corps, this.reseau = false});

  final int statut;
  final Map<String, dynamic>? corps;
  final bool reseau;
  RequestOptions? derniere;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    derniere = options;
    if (reseau) {
      throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
    }
    return ResponseBody.fromString(
      jsonEncode(corps ?? const {}),
      statut,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

DocumentRepository repo(_Adaptateur a) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = a;
  return DocumentRepository(dio);
}

void main() {
  test('pré-contrôle : can_request=false et message serveur conservés', () async {
    final a = _Adaptateur(corps: {'can_request': false, 'message': 'Une demande est déjà en cours.'});
    final r = await repo(a).verifierRestriction('ATTESTATION_TRAVAIL');
    expect(r.canRequest, isFalse);
    expect(r.message, 'Une demande est déjà en cours.');
    expect(a.derniere!.queryParameters['type_document'], 'ATTESTATION_TRAVAIL');
  });

  test('pré-contrôle : can_request=true', () async {
    final r = await repo(_Adaptateur(corps: {'can_request': true})).verifierRestriction('AUTRE');
    expect(r.canRequest, isTrue);
  });

  test('pré-contrôle en échec (réseau) : l\'erreur remonte, jamais permissif', () async {
    expect(
      repo(_Adaptateur(reseau: true)).verifierRestriction('ATTESTATION_TRAVAIL'),
      throwsA(isA<DioException>()),
    );
  });

  test('pré-contrôle en 500 / 401 : l\'erreur remonte', () async {
    expect(repo(_Adaptateur(statut: 500)).verifierRestriction('AUTRE'), throwsA(isA<DioException>()));
    expect(repo(_Adaptateur(statut: 401)).verifierRestriction('AUTRE'), throwsA(isA<DioException>()));
  });

  test('réponse sans can_request : refus', () {
    expect(CanRequestResult.fromJson(const {}).canRequest, isFalse);
    expect(CanRequestResult.fromJson(null).canRequest, isFalse);
  });

  test('409 du POST : l\'erreur remonte avec le message serveur', () async {
    final a = _Adaptateur(statut: 409, corps: {'code': 'DEMANDE_DOCUMENT_DEJA_EN_COURS', 'message': 'Déjà en cours.'});
    expect(
      repo(a).create(typeDocument: 'ATTESTATION_TRAVAIL'),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'statut', 409)),
    );
  });
}

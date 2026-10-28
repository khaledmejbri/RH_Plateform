import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/pointage/data/presence_errors.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';

void main() {
  group('messageMotifPointage', () {
    test('REJETE_HORS_ZONE message clair', () {
      expect(
        messageMotifPointage(PointageStatut.rejeteHorsZone),
        contains('hors de la zone'),
      );
    });

    test('REJETE_QR_EXPIRE', () {
      expect(
        messageMotifPointage(PointageStatut.rejeteQrExpire),
        contains('expiré'),
      );
    });

    test('REJETE_EMPLACEMENT_ABSENT', () {
      expect(
        messageMotifPointage(PointageStatut.rejeteEmplacementAbsent),
        contains('emplacement'),
      );
    });

    test('REJETE_PRECISION_GPS', () {
      expect(
        messageMotifPointage(PointageStatut.rejetePrecisionGps),
        contains('Signal GPS trop faible'),
      );
    });

    test('motif serveur prioritaire', () {
      expect(
        messageMotifPointage(
          PointageStatut.rejeteHorsZone,
          motifServeur: 'Distance 72 m',
        ),
        'Distance 72 m',
      );
    });
  });

  group('messageErreurPresence', () {
    test('422 utilise message serveur', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/x'),
        response: Response(
          requestOptions: RequestOptions(path: '/x'),
          statusCode: 422,
          data: {'message': 'GPS manquant'},
        ),
        type: DioExceptionType.badResponse,
      );
      expect(messageErreurPresence(e), 'GPS manquant');
    });

    test('403 fallback métier', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/x'),
        response: Response(
          requestOptions: RequestOptions(path: '/x'),
          statusCode: 403,
        ),
        type: DioExceptionType.badResponse,
      );
      expect(messageErreurPresence(e), contains('Accès refusé'));
    });
  });

  group('PointageResult / PresenceSite', () {
    test('parse VALIDE camelCase', () {
      final r = PointageResult.fromJson({
        'statut': 'VALIDE',
        'serverTs': '2026-08-13T10:15:00Z',
        'siteId': 's1',
        'distanceMetres': 12.5,
      });
      expect(r.isValide, isTrue);
      expect(r.distanceMetres, 12.5);
      expect(r.serverTs, isNotNull);
    });

    test('parse REJETE snake_case', () {
      final r = PointageResult.fromJson({
        'statut': 'REJETE_HORS_ZONE',
        'motif_rejet': 'Hors zone',
        'distance_metres': 61,
      });
      expect(r.isRejet, isTrue);
      expect(r.motifRejet, 'Hors zone');
      expect(r.distanceMetres, 61);
    });

    test('PresenceSite hasEmplacement', () {
      final sans = PresenceSite.fromJson({
        'id': '1',
        'code': 'A',
        'libelle': 'Accueil',
      });
      expect(sans.hasEmplacement, isFalse);
      final avec = PresenceSite.fromJson({
        'identifiant': '1',
        'code': 'A',
        'libelle': 'Accueil',
        'latitude': 36.8,
        'longitude': 10.1,
        'rayon_metres': 50,
      });
      expect(avec.hasEmplacement, isTrue);
      expect(avec.rayonMetres, 50);
    });

    test('PointageRequest toJson', () {
      final body = const PointageRequest(
        qrToken: 'p1.abc.sig',
        type: TypePointage.entree,
        latitude: 36.8,
        longitude: 10.1,
        accuracyMeters: 8,
        idempotencyKey: 'k1',
      ).toJson();
      expect(body['type'], 'ENTREE');
      expect(body['qr_token'], 'p1.abc.sig');
      expect(body['idempotency_key'], 'k1');
      expect(body['accuracy_metres'], 8);
    });
  });
}

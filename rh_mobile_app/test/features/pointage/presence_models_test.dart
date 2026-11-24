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

  group('PointagesEquipePage / PointageEquipeItem', () {
    test('parse page complète snake_case et formate horodatage en heure de Tunis', () {
      final page = PointagesEquipePage.fromJson({
        'elements': [
          {
            'id': 'pt-1',
            'collaborateur_id': 'col-1',
            'collaborateur_nom': 'Sarra Khemiri',
            'collaborateur_matricule': 'COL-WEB',
            'type': 'ENTREE',
            'type_libelle': 'Entrée',
            'statut': 'VALIDE',
            'statut_libelle': 'Pointage validé',
            'server_ts': '2026-11-24T08:15:12Z',
            'journee': '2026-11-24',
            'site_id': 'site-1',
            'site_code': 'SIEGE',
            'site_libelle': 'Siège Social',
            'manuel': false,
            'motif_rejet': null,
          },
          {
            'id': 'pt-2',
            'collaborateur_id': 'col-2',
            'collaborateur_nom': 'Ahmed Nasri',
            'type': 'SORTIE',
            'statut': 'REJETE_HORS_ZONE',
            'server_ts': '2026-11-24T16:30:00Z',
            'site_libelle': 'Annexe',
            'manuel': false,
            'motif_rejet': 'Hors de la zone (95 m)',
          },
        ],
        'page': 0,
        'size': 20,
        'total_elements': 25,
        'total_pages': 2,
        'du': '2026-11-18',
        'au': '2026-11-24',
        'effectif_equipe': 2,
      });

      expect(page.page, 0);
      expect(page.size, 20);
      expect(page.totalElements, 25);
      expect(page.totalPages, 2);
      expect(page.du, '2026-11-18');
      expect(page.au, '2026-11-24');
      expect(page.effectifEquipe, 2);
      expect(page.aSuite, isTrue);
      expect(page.elements, hasLength(2));

      final e1 = page.elements[0];
      expect(e1.id, 'pt-1');
      expect(e1.collaborateurId, 'col-1');
      expect(e1.collaborateurNom, 'Sarra Khemiri');
      expect(e1.collaborateurMatricule, 'COL-WEB');
      expect(e1.estValide, isTrue);
      expect(e1.estRejet, isFalse);
      expect(e1.estSortie, isFalse);
      expect(e1.typeAffiche, 'Entrée');
      expect(e1.statutAffiche, 'Pointage validé');
      expect(e1.horodatageTunis, '24/11/2026 09:15');
      expect(e1.siteLibelle, 'Siège Social');

      final e2 = page.elements[1];
      expect(e2.estValide, isFalse);
      expect(e2.estRejet, isTrue);
      expect(e2.estSortie, isTrue);
      expect(e2.typeAffiche, 'Sortie');
      expect(e2.statutAffiche, 'Refusé');
      expect(e2.horodatageTunis, '24/11/2026 17:30');
      expect(e2.motifRejet, 'Hors de la zone (95 m)');
    });

    test('parse tolérant avec champs optionnels absents', () {
      final page = PointagesEquipePage.fromJson({
        'elements': [
          {
            'id': 'pt-3',
            'collaborateur_id': 'col-3',
            'statut': 'VALIDE_MANUEL',
            'manuel': true,
          },
        ],
        'page': 1,
        'size': 20,
        'total_elements': 21,
        'total_pages': 2,
      });

      expect(page.aSuite, isFalse);
      expect(page.elements.single.estValide, isTrue);
      expect(page.elements.single.manuel, isTrue);
      expect(page.elements.single.statutAffiche, 'Validé manuellement');
      expect(page.elements.single.typeAffiche, 'Entrée');
      expect(page.elements.single.horodatageTunis, '—');
    });

    test('codes erreur PERIODE_TROP_LONGUE et RECHERCHE_TROP_LONGUE', () {
      expect(
        messageParCodeErreur('PERIODE_TROP_LONGUE'),
        'La période ne peut pas dépasser 31 jours.',
      );
      expect(
        messageParCodeErreur('RECHERCHE_TROP_LONGUE'),
        'La recherche ne peut pas dépasser 100 caractères.',
      );
    });
  });
}

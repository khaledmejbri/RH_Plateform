import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/notifications/notification_model.dart';
import 'package:rh_mobile_app/core/notifications/notification_navigation.dart';

const _uuid = '3f2504e0-4f89-11d3-9a0c-0305e82c3301';

AppNotification _dto(Map<String, dynamic> extra) => AppNotification.fromMap({
      'id': 'n1',
      'sujet': 'Demande validée',
      'contenu': 'Votre demande DEM-202611-0003 est validée.',
      ...extra,
    });

void main() {
  group('AppNotification.fromMap (NotificationDto)', () {
    test('lit les champs structurés et la date de réception', () {
      final n = _dto({
        'evenement': 'DEMANDE_VALIDEE',
        'objet_type': 'demande',
        'objet_id': _uuid,
        'objet_numero': 'DEM-202611-0003',
        'recu_le': '2026-11-06T10:30:00',
        'lu': true,
      });
      expect(n.id, 'n1');
      expect(n.subject, 'Demande validée');
      expect(n.objetType, 'DEMANDE');
      expect(n.objetId, _uuid);
      expect(n.objetNumero, 'DEM-202611-0003');
      expect(n.isRead, isTrue);
      expect(n.receivedAt, DateTime(2026, 11, 6, 10, 30));
    });

    test('ancien message : champs absents, non lu, repli sur l’ancien format', () {
      final n = AppNotification.fromMap({'subject': 'Ancien', 'content': 'Texte', 'recipient': 'x'});
      expect(n.subject, 'Ancien');
      expect(n.content, 'Texte');
      expect(n.objetType, isNull);
      expect(n.isRead, isFalse);
    });
  });

  group('PageNotifications', () {
    test('aSuite tant que (page + 1) x taille < total', () {
      expect(const PageNotifications(contenu: [], page: 0, taille: 20, total: 25).aSuite, isTrue);
      expect(const PageNotifications(contenu: [], page: 1, taille: 20, total: 25).aSuite, isFalse);
    });

    test('lit contenu, page, taille et total', () {
      final p = PageNotifications.fromJson({
        'contenu': [
          {'id': 'a', 'sujet': 'S', 'contenu': 'C', 'lu': false},
        ],
        'page': 1,
        'taille': 20,
        'total': 21,
      });
      expect(p.contenu.single.id, 'a');
      expect(p.page, 1);
      expect(p.total, 21);
    });
  });

  group('routePourNotification', () {
    test('route par type d’objet et identifiant', () {
      expect(routePourNotification(_dto({'objet_type': 'DEMANDE', 'objet_id': _uuid})),
          '/demandes-admin/$_uuid');
      expect(routePourNotification(_dto({'objet_type': 'DOCUMENT', 'objet_id': _uuid})),
          '/documents/$_uuid');
      expect(routePourNotification(_dto({'objet_type': 'EVALUATION', 'objet_id': _uuid})),
          '/evaluations/$_uuid');
      expect(routePourNotification(_dto({'objet_type': 'POINTAGE_MANUEL', 'objet_id': _uuid})),
          '/pointage/manuels/$_uuid');
      expect(routePourNotification(_dto({'objet_type': 'FORMATION', 'objet_id': _uuid})),
          '/formations/$_uuid');
    });

    test('identifiant invalide : liste du module, jamais un chemin libre', () {
      expect(routePourNotification(_dto({'objet_type': 'DEMANDE', 'objet_id': '../x'})),
          '/demandes-admin');
    });

    test('alerte d’écart : aucun écran mobile', () {
      expect(routePourNotification(_dto({'objet_type': 'ALERTE_ECART', 'objet_id': _uuid})), isNull);
    });

    test('ancienne notification sans type : repli sur les mots-clés', () {
      final n = AppNotification.fromMap({'sujet': 'Info', 'contenu': 'Votre demande de congé est validée.'});
      expect(routePourNotification(n), '/demandes-admin');
      final sans = AppNotification.fromMap({'sujet': 'Bonjour', 'contenu': 'Bienvenue.'});
      expect(routePourNotification(sans), isNull);
    });
  });
}

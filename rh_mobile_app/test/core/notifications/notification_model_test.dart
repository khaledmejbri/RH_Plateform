import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/notifications/notification_model.dart';

AppNotification _notif(String content, {String subject = 'Sujet'}) =>
    AppNotification(
      id: '1',
      subject: subject,
      content: content,
      receivedAt: DateTime(2026, 11, 4),
    );

void main() {
  group('AppNotification.displayContent', () {
    test('masque le préfixe TYPE|cle=valeur|', () {
      final n = _notif(
        'EVALUATION_CAMPAGNE_OUVERTE|campagne=c1|evaluation=e1|La campagne est active.',
      );
      expect(n.displayContent, 'La campagne est active.');
    });

    test('masque un autre type de préfixe', () {
      final n = _notif('AUTRE_TYPE|demande=d1|Votre demande est validée.');
      expect(n.displayContent, 'Votre demande est validée.');
    });

    test('conserve un texte sans préfixe', () {
      final n = _notif('Votre demande est validée.');
      expect(n.displayContent, 'Votre demande est validée.');
    });

    test('conserve un texte contenant une barre verticale', () {
      final n = _notif('Salle A | 10h00');
      expect(n.displayContent, 'Salle A | 10h00');
    });

    test('repli sur le sujet si le texte est vide', () {
      final n = _notif('TYPE|cle=valeur|', subject: 'Mon sujet');
      expect(n.displayContent, 'Mon sujet');
    });
  });
}

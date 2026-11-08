import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/notifications/notification_model.dart';
import 'package:rh_mobile_app/features/notifications/liste_notifications.dart';

AppNotification _n(String id) =>
    AppNotification(id: id, subject: 's', content: 'c', receivedAt: DateTime(2026, 11, 6));

void main() {
  test('fusionnerNotifications : ajoute à la suite sans doublon', () {
    final r = fusionnerNotifications([_n('1'), _n('2')], [_n('2'), _n('3')]);
    expect(r.map((n) => n.id), ['1', '2', '3']);
  });

  test('taille de page : 20', () {
    expect(tailleListeNotifications, 20);
  });
}

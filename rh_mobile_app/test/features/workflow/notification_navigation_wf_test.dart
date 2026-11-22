import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/notifications/notification_model.dart';
import 'package:rh_mobile_app/core/notifications/notification_navigation.dart';

const _uuid = '00000000-0000-4000-8000-000000000042';

AppNotification _notif({
  String? evenement,
  String? objetType,
  String? objetId,
}) {
  return AppNotification.fromMap({
    'id': 'notif-1',
    'sujet': 'Workflow',
    'contenu': 'Notification workflow',
    if (evenement != null) 'evenement': evenement,
    if (objetType != null) 'objet_type': objetType,
    if (objetId != null) 'objet_id': objetId,
  });
}

void main() {
  test('événements WF_TACHE_CREEE, WF_RELANCE et WF_ESCALADE routent vers /taches avant objetType', () {
    expect(
      routePourNotification(_notif(evenement: 'WF_TACHE_CREEE', objetType: 'CONGE', objetId: _uuid)),
      '/taches',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_RELANCE', objetType: 'DOCUMENT', objetId: _uuid)),
      '/taches',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_ESCALADE', objetType: 'CONGE', objetId: _uuid)),
      '/taches',
    );
  });

  test('WF_DELEGATION route vers /delegations et WF_BLOQUEE renvoie null', () {
    expect(
      routePourNotification(_notif(evenement: 'WF_DELEGATION', objetType: 'CONGE', objetId: _uuid)),
      '/delegations',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_BLOQUEE', objetType: 'CONGE', objetId: _uuid)),
      isNull,
    );
  });

  test('WF_DECISION et WF_FIN routent vers le détail de l’objet métier y compris M01 / HEURES_SUP', () {
    expect(
      routePourNotification(_notif(evenement: 'WF_DECISION', objetType: 'CONGE', objetId: _uuid)),
      '/demandes-admin/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_FIN', objetType: 'AUTORISATION_SORTIE', objetId: _uuid)),
      '/demandes-admin/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_FIN', objetType: 'ORDRE_MISSION', objetId: _uuid)),
      '/demandes-admin/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_DECISION', objetType: 'DOCUMENT', objetId: _uuid)),
      '/documents/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_FIN', objetType: 'FORMATION', objetId: _uuid)),
      '/formations/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_DECISION', objetType: 'POINTAGE_MANUEL', objetId: _uuid)),
      '/pointage/manuels/$_uuid',
    );
    expect(
      routePourNotification(_notif(evenement: 'WF_FIN', objetType: 'HEURES_SUP', objetId: _uuid)),
      '/heures-sup/moi',
    );
  });
}

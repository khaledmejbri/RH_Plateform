import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/constants/api_constants.dart';

void main() {
  group('adresse du serveur enregistrée dans l’appli', () {
    test('ignorée en production (ni SERVEUR_EDITABLE ni débogage)', () {
      expect(ApiConstants.surchargeAutorisee(editable: false, debug: false), isFalse);
    });

    test('prise en compte si le champ « Serveur » est modifiable', () {
      expect(ApiConstants.surchargeAutorisee(editable: true, debug: false), isTrue);
    });

    test('prise en compte en débogage', () {
      expect(ApiConstants.surchargeAutorisee(editable: false, debug: true), isTrue);
    });
  });
}

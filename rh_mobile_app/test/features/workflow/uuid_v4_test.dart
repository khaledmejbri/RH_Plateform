import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/workflow/data/uuid_v4.dart';

void main() {
  final regexUuidV4 = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
  );

  test('génère un UUID v4 RFC 4122 valide de 36 caractères', () {
    final u = genererUuidV4();
    expect(u.length, 36);
    expect(regexUuidV4.hasMatch(u), isTrue);
  });

  test('génère des identifiants distincts à chaque appel', () {
    final genere = <String>{for (var i = 0; i < 100; i++) genererUuidV4()};
    expect(genere.length, 100);
  });
}

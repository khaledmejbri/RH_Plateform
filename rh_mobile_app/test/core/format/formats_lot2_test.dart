import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/format/formats.dart';

void main() {
  test('formatDateApi : aaaa-mm-jj', () {
    expect(formatDateApi(DateTime(2026, 1, 5)), '2026-01-05');
  });

  test('formatDuree : heures et minutes', () {
    expect(formatDuree(482), '8 h 02');
    expect(formatDuree(45), '45 min');
    expect(formatDuree(120), '2 h');
    expect(formatDuree(0), '0 min');
    expect(formatDuree(-5), '0 min');
  });

  test('formatRetard : null = tiret, 0 = à l’heure', () {
    expect(formatRetard(null), '—');
    expect(formatRetard(0), 'À l’heure');
    expect(formatRetard(12), '12 min de retard');
  });

  test('libelleBadge : masqué à 0, exact jusqu’à 99, 99+ au-delà', () {
    expect(libelleBadge(0), isNull);
    expect(libelleBadge(1), '1');
    expect(libelleBadge(99), '99');
    expect(libelleBadge(100), '99+');
    expect(libelleBadge(120), '99+');
  });
}

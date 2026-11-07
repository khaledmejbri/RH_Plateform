import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/format/formats.dart';

void main() {
  group('formatDate', () {
    test('jj/mm/aaaa depuis une date ISO', () {
      expect(formatDateIso('2026-11-06'), '06/11/2026');
      expect(formatDateIso('2026-11-25T08:30:00'), '25/11/2026');
      expect(formatDate(DateTime(2026, 1, 5)), '05/01/2026');
    });

    test('date absente ou illisible : tiret, jamais la valeur brute', () {
      expect(formatDateIso(null), '—');
      expect(formatDateIso(''), '—');
      expect(formatDateIso('pas une date'), '—');
    });

    test('date-heure jj/mm/aaaa hh:mm', () {
      expect(formatDateHeure(DateTime(2026, 11, 6, 14, 5)), '06/11/2026 14:05');
      expect(formatDateHeureIso('2026-11-06T09:07:00'), '06/11/2026 09:07');
    });

    test('aucune date ISO dans le résultat', () {
      expect(formatDateIso('2026-11-25'), isNot(contains('2026-11-25')));
    });
  });

  group('formatPeriode', () {
    test('période d’un jour', () {
      expect(formatPeriode('2026-11-06', '2026-11-06'), '06/11/2026 (1 jour)');
    });

    test('période de 3 jours', () {
      expect(
        formatPeriode('2026-11-06', '2026-11-08'),
        'du 06/11/2026 au 08/11/2026 (3 jours)',
      );
    });

    test('chevauchement de mois', () {
      expect(nombreJoursPeriode(DateTime(2026, 11, 29), DateTime(2026, 12, 2)), 4);
    });

    test('bornes absentes', () {
      expect(formatPeriode(null, null), '—');
      expect(formatPeriode('2026-11-06', null), '06/11/2026');
    });
  });

  group('nombres', () {
    test('décimal à virgule', () {
      expect(formatDecimal(13.5), '13,5');
      expect(formatDecimal(13), '13');
    });

    test('aucun score factice quand il n’y a pas de note', () {
      expect(formatNote(null, 20), '—');
      expect(formatNote(13.5, 20), '13,5 / 20');
      expect(formatNote(0.13, 5, decimales: 2), '0,13 / 5');
    });

    test('initiale', () {
      expect(initialeDe('sarra'), 'S');
      expect(initialeDe(null), '?');
    });
  });
}

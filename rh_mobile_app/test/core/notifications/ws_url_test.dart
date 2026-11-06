import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/notifications/ws_url.dart';

void main() {
  group('deduireUrlWebSocket', () {
    test('http sans port → ws sans port', () {
      expect(deduireUrlWebSocket('http://exemple.test'), 'ws://exemple.test/ws');
    });
    test('http avec port explicite', () {
      expect(deduireUrlWebSocket('http://10.0.2.2:8080'), 'ws://10.0.2.2:8080/ws');
      expect(deduireUrlWebSocket('http://localhost:8090'), 'ws://localhost:8090/ws');
    });
    test('https → wss, avec ou sans port', () {
      expect(deduireUrlWebSocket('https://rh.exemple.test'), 'wss://rh.exemple.test/ws');
      expect(deduireUrlWebSocket('https://rh.exemple.test:8443'), 'wss://rh.exemple.test:8443/ws');
    });
    test('chemin ou slash final de l\'API ignoré', () {
      expect(deduireUrlWebSocket('https://rh.exemple.test/'), 'wss://rh.exemple.test/ws');
      expect(deduireUrlWebSocket('http://h:8080/api'), 'ws://h:8080/ws');
    });
    test('URL invalide → exception', () {
      expect(() => deduireUrlWebSocket(''), throwsFormatException);
    });
  });

  group('delaiReconnexion', () {
    test('1 s, 2 s, 4 s… plafonné à 60 s', () {
      final secondes = [for (var i = 0; i < 9; i++) delaiReconnexion(i).inSeconds];
      expect(secondes, [1, 2, 4, 8, 16, 32, 60, 60, 60]);
    });
    test('croissant et jamais au-delà de 60 s', () {
      var precedent = Duration.zero;
      for (var i = 0; i < 100; i++) {
        final d = delaiReconnexion(i);
        expect(d >= precedent, isTrue);
        expect(d <= const Duration(seconds: 60), isTrue);
        precedent = d;
      }
    });
  });
}

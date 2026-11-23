import 'dart:math';

/// Génère un identifiant unique UUID version 4 conforme à la RFC 4122.
///
/// Utilise [Random.secure] par défaut afin de garantir l'unicité des clés
/// d'idempotence lors des soumissions et reprises de décisions workflow.
String genererUuidV4([Random? source]) {
  final rng = source ?? Random.secure();
  final octets = List<int>.generate(16, (_) => rng.nextInt(256));
  octets[6] = (octets[6] & 0x0f) | 0x40; // Version 4
  octets[8] = (octets[8] & 0x3f) | 0x80; // Variant RFC 4122

  String hex(int debut, int fin) {
    final buf = StringBuffer();
    for (var i = debut; i < fin; i++) {
      buf.write(octets[i].toRadixString(16).padLeft(2, '0'));
    }
    return buf.toString();
  }

  return '${hex(0, 4)}-${hex(4, 6)}-${hex(6, 8)}-${hex(8, 10)}-${hex(10, 16)}';
}

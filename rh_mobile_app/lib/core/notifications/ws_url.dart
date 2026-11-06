/// URL du WebSocket STOMP déduite de l'URL de l'API (D-T02.d) : même hôte et même port,
/// `wss` si l'API est en `https`, `ws` sinon, chemin `/ws`. Aucune adresse en dur.
String deduireUrlWebSocket(String baseUrl) {
  final brut = baseUrl.trim();
  final uri = Uri.tryParse(brut.contains('://') ? brut : 'http://$brut');
  if (uri == null || uri.host.isEmpty) {
    throw FormatException('URL de l\'API invalide : $baseUrl');
  }
  final schema = uri.scheme == 'https' ? 'wss' : 'ws';
  return Uri(
    scheme: schema,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    path: '/ws',
  ).toString();
}

/// Attente avant la n-ième reconnexion (n >= 0) : 1 s, 2 s, 4 s… plafonnée à 60 s.
Duration delaiReconnexion(int tentative) {
  final n = tentative < 0 ? 0 : tentative;
  if (n >= 6) return const Duration(seconds: 60); // 2^6 = 64 s > 60 s
  final secondes = 1 << n;
  return Duration(seconds: secondes > 60 ? 60 : secondes);
}

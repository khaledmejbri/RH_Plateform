import 'package:dio/dio.dart';

/// Messages d’erreur M01 (validation manager nœud).
String messageErreurDemandeAdmin(Object error, {String fallback = 'Une erreur est survenue'}) {
  if (error is! DioException) return fallback;
  final status = error.response?.statusCode;
  final server = _messageServeur(error.response?.data);
  if (status == 403) {
    return server ??
        'Seul le manager actif du nœud d’unité du demandeur peut valider cette étape.';
  }
  if (status == 400 || status == 409 || status == 422) {
    return server ?? fallback;
  }
  if (error.type == DioExceptionType.connectionError ||
      error.type == DioExceptionType.connectionTimeout) {
    return 'Impossible de joindre le serveur.';
  }
  return server ?? fallback;
}

String? _messageServeur(dynamic data) {
  if (data is Map) {
    final erreur = data['erreur'] ?? data['message'];
    if (erreur != null && erreur.toString().trim().isNotEmpty) {
      return erreur.toString();
    }
  }
  return null;
}

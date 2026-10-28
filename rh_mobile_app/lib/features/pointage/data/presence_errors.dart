import 'package:dio/dio.dart';

import 'presence_models.dart';

/// Libellés métier pour les statuts `REJETE_*` (R-P07…R-P12).
String messageMotifPointage(String? statut, {String? motifServeur}) {
  final fromServer = motifServeur?.trim();
  if (fromServer != null && fromServer.isNotEmpty) {
    return fromServer;
  }
  switch (statut) {
    case PointageStatut.rejeteQrInvalide:
      return 'Code QR invalide ou altéré. Rescanner le QR du site.';
    case PointageStatut.rejeteQrExpire:
      return 'Ce code QR a expiré. Contactez les RH pour en générer un nouveau.';
    case PointageStatut.rejeteQrRevoque:
      return 'Ce code QR a été révoqué. Utilisez le QR actuellement affiché sur le site.';
    case PointageStatut.rejeteHorsZone:
      return 'Vous êtes hors de la zone de pointage du site.';
    case PointageStatut.rejeteSiteInactif:
      return 'Ce site de pointage est inactif.';
    case PointageStatut.rejeteEmplacementAbsent:
      return 'L’emplacement GPS du site n’est pas encore configuré. Contactez les RH.';
    case PointageStatut.rejetePrecisionGps:
      return 'Signal GPS trop faible. Placez-vous à découvert et réessayez.';
    case PointageStatut.rejetePositionSimulee:
      return 'Position GPS simulée détectée. Désactivez toute application de localisation fictive.';
    case PointageStatut.valide:
      return 'Pointage enregistré.';
    default:
      if (statut != null && statut.isNotEmpty) {
        return 'Pointage refusé ($statut).';
      }
      return 'Pointage refusé.';
  }
}

String messageErreurPresence(
  Object error, {
  String fallback = 'Une erreur est survenue',
}) {
  if (error is! DioException) return fallback;
  final status = error.response?.statusCode;
  final server = _messageServeur(error.response?.data);

  if (status == 401) {
    return server ?? 'Session expirée. Reconnectez-vous.';
  }
  if (status == 403) {
    return server ?? 'Accès refusé pour cette opération.';
  }
  if (status == 404) {
    return server ?? 'Ressource introuvable.';
  }
  if (status == 400 || status == 409 || status == 422) {
    return server ??
        'Données invalides. Vérifiez le GPS et le type de pointage.';
  }
  if (error.type == DioExceptionType.connectionError ||
      error.type == DioExceptionType.connectionTimeout ||
      error.type == DioExceptionType.receiveTimeout) {
    return 'Serveur injoignable. Vérifiez votre connexion.';
  }
  return server ?? fallback;
}

String? _messageServeur(dynamic data) {
  if (data is Map) {
    final erreur = data['erreur'] ??
        data['message'] ??
        data['motifRejet'] ??
        data['motif_rejet'];
    if (erreur != null && erreur.toString().trim().isNotEmpty) {
      return erreur.toString();
    }
  }
  return null;
}

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
    case PointageStatut.rejeteVisageNonReconnu:
      return 'Visage non reconnu. Réessayez face à la caméra, dans un endroit bien éclairé.';
    case PointageStatut.rejeteVivacite:
      return 'Contrôle du vivant échoué. Suivez les consignes à l’écran et réessayez.';
    case PointageStatut.rejeteVerificationFacialeAbsente:
      return 'Vérification du visage absente. Terminez votre enrôlement, ou demandez le '
          'ré-enrôlement si vous avez changé de téléphone.';
    case PointageStatut.rejeteHorsHoraire:
      return 'Pointage refusé : hors des horaires d’ouverture du site.';
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
  final server = _messageServeur(error.response?.data) ??
      messageParCodeErreur(codeErreurPresence(error));

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

/// Code métier (`code` du `ErrorResponse`) d'une erreur HTTP, `null` sinon.
String? codeErreurPresence(Object error) {
  if (error is! DioException) return null;
  final data = error.response?.data;
  if (data is! Map) return null;
  final code = data['code']?.toString().trim();
  return (code == null || code.isEmpty) ? null : code;
}

/// Libellés de repli quand le serveur ne fournit pas de message (le message serveur reste prioritaire).
String? messageParCodeErreur(String? code) {
  switch (code) {
    case 'VALIDATION_ERROR':
      return 'Données invalides. Vérifiez les champs saisis '
          '(1 à 50 collaborateurs, durées et dates).';
    case 'HEURE_HORS_FENETRE':
      return 'L’heure déclarée est hors de la fenêtre de pointage du site.';
    case 'QR_INVALIDE':
      return 'Code QR invalide ou altéré. Rescanner le QR du site.';
    case 'SITE_INTROUVABLE':
      return 'Site introuvable.';
    case 'SITE_SANS_HORAIRE':
      return 'Ce site n’a pas d’horaire : aucune heure sup possible.';
    case 'AUCUN_JOUR_OUVERT':
      return 'Le site est fermé tous les jours de cette période.';
    case 'PLAGE_DEJA_COMMENCEE':
      return 'La période a déjà commencé : la demande n’est plus possible.';
    case 'PLAGE_TROP_LONGUE':
      return 'La période ne peut pas dépasser 31 jours.';
    case 'PERIODE_INVALIDE':
      return 'La date de fin précède la date de début.';
    case 'MOTIF_OBLIGATOIRE':
      return 'Le motif est obligatoire.';
    case 'DUREE_HEURES_SUP_INVALIDE':
      return 'Durée d’ouverture invalide (de 30 min à 4 h, par pas de 30 min).';
    case 'OUVERTURE_ABSENTE':
      return 'Choisissez une ouverture avant et/ou après l’horaire.';
    case 'COLLABORATEUR_INVALIDE':
      return 'Un des collaborateurs choisis est introuvable ou inactif.';
    case 'HORS_PERIMETRE':
      return 'Vous n’êtes pas le valideur d’un des collaborateurs choisis.';
    case 'HORAIRES_CHEVAUCHANTS':
    case 'HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN':
      return 'La fenêtre élargie chevauche celle d’un jour voisin.';
    case 'CHEVAUCHEMENT_HEURES_SUP':
      return 'Une demande existe déjà pour un collaborateur, ce site et cette date.';
    case 'STATUT_INCOMPATIBLE':
      return 'Cette demande n’est plus modifiable (statut changé).';
    case 'REFERENTIEL_INDISPONIBLE':
      return 'Service temporairement indisponible. Réessayez dans un instant.';
    default:
      return null;
  }
}

/// `true` si l'erreur vient d'un manque de réseau (écran « hors ligne »).
bool estHorsLigne(Object error) =>
    error is DioException &&
    (error.type == DioExceptionType.connectionError ||
        error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.type == DioExceptionType.sendTimeout);

/// Fin du blocage si l'erreur est un 409 `POINTAGE_BLOQUE_VISAGE` (3 échecs faciaux, D-P10), sinon `null`.
DateTime? blocageVisageJusqua(Object error) {
  if (error is! DioException || error.response?.statusCode != 409) return null;
  final data = error.response?.data;
  if (data is! Map || data['code'] != 'POINTAGE_BLOQUE_VISAGE') return null;
  // Blocage signalé sans heure exploitable : 15 minutes par défaut (durée de la règle).
  return DateTime.tryParse('${data['bloque_jusqu_a'] ?? ''}') ??
      DateTime.now().add(const Duration(minutes: 15));
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

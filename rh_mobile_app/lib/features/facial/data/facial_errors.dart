import 'facial_repository.dart';

/// Messages français pour les erreurs nommées du contrat (ADR-M09-FACE).
String messageErreurFaciale(Object error) {
  if (error is! FacialApiException) return 'Une erreur est survenue. Réessayez.';
  switch (error.code) {
    case 'IDENTITE_INCOHERENTE':
      return 'Votre session ne correspond pas à cette fiche. Reconnectez-vous.';
    case 'AUCUNE_VERSION_PUBLIEE':
      return 'Le texte de consentement n’est pas encore publié. Réessayez plus tard.';
    case 'VERIFICATION_FACIALE_INACTIVE':
      return 'La vérification faciale n’est pas activée pour le moment.';
    case 'DEJA_ENROLE':
      return 'Vous êtes déjà enrôlé sur cet appareil.';
    case 'REENROLEMENT_A_DEBLOQUER':
      return 'Un nouvel enrôlement exige un déblocage par les RH. '
          'Demandez le ré-enrôlement depuis votre profil.';
    case 'APPAREIL_INCOMPATIBLE':
      return 'Cet appareil ne peut pas être enrôlé. Le pointage QR + GPS reste possible.';
    case 'VERSION_CONSENTEMENT_OBSOLETE':
      return 'Une nouvelle version du texte de consentement a été publiée. Relisez-la avant de continuer.';
    case 'VIVACITE_ECHOUEE':
      return 'Le contrôle du vivant a échoué. Réessayez.';
    case 'RECONSENTEMENT_NON_ATTENDU':
      return 'Aucun nouveau consentement n’est attendu.';
    case 'DECISION_DEJA_ENREGISTREE':
      return 'Votre décision est déjà enregistrée.';
    case 'AUCUN_CONSENTEMENT_ACTIF':
      return 'Aucun consentement actif à retirer.';
    case 'STATUT_INCOMPATIBLE':
      return 'Cette opération n’est pas possible dans votre situation actuelle.';
    case 'DEMANDE_DEJA_EN_COURS':
      return 'Une demande de ré-enrôlement est déjà en cours. Les RH doivent la débloquer.';
    case 'ACCESS_DENIED':
      return 'Accès refusé pour cette opération.';
  }
  return error.message;
}

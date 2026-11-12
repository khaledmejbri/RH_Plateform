// Textes en clair d'une demande de pointage manuel (D-P24, D-P24.b, D-P24.e).
// Fonctions pures : aucune carte, aucune coordonnée (C-L-03) ; tout champ absent donne `null`.

import '../../../core/format/formats.dart';
import 'presence_models.dart';

/// Longueur minimale / maximale du commentaire du valideur (D-P24.b), comptée après `trim`.
const int commentaireValideurMin = 10;
const int commentaireValideurMax = 500;

String _deux(int n) => n.toString().padLeft(2, '0');

String _metres(double m) => '${m.round()} m';

/// « 11/11 à 08:02 » en heure d'Africa/Tunis (UTC+1 fixe, sans heure d'été), quel que soit le fuseau de l'appareil.
String jourHeureTunis(DateTime d) {
  final t = d.toUtc().add(const Duration(hours: 1));
  return '${_deux(t.day)}/${_deux(t.month)} à ${_deux(t.hour)}:${_deux(t.minute)}';
}

String _jourHeure(DateTime d) => jourHeureTunis(d);

/// « 280 m du site (zone de 50 m) ».
String? libellePosition(DemandeManuelle d) {
  final dist = d.distanceMetres;
  if (dist == null) return null;
  final rayon = d.rayonMetres;
  return rayon == null ? '${_metres(dist)} du site' : '${_metres(dist)} du site (zone de ${_metres(rayon)})';
}

/// « ± 35 m ».
String? libellePrecision(DemandeManuelle d) =>
    d.precisionMetres == null ? null : '± ${_metres(d.precisionMetres!)}';

/// « Demandée 1 h 40 après la tentative » (D-P24.e : minutes entières, « 40 min » / « 1 h 40 »).
String? libelleDelaiDemande(DemandeManuelle d) => d.delaiDemandeMinutes == null
    ? null
    : 'Demandée ${formatDuree(d.delaiDemandeMinutes!)} après la tentative';

/// « Décidée 40 min après la tentative ».
String? libelleDelaiDecision(DemandeManuelle d) => d.delaiDecisionMinutes == null
    ? null
    : 'Décidée ${formatDuree(d.delaiDecisionMinutes!)} après la tentative';

/// « Expire le 11/11 à 08:02 » (en attente), « Expirée depuis le 11/11 à 08:02 » (EXPIREE) ; `null` sinon.
String? libelleEcheance(DemandeManuelle d) {
  final e = d.echeance;
  if (e == null) return null;
  if (d.expiree) return 'Expirée depuis le ${_jourHeure(e)}';
  if (d.enAttente) return 'Expire le ${_jourHeure(e)}';
  return null;
}

/// « 2 validés ce mois-ci, seuil de 3 » (D-P04).
String? libelleManuelsDuMois(DemandeManuelle d) {
  final n = d.manuelsDuMois;
  if (n == null) return null;
  final seuil = d.seuilMois;
  return seuil == null ? '$n ce mois-ci' : '$n sur $seuil autorisés ce mois-ci';
}

/// « 2 refus de pointage aujourd’hui » (compteur D-P20).
String? libelleRefusDuJour(DemandeManuelle d) {
  final n = d.refusDuJour;
  if (n == null) return null;
  return '$n refus de pointage aujourd’hui';
}

/// Lignes (libellé, valeur) réservées au valideur. Vides pour le demandeur : le serveur n'envoie rien (D-P24.d).
List<(String, String)> lignesInfosValideur(DemandeManuelle d) {
  final lignes = <(String, String)>[];
  void ajouter(String libelle, String? valeur) {
    if (valeur != null && valeur.isNotEmpty) lignes.add((libelle, valeur));
  }

  ajouter('Position', libellePosition(d));
  ajouter('Précision GPS', libellePrecision(d));
  ajouter('Mode QR', d.modeQrLibelle);
  ajouter('Délai', libelleDelaiDemande(d));
  ajouter('Pointages manuels', libelleManuelsDuMois(d));
  ajouter('Refus du jour', libelleRefusDuJour(d));
  return lignes;
}

/// Contrôle local du commentaire de validation (D-P24.b). `null` = valide.
///
/// Avec signal : obligatoire. Sans signal : facultatif, mais s'il est saisi il fait au moins 10 caractères
/// (le serveur répond 400 `COMMENTAIRE_TROP_COURT` de 1 à 9). Longueur comptée après `trim`.
String? erreurCommentaireValidation(String brut, {required bool avecSignal}) {
  final t = brut.trim();
  if (t.isEmpty) {
    return avecSignal ? 'Un commentaire est obligatoire pour valider une demande signalée.' : null;
  }
  if (t.length < commentaireValideurMin) {
    return 'Le commentaire doit faire au moins $commentaireValideurMin caractères.';
  }
  if (t.length > commentaireValideurMax) {
    return 'Le commentaire ne peut pas dépasser $commentaireValideurMax caractères.';
  }
  return null;
}

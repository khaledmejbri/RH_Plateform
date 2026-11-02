import 'heures_sup_models.dart';

/// Durées d'ouverture proposées : de 30 min à 4 h, par pas de 30 min (D-P13).
const dureesOuvertureMinutes = [30, 60, 90, 120, 150, 180, 210, 240];
const maxBeneficiaires = 50;
const maxJoursPlage = 31;

/// Garde-fous de saisie (confort de l'utilisateur) : le serveur reste l'autorité et son message prime.
/// Retourne le premier message d'erreur en français, ou `null` si le formulaire est complet.
String? validerFormulaireHeuresSup({
  required int nbBeneficiaires,
  required String? siteId,
  required DateTime? dateDebut,
  required DateTime? dateFin,
  required int avantMinutes,
  required int apresMinutes,
  required String motif,
}) {
  if (nbBeneficiaires < 1) return 'Sélectionnez au moins un collaborateur.';
  if (nbBeneficiaires > maxBeneficiaires) {
    return 'Une demande ne peut pas dépasser $maxBeneficiaires collaborateurs.';
  }
  if (siteId == null || siteId.isEmpty) return 'Choisissez un site.';
  if (dateDebut == null) return 'Choisissez la date de début.';
  if (dateFin != null) {
    final d = DateTime.utc(dateDebut.year, dateDebut.month, dateDebut.day);
    final f = DateTime.utc(dateFin.year, dateFin.month, dateFin.day);
    if (f.isBefore(d)) return 'La date de fin précède la date de début.';
    // Jours calendaires inclus, calculés en UTC (pas d'effet d'heure d'été).
    final jours = f.difference(d).inDays + 1;
    if (jours > maxJoursPlage) return 'La période ne peut pas dépasser $maxJoursPlage jours.';
  }
  if (avantMinutes == 0 && apresMinutes == 0) {
    return 'Choisissez une ouverture avant et/ou après l’horaire.';
  }
  for (final m in [avantMinutes, apresMinutes]) {
    if (m != 0 && !dureesOuvertureMinutes.contains(m)) {
      return 'Durée d’ouverture invalide (de 30 min à 4 h, par pas de 30 min).';
    }
  }
  if (motif.trim().isEmpty) return 'Le motif est obligatoire.';
  return null;
}

/// Construit la requête à partir de la saisie (une date de fin égale au début est omise).
CreationHeuresSupRequest construireDemande({
  required Iterable<String> collaborateurIds,
  required String siteId,
  required DateTime dateDebut,
  required DateTime? dateFin,
  required int avantMinutes,
  required int apresMinutes,
  required String motif,
}) {
  final memeJour = dateFin != null && dateApi(dateFin) == dateApi(dateDebut);
  return CreationHeuresSupRequest(
    collaborateurIds: collaborateurIds.toList(),
    siteId: siteId,
    dateDebut: dateDebut,
    dateFin: memeJour ? null : dateFin,
    ouvertureAvantMinutes: avantMinutes,
    ouvertureApresMinutes: apresMinutes,
    motif: motif,
  );
}

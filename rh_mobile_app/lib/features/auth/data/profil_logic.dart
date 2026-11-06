import '../../demandes_admin/data/demande_admin_models.dart';
import '../../organigramme/data/organigramme_models.dart';

/// Statuts M01 comptés dans « Demandes en cours » (D-T02.b), comme l'onglet « En cours » de « Mes demandes ».
const statutsDemandeEnCours = {'EN_VALIDATION_SUPERIEUR', 'EN_VALIDATION_RRH'};

int compterDemandesEnCours(Iterable<DemandeAdminItem> demandes) =>
    demandes.where((d) => statutsDemandeEnCours.contains(d.statut)).length;

/// Texte « Mon responsable » : même source que l'encart de l'organigramme (manager du nœud d'affectation).
/// Aucun nœud, aucun manager ou nom vide → « Non renseigné ».
String libelleResponsable(List<OrganigrammeNoeud> racines, String moiId) {
  if (moiId.isEmpty) return 'Non renseigné';
  final manager = _noeudAffectation(racines, moiId)?.manager;
  if (manager == null) return 'Non renseigné';
  if (manager.identifiant == moiId) return 'Vous êtes le responsable de votre unité';
  final nom = manager.displayName;
  return nom.isEmpty ? 'Non renseigné' : nom;
}

OrganigrammeNoeud? _noeudAffectation(List<OrganigrammeNoeud> noeuds, String moiId) {
  for (final n in noeuds) {
    if (n.membres.any((m) => m.identifiant == moiId)) return n;
    final sous = _noeudAffectation(n.enfants, moiId);
    if (sous != null) return sous;
  }
  // Repli : nœud dont je suis le manager sans figurer parmi les membres.
  for (final n in noeuds) {
    if (n.manager?.identifiant == moiId) return n;
  }
  return null;
}

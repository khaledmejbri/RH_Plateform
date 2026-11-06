import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/auth/data/profil_logic.dart';
import 'package:rh_mobile_app/features/demandes_admin/data/demande_admin_models.dart';
import 'package:rh_mobile_app/features/organigramme/data/organigramme_models.dart';

OrganigrammeMembre membre(String id, String prenom, String nom) =>
    OrganigrammeMembre(identifiant: id, matricule: id, prenom: prenom, nom: nom);

OrganigrammeNoeud noeud({
  required String id,
  OrganigrammeMembre? manager,
  List<OrganigrammeMembre> membres = const [],
  List<OrganigrammeNoeud> enfants = const [],
}) =>
    OrganigrammeNoeud(
      identifiant: id,
      code: id,
      libelle: id,
      typeNoeud: 'SERVICE',
      actif: true,
      manager: manager,
      membres: membres,
      enfants: enfants,
    );

DemandeAdminItem demande(String statut) => DemandeAdminItem.fromJson({'identifiant': 'x', 'statut': statut});

void main() {
  group('libelleResponsable', () {
    test('manager du nœud d\'affectation', () {
      final racines = [
        noeud(id: 'dir', enfants: [
          noeud(id: 'svc', manager: membre('m1', 'Omar', 'Belhadj'), membres: [membre('c1', 'Sarra', 'Khemiri')]),
        ]),
      ];
      expect(libelleResponsable(racines, 'c1'), 'Omar Belhadj');
    });

    test('sans manager ou collaborateur introuvable → Non renseigné', () {
      final racines = [noeud(id: 'svc', membres: [membre('c1', 'A', 'B')])];
      expect(libelleResponsable(racines, 'c1'), 'Non renseigné');
      expect(libelleResponsable(racines, 'inconnu'), 'Non renseigné');
      expect(libelleResponsable(racines, ''), 'Non renseigné');
    });

    test('je suis le responsable', () {
      final moi = membre('c1', 'A', 'B');
      final racines = [noeud(id: 'svc', manager: moi, membres: [moi])];
      expect(libelleResponsable(racines, 'c1'), contains('responsable'));
    });
  });

  group('compterDemandesEnCours', () {
    test('compte seulement EN_VALIDATION_SUPERIEUR et EN_VALIDATION_RRH', () {
      final n = compterDemandesEnCours([
        demande('EN_VALIDATION_SUPERIEUR'),
        demande('EN_VALIDATION_RRH'),
        demande('APPROUVEE'),
        demande('REFUSEE'),
        demande('ANNULEE'),
      ]);
      expect(n, 2);
    });
    test('liste vide → 0 (jamais une valeur figée)', () {
      expect(compterDemandesEnCours(const []), 0);
    });
  });
}

import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/dossier/data/dossier_models.dart';

void main() {
  test('hiérarchie : chemin, valideur et encadrés', () {
    final h = Hierarchie.fromJson({
      'collaborateur': {
        'collaborateur_id': 'c',
        'nom_complet': 'Omar Belhadj',
        'poste_libelle': 'Chef de service',
      },
      'chemin': [
        {'unite_id': 'u1', 'libelle': 'Direction Technique', 'type_libelle': 'Direction'},
        {'unite_id': 'u2', 'libelle': 'Service Web', 'type_libelle': 'Service'},
      ],
      'responsable_direct': {'collaborateur_id': 'r', 'nom_complet': 'Youssef Chaabane'},
      'valideur': {'collaborateur_id': 'r', 'nom_complet': 'Youssef Chaabane'},
      'valideur_est_rh': false,
      'collaborateurs_encadres': [
        {
          'collaborateur_id': 'a',
          'nom_complet': 'Sarra Khemiri',
          'poste_libelle': 'Développeuse',
          'unite_libelle': 'Service Web',
        },
      ],
    });
    expect(h.cheminAffiche, 'Direction Technique › Service Web');
    expect(h.valideurAffiche, 'Youssef Chaabane');
    expect(h.collaborateursEncadres.single.sousTitre, 'Développeuse · Service Web');
  });

  test('sans valideur : demandes à la RH', () {
    final h = Hierarchie.fromJson({'valideur': null, 'valideur_est_rh': true});
    expect(h.valideurAffiche, 'Aucun supérieur — demandes à la RH');
    expect(h.collaborateursEncadres, isEmpty);
  });

  test('fiche annuaire : responsable optionnel', () {
    final f = FicheAnnuaire.fromJson({
      'collaborateur_id': 'c',
      'nom_complet': 'Omar Belhadj',
      'poste_libelle': 'Chef de service',
      'intitule_role': 'Responsable de service',
      'unite_libelle': 'Service Web',
      'responsable': {'collaborateur_id': 'r', 'nom_complet': 'Youssef Chaabane'},
      'courriel_professionnel': 'omar@rh-evenement.test',
    });
    expect(f.fonction, 'Responsable de service');
    expect(f.responsableNom, 'Youssef Chaabane');
    final sans = FicheAnnuaire.fromJson({'collaborateur_id': 'x', 'nom_complet': 'A B'});
    expect(sans.responsableNom, isNull);
    expect(sans.courriel, isNull);
  });

  test('fiche d’équipe : demandes en libellés', () {
    final f = FicheEquipe.fromJson({
      'annuaire': {'collaborateur_id': 'c', 'nom_complet': 'Sarra Khemiri'},
      'demandes': [
        {
          'identifiant': 'd1',
          'numero': 'DEM-202611-0003',
          'type_libelle': 'Congé annuel',
          'statut_libelle': 'Approuvée',
          'periode_debut': '2026-11-25',
          'periode_fin': '2026-11-27',
        },
      ],
    });
    expect(f.demandes.single.numero, 'DEM-202611-0003');
    expect(f.demandes.single.statutLibelle, 'Approuvée');
  });

  test('routePourPersonne : équipe si valideur, annuaire sinon', () {
    expect(routePourPersonne('a', {'a', 'b'}), '/equipe/a');
    expect(routePourPersonne('z', {'a', 'b'}), '/annuaire/z');
    expect(routePourPersonne('z', <String>{}), '/annuaire/z');
  });
}

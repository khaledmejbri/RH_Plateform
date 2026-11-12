import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/features/pointage/data/journee_models.dart';

void main() {
  final json = <String, dynamic>{
    'journee': '2026-11-05',
    'site_libelle': 'Siège',
    'temps_minutes': 482,
    'retard_minutes': 12,
    'etat': 'COMPLET',
    'etat_libelle': 'Complet',
    'pointages': [
      {
        'id': 'p1',
        'type': 'ENTREE',
        'type_libelle': 'Entrée',
        'statut': 'REJETE_HORS_ZONE',
        'statut_libelle': 'Refusé - hors zone',
        'server_ts': '2026-11-05T08:05:00',
        'manuel': false,
        'motif_rejet': 'Hors zone de pointage (179,8 m pour une zone de 50 m)',
        'manuel_possible': true,
      },
      {
        'id': 'p2',
        'type': 'ENTREE',
        'statut': 'REJETE_HORS_HORAIRE',
        'manuel_possible': false,
        'manuel_impossible_motif': 'pointage hors de l’horaire du site',
      },
      {
        'id': 'p3',
        'type': 'ENTREE',
        'statut': 'REJETE_HORS_ZONE',
      },
    ],
  };

  test('lit une journée avec ses pointages', () {
    final j = JourneePresence.fromJson(json);
    expect(j.tempsMinutes, 482);
    expect(j.retardMinutes, 12);
    expect(j.etatAffiche, 'Complet');
    expect(j.pointages, hasLength(3));
  });

  test('motif : texte du serveur prioritaire, libellé du statut sinon', () {
    final j = JourneePresence.fromJson(json);
    expect(j.pointages[0].motifAffiche, 'Hors zone de pointage (179,8 m pour une zone de 50 m)');
    expect(j.pointages[1].motifAffiche, contains('horaires'));
  });

  test('bouton de demande piloté par manuel_possible, motif sinon, rien si le champ est absent', () {
    final j = JourneePresence.fromJson(json);
    expect(j.pointages[0].manuelPossible, isTrue);
    expect(j.pointages[1].manuelPossible, isFalse);
    expect(j.pointages[1].manuelImpossibleMotif, 'pointage hors de l’horaire du site');
    expect(j.pointages[2].manuelPossible, isFalse, reason: 'champ absent : pas de bouton');
    expect(j.pointages[2].manuelImpossibleMotif, isNull);
  });

  test('retard null (site sans horaire) distinct de 0', () {
    final sans = JourneePresence.fromJson({'journee': '2026-11-05', 'retard_minutes': null});
    final zero = JourneePresence.fromJson({'journee': '2026-11-05', 'retard_minutes': 0});
    expect(sans.retardMinutes, isNull);
    expect(zero.retardMinutes, 0);
  });

  test('pointage valide : pas de motif, statut en clair', () {
    final p = PointageJournee.fromJson({'id': 'p', 'type': 'SORTIE', 'statut': 'VALIDE'});
    expect(p.motifAffiche, isNull);
    expect(p.statutAffiche, 'Validé');
    expect(p.typeAffiche, 'Sortie');
  });

  test('état sans libellé du serveur : table de libellés', () {
    final j = JourneePresence.fromJson({'journee': '2026-11-05', 'etat': 'INCOMPLET'});
    expect(j.etatAffiche, 'Pointage incomplet');
  });

  test('synthèse et présence d’équipe', () {
    final p = PresenceEquipe.fromJson({
      'collaborateur': {'collaborateur_id': 'c', 'nom_complet': 'Sarra Khemiri'},
      'synthese': {
        'journees_pointees': 18,
        'temps_total_minutes': 9012,
        'retards_nombre': 3,
        'retards_cumul_minutes': 41,
      },
      'journees': [json],
      'pointages_manuels': <dynamic>[],
    });
    expect(p.nomComplet, 'Sarra Khemiri');
    expect(p.synthese.retardsNombre, 3);
    expect(p.synthese.retardsCumulMinutes, 41);
    expect(p.journees, hasLength(1));
  });

  test('détail de pointage manuel : valideur absent = RH', () {
    final d = DemandeManuelleDetail.fromJson({
      'id': 'd1',
      'statut': 'EN_ATTENTE',
      'type': 'ENTREE',
      'justification': 'Signal GPS trop faible',
      'site_libelle': 'Siège',
      'pointage_rejete': {
        'statut': 'REJETE_HORS_ZONE',
        'motif_rejet': 'Hors zone',
        'distance_metres': 179.8,
      },
      'seuil_mois': 3,
    });
    expect(d.valideurAffiche, 'les RH');
    expect(d.motifRejetAutomatique, 'Hors zone');
    expect(d.distanceMetres, 179.8);
    expect(d.siteLibelle, 'Siège');
  });
}

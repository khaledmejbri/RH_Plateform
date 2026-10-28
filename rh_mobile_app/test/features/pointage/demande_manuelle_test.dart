import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';

void main() {
  test('DemandeManuelle : valideur responsable', () {
    final d = DemandeManuelle.fromJson({
      'id': 'd1',
      'statut': 'EN_ATTENTE',
      'type': 'ENTREE',
      'heure_declaree': '2026-10-27T08:05:00Z',
      'justification': 'GPS faible au rez-de-chaussée',
      'valideur_nom': 'Omar Belhadj',
      'validation_par_rh': false,
    });
    expect(d.enAttente, isTrue);
    expect(d.valideurLibelle, 'Omar Belhadj');
    expect(d.typeLibelle, 'Entrée');
  });

  test('DemandeManuelle : sans responsable → les RH', () {
    final d = DemandeManuelle.fromJson({
      'id': 'd2',
      'statut': 'REFUSEE',
      'type': 'SORTIE',
      'validation_par_rh': true,
      'motif_refus': 'Absent',
    });
    expect(d.valideurLibelle, 'les RH');
    expect(d.statutLibelle, 'Refusée');
  });

  test('PointageResult garde l’identifiant du rejet', () {
    final r = PointageResult.fromJson({'id': 'p1', 'statut': 'REJETE_HORS_ZONE'});
    expect(r.id, 'p1');
    expect(r.isRejet, isTrue);
  });
}

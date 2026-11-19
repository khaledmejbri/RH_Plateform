import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/conges/data/solde_models.dart';
import 'package:rh_mobile_app/features/dossier/data/champs_perso_models.dart';

void main() {
  test('V4 : champs lus tels quels, valeur déjà formatée non arrondie', () {
    final l = ChampPersoAffiche.listeDepuisReponse({
      'champs': [
        {'code': 'PERSO_POINTURE', 'libelle': 'Pointure', 'type_valeur': 'NOMBRE', 'valeur_affichee': '42,50'},
        {'code': 'PERSO_DATE', 'libelle': 'Date', 'type_valeur': 'DATE', 'valeur_affichee': '15/11/2026'},
      ],
    });
    expect(l, hasLength(2));
    expect(l.first.valeurAffichee, '42,50');
    expect(l.last.valeurAffichee, '15/11/2026');
  });

  test('clé absente, null ou corps inattendu : liste vide', () {
    expect(ChampPersoAffiche.listeDepuisReponse(<String, dynamic>{}), isEmpty);
    expect(ChampPersoAffiche.listeDepuisReponse({'champs': null}), isEmpty);
    expect(ChampPersoAffiche.listeDepuisReponse('x'), isEmpty);
    expect(ChampPersoAffiche.listeDepuisReponse(null), isEmpty);
  });

  test('champ sans libellé : libellé de repli, valeur vide = null', () {
    final c = ChampPersoAffiche.fromJson({'code': 'A', 'valeur_affichee': '  '});
    expect(c.libelle, 'Information');
    expect(c.valeurAffichee, isNull);
  });

  test('mouvement : valeurs de champs personnalisés, absentes = vide', () {
    final m = MouvementSolde.fromJson({
      'type': 'AJUSTEMENT',
      'nombre': 2,
      'champs_personnalises': [
        {'code': 'X', 'libelle': 'Référence décision', 'valeur_affichee': 'DG-2026-15'},
        {'code': 'Y', 'libelle': '', 'valeur_affichee': 'ignoré'},
      ],
    });
    expect(m.champsPersonnalises.map((v) => v.ligne), ['Référence décision : DG-2026-15']);
    expect(MouvementSolde.fromJson({'type': 'ACQUISITION', 'nombre': 1}).champsPersonnalises, isEmpty);
  });
}

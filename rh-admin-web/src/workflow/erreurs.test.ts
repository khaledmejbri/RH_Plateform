import { describe, expect, it } from 'vitest';
import { grouperErreursParEtape, rattacherErreurs } from './erreurs';

describe('erreurs — rattacherErreurs (D-W7-05)', () => {
  it('extrait l’index d’étape et le sous-champ', () => {
    expect(rattacherErreurs('etapes[2].intervenants.regle')).toEqual({
      indexEtape: 2,
      champ: 'intervenants.regle',
    });
    expect(rattacherErreurs('etapes[0].code')).toEqual({
      indexEtape: 0,
      champ: 'code',
    });
    expect(rattacherErreurs('etapes[1]')).toEqual({
      indexEtape: 1,
      champ: null,
    });
  });

  it('rattache les chemins globaux à indexEtape=null', () => {
    expect(rattacherErreurs('etapes')).toEqual({ indexEtape: null, champ: 'etapes' });
    expect(rattacherErreurs('options.separation_des_taches')).toEqual({
      indexEtape: null,
      champ: 'options.separation_des_taches',
    });
    expect(rattacherErreurs(null)).toEqual({ indexEtape: null, champ: null });
  });

  it('groupe les erreurs par étape et globales (-1)', () => {
    const m = grouperErreursParEtape([
      { chemin: 'etapes', code: 'ETAPES_VIDE', niveau: 'ERREUR', message: 'Aucune étape' },
      { chemin: 'etapes[0].libelle', code: 'LIBELLE_VIDE', niveau: 'ERREUR', message: 'Libellé requis' },
      { chemin: 'etapes[0].delai.duree', code: 'DELAI_INVALIDE', niveau: 'AVERTISSEMENT', message: 'Délai court' },
    ]);
    expect(m.get(-1)).toHaveLength(1);
    expect(m.get(0)).toHaveLength(2);
  });
});

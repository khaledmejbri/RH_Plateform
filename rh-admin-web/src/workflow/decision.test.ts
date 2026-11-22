import { describe, expect, it } from 'vitest';
import { motifValide, regleMotif } from './decision';

describe('decision — regleMotif', () => {
  it('REFUSER et RENVOYER exigent 5 à 500 caractères', () => {
    expect(regleMotif('REFUSER', false)).toEqual({ obligatoire: true, min: 5, max: 500 });
    expect(regleMotif('RENVOYER', false)).toEqual({ obligatoire: true, min: 5, max: 500 });
    expect(regleMotif('REFUSER', true).min).toBe(5);
  });

  it('APPROUVER avec commentaire obligatoire passe le seuil à 10', () => {
    expect(regleMotif('APPROUVER', true)).toEqual({ obligatoire: true, min: 10, max: 500 });
    expect(regleMotif('APPROUVER', false).obligatoire).toBe(false);
    expect(regleMotif('FAIT', false).obligatoire).toBe(false);
  });
});

describe('decision — motifValide', () => {
  it('compte les caractères sans les espaces de bord', () => {
    const r = regleMotif('REFUSER', false);
    expect(motifValide('   abcd  ', r)).toBe(false);
    expect(motifValide(' abcde ', r)).toBe(true);
    expect(motifValide('x'.repeat(500), r)).toBe(true);
    expect(motifValide('x'.repeat(501), r)).toBe(false);
  });

  it('un commentaire facultatif peut être vide', () => {
    expect(motifValide('', regleMotif('APPROUVER', false))).toBe(true);
  });
});

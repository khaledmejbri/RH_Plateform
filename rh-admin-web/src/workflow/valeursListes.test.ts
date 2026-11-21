import { describe, expect, it } from 'vitest';
import { estCodeListeValide, referentielPourVariable } from './valeursListes';

describe('valeursListes — correspondance et validation de codes (D-W7-07)', () => {
  it('associe type_conge et type_document à leurs référentiels', () => {
    expect(referentielPourVariable('type_conge')).toBe('TYPE_CONGE');
    expect(referentielPourVariable('type_document')).toBe('TYPE_DOCUMENT');
    expect(referentielPourVariable('origine')).toBeNull();
  });

  it('valide les codes techniques selon ^[A-Z0-9_]{1,40}$', () => {
    expect(estCodeListeValide('ANNUEL')).toBe(true);
    expect(estCodeListeValide('CONGE_2026')).toBe(true);
    expect(estCodeListeValide('a')).toBe(false);
    expect(estCodeListeValide('avec espace')).toBe(false);
    expect(estCodeListeValide('')).toBe(false);
    expect(estCodeListeValide('A'.repeat(41))).toBe(false);
  });
});

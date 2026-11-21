import { describe, expect, it } from 'vitest';
import { controlerRepartition, heuresVersMinutes, minutesVersHeuresTexte } from './societeUtils';

describe('societeUtils — conversions heures ⇄ minutes', () => {
  it('convertit des heures avec virgule ou point en minutes', () => {
    expect(heuresVersMinutes('40')).toBe(2400);
    expect(heuresVersMinutes('7,5')).toBe(450);
    expect(heuresVersMinutes('8.25')).toBe(495);
    expect(heuresVersMinutes('0')).toBe(0);
    expect(heuresVersMinutes('')).toBeNull();
    expect(heuresVersMinutes('-2')).toBeNull();
    expect(heuresVersMinutes('abc')).toBeNull();
  });

  it('formate des minutes en heures avec virgule française', () => {
    expect(minutesVersHeuresTexte(2400)).toBe('40');
    expect(minutesVersHeuresTexte(450)).toBe('7,5');
    expect(minutesVersHeuresTexte(480)).toBe('8');
    expect(minutesVersHeuresTexte(null)).toBe('');
  });
});

describe('societeUtils — controlerRepartition', () => {
  it('valide une répartition conforme à la durée hebdomadaire', () => {
    const res = controlerRepartition(
      ['LUN', 'MAR', 'MER', 'JEU', 'VEN'],
      2400,
      { LUN: 480, MAR: 480, MER: 480, JEU: 480, VEN: 480 },
    );
    expect(res.valide).toBe(true);
    expect(res.ecartMinutes).toBe(0);
    expect(res.message).toBeNull();
  });

  it('signale un écart entre la somme journalière et la durée hebdomadaire', () => {
    const res = controlerRepartition(
      ['LUN', 'MAR', 'MER', 'JEU', 'VEN'],
      2400,
      { LUN: 480, MAR: 480, MER: 480, JEU: 480, VEN: 420 },
    );
    expect(res.valide).toBe(false);
    expect(res.sommeMinutes).toBe(2340);
    expect(res.ecartMinutes).toBe(-60);
    expect(res.message).toContain('39');
    expect(res.message).toContain('40');
  });

  it('refuse une liste vide de jours travaillés ou un jour à 0', () => {
    expect(controlerRepartition([], 2400, {}).valide).toBe(false);
    expect(controlerRepartition(['LUN'], 480, { LUN: 0 }).valide).toBe(false);
  });
});

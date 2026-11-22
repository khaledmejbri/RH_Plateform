import { describe, expect, it } from 'vitest';
import {
  controlerRepartition,
  erreurDureeHebdo,
  erreurDureeSortie,
  heuresVersMinutes,
  minutesVersHeuresTexte,
} from './societeUtils';

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

describe('societeUtils — bornes alignées sur le serveur', () => {
  it('durée hebdomadaire : 60 à 3600 minutes par pas de 30', () => {
    expect(erreurDureeHebdo(2400)).toBeNull();
    expect(erreurDureeHebdo(60)).toBeNull();
    expect(erreurDureeHebdo(3600)).toBeNull();
    expect(erreurDureeHebdo(30)).not.toBeNull();
    expect(erreurDureeHebdo(3630)).not.toBeNull();
    expect(erreurDureeHebdo(4320)).not.toBeNull();
    expect(erreurDureeHebdo(2410)).not.toBeNull();
    expect(erreurDureeHebdo(null)).not.toBeNull();
  });

  it('durée de sortie : 30 à 480 minutes par pas de 15', () => {
    expect(erreurDureeSortie(30)).toBeNull();
    expect(erreurDureeSortie(120)).toBeNull();
    expect(erreurDureeSortie(480)).toBeNull();
    expect(erreurDureeSortie(15)).not.toBeNull();
    expect(erreurDureeSortie(100)).not.toBeNull();
    expect(erreurDureeSortie(495)).not.toBeNull();
  });

  it('jour travaillé : 1 à 720 minutes', () => {
    expect(controlerRepartition(['LUN'], 720, { LUN: 720 }).valide).toBe(true);
    expect(controlerRepartition(['LUN'], 780, { LUN: 780 }).valide).toBe(false);
  });
});

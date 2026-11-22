import { describe, expect, it } from 'vitest';
import type { EtapeDefinition } from '../api/workflowApi';
import {
  completerEtape,
  nouvelleEtapeParDefaut,
  nouvelleTacheParDefaut,
  normaliserEtapePourType,
  optionsPublics,
  premierCodeLibre,
} from './etapes';

const VALIDATION_COMPLETE: EtapeDefinition = {
  code: 'ETAPE_1',
  libelle: 'Validation RH',
  type: 'VALIDATION',
  intervenants: { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'TOUS' },
  repli_si_vide: { action: 'BLOQUER' },
  actions: ['APPROUVER', 'REFUSER', 'RENVOYER'],
  controle_avant_approbation: true,
  delai: { duree: 48, unite: 'HEURES' },
  a_echeance: { action: 'RIEN' },
  notifications: { a_l_activation: ['DEMANDEUR'] },
};

describe('etapes — premierCodeLibre', () => {
  it('prend le premier code libre et non la longueur de la liste', () => {
    expect(premierCodeLibre('ETAPE', [])).toBe('ETAPE_1');
    expect(premierCodeLibre('ETAPE', ['ETAPE_1', 'ETAPE_3'])).toBe('ETAPE_2');
    expect(premierCodeLibre('ETAPE', ['ETAPE_2'])).toBe('ETAPE_1');
    expect(premierCodeLibre('TACHE', ['TACHE_1', 'TACHE_2'])).toBe('TACHE_3');
  });

  it('une nouvelle étape ne duplique jamais un code existant après suppression', () => {
    const e = nouvelleEtapeParDefaut(['ETAPE_1', 'ETAPE_3']);
    expect(e.code).toBe('ETAPE_2');
    expect(nouvelleTacheParDefaut(['TACHE_2']).code).toBe('TACHE_1');
  });
});

describe('etapes — normaliserEtapePourType (DefinitionValidateur)', () => {
  it('VALIDATION vers TACHE retire mode, actions et contrôle, garde le reste', () => {
    const r = normaliserEtapePourType(VALIDATION_COMPLETE, 'TACHE');
    expect(r.type).toBe('TACHE');
    expect(r.intervenants).toEqual({ regle: 'ROLE', params: { roles: ['RH'] } });
    expect(r.actions).toBeUndefined();
    expect(r.controle_avant_approbation).toBeUndefined();
    expect(r.repli_si_vide).toEqual({ action: 'BLOQUER' });
    expect(r.delai).toEqual({ duree: 48, unite: 'HEURES' });
    expect(r.notifications).toEqual({ a_l_activation: ['DEMANDEUR'] });
  });

  it('vers INFORMATION retire intervenants et repli, garde le public', () => {
    const r = normaliserEtapePourType({ ...VALIDATION_COMPLETE, public: ['DEMANDEUR'] }, 'INFORMATION');
    expect(r.intervenants).toBeUndefined();
    expect(r.repli_si_vide).toBeUndefined();
    expect(r.actions).toBeUndefined();
    expect(r.public).toEqual(['DEMANDEUR']);
  });

  it('vers TACHES_PARALLELES retire intervenants et repli et crée deux tâches distinctes', () => {
    const r = normaliserEtapePourType(VALIDATION_COMPLETE, 'TACHES_PARALLELES');
    expect(r.intervenants).toBeUndefined();
    expect(r.repli_si_vide).toBeUndefined();
    expect(r.taches).toHaveLength(2);
    expect(new Set(r.taches?.map((t) => t.code)).size).toBe(2);
  });

  it('vers VALIDATION écrit les valeurs par défaut affichées', () => {
    const tache: EtapeDefinition = { code: 'ETAPE_2', libelle: 'Faire', type: 'TACHE' };
    const r = normaliserEtapePourType(tache, 'VALIDATION');
    expect(r.intervenants).toEqual({ regle: 'MANAGER_NOEUD', params: {}, mode: 'UN_SEUL' });
    expect(r.repli_si_vide).toEqual({ action: 'BLOQUER' });
    expect(r.actions).toEqual(['APPROUVER', 'REFUSER']);
    expect(r.delai).toEqual({ duree: 24, unite: 'HEURES' });
    expect(r.a_echeance).toEqual({ action: 'RIEN' });
  });

  it('remplace la règle DEMANDEUR, interdite en VALIDATION', () => {
    const tache: EtapeDefinition = {
      code: 'ETAPE_2',
      libelle: 'Faire',
      type: 'TACHE',
      intervenants: { regle: 'DEMANDEUR', params: {} },
    };
    expect(normaliserEtapePourType(tache, 'VALIDATION').intervenants?.regle).toBe('MANAGER_NOEUD');
  });
});

describe('etapes — escalade et actions en VALIDATION', () => {
  it('remplace une escalade vers DEMANDEUR par ROLE RH et ADMIN', () => {
    const tache: EtapeDefinition = {
      code: 'E',
      libelle: 'Étape',
      type: 'TACHE',
      a_echeance: { action: 'ESCALADER', intervenants: { regle: 'DEMANDEUR', params: {} } },
    };
    const r = normaliserEtapePourType(tache, 'VALIDATION');
    expect(r.a_echeance?.intervenants).toEqual({ regle: 'ROLE', params: { roles: ['RH', 'ADMIN'] } });
  });

  it('impose APPROUVER et REFUSER dans les actions', () => {
    const r = completerEtape({ ...VALIDATION_COMPLETE, actions: ['RENVOYER'] });
    expect(r.actions).toEqual(['APPROUVER', 'REFUSER', 'RENVOYER']);
  });
});

describe('etapes — completerEtape', () => {
  it('ne change pas une étape déjà complète et ignore INFORMATION', () => {
    expect(completerEtape(VALIDATION_COMPLETE)).toEqual(VALIDATION_COMPLETE);
    const info: EtapeDefinition = { code: 'INFO', libelle: 'Info', type: 'INFORMATION', public: ['DEMANDEUR'] };
    expect(completerEtape(info)).toBe(info);
  });

  it('TACHE : pas de mode même si une règle en portait un', () => {
    const r = completerEtape({
      code: 'T',
      libelle: 'Tâche',
      type: 'TACHE',
      intervenants: { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'UN_SEUL' },
    });
    expect(r.intervenants?.mode).toBeUndefined();
  });
});

describe('etapes — optionsPublics', () => {
  it('remplace FONCTION:<code> par les fonctions actives et garde les choix existants', () => {
    const r = optionsPublics(
      ['DEMANDEUR', 'ROLE:RH', 'FONCTION:<code>'],
      [{ code: 'DAF' }, { code: 'RRH' }],
      ['FONCTION:ANCIENNE', 'DEMANDEUR'],
    );
    expect(r).toEqual(['DEMANDEUR', 'ROLE:RH', 'FONCTION:DAF', 'FONCTION:RRH', 'FONCTION:ANCIENNE']);
  });
});

import { describe, expect, it } from 'vitest';
import type { OrganigrammeNoeud } from '../api/rhClient';
import {
  calculerCompteursStructure,
  construireIndexNoeuds,
  estARattacher,
  flattenNoeuds,
  matchesSearch,
  normaliserVueOrganigramme,
  peutRattacher,
  typesCreables,
} from './organigramme';

function noeud(partiel: Partial<OrganigrammeNoeud> & Pick<OrganigrammeNoeud, 'identifiant' | 'code' | 'libelle' | 'type_noeud'>): OrganigrammeNoeud {
  return {
    parent_identifiant: null,
    actif: true,
    manager: null,
    membres: [],
    enfants: [],
    ...partiel,
  };
}

describe('organigramme — sélecteur de vue URL', () => {
  it('retourne liste uniquement pour ?vue=liste et arbre par défaut', () => {
    expect(normaliserVueOrganigramme('liste')).toBe('liste');
    expect(normaliserVueOrganigramme('arbre')).toBe('arbre');
    expect(normaliserVueOrganigramme(null)).toBe('arbre');
    expect(normaliserVueOrganigramme('')).toBe('arbre');
    expect(normaliserVueOrganigramme('invalide')).toBe('arbre');
  });
});

describe('organigramme — règles D-H04 et compteurs', () => {
  const srv = noeud({
    identifiant: 'srv-1',
    code: 'SRV-BACKEND',
    libelle: 'Service Backend',
    type_noeud: 'Service',
    parent_identifiant: 'dept-1',
    membres: [
      { identifiant: 'c1', matricule: 'COL-1', prenom: 'Sarra', nom: 'Khemiri' },
      { identifiant: 'c2', matricule: 'COL-2', prenom: 'Ahmed', nom: 'Nasri' },
    ],
  });
  const deptARattacher = noeud({
    identifiant: 'dept-orphelin',
    code: 'DEPT-ORPH',
    libelle: 'Département À rattacher',
    type_noeud: 'Département',
    parent_identifiant: null,
  });
  const dept = noeud({
    identifiant: 'dept-1',
    code: 'DTECH-SW',
    libelle: 'Département Software',
    type_noeud: 'Département',
    parent_identifiant: 'dir-1',
    manager: { identifiant: 'cd-1', matricule: 'CD-SW', prenom: 'Youssef', nom: 'Chaabane' },
    enfants: [srv],
  });
  const dir = noeud({
    identifiant: 'dir-1',
    code: 'DTECH',
    libelle: 'Direction Technique',
    type_noeud: 'Direction',
    parent_identifiant: 'dg-1',
    enfants: [dept],
  });
  const dg = noeud({
    identifiant: 'dg-1',
    code: 'DG',
    libelle: 'Direction Générale',
    type_noeud: 'Direction Générale',
    enfants: [dir],
  });
  const inactif = noeud({
    identifiant: 'srv-inactif',
    code: 'OLD',
    libelle: 'Ancien service',
    type_noeud: 'Service',
    actif: false,
  });

  const racines = [dg, deptARattacher, inactif];
  const tous = flattenNoeuds(racines);

  it('calcule les compteurs par type sur les unités actives uniquement', () => {
    const compteurs = calculerCompteursStructure(tous, 39);
    expect(compteurs).toEqual({
      directionGenerale: 1,
      directions: 1,
      departements: 2,
      services: 1,
      aRattacher: 1,
      sansResponsable: 4,
      collaborateursActifs: 39,
    });
  });

  it('applique les règles de création et de rattachement D-H04', () => {
    expect(typesCreables(null, true)).toEqual(['Direction', 'Département', 'Service']);
    expect(typesCreables(null, false)).toEqual(['Direction Générale', 'Département', 'Service']);
    expect(typesCreables(dept, true)).toEqual(['Service']);
    expect(typesCreables(srv, true)).toEqual([]);

    expect(peutRattacher(deptARattacher, dir)).toBe(true);
    expect(peutRattacher(deptARattacher, null)).toBe(true);
    expect(peutRattacher(dir, dept)).toBe(false);
    expect(peutRattacher(dir, dg)).toBe(true);
    expect(estARattacher(deptARattacher)).toBe(true);
    expect(estARattacher(dir)).toBe(false);
  });

  it('filtre les nœuds par libellé, code, responsable ou membre sans accents', () => {
    expect(matchesSearch(srv, 'sarra')).toBe(true);
    expect(matchesSearch(dept, 'chaabane')).toBe(true);
    expect(matchesSearch(dept, 'departement')).toBe(true);
    expect(matchesSearch(srv, 'introuvable')).toBe(false);
    expect(construireIndexNoeuds(tous).get('dept-1')?.libelle).toBe('Département Software');
  });
});

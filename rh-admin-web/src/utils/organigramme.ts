import type { OrganigrammeNoeud } from '../api/rhClient';
import { correspond } from './filtres';

/** Modes d'affichage de l'écran Organigramme. */
export type VueOrganigramme = 'arbre' | 'liste';

/** D-H04 : catalogue fermé, du plus haut au plus bas niveau (même ordre que le serveur). */
export const TYPES_NOEUD = ['Direction Générale', 'Direction', 'Département', 'Service'] as const;
export const NIVEAU_DG = 0;
export const NIVEAU_DIRECTION = 1;

/** Normalise le paramètre `?vue=` de l'URL (défaut : `'arbre'`). */
export function normaliserVueOrganigramme(brut: string | null | undefined): VueOrganigramme {
  return brut === 'liste' ? 'liste' : 'arbre';
}

export function normaliserTypeNoeud(t: string): string {
  return t.normalize('NFD').replace(/\p{M}/gu, '').replace(/[\s_-]/g, '').toLowerCase();
}

/** Niveau du type (0 = Direction Générale … 3 = Service), -1 si type non reconnu. */
export function niveauType(t: string | null | undefined): number {
  if (!t) return -1;
  const cle = normaliserTypeNoeud(t);
  return TYPES_NOEUD.findIndex((x) => normaliserTypeNoeud(x) === cle);
}

/** Types qu'on peut créer sous `parent` (ou sans nœud sélectionné). La DG seulement si elle n'existe pas encore. */
export function typesCreables(parent: OrganigrammeNoeud | null, dgExiste: boolean): string[] {
  if (!parent) {
    // Une direction est rattachée d'office à la DG : il faut donc que la DG existe.
    return TYPES_NOEUD.filter((_, i) => (i === NIVEAU_DG ? !dgExiste : i !== NIVEAU_DIRECTION || dgExiste));
  }
  const niveau = niveauType(parent.type_noeud);
  if (niveau < 0) return [];
  return TYPES_NOEUD.filter((_, i) => i > niveau && (i !== NIVEAU_DIRECTION || niveau === NIVEAU_DG));
}

/** Même règle que le serveur : parent de niveau strictement supérieur ; une direction uniquement sous la DG. */
export function peutRattacher(noeud: Pick<OrganigrammeNoeud, 'type_noeud'>, parent: Pick<OrganigrammeNoeud, 'type_noeud'> | null): boolean {
  const n = niveauType(noeud.type_noeud);
  if (n < 0) return false;
  if (!parent) return n > NIVEAU_DIRECTION;
  if (n === NIVEAU_DG) return false;
  const p = niveauType(parent.type_noeud);
  if (p < 0 || p >= n) return false;
  return n !== NIVEAU_DIRECTION || p === NIVEAU_DG;
}

export function flattenNoeuds(racines: OrganigrammeNoeud[]): OrganigrammeNoeud[] {
  const out: OrganigrammeNoeud[] = [];
  const walk = (n: OrganigrammeNoeud) => {
    out.push(n);
    n.enfants.forEach(walk);
  };
  racines.forEach(walk);
  return out;
}

export function collectDescendantIds(noeud: OrganigrammeNoeud): Set<string> {
  const ids = new Set<string>();
  const walk = (n: OrganigrammeNoeud) => {
    ids.add(n.identifiant);
    n.enfants.forEach(walk);
  };
  walk(noeud);
  return ids;
}

export function findNoeud(racines: OrganigrammeNoeud[], id: string): OrganigrammeNoeud | null {
  for (const n of flattenNoeuds(racines)) {
    if (n.identifiant === id) return n;
  }
  return null;
}

export function findPathIds(racines: OrganigrammeNoeud[], targetId: string): string[] {
  const path: string[] = [];
  const walk = (nodes: OrganigrammeNoeud[], trail: string[]): boolean => {
    for (const n of nodes) {
      const next = [...trail, n.identifiant];
      if (n.identifiant === targetId) {
        path.push(...next);
        return true;
      }
      if (walk(n.enfants, next)) return true;
    }
    return false;
  };
  walk(racines, []);
  return path;
}

export function construireIndexNoeuds(noeuds: OrganigrammeNoeud[]): Map<string, OrganigrammeNoeud> {
  return new Map(noeuds.map((n) => [n.identifiant, n]));
}

/** Recherche d'une unité, d'un responsable ou d'un membre (sans accents, tous les mots). */
export function matchesSearch(n: OrganigrammeNoeud, q: string): boolean {
  if (!q.trim()) return true;
  return correspond(
    [
      n.libelle,
      n.code,
      n.type_noeud,
      n.titre_poste,
      n.manager ? `${n.manager.prenom} ${n.manager.nom}` : '',
      n.manager?.matricule,
      ...(n.membres ?? []).map((m) => `${m.prenom} ${m.nom} ${m.matricule}`),
    ],
    q,
  );
}

export function collectSearchMatchIds(racines: OrganigrammeNoeud[], q: string): Set<string> {
  const ids = new Set<string>();
  if (!q) return ids;
  for (const n of flattenNoeuds(racines)) {
    if (matchesSearch(n, q)) {
      findPathIds(racines, n.identifiant).forEach((id) => ids.add(id));
      ids.add(n.identifiant);
    }
  }
  return ids;
}

export function sansResponsable(n: OrganigrammeNoeud): boolean {
  return n.actif && !n.manager;
}

export function estARattacher(n: Pick<OrganigrammeNoeud, 'parent_identifiant' | 'type_noeud'>): boolean {
  return !n.parent_identifiant && niveauType(n.type_noeud) > NIVEAU_DIRECTION;
}

export type CompteursStructure = {
  directionGenerale: number;
  directions: number;
  departements: number;
  services: number;
  aRattacher: number;
  sansResponsable: number;
  collaborateursActifs: number;
};

/** D-H05 : compteurs par type de nœud D-H04 sur les nœuds actifs. */
export function calculerCompteursStructure(
  tousNoeuds: ReadonlyArray<OrganigrammeNoeud>,
  collaborateursActifs: number,
): CompteursStructure {
  const actifs = tousNoeuds.filter((n) => n.actif !== false);
  return {
    directionGenerale: actifs.filter((n) => niveauType(n.type_noeud) === 0).length,
    directions: actifs.filter((n) => niveauType(n.type_noeud) === 1).length,
    departements: actifs.filter((n) => niveauType(n.type_noeud) === 2).length,
    services: actifs.filter((n) => niveauType(n.type_noeud) === 3).length,
    aRattacher: actifs.filter((n) => estARattacher(n)).length,
    sansResponsable: actifs.filter((n) => !n.manager).length,
    collaborateursActifs,
  };
}

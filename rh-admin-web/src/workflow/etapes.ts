import type { EtapeDefinition, IntervenantsDefinition, TacheParalleleDefinition } from '../api/workflowApi';

/**
 * Règles d'une étape alignées sur `DefinitionValidateur` (svc-workflow) :
 * - `mode`, `actions`, `controle_avant_approbation` : VALIDATION seulement ;
 * - `intervenants` et `repli_si_vide` : VALIDATION et TACHE seulement ;
 * - 2 à 15 tâches parallèles, codes uniques.
 */

export const NB_TACHES_MIN = 2;
export const NB_TACHES_MAX = 15;
export const NB_ETAPES_MAX = 30;

/** Premier code `PREFIXE_n` (n ≥ 1) absent de `codesExistants`. */
export function premierCodeLibre(prefixe: string, codesExistants: ReadonlyArray<string>): string {
  const pris = new Set(codesExistants);
  let n = 1;
  while (pris.has(`${prefixe}_${n}`)) n += 1;
  return `${prefixe}_${n}`;
}

function numeroDuCode(code: string): number {
  const m = /_(\d+)$/.exec(code);
  return m ? Number(m[1]) : 1;
}

export function intervenantsParDefaut(avecMode: boolean): IntervenantsDefinition {
  return avecMode
    ? { regle: 'MANAGER_NOEUD', params: {}, mode: 'UN_SEUL' }
    : { regle: 'MANAGER_NOEUD', params: {} };
}

export function nouvelleTacheParDefaut(codesExistants: ReadonlyArray<string>): TacheParalleleDefinition {
  const code = premierCodeLibre('TACHE', codesExistants);
  return {
    code,
    libelle: `Tâche ${numeroDuCode(code)}`,
    intervenants: { regle: 'ROLE', params: { roles: ['RH'] } },
    repli_si_vide: { action: 'BLOQUER' },
    obligatoire: true,
  };
}

export function nouvelleEtapeParDefaut(codesExistants: ReadonlyArray<string>): EtapeDefinition {
  const code = premierCodeLibre('ETAPE', codesExistants);
  return {
    code,
    libelle: `Nouvelle étape ${numeroDuCode(code)}`,
    type: 'VALIDATION',
    intervenants: { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'UN_SEUL' },
    repli_si_vide: { action: 'BLOQUER' },
    actions: ['APPROUVER', 'REFUSER'],
    delai: { duree: 24, unite: 'HEURES' },
    a_echeance: { action: 'RIEN' },
  };
}

function sansMode(iv: IntervenantsDefinition): IntervenantsDefinition {
  const copie: IntervenantsDefinition = { ...iv };
  delete copie.mode;
  return copie;
}

/**
 * Écrit dans la définition les valeurs par défaut que l'écran affiche (intervenant « Responsable direct »,
 * mode « Un seul », repli « Bloquer ») : ce qui est affiché est ce qui est enregistré.
 */
export function completerEtape(etape: EtapeDefinition): EtapeDefinition {
  const type = etape.type;
  if (type !== 'VALIDATION' && type !== 'TACHE' && type !== 'TACHES_PARALLELES') return etape;
  const suite: EtapeDefinition = { ...etape };
  if (type === 'VALIDATION' || type === 'TACHE') {
    const iv = suite.intervenants ?? intervenantsParDefaut(type === 'VALIDATION');
    suite.intervenants = type === 'VALIDATION' ? { ...iv, mode: iv.mode ?? 'UN_SEUL' } : sansMode(iv);
    suite.repli_si_vide = suite.repli_si_vide ?? { action: 'BLOQUER' };
  }
  if (type === 'VALIDATION') {
    // Le serveur exige APPROUVER et REFUSER (RENVOYER est facultatif).
    const actions = [...(suite.actions ?? [])];
    for (const a of ['REFUSER', 'APPROUVER']) if (!actions.includes(a)) actions.unshift(a);
    suite.actions = actions;
  }
  // `null` = champ vidé volontairement par l'utilisateur : on ne le réécrit pas (le serveur signalera l'absence).
  if (suite.delai === undefined) suite.delai = { duree: 24, unite: 'HEURES' };
  if (suite.a_echeance === undefined) suite.a_echeance = { action: 'RIEN' };
  return suite;
}

/**
 * Normalise une étape au changement de type : retire les propriétés interdites hors VALIDATION
 * (`mode`, `actions`, `controle_avant_approbation`), retire `intervenants` et `repli_si_vide` pour
 * INFORMATION et TACHES_PARALLELES, garde les autres propriétés, puis écrit les valeurs par défaut.
 */
export function normaliserEtapePourType(etape: EtapeDefinition, type: string): EtapeDefinition {
  const suite: EtapeDefinition = { ...etape, type };
  // Un `null` venu du serveur équivaut à « absent » : les valeurs par défaut sont alors écrites.
  if (suite.delai === null) delete suite.delai;
  if (suite.a_echeance === null) delete suite.a_echeance;
  if (type !== 'VALIDATION') {
    delete suite.actions;
    delete suite.controle_avant_approbation;
    if (suite.intervenants) suite.intervenants = sansMode(suite.intervenants);
  } else if (suite.intervenants?.regle === 'DEMANDEUR') {
    // La règle DEMANDEUR est interdite sur une étape de validation (invariant I1).
    delete suite.intervenants;
  }
  if (type === 'VALIDATION' && suite.a_echeance?.intervenants?.regle === 'DEMANDEUR') {
    // Escalade vers le demandeur interdite en VALIDATION : repli sur le rôle RH / ADMIN.
    suite.a_echeance = {
      ...suite.a_echeance,
      intervenants: { regle: 'ROLE', params: { roles: ['RH', 'ADMIN'] } },
    };
  }
  if (type === 'INFORMATION' || type === 'TACHES_PARALLELES') {
    delete suite.intervenants;
    delete suite.repli_si_vide;
  }
  if (type === 'TACHES_PARALLELES' && (!suite.taches || suite.taches.length === 0)) {
    const t1 = nouvelleTacheParDefaut([]);
    const t2 = nouvelleTacheParDefaut([t1.code]);
    suite.taches = [t1, t2];
  }
  if (type === 'INFORMATION' && !suite.public) suite.public = [];
  return completerEtape(suite);
}

/**
 * Publics proposés : les publics fixes du catalogue, puis une entrée `FONCTION:<code>` par fonction active
 * (l'entrée littérale `FONCTION:<code>` du catalogue est remplacée). Les publics déjà choisis restent visibles.
 */
export function optionsPublics(
  publicsCatalogue: ReadonlyArray<string>,
  fonctionsActives: ReadonlyArray<{ code: string }>,
  selection: ReadonlyArray<string> = [],
): string[] {
  const fixes = publicsCatalogue.filter((p) => !p.startsWith('FONCTION:'));
  const fonctions = fonctionsActives.map((f) => `FONCTION:${f.code}`);
  const sup = selection.filter((p) => !fixes.includes(p) && !fonctions.includes(p));
  return [...fixes, ...fonctions, ...sup];
}

import type {
  CatalogueWorkflow,
  ConditionEt,
  ConditionFeuille,
  ConditionNon,
  ConditionOu,
  ConditionWorkflow,
  VariableCatalogue,
} from '../api/workflowApi';

export const LIMITE_PROFONDEUR_CONDITION = 5;
export const LIMITE_FEUILLES_CONDITION = 20;
export const LIMITE_ENFANTS_GROUPE = 10;

export function estEt(c: ConditionWorkflow): c is ConditionEt {
  return 'et' in c && Array.isArray(c.et);
}

export function estOu(c: ConditionWorkflow): c is ConditionOu {
  return 'ou' in c && Array.isArray(c.ou);
}

export function estNon(c: ConditionWorkflow): c is ConditionNon {
  return 'non' in c && c.non !== undefined;
}

export function estFeuille(c: ConditionWorkflow): c is ConditionFeuille {
  return 'champ' in c && 'op' in c;
}

/** Profondeur maximale de l'arbre de condition (feuille = 1). */
export function profondeurCondition(c: ConditionWorkflow | null | undefined): number {
  if (!c) return 0;
  if (estEt(c)) return 1 + (c.et.length === 0 ? 0 : Math.max(...c.et.map(profondeurCondition)));
  if (estOu(c)) return 1 + (c.ou.length === 0 ? 0 : Math.max(...c.ou.map(profondeurCondition)));
  if (estNon(c)) return 1 + profondeurCondition(c.non);
  return 1;
}

/** Nombre total de conditions feuilles dans l'arbre. */
export function compterFeuillesCondition(c: ConditionWorkflow | null | undefined): number {
  if (!c) return 0;
  if (estEt(c)) return c.et.reduce((acc, x) => acc + compterFeuillesCondition(x), 0);
  if (estOu(c)) return c.ou.reduce((acc, x) => acc + compterFeuillesCondition(x), 0);
  if (estNon(c)) return compterFeuillesCondition(c.non);
  return 1;
}

const LIBELLE_OP: Readonly<Record<string, string>> = {
  egal: '=',
  different: '≠',
  inf: '<',
  inf_egal: '≤',
  sup: '>',
  sup_egal: '≥',
  contient: 'contient',
  ne_contient_pas: 'ne contient pas',
  dans: 'parmi',
  hors: 'hors de',
  vide: 'est vide',
  non_vide: 'est renseigné',
};

export function libelleOperateur(op: string): string {
  return LIBELLE_OP[op] ?? op;
}

export function operateurSansValeur(op: string): boolean {
  return op === 'vide' || op === 'non_vide';
}

function formaterValeur(
  valeur: unknown,
  variable: VariableCatalogue | undefined,
  libelles?: Readonly<Record<string, string>>,
): string {
  if (valeur === null || valeur === undefined || valeur === '') return '—';
  if (typeof valeur === 'boolean') return valeur ? 'Oui' : 'Non';
  if (typeof valeur === 'number') {
    return valeur.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }
  if (Array.isArray(valeur)) {
    return valeur.map((v) => formaterValeur(v, variable, libelles)).join(', ');
  }
  const str = String(valeur);
  if (variable?.type === 'DATE') {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str);
    if (m) return `${m[3]}/${m[2]}/${m[1]}`;
  }
  if (libelles && libelles[str]) {
    return libelles[str];
  }
  return str;
}

function indexerVariables(
  catalogue: Pick<CatalogueWorkflow, 'types' | 'variables_communes'> | null | undefined,
  objetType?: string,
): Map<string, VariableCatalogue> {
  const map = new Map<string, VariableCatalogue>();
  if (!catalogue) return map;
  if (objetType) {
    const t = catalogue.types.find((x) => x.objet_type === objetType);
    for (const v of t?.variables ?? []) map.set(v.nom, v);
  } else {
    for (const t of catalogue.types) {
      for (const v of t.variables) {
        if (!map.has(v.nom)) map.set(v.nom, v);
      }
    }
  }
  for (const v of catalogue.variables_communes ?? []) map.set(v.nom, v);
  return map;
}

function rendreNoeud(
  c: ConditionWorkflow,
  vars: Map<string, VariableCatalogue>,
  libelles?: Readonly<Record<string, string>>,
): string {
  if (estEt(c)) {
    if (c.et.length === 0) return '(toujours vrai)';
    return c.et.map((x) => (estOu(x) ? `(${rendreNoeud(x, vars, libelles)})` : rendreNoeud(x, vars, libelles))).join(' et ');
  }
  if (estOu(c)) {
    if (c.ou.length === 0) return '(toujours faux)';
    return c.ou.map((x) => (estEt(x) ? `(${rendreNoeud(x, vars, libelles)})` : rendreNoeud(x, vars, libelles))).join(' ou ');
  }
  if (estNon(c)) {
    return `non (${rendreNoeud(c.non, vars, libelles)})`;
  }
  const v = vars.get(c.champ);
  const nomChamp = v?.libelle ?? c.champ;
  const op = libelleOperateur(c.op);
  if (operateurSansValeur(c.op)) {
    return `${nomChamp} ${op}`;
  }
  return `${nomChamp} ${op} ${formaterValeur(c.valeur, v, libelles)}`;
}

/**
 * Aperçu en phrase française d'une condition de workflow (D-W7-06).
 * Exemple : « si Jours décomptés > 10 et Type de congé parmi Annuel ».
 */
export function resumeCondition(
  condition: ConditionWorkflow | null | undefined,
  catalogue: Pick<CatalogueWorkflow, 'types' | 'variables_communes'> | null | undefined,
  libelles?: Readonly<Record<string, string>>,
  objetType?: string,
): string {
  if (!condition) return 'Toujours appliquée (aucune condition)';
  const vars = indexerVariables(catalogue, objetType);
  return `si ${rendreNoeud(condition, vars, libelles)}`;
}

export type ResultatSaisieNombre =
  | { statut: 'vide' }
  | { statut: 'ok'; valeur: number }
  | { statut: 'erreur'; message: string };

const FORMAT_NOMBRE = /^-?\d+([.,]\d+)?$/;

/**
 * Convertit la saisie d'un nombre (virgule ou point) en valeur numérique.
 * Fonction pure : le composant garde le texte brut et ne convertit qu'au besoin (D-W7-06).
 * ENTIER refuse les décimales.
 */
export function convertirSaisieNombre(texte: string, type: 'ENTIER' | 'DECIMAL'): ResultatSaisieNombre {
  const propre = texte.trim();
  if (propre === '') return { statut: 'vide' };
  if (!FORMAT_NOMBRE.test(propre)) {
    return { statut: 'erreur', message: 'Saisissez un nombre (ex. 10, -2 ou 2,5).' };
  }
  if (type === 'ENTIER' && /[.,]/.test(propre)) {
    return { statut: 'erreur', message: 'Un nombre entier est attendu (sans décimales).' };
  }
  const n = Number(propre.replace(',', '.'));
  if (!Number.isFinite(n)) return { statut: 'erreur', message: 'Nombre trop grand.' };
  return { statut: 'ok', valeur: n };
}

/** Profondeur de l'arbre si on enveloppe `noeud` (placé au niveau `niveau`, racine = 1) dans un NON. */
export function profondeurApresNon(niveau: number, noeud: ConditionWorkflow): number {
  return niveau + profondeurCondition(noeud);
}

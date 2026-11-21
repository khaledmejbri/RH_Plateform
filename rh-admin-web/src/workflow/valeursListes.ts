import type { ValeurLibelle } from '../api/parametresApi';
import { valeursActives } from '../utils/parametresStore';

/**
 * Correspondance unique des variables de type LISTE vers les référentiels paramétrables (D-W7-07).
 * Toute autre variable LISTE utilise une saisie libre de codes contrôlée par {@link estCodeListeValide}.
 */
export const REFERENTIEL_PAR_VARIABLE: Readonly<Record<string, string>> = {
  type_conge: 'TYPE_CONGE',
  type_document: 'TYPE_DOCUMENT',
};

const MOTIF_CODE_LISTE = /^[A-Z0-9_]{1,40}$/;

/** Même règle que le serveur (D-W3-05) : majuscules, chiffres et underscore, 1 à 40 caractères. */
export function estCodeListeValide(code: string): boolean {
  return MOTIF_CODE_LISTE.test(code.trim());
}

/** Retourne le code de référentiel associé à une variable de type LISTE, ou null si saisie libre. */
export function referentielPourVariable(nomVariable: string): string | null {
  return REFERENTIEL_PAR_VARIABLE[nomVariable] ?? null;
}

/** Options disponibles depuis le cache de paramétrage pour une variable LISTE connue. */
export function optionsVariableListe(nomVariable: string): ValeurLibelle[] {
  const ref = referentielPourVariable(nomVariable);
  if (!ref) return [];
  return valeursActives(ref);
}

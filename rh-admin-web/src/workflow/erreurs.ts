import type { ErreurDefinition } from '../api/workflowApi';

export type RattachementErreur = {
  indexEtape: number | null;
  champ: string | null;
};

/**
 * Rattache un chemin d'erreur retourné par `svc-workflow` (ex. `etapes[2].intervenants.regle`, `etapes[0].code`,
 * `options.separation_des_taches`, `etapes`) à un index d'étape et un champ (D-W7-05).
 */
export function rattacherErreurs(chemin: string | null | undefined): RattachementErreur {
  if (!chemin || !chemin.trim()) {
    return { indexEtape: null, champ: null };
  }
  const propre = chemin.trim();
  const matchEtape = /^etapes\[(\d+)\](?:\.(.+))?$/.exec(propre);
  if (matchEtape) {
    const indexEtape = Number(matchEtape[1]);
    const champ = matchEtape[2] ? matchEtape[2] : null;
    return { indexEtape: Number.isInteger(indexEtape) ? indexEtape : null, champ };
  }
  return { indexEtape: null, champ: propre };
}

/** Groupe les erreurs de définition par index d'étape (-1 = erreurs globales). */
export function grouperErreursParEtape(
  erreurs: ReadonlyArray<ErreurDefinition>,
): Map<number, ErreurDefinition[]> {
  const map = new Map<number, ErreurDefinition[]>();
  for (const err of erreurs) {
    const { indexEtape } = rattacherErreurs(err.chemin);
    const cle = indexEtape ?? -1;
    const arr = map.get(cle) ?? [];
    arr.push(err);
    map.set(cle, arr);
  }
  return map;
}

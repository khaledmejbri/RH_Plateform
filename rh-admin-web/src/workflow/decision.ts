/**
 * Règle du motif d'une décision sur une tâche, calquée sur `DecisionService` (svc-workflow) :
 * - REFUSER et RENVOYER (complément) : motif obligatoire de 5 à 500 caractères (MOTIF_OBLIGATOIRE) ;
 * - APPROUVER avec commentaire obligatoire (`commentaire_obligatoire_si`) : 10 à 500 caractères
 *   (COMMENTAIRE_OBLIGATOIRE) ;
 * - sinon le commentaire est facultatif (500 caractères au plus).
 */
export const MOTIF_MAX = 500;

export type RegleMotif = { obligatoire: boolean; min: number; max: number };

export function regleMotif(action: string, commentaireObligatoire: boolean): RegleMotif {
  if (action === 'REFUSER' || action === 'RENVOYER') return { obligatoire: true, min: 5, max: MOTIF_MAX };
  if (action === 'APPROUVER' && commentaireObligatoire) return { obligatoire: true, min: 10, max: MOTIF_MAX };
  return { obligatoire: false, min: 0, max: MOTIF_MAX };
}

/** Vrai si la saisie (espaces retirés) respecte la règle. */
export function motifValide(texte: string, regle: RegleMotif): boolean {
  const n = texte.trim().length;
  if (regle.obligatoire) return n >= regle.min && n <= regle.max;
  return n <= regle.max;
}

import { libelle, libelleConnu } from '../utils/libelles';

/**
 * Catalogue M07 — familles métier × niveaux de séniorité.
 * Lot 3 (D-R07) : les valeurs viennent du paramétrage (`utils/parametresStore.ts`) ; plus de liste figée ici.
 */

/** Code de niveau tel que stocké (casse normalisée) ; les alias historiques sont gérés par le serveur. */
export function normalizeNiveauSeniorite(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  return raw.trim().toUpperCase() || undefined;
}

/** Libellé courant de la famille ; code absent → chaîne vide, code inconnu → « Valeur non reconnue ». */
export function libelleFamilleMetier(code?: string | null): string {
  if (!code) return '';
  return libelle('familleMetier', code);
}

export function libelleNiveauSeniorite(code?: string | null): string {
  const normalized = normalizeNiveauSeniorite(code);
  if (!normalized) return '';
  return libelle('niveauSeniorite', normalized);
}

export function profilMetierLabel(familleCode?: string | null, niveauCode?: string | null): string {
  const famille = libelleFamilleMetier(familleCode);
  const niveau = libelleNiveauSeniorite(niveauCode);
  if (famille && niveau) return `${famille} × ${niveau}`;
  return famille || niveau || '';
}

/** Appréciation score /20 — § 12.5 / UX § 6.1 */
export function appreciationFromScore(scoreSur20?: number | null): string | undefined {
  if (scoreSur20 == null || Number.isNaN(scoreSur20)) return undefined;
  const code = scoreSur20 <= 7 ? 'INSUFFISANT'
    : scoreSur20 <= 10 ? 'A_AMELIORER'
    : scoreSur20 <= 14 ? 'SATISFAISANT'
    : scoreSur20 <= 17 ? 'POSITIF'
    : 'EXCELLENT';
  return libelleConnu('appreciation', code);
}

export type CouleurAlerte = 'VERT' | 'ORANGE' | 'ROUGE';

export function normalizeCouleurAlerte(raw?: string | null): CouleurAlerte | undefined {
  if (!raw) return undefined;
  const upper = raw.trim().toUpperCase();
  if (upper === 'VERT' || upper === 'ORANGE' || upper === 'ROUGE') return upper;
  return undefined;
}

/** D-E12.b : libellés du codage couleur (codes VERT / ORANGE / ROUGE inchangés côté API). */
export const ALERTE_META: Record<CouleurAlerte, { label: string; court: string; className: string; icon: string }> = {
  VERT: { label: 'Conforme — aucun critère sous le seuil', court: 'Conforme', className: 'badge--eval-vert', icon: '✓' },
  ORANGE: { label: 'Vigilance — 2 critères sous le seuil', court: 'Vigilance', className: 'badge--eval-orange', icon: '!' },
  ROUGE: { label: 'Plan d’action requis — 3 critères ou plus sous le seuil', court: 'Plan d’action requis', className: 'badge--eval-rouge', icon: '!!' },
};

export const DISCREPANCY_FR: Record<string, { label: string; icon: string; badge?: string }> = {
  ALIGNED: { label: 'Aligné', icon: '≈' },
  MODERATE: { label: 'Écart modéré', icon: '~' },
  HIGH: { label: 'Alerte', icon: '↑', badge: 'badge--warning' },
  CRITICAL: { label: 'Critique', icon: '!!', badge: 'badge--eval-rouge' },
};

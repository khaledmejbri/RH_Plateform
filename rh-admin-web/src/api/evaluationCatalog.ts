/** Catalogue M07 — familles métier (seed H-B1) × niveaux de séniorité (spec E2/E3). */

export type FamilleMetier = {
  code: string;
  libelle: string;
  actif: boolean;
  systeme?: boolean;
};

export type NiveauSeniorite = {
  code: string;
  libelle: string;
};

/** Seed BA tant que GET /familles-metier n’est pas dispo. */
export const FAMILLES_METIER_SEED: FamilleMetier[] = [
  { code: 'EXPLOITATION', libelle: 'Exploitation / terrain assainissement', actif: true, systeme: true },
  { code: 'GENIE_CIVIL', libelle: 'Génie civil / travaux', actif: true, systeme: true },
  { code: 'DEV_LOGICIEL', libelle: 'Développement logiciel / SI', actif: true, systeme: true },
  { code: 'SUPPORT_ADMIN', libelle: 'Support administratif', actif: true, systeme: true },
  { code: 'MAINTENANCE', libelle: 'Maintenance technique', actif: true, systeme: true },
  { code: 'HSE_QUALITE', libelle: 'HSE / qualité', actif: true, systeme: true },
];

export const NIVEAUX_SENIORITE: NiveauSeniorite[] = [
  { code: 'JUNIOR', libelle: 'Junior' },
  { code: 'CONFIRME', libelle: 'Confirmé' },
  { code: 'SENIOR', libelle: 'Senior' },
  { code: 'TEAM_LEAD', libelle: 'Team lead' },
];

const NIVEAU_ALIAS: Record<string, string> = {
  CONFIRMED: 'CONFIRME',
  MID: 'CONFIRME',
};

export function normalizeNiveauSeniorite(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const upper = raw.trim().toUpperCase();
  return NIVEAU_ALIAS[upper] ?? upper;
}

export function libelleFamilleMetier(code?: string | null, catalogue: FamilleMetier[] = FAMILLES_METIER_SEED): string {
  if (!code) return '';
  return catalogue.find(f => f.code === code)?.libelle ?? 'Famille non reconnue';
}

export function libelleNiveauSeniorite(code?: string | null): string {
  const normalized = normalizeNiveauSeniorite(code);
  if (!normalized) return '';
  return NIVEAUX_SENIORITE.find(n => n.code === normalized)?.libelle ?? 'Niveau non reconnu';
}

export function profilMetierLabel(
  familleCode?: string | null,
  niveauCode?: string | null,
  catalogue: FamilleMetier[] = FAMILLES_METIER_SEED,
): string {
  const famille = libelleFamilleMetier(familleCode, catalogue);
  const niveau = libelleNiveauSeniorite(niveauCode);
  if (famille && niveau) return `${famille} × ${niveau}`;
  return famille || niveau || '';
}

/** Appréciation score /20 — § 12.5 / UX § 6.1 */
export function appreciationFromScore(scoreSur20?: number | null): string | undefined {
  if (scoreSur20 == null || Number.isNaN(scoreSur20)) return undefined;
  if (scoreSur20 <= 7) return 'Insuffisant';
  if (scoreSur20 <= 10) return 'À améliorer';
  if (scoreSur20 <= 14) return 'Satisfaisant';
  if (scoreSur20 <= 17) return 'Positif';
  return 'Excellent';
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

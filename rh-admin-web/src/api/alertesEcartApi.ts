import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';
import { libelle, libelleConnu } from '../utils/libelles';

/** Alertes d'écart d'étoiles (M07, D-E04 à D-E10, SCRUM-55) — contrat : ADR-M07-alertes-ecart § 9.1. */
const BASE = '/api/rh/v1/admin/evaluations/alertes-ecart';

/** Code d'un niveau d'alerte (référentiel NIVEAU_ALERTE, lot 3) ou `ALIGNE` : aucun `switch` sur les codes. */
export type NiveauEcart = string;
export type StatutAlerteEcart = 'OUVERTE' | 'EN_COURS' | 'TRAITEE';
export type SensGlobal = 'RO_INFERIEUR' | 'RO_SUPERIEUR' | 'DEUX_SENS';
export type SensEcart = 'RO_INFERIEUR' | 'RO_SUPERIEUR';
/** Code d'une conclusion de clôture (référentiel CONCLUSION_ALERTE, lot 3). */
export type ConclusionAlerteEcart = string;
export type EvenementAlerteEcart =
  | 'CREATION'
  | 'RECALCUL'
  | 'PRISE_EN_CHARGE'
  | 'CLOTURE'
  | 'REOUVERTURE_AUTOMATIQUE';

export const LIBELLES_SENS_GLOBAL: Record<SensGlobal, string> = {
  RO_INFERIEUR: 'Le responsable note plus bas que le collaborateur',
  RO_SUPERIEUR: 'Le responsable note plus haut que le collaborateur',
  DEUX_SENS: 'Écarts dans les deux sens',
};

/** Sens d'un écart, version courte (colonne du tableau). */
export const LIBELLES_SENS_ECART: Record<SensEcart, string> = {
  RO_INFERIEUR: 'Plus basse',
  RO_SUPERIEUR: 'Plus haute',
};

/** Lecture neutre de l'écart, affichée une seule fois par alerte (EVA-15). */
export const LECTURE_NEUTRE: Record<SensEcart, string> = {
  RO_INFERIEUR: 'Note du responsable plus basse que l’auto-évaluation : à clarifier lors d’un échange.',
  RO_SUPERIEUR: 'Note du responsable plus haute que l’auto-évaluation : à clarifier lors d’un échange.',
};

export const LIBELLES_EVENEMENT: Record<EvenementAlerteEcart, string> = {
  CREATION: 'Création de l’alerte',
  RECALCUL: 'Recalcul après modification des notes',
  PRISE_EN_CHARGE: 'Prise en charge',
  CLOTURE: 'Clôture',
  REOUVERTURE_AUTOMATIQUE: 'Réouverture automatique',
};

/** Libellé courant de la conclusion (paramétrage) ; celui du serveur sert de repli si le code est retiré. */
export function libelleConclusion(code: string | null | undefined, serveur?: string | null): string {
  if (!code) return '—';
  return libelleConnu('conclusionAlerte', code) ?? serveur ?? libelle('conclusionAlerte', code);
}

export type AlerteEcartResume = {
  identifiant: string;
  evaluationId: string;
  campagneId: string;
  /** Ajoutés par le backend (lot 2) : absents sur un serveur plus ancien. */
  collaborateurId?: string | null;
  superieurId?: string | null;
  campagneNom: string | null;
  collaborateurNom: string | null;
  collaborateurMatricule: string | null;
  collaborateurUniteId: string | null;
  collaborateurUniteLibelle: string | null;
  superieurNom: string | null;
  niveau: NiveauEcart;
  niveauDetection: NiveauEcart;
  statut: StatutAlerteEcart;
  sensGlobal: SensGlobal | null;
  nbCompetencesEnEcart: number;
  ecartMax: number;
  detecteeLe: string;
  notesModifieesApresDetection: boolean;
};

export type LigneAlerteEcart = {
  questionId: string;
  competence: string;
  ordre: number;
  etoilesCollaborateurDetection: number;
  etoilesRoDetection: number;
  ecartDetection: number;
  niveauDetection: NiveauEcart;
  sensDetection: SensEcart | null;
  etoilesCollaborateurActuelles: number;
  etoilesRoActuelles: number;
  ecartActuel: number;
  niveauActuel: NiveauEcart;
  sensActuel: SensEcart | null;
  libelleSens: string | null;
  aideSens: string | null;
};

export type HistoriqueAlerteEcart = {
  identifiant: string;
  typeEvenement: EvenementAlerteEcart;
  /** Libellés français fournis par le serveur (lot 1B) ; repli sur les tables locales. */
  typeEvenementLibelle?: string | null;
  statutAvantLibelle?: string | null;
  statutApresLibelle?: string | null;
  auteurLibelle: string | null;
  auteurSysteme: boolean;
  le: string;
  statutAvant: StatutAlerteEcart | null;
  statutApres: StatutAlerteEcart | null;
  niveauAvant: NiveauEcart | null;
  niveauApres: NiveauEcart | null;
  conclusion: ConclusionAlerteEcart | null;
  conclusionLibelle: string | null;
  commentaire: string | null;
};

export type MotifNonTraitable = 'LECTURE_SEULE' | 'CONCERNE';

export type AlerteEcartDetail = AlerteEcartResume & {
  libelleSensGlobal: string | null;
  aideSensGlobal: string | null;
  lignes: LigneAlerteEcart[];
  conclusion: ConclusionAlerteEcart | null;
  conclusionLibelle: string | null;
  historique: HistoriqueAlerteEcart[];
  peutTraiter: boolean;
  motifNonTraitable: MotifNonTraitable | null;
};

export type PageAlertes = {
  elements: AlerteEcartResume[];
  total: number;
  page: number;
  taille: number;
};

export type CompteurAlertes = { nonCloses: number; urgentesNonCloses: number };

export type FiltreAlertes = {
  statut?: string;
  niveau?: string;
  campagneId?: string;
  uniteId?: string;
};

export class AlerteEcartApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly donnees?: Record<string, unknown>;

  constructor(message: string, status: number, code?: string, donnees?: Record<string, unknown>) {
    super(message);
    this.name = 'AlerteEcartApiError';
    this.status = status;
    this.code = code;
    this.donnees = donnees;
  }
}

type ErreurServeur = { code?: string; message?: string; erreur?: string; donnees?: Record<string, unknown> };

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new AlerteEcartApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const body = await parseJson<ErreurServeur>(res).catch((): ErreurServeur => ({}));
    throw new AlerteEcartApiError(
      body.message ?? body.erreur ?? (res.statusText || 'Erreur serveur'),
      res.status,
      body.code,
      body.donnees,
    );
  }
  return parseJson<T>(res);
}

/** Échec réseau (fetch rejeté) : message lisible, rien n'est modifié localement. */
function reseau(e: unknown): never {
  if (e instanceof AlerteEcartApiError) throw e;
  throw new AlerteEcartApiError('Le serveur est injoignable. Vérifiez votre connexion puis réessayez.', 0);
}

function call<T>(path: string, init?: RequestInit): Promise<T> {
  return apiFetch(path, init).then((r) => handleResponse<T>(r), reseau);
}

export function getAlertesEcart(f: FiltreAlertes, page = 0, taille = 20): Promise<PageAlertes> {
  const q = new URLSearchParams({ page: String(page), taille: String(taille) });
  if (f.statut) q.set('statut', f.statut);
  if (f.niveau) q.set('niveau', f.niveau);
  if (f.campagneId) q.set('campagneId', f.campagneId);
  if (f.uniteId) q.set('uniteId', f.uniteId);
  return call<Partial<PageAlertes>>(`${BASE}?${q}`).then((raw) => ({
    elements: Array.isArray(raw.elements) ? raw.elements : [],
    total: raw.total ?? 0,
    page: raw.page ?? page,
    taille: raw.taille ?? taille,
  }));
}

export type NiveauOuvert = { code: string; libelle: string; couleur: string | null; seuil: number; nombre: number };
export type OuvertesParNiveau = { niveaux: NiveauOuvert[]; total: number };

type BrutOuvertes = NiveauOuvert[] | { niveaux?: NiveauOuvert[]; elements?: NiveauOuvert[]; liste?: NiveauOuvert[]; total?: number };

/** Alertes OUVERTE + EN_COURS par niveau (jamais TRAITEE), éventuellement pour une campagne ; liste des niveaux du serveur. */
export function getAlertesOuvertesParNiveau(campagneId?: string): Promise<OuvertesParNiveau> {
  const q = campagneId ? `?campagneId=${encodeURIComponent(campagneId)}` : '';
  return call<BrutOuvertes>(`/api/rh/v1/admin/evaluations/alertes-ecart/ouvertes-par-niveau${q}`).then((raw) => {
    const niveaux = Array.isArray(raw) ? raw : raw.niveaux ?? raw.elements ?? raw.liste ?? [];
    const total = !Array.isArray(raw) && typeof raw.total === 'number' ? raw.total : niveaux.reduce((n, x) => n + x.nombre, 0);
    return { niveaux: [...niveaux].sort((x, y) => y.seuil - x.seuil), total };
  });
}

export function getCompteurAlertesEcart(): Promise<CompteurAlertes> {
  return call<CompteurAlertes>(`${BASE}/compteur`);
}

export function getAlerteEcart(id: string): Promise<AlerteEcartDetail> {
  return call<AlerteEcartDetail>(`${BASE}/${encodeURIComponent(id)}`);
}

export function postPriseEnChargeAlerte(id: string, commentaire?: string): Promise<AlerteEcartDetail> {
  const c = commentaire?.trim();
  return call<AlerteEcartDetail>(`${BASE}/${encodeURIComponent(id)}/prise-en-charge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(c ? { commentaire: c } : {}),
  });
}

export function postClotureAlerte(
  id: string,
  conclusion: ConclusionAlerteEcart,
  commentaire: string,
): Promise<AlerteEcartDetail> {
  return call<AlerteEcartDetail>(`${BASE}/${encodeURIComponent(id)}/cloture`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conclusion, commentaire: commentaire.trim() }),
  });
}

/** Message lisible pour une action (prise en charge / clôture), selon le code HTTP (D-E08, SCRUM-55 règle 5). */
export function messageErreurAction(e: unknown): string {
  if (!(e instanceof AlerteEcartApiError)) return 'Une erreur inattendue est survenue. Rien n’a été modifié.';
  if (e.status === 409) {
    const statut = e.donnees?.statutActuel;
    if (statut === 'EN_COURS') return 'Cette alerte est déjà prise en charge.';
    return 'Cette alerte a déjà été traitée.';
  }
  if (e.status === 403) return 'Action non autorisée.';
  if (e.status === 404) return 'Alerte introuvable.';
  if (e.status === 0) return `${e.message} Rien n’a été modifié.`;
  if (e.status >= 500) return 'Le serveur a rencontré une erreur. Rien n’a été modifié, réessayez plus tard.';
  return e.message;
}

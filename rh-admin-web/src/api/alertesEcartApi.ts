import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';

/** Alertes d'écart d'étoiles (M07, D-E04 à D-E10, SCRUM-55) — contrat : ADR-M07-alertes-ecart § 9.1. */
const BASE = '/api/rh/v1/admin/evaluations/alertes-ecart';

export type NiveauEcart = 'ALIGNE' | 'ORANGE' | 'ROUGE' | 'URGENTE';
export type StatutAlerteEcart = 'OUVERTE' | 'EN_COURS' | 'TRAITEE';
export type SensGlobal = 'RO_INFERIEUR' | 'RO_SUPERIEUR' | 'DEUX_SENS';
export type SensEcart = 'RO_INFERIEUR' | 'RO_SUPERIEUR';
export type ConclusionAlerteEcart =
  | 'MANQUE_EFFORT'
  | 'SURESTIMATION_COLLABORATEUR'
  | 'SUIVI_INSUFFISANT_RO'
  | 'SOUS_ESTIMATION_COLLABORATEUR'
  | 'NOTE_GONFLEE_RO'
  | 'ECART_JUSTIFIE'
  | 'AUTRE';
export type EvenementAlerteEcart =
  | 'CREATION'
  | 'RECALCUL'
  | 'PRISE_EN_CHARGE'
  | 'CLOTURE'
  | 'REOUVERTURE_AUTOMATIQUE';

export const LIBELLES_NIVEAU_ECART: Record<NiveauEcart, string> = {
  ALIGNE: 'Aligné',
  ORANGE: 'Alerte',
  ROUGE: 'Critique',
  URGENTE: 'Urgente',
};

export const LIBELLES_STATUT_ALERTE: Record<StatutAlerteEcart, string> = {
  OUVERTE: 'Ouverte',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitée',
};

export const LIBELLES_SENS_GLOBAL: Record<SensGlobal, string> = {
  RO_INFERIEUR: 'Note du responsable plus basse',
  RO_SUPERIEUR: 'Note du responsable plus haute',
  DEUX_SENS: 'Écarts dans les deux sens',
};

/** Ordre d'affichage des 7 conclusions (D-E08.c). */
export const CONCLUSIONS: Array<{ code: ConclusionAlerteEcart; libelle: string }> = [
  { code: 'MANQUE_EFFORT', libelle: 'Manque d’effort confirmé' },
  { code: 'SURESTIMATION_COLLABORATEUR', libelle: 'Le collaborateur a surestimé son niveau' },
  { code: 'SUIVI_INSUFFISANT_RO', libelle: 'Le responsable ne suivait pas assez le travail' },
  { code: 'SOUS_ESTIMATION_COLLABORATEUR', libelle: 'Le collaborateur a sous-estimé son travail' },
  { code: 'NOTE_GONFLEE_RO', libelle: 'Le responsable a surévalué la note' },
  { code: 'ECART_JUSTIFIE', libelle: 'Écart expliqué, aucune action' },
  { code: 'AUTRE', libelle: 'Autre (préciser dans le commentaire)' },
];

export const LIBELLES_EVENEMENT: Record<EvenementAlerteEcart, string> = {
  CREATION: 'Création de l’alerte',
  RECALCUL: 'Recalcul après modification des notes',
  PRISE_EN_CHARGE: 'Prise en charge',
  CLOTURE: 'Clôture',
  REOUVERTURE_AUTOMATIQUE: 'Réouverture automatique',
};

export function libelleConclusion(code: string | null | undefined, serveur?: string | null): string {
  if (serveur) return serveur;
  return CONCLUSIONS.find((c) => c.code === code)?.libelle ?? code ?? '—';
}

export type AlerteEcartResume = {
  identifiant: string;
  evaluationId: string;
  campagneId: string;
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

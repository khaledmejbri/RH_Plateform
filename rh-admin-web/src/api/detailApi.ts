import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';
import type {
  CollaborateurRow,
  DemandeAdministrative,
  DemandeDocument,
  DemandeFormation,
  PlainteRh,
} from './rhClient';
import type { DemandeHeuresSup, HeuresComptees } from './heuresSupApi';
import type { PagePresence } from './presenceApi';

/**
 * Lectures des fiches de détail (lot 2, ticket 2D, `docs/jira/api_lot2.md`). Toutes passent par la gateway.
 * Erreur HTTP : `status` 0 = serveur injoignable, 403 = accès refusé, 404 = élément introuvable.
 */
export class HttpError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message);
    this.name = 'HttpError';
  }
}

export async function getJson<T>(chemin: string): Promise<T> {
  let res: Response;
  try {
    res = await apiFetch(chemin);
  } catch {
    throw new HttpError('Serveur injoignable', 0);
  }
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new HttpError('Session expirée', 401);
  }
  if (!res.ok) {
    const corps = await parseJson<{ erreur?: string; message?: string; code?: string }>(res).catch(
      (): { erreur?: string; message?: string; code?: string } => ({}),
    );
    throw new HttpError(corps.erreur ?? corps.message ?? (res.statusText || 'Erreur'), res.status, corps.code);
  }
  return parseJson<T>(res);
}

export async function postSansCorps(chemin: string): Promise<void> {
  let res: Response;
  try {
    res = await apiFetch(chemin, { method: 'POST' });
  } catch {
    throw new HttpError('Serveur injoignable', 0);
  }
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new HttpError('Session expirée', 401);
  }
  if (!res.ok) throw new HttpError(res.statusText || 'Erreur', res.status);
}

/** Statut HTTP d'une erreur quelle que soit la classe (HttpError, RhApiError, PresenceApiError, axios…). */
export function statutHttp(e: unknown): number | undefined {
  if (e && typeof e === 'object') {
    const s = (e as { status?: unknown }).status;
    if (typeof s === 'number') return s;
    const r = (e as { response?: { status?: unknown } }).response?.status;
    if (typeof r === 'number') return r;
  }
  return undefined;
}

const enc = encodeURIComponent;
const REF = '/api/referentiel/v1';
const RH = '/api/rh/v1';

/* ─── Référentiel ───────────────────────────────────────────────────────── */

export type PersonneResume = {
  collaborateur_id: string;
  nom_complet: string;
  matricule?: string | null;
  poste_libelle?: string | null;
  intitule_role?: string | null;
  unite_id?: string | null;
  unite_libelle?: string | null;
  actif?: boolean;
};

export type UniteResume = { unite_id: string; libelle: string; type_noeud?: string | null; type_libelle?: string | null };

export type Hierarchie = {
  collaborateur: PersonneResume;
  chemin: UniteResume[];
  responsable_direct: PersonneResume | null;
  valideur: PersonneResume | null;
  valideur_est_rh: boolean;
  noeuds_manages: UniteResume[];
  collaborateurs_encadres: PersonneResume[];
};

export type FicheUnite = {
  unite: UniteResume;
  code: string;
  actif: boolean;
  a_rattacher: boolean;
  chemin: UniteResume[];
  parent: UniteResume | null;
  responsable: PersonneResume | null;
  sous_unites: Array<{ unite: UniteResume; effectif_total: number }>;
  membres: PersonneResume[];
  effectif_direct: number;
  effectif_total: number;
};

export const getCollaborateurFiche = (id: string) => getJson<CollaborateurRow>(`${REF}/collaborateurs/${enc(id)}`);
export const getHierarchie = (id: string) => getJson<Hierarchie>(`${REF}/collaborateurs/${enc(id)}/hierarchie`);
export const getFicheUnite = (id: string) => getJson<FicheUnite>(`${REF}/unites/${enc(id)}/fiche`);

/* ─── Listes filtrées par collaborateur ─────────────────────────────────── */

export const getDemandesDuCollaborateur = (id: string) =>
  getJson<DemandeAdministrative[]>(`${RH}/demandes-administratives/liste?collaborateur_id=${enc(id)}`);
export const getDemandesDeUnite = (uniteId: string) =>
  getJson<DemandeAdministrative[]>(`${RH}/demandes-administratives/liste?unite_id=${enc(uniteId)}&statut=EN_VALIDATION_RRH`);
export const getDocumentsDuCollaborateur = (id: string) =>
  getJson<DemandeDocument[]>(`${RH}/demandes-documents-administratifs/liste?collaborateur_id=${enc(id)}`);
export type DemandeFormationCiblee = DemandeFormation & {
  demandeur_matricule?: string;
  demandeur_unite?: string | null;
  collaborateurs_cibles?: Array<{ identifiant: string; nom: string; matricule?: string | null }>;
};
export const getFormationsDuCollaborateur = (id: string) =>
  getJson<DemandeFormationCiblee[]>(`${RH}/demandes-formations/liste?collaborateur_id=${enc(id)}`);
export const getPlaintesDuCollaborateur = (id: string) =>
  getJson<PlainteRh[]>(`${RH}/plaintes/liste?collaborateur_id=${enc(id)}`);

/* ─── Détails par identifiant ───────────────────────────────────────────── */

export type DemandeAdministrativeDetail = DemandeAdministrative & {
  valideur_attendu_identifiant?: string | null;
  valideur_attendu_nom_complet?: string | null;
};
export const getDemande = (id: string) => getJson<DemandeAdministrativeDetail>(`${RH}/demandes-administratives/${enc(id)}`);
export const getDocument = (id: string) => getJson<DemandeDocument>(`${RH}/demandes-documents-administratifs/${enc(id)}`);
export const getFormation = (id: string) => getJson<DemandeFormationCiblee>(`${RH}/demandes-formations/${enc(id)}`);
export type FormationHistorique = {
  identifiant: string;
  action?: string;
  etape_libelle?: string | null;
  acteur_nom?: string | null;
  commentaire?: string | null;
  date_action: string;
};
export const getFormationHistorique = (id: string) =>
  getJson<FormationHistorique[]>(`${RH}/demandes-formations/${enc(id)}/historique`);
export const getPlainte = (id: string) => getJson<PlainteRh>(`${RH}/plaintes/${enc(id)}`);

/* ─── Présence (snake_case) ─────────────────────────────────────────────── */

export type SynthesePresence = {
  collaborateur_id: string;
  collaborateur_nom: string | null;
  collaborateur_matricule: string | null;
  du: string;
  au: string;
  journees_pointees: number;
  temps_total_minutes: number;
  retards_nombre: number;
  retards_cumul_minutes: number;
  journees_incompletes: number;
  journees_en_cours: number;
  pointages_manuels_valides: number;
  pointages_manuels_en_attente: number;
  heures_sup_minutes: number;
};

export type PointageJournee = {
  id: string;
  type: string;
  type_libelle?: string;
  statut: string;
  statut_libelle?: string;
  server_ts: string;
  manuel: boolean;
  motif_rejet?: string | null;
};

export type JourneePresence = {
  journee: string;
  site_id: string | null;
  site_libelle: string | null;
  temps_minutes: number;
  retard_minutes: number | null;
  etat: string | null;
  etat_libelle: string | null;
  pointages: PointageJournee[];
};

export type DemandeManuelleBrute = {
  id: string;
  numero?: string | null;
  statut: string;
  type: string;
  heure_declaree?: string | null;
  justification?: string;
  collaborateur_id?: string;
  collaborateur_nom?: string | null;
  collaborateur_matricule?: string | null;
  motif_rejet_initial?: string | null;
  valideur_nom?: string | null;
  validation_par_rh?: boolean;
  decide_par?: string | null;
  decide_le?: string | null;
  motif_refus?: string | null;
  cree_le?: string | null;
  manuels_du_mois?: number | null;
  alerte_seuil?: boolean;
};

export type PresenceCollaborateur = {
  collaborateur: { collaborateur_id: string; nom_complet: string; matricule?: string | null };
  synthese: SynthesePresence;
  journees: JourneePresence[];
  pointages_manuels: DemandeManuelleBrute[];
};

const PRES = `${RH}/admin/presence`;

export const getPresenceCollaborateur = (id: string, du: string, au: string) =>
  getJson<PresenceCollaborateur>(`${PRES}/collaborateurs/${enc(id)}/presence?du=${enc(du)}&au=${enc(au)}`);

export type SynthesePresenceSite = {
  site_code: string;
  site_libelle: string;
  collaborateurs: number;
  journees_pointees: number;
  temps_total_minutes: number;
  retards_nombre: number;
  retards_cumul_minutes: number;
  journees_incompletes: number;
  journees_en_cours: number;
};
export const getSyntheseSite = (id: string, du: string, au: string) =>
  getJson<SynthesePresenceSite>(`${PRES}/sites/${enc(id)}/synthese?du=${enc(du)}&au=${enc(au)}`);

export type DemandeManuelleDetail = DemandeManuelleBrute & {
  site_libelle?: string | null;
  unite_libelle?: string | null;
  valideur?: { collaborateur_id: string; nom_complet: string } | null;
  pointage_rejete?: {
    statut: string;
    statut_libelle?: string | null;
    motif_rejet?: string | null;
    distance_metres?: number | null;
    server_ts?: string | null;
  } | null;
  seuil_mois?: number | null;
};
export const getDemandeManuelleDetail = (id: string) =>
  getJson<DemandeManuelleDetail>(`${PRES}/demandes-manuelles/${enc(id)}`);

export const getDemandeHeuresSupDetail = (id: string) =>
  getJson<DemandeHeuresSup>(`${PRES}/heures-sup/demandes/${enc(id)}`);
export const getHeuresSupComptees = (id: string) =>
  getJson<HeuresComptees[] | PagePresence<HeuresComptees>>(`${PRES}/heures-sup/demandes/${enc(id)}/comptees`);

/* ─── Évaluations (camelCase) ───────────────────────────────────────────── */

const EVAL = `${RH}/admin/evaluations`;

export type EvaluationLigne = {
  identifiant: string;
  campagneId: string | null;
  campagneNom: string | null;
  collaborateurId: string | null;
  collaborateurNom: string | null;
  collaborateurMatricule: string | null;
  collaborateurPoste: string | null;
  superieurId: string | null;
  superieurNom: string | null;
  superieurMatricule: string | null;
  statut: string;
  etapeActuelle: string;
  scoreSur20: number | null;
  couleurAlerte: string | null;
  aNoteResponsable: boolean;
  niveauAlerte: string | null;
  alerteStatut: string | null;
  alerteId: string | null;
  familleMetierLibelle: string | null;
  niveauSenioriteLibelle: string | null;
  profilMetierIncomplet: boolean;
  validationCollaborateurLe: string | null;
  validationSuperieurLe: string | null;
  creeLe: string;
};

export type CampagneDetail = {
  identifiant: string;
  nom: string;
  description?: string | null;
  type: string;
  statut: string;
  annee?: number | null;
  dateDebut?: string | null;
  dateFin?: string | null;
  dureeJours?: number | null;
  templateGeneral?: { identifiant: string; nom: string } | null;
  templateTechnique?: { identifiant: string; nom: string } | null;
  compteurs: { total: number; enCours: number; validees: number; tauxCompletion: number; sansQuestionnaireTechnique: number };
  alertesOuvertesParNiveau: { ORANGE: number; ROUGE: number; URGENTE: number; total: number };
  creeLe?: string;
};

export type QuestionEvaluation = {
  questionId: string;
  libelle: string;
  section?: string | null;
  type?: string | null;
  reponseCollaborateur?: string | null;
  noteCollaborateur?: number | null;
  reponseResponsable?: string | null;
  noteResponsable?: number | null;
  commentaireResponsable?: string | null;
  ecart?: number | null;
  niveauEcart?: string | null;
};

export type EvaluationDetail = {
  entete: EvaluationLigne;
  questionsGenerales: QuestionEvaluation[];
  competencesTechniques: QuestionEvaluation[];
  scores: { moyenneCollaborateur: number | null; moyenneResponsable: number | null; scoreSur20: number | null; appreciation: string | null; appreciationLibelle?: string | null } | null;
  alerte: { identifiant: string; niveau: string; statut: string } | null;
  historique: Array<{ evenement: string; le: string; acteurNom: string | null }>;
};

export const getCampagneDetail = (id: string) => getJson<CampagneDetail>(`${EVAL}/campaigns/${enc(id)}`);
export const getEvaluationsCampagne = (id: string, page = 0, taille = 100) =>
  getJson<{ elements: EvaluationLigne[]; total: number; page: number; taille: number }>(
    `${EVAL}/campaigns/${enc(id)}/evaluations?page=${page}&taille=${taille}`,
  );
export const getEvaluationDetail = (id: string) => getJson<EvaluationDetail>(`${EVAL}/${enc(id)}/detail`);
export const getEvaluationsDuCollaborateur = (id: string) =>
  getJson<EvaluationLigne[]>(`${EVAL}/collaborateur/${enc(id)}`);
export const getAlertesDuCollaborateur = (id: string) =>
  getJson<{ elements: Array<{
    identifiant: string;
    campagneNom: string | null;
    niveau: string;
    statut: string;
    detecteeLe: string;
  }> }>(`${EVAL}/alertes-ecart?collaborateurId=${enc(id)}&taille=50`);

/* ─── Notifications persistées (2G, snake_case) ─────────────────────────── */

export type NotificationDto = {
  id: string;
  sujet?: string | null;
  contenu?: string | null;
  evenement?: string | null;
  objet_type?: string | null;
  objet_id?: string | null;
  objet_numero?: string | null;
  emis_le?: string | null;
  recu_le?: string | null;
  lu: boolean;
};

export type PageNotifications = { contenu: NotificationDto[]; page: number; taille: number; total: number };

const NOTIF = `${RH}/notifications`;
export const getNotifications = (page: number, seulementNonLues: boolean) =>
  getJson<PageNotifications>(`${NOTIF}?page=${page}&taille=20${seulementNonLues ? '&lu=false' : ''}`);
export const getCompteurNotifications = () => getJson<{ non_lues: number }>(`${NOTIF}/compteur`);
export const marquerNotificationLue = (id: string) => postSansCorps(`${NOTIF}/${enc(id)}/lue`);
export const marquerToutesLues = () => postSansCorps(`${NOTIF}/lues`);

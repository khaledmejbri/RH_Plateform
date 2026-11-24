import { apiFetch, clearToken } from './auth';

/**
 * Client API du moteur de workflow (`svc-workflow`, lot W7a, D-W7-03).
 * Aucun port de service en dur : tout passe par la gateway via `apiFetch`.
 */

// ─── Types de définition (§ 9.3, calqués sur definition/*.java) ─────────────

export type ConditionFeuille = {
  champ: string;
  op: string;
  valeur?: unknown;
};

export type ConditionEt = { et: ConditionWorkflow[] };
export type ConditionOu = { ou: ConditionWorkflow[] };
export type ConditionNon = { non: ConditionWorkflow };

export type ConditionWorkflow = ConditionFeuille | ConditionEt | ConditionOu | ConditionNon;

export type IntervenantsDefinition = {
  regle: string;
  params?: Record<string, unknown>;
  mode?: 'UN_SEUL' | 'TOUS';
  [extra: string]: unknown;
};

export type RepliSiVideDefinition = {
  action: 'PASSER' | 'ROLE' | 'BLOQUER';
  roles?: string[];
  [extra: string]: unknown;
};

export type TacheParalleleDefinition = {
  code: string;
  libelle: string;
  intervenants?: IntervenantsDefinition;
  repli_si_vide?: RepliSiVideDefinition;
  obligatoire?: boolean;
  [extra: string]: unknown;
};

export type DelaiDefinition = {
  duree?: number;
  unite?: 'HEURES' | 'JOURS_OUVRES';
  plafond_avant_variable?: string | null;
  [extra: string]: unknown;
};

export type RelancesDefinition = {
  nombre?: number;
  avant_echeance_heures?: number;
  intervalle_heures?: number;
  [extra: string]: unknown;
};

export type EcheanceDefinition = {
  action?: 'RIEN' | 'ESCALADER' | 'EXPIRER' | 'REFUSER_AUTO' | 'APPROUVER_AUTO';
  apres?: { duree?: number; unite?: string; [extra: string]: unknown };
  intervenants?: IntervenantsDefinition;
  [extra: string]: unknown;
};

export type TransitionDefinition = {
  aller_a?: string;
  [extra: string]: unknown;
};

export type NotificationsEtapeDefinition = {
  a_l_activation?: string[];
  a_la_fin?: string[];
  canal?: 'WEBSOCKET' | 'BOTH';
  [extra: string]: unknown;
};

export type EtapeDefinition = {
  code: string;
  libelle: string;
  type: 'VALIDATION' | 'TACHE' | 'TACHES_PARALLELES' | 'INFORMATION' | string;
  condition?: ConditionWorkflow | null;
  intervenants?: IntervenantsDefinition | null;
  repli_si_vide?: RepliSiVideDefinition | null;
  taches?: TacheParalleleDefinition[] | null;
  public?: string[] | null;
  actions?: string[] | null;
  controle_avant_approbation?: boolean | null;
  commentaire_obligatoire_si?: ConditionWorkflow | null;
  delai?: DelaiDefinition | null;
  relances?: RelancesDefinition | null;
  a_echeance?: EcheanceDefinition | null;
  si_approuve?: TransitionDefinition | null;
  si_refuse?: TransitionDefinition | null;
  notifications?: NotificationsEtapeDefinition | null;
  /** Propriétés inconnues conservées intactes (D-W7-05). */
  [extra: string]: unknown;
};

export type PorteeSitesPointage = 'TOUTE_LA_SOCIETE' | 'SITES_SELECTIONNES';

export type TypePointageNotif = 'ENTREE' | 'SORTIE';

export type CanalNotificationPointage = 'WEBSOCKET' | 'BOTH';

export type NotificationsDynamiquesDefinition = {
  notifier_ro: boolean;
  portee_sites: PorteeSitesPointage;
  site_ids: string[];
  types_pointage: TypePointageNotif[];
  canal: CanalNotificationPointage;
};

export type DefinitionWorkflow = {
  format: number;
  objet_type: string;
  options?: {
    separation_des_taches?: boolean;
    [extra: string]: unknown;
  } | null;
  etapes: EtapeDefinition[];
  notifications_dynamiques?: NotificationsDynamiquesDefinition | null;
  [extra: string]: unknown;
};

export type ErreurDefinition = {
  chemin: string | null;
  code: string;
  niveau: 'ERREUR' | 'AVERTISSEMENT';
  message: string;
};

// ─── Types Catalogue & Administration (§ 9.7.2) ─────────────────────────────

export type TypeVariableCatalogue = 'ENTIER' | 'DECIMAL' | 'DATE' | 'DATE_HEURE' | 'TEXTE' | 'LISTE' | 'BOOLEEN';

export type VariableCatalogue = {
  nom: string;
  type: TypeVariableCatalogue;
  libelle: string;
  valeurs: string[] | null;
  source_valeurs: string | null;
};

export type TypeObjetCatalogue = {
  objet_type: string;
  libelle: string;
  service_proprietaire: string;
  chemin_effet: string;
  variables: VariableCatalogue[];
  effets: string[];
  validation_humaine_obligatoire: boolean;
  supporte_complement: boolean;
};

export type RegleCatalogue = { code: string; libelle: string };

export type CatalogueWorkflow = {
  types: TypeObjetCatalogue[];
  variables_communes: VariableCatalogue[];
  effets: string[];
  regles: RegleCatalogue[];
  operateurs: Record<string, string[]>;
  publics: string[];
  fonctions: Array<{ code: string; libelle: string }>;
  fonctions_disponibles: boolean;
};

export type ModeActivation = 'HERITE' | 'MOTEUR';

export type ActivationWorkflow = {
  objet_type: string;
  libelle: string;
  mode: ModeActivation;
  modifie_le: string | null;
  version: number;
};

export type PageWorkflow<T> = {
  elements: T[];
  page: number;
  taille: number;
  total: number;
};

export type ModeleResume = {
  id: string;
  code: string;
  libelle: string;
  description: string | null;
  objet_type: string;
  sous_type: string | null;
  actif: boolean;
  version_publiee_numero: number | null;
  brouillon_numero: number | null;
  modifie_le: string | null;
  version: number;
};

export type ModeleDetail = ModeleResume & {
  cree_le: string | null;
  brouillon_id: string | null;
};

export type StatutVersion = 'BROUILLON' | 'PUBLIEE' | 'RETIREE';

export type VersionResume = {
  id: string;
  numero: number;
  statut: StatutVersion;
  empreinte: string | null;
  modifie_le: string | null;
  modifie_par_nom: string | null;
  publie_le: string | null;
  retire_le: string | null;
  version: number;
};

export type VersionDetail = {
  id: string;
  modele_id: string;
  numero: number;
  statut: StatutVersion;
  definition: DefinitionWorkflow;
  empreinte: string | null;
  version: number;
  modifie_le: string | null;
  modifie_par_nom: string | null;
  publie_le: string | null;
  erreurs: ErreurDefinition[];
};

export type ValidationResponse = {
  valide: boolean;
  erreurs: ErreurDefinition[];
};

export type CandidatSimule = {
  nom: string;
  intitule: string | null;
  origine: string | null;
};

export type EtapeSimulee = {
  code: string;
  libelle: string;
  statut: string;
  raison: string | null;
  candidats: CandidatSimule[];
  echeance_simulee: string | null;
};

export type SimulationResponse = {
  etapes: EtapeSimulee[];
};

export type DiffEtape = {
  code: string;
  type_changement: 'AJOUTEE' | 'SUPPRIMEE' | 'MODIFIEE' | 'DEPLACEE' | string;
  champs_modifies: string[];
};

export type ComparaisonVersions = {
  version_source_id: string;
  version_cible_id: string;
  etapes: DiffEtape[];
};

export type JournalModele = {
  id: string;
  modele_id: string;
  version_id: string | null;
  action: string;
  avant: Record<string, unknown> | null;
  apres: Record<string, unknown> | null;
  auteur_nom: string | null;
  horodatage: string;
};

// ─── Types Utilisateur / Mes tâches (§ 9.7.3) ───────────────────────────────

export type TacheListeItem = {
  tache_id: string;
  instance_id: string;
  type: 'VALIDATION' | 'TACHE' | 'COMPLEMENT' | string;
  libelle_etape: string;
  objet_type: string;
  objet_libelle: string;
  objet_numero: string | null;
  objet_id: string;
  demandeur_nom: string;
  echeance_le: string | null;
  en_retard: boolean;
  origine: string | null;
  pour_nom: string | null;
  actions_permises: string[];
  version: number;
  resume: Record<string, unknown> | null;
};

export type CompteurTaches = {
  a_traiter: number;
  en_retard: number;
};

export type EtapeCourante = {
  code: string;
  libelle: string;
  echeance_le: string | null;
  intitule_intervenants: string | null;
};

export type EtapeSuivi = {
  code: string;
  libelle: string;
  statut: string;
  raison: string | null;
  intitule_intervenants: string | null;
  candidats: string[] | null;
  decideur_nom: string | null;
  pour_nom: string | null;
  resultat: string | null;
  motif: string | null;
  echeance_le: string | null;
  date_decision: string | null;
};

export type SuiviInstance = {
  instance_id: string;
  objet_type: string;
  objet_id: string;
  objet_numero: string | null;
  modele_code: string;
  modele_version_numero: number;
  statut: string;
  etape_courante: EtapeCourante | null;
  demarre_le: string;
  termine_le: string | null;
  resultat_motif: string | null;
  etapes: EtapeSuivi[];
};

export type TacheDetail = {
  tache_id: string;
  instance_id: string;
  type: string;
  statut: string;
  libelle: string;
  objet_type: string;
  objet_libelle: string;
  objet_numero: string | null;
  objet_id: string;
  demandeur_nom: string;
  echeance_le: string | null;
  en_retard: boolean;
  actions_permises: string[];
  commentaire_obligatoire: boolean;
  version: number;
  resume: Record<string, unknown> | null;
  suivi: SuiviInstance | null;
};

export type ResultatDecision =
  | {
      statut: 200;
      reponse: {
        tache_id: string;
        decision: string;
        instance: { statut: string; etape_courante: EtapeCourante | null };
      };
    }
  | { statut: 202; reponse: { statut: string } };

// ─── Types Supervision, Réaffectation et Délégations (lot W4 / W7b) ─────────

export type InstanceResume = {
  id: string;
  objet_type: string;
  objet_libelle: string;
  objet_numero: string | null;
  objet_id: string;
  modele_code: string;
  demandeur_id: string;
  demandeur_nom: string;
  statut: string;
  etape_courante_code: string | null;
  etape_courante_libelle: string | null;
  echeance_le: string | null;
  en_retard: boolean;
  demarre_le: string;
  termine_le: string | null;
};

export type CandidatAdmin = {
  collaborateur_id: string;
  compte_id: string | null;
  nom: string;
  origine: string;
  pour_collaborateur_id: string | null;
  pour_nom: string | null;
  ajoute_le: string | null;
  retire_le: string | null;
};

export type EtapeExecutionAdmin = {
  id: string;
  etape_code: string;
  libelle: string;
  ordre: number;
  statut: string;
  raison_saut: string | null;
  resultat: string | null;
  nb_renvois: number;
  active_le: string | null;
  terminee_le: string | null;
};

export type TacheAdmin = {
  id: string;
  etape_execution_id: string;
  code_tache: string;
  type: string;
  statut: string;
  libelle: string;
  obligatoire: boolean;
  echeance_le: string | null;
  prochaine_relance_le: string | null;
  en_retard: boolean;
  nb_relances: number;
  escalade_a_faire_le: string | null;
  escalade_le: string | null;
  version: number;
  decision: string | null;
  decideur_nom: string | null;
  decide_le: string | null;
  pour_nom: string | null;
  commentaire: string | null;
  candidats: CandidatAdmin[];
};

export type HistoriqueAdmin = {
  id: string;
  tache_id: string | null;
  type_evenement: string;
  acteur_compte_id: string | null;
  acteur_nom: string | null;
  pour_nom: string | null;
  details: Record<string, unknown> | null;
  horodatage: string;
};

export type InstanceDetailAdmin = {
  id: string;
  objet_type: string;
  objet_libelle: string;
  objet_numero: string | null;
  objet_id: string;
  modele_code: string;
  modele_version_numero: number;
  demandeur_id: string;
  demandeur_nom: string;
  statut: string;
  etape_courante_code: string | null;
  etape_courante_libelle: string | null;
  demarre_le: string;
  termine_le: string | null;
  resultat_motif: string | null;
  variables: Record<string, unknown> | null;
  etapes: EtapeExecutionAdmin[];
  taches: TacheAdmin[];
  historique: HistoriqueAdmin[];
};

export type ReaffecterTacheResponse = {
  tache_id: string;
  instance_id: string;
  statut_instance: string;
  echeance_le: string | null;
  version: number;
  candidats: CandidatAdmin[];
};

export type IndicateurEtape = {
  modele_code: string;
  etape_code: string;
  libelle: string;
  delai_moyen_heures: number;
  nb: number;
};

export type IndicateursWorkflow = {
  en_cours: number;
  en_retard: number;
  bloquees: number;
  par_etape: IndicateurEtape[];
};

export type DelegationAdmin = {
  id: string;
  delegant_id: string;
  delegant_nom: string;
  delegue_id: string;
  delegue_nom: string;
  du: string;
  au: string;
  objet_types: string[] | null;
  motif: string | null;
  origine: string;
  active: boolean;
  creee_le: string;
  annulee_le: string | null;
};

// ─── Erreur typée (D-W7-03) ──────────────────────────────────────────────────

export class WorkflowApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
    readonly erreurs: ErreurDefinition[] = [],
  ) {
    super(message);
  }
}

type CorpsErreurWorkflow = {
  code?: string;
  message?: string;
  erreur?: string;
  details?: unknown;
  erreurs?: ErreurDefinition[];
};

async function lireReponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new WorkflowApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const corps = (await res.json().catch(() => ({}))) as CorpsErreurWorkflow;
    const erreurs = Array.isArray(corps.erreurs)
      ? corps.erreurs
      : Array.isArray(corps.details)
        ? (corps.details as ErreurDefinition[])
        : [];
    throw new WorkflowApiError(
      corps.message ?? corps.erreur ?? res.statusText,
      res.status,
      corps.code ?? null,
      erreurs,
    );
  }
  if (res.status === 204) return undefined as T;
  const texte = await res.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
}

async function appeler<T>(url: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await apiFetch(url, init);
  } catch {
    throw new WorkflowApiError('Le serveur ne répond pas.', 0);
  }
  return lireReponse<T>(res);
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const ADMIN = '/api/rh/v1/admin/workflow';
const USER = '/api/rh/v1/workflow';

function jsonBody(corps: unknown): RequestInit {
  return { headers: JSON_HEADERS, body: JSON.stringify(corps) };
}

/** Codes 409 pour lesquels « la ressource a changé entre-temps » est un message exact (rechargement utile). */
const CODES_CONFLIT_RECHARGEABLE: ReadonlySet<string> = new Set([
  'MODIFIE_ENTRE_TEMPS',
  'BROUILLON_EXISTANT',
  'VERSION_NON_BROUILLON',
]);

/**
 * Vrai pour un conflit de version à résoudre en rechargeant. Les autres 409 (ex. `MODELE_ACTIF_EXISTANT`,
 * `MODELE_DEJA_PUBLIE`) ont un message propre, affiché tel quel dans la fenêtre laissée ouverte.
 */
export function estConflitWorkflow(e: unknown): boolean {
  return e instanceof WorkflowApiError && e.status === 409 && e.code !== null && CODES_CONFLIT_RECHARGEABLE.has(e.code);
}

/**
 * Enveloppe une action exécutée dans `ConfirmDialog.envoyer` : le message d'erreur affiché dans la fenêtre
 * est le message français de `messageErreurWorkflow`, le statut et le code sont conservés.
 */
export async function executerPourConfirmation(action: () => Promise<unknown>): Promise<void> {
  try {
    await action();
  } catch (e) {
    if (e instanceof WorkflowApiError) {
      throw new WorkflowApiError(messageErreurWorkflow(e), e.status, e.code, e.erreurs);
    }
    throw new Error(messageErreurWorkflow(e));
  }
}

export function messageErreurWorkflow(e: unknown): string {
  if (!(e instanceof WorkflowApiError)) return 'Une erreur inattendue est survenue.';
  if (e.status === 0) return 'Le serveur ne répond pas. Réessayez dans quelques instants.';
  if (e.code === 'PUBLICATION_QUATRE_YEUX') {
    return 'La publication doit être faite par une autre personne que l’auteur du brouillon.';
  }
  if (e.status === 403) return e.message || 'Action non autorisée pour votre profil.';
  if (e.status >= 500) return 'Le service de workflow a rencontré une erreur. Réessayez plus tard.';
  return e.message;
}

// ─── Endpoints Administration ────────────────────────────────────────────────

export function getCatalogueWorkflow(): Promise<CatalogueWorkflow> {
  return appeler<CatalogueWorkflow>(`${ADMIN}/catalogue`);
}

export function getActivationsWorkflow(): Promise<ActivationWorkflow[]> {
  return appeler<ActivationWorkflow[]>(`${ADMIN}/activations`);
}

export function putActivationWorkflow(
  objetType: string,
  mode: ModeActivation,
  version: number,
): Promise<ActivationWorkflow> {
  return appeler<ActivationWorkflow>(`${ADMIN}/activations/${encodeURIComponent(objetType)}`, {
    method: 'PUT',
    ...jsonBody({ mode, version }),
  });
}

export function getModelesWorkflow(params: {
  objet_type?: string;
  actif?: boolean;
  page?: number;
  taille?: number;
} = {}): Promise<PageWorkflow<ModeleResume>> {
  const q = new URLSearchParams();
  if (params.objet_type) q.set('objet_type', params.objet_type);
  if (params.actif !== undefined) q.set('actif', String(params.actif));
  q.set('page', String(params.page ?? 0));
  q.set('taille', String(params.taille ?? 50));
  return appeler<PageWorkflow<ModeleResume>>(`${ADMIN}/modeles?${q}`);
}

export function creerModeleWorkflow(corps: {
  code: string;
  libelle: string;
  description?: string | null;
  objet_type: string;
  sous_type?: string | null;
}): Promise<ModeleDetail> {
  return appeler<ModeleDetail>(`${ADMIN}/modeles`, {
    method: 'POST',
    ...jsonBody(corps),
  });
}

export function getModeleWorkflow(id: string): Promise<ModeleDetail> {
  return appeler<ModeleDetail>(`${ADMIN}/modeles/${encodeURIComponent(id)}`);
}

export function modifierModeleWorkflow(
  id: string,
  corps: { libelle: string; description?: string | null; version: number },
): Promise<ModeleDetail> {
  return appeler<ModeleDetail>(`${ADMIN}/modeles/${encodeURIComponent(id)}`, {
    method: 'PUT',
    ...jsonBody(corps),
  });
}

export function activerModeleWorkflow(id: string, version: number): Promise<ModeleDetail> {
  return appeler<ModeleDetail>(`${ADMIN}/modeles/${encodeURIComponent(id)}/activer`, {
    method: 'POST',
    ...jsonBody({ version }),
  });
}

export function desactiverModeleWorkflow(id: string, version: number): Promise<ModeleDetail> {
  return appeler<ModeleDetail>(`${ADMIN}/modeles/${encodeURIComponent(id)}/desactiver`, {
    method: 'POST',
    ...jsonBody({ version }),
  });
}

export function supprimerModeleWorkflow(id: string): Promise<void> {
  return appeler<void>(`${ADMIN}/modeles/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function getVersionsModele(modeleId: string): Promise<VersionResume[]> {
  return appeler<VersionResume[]>(`${ADMIN}/modeles/${encodeURIComponent(modeleId)}/versions`);
}

export function creerBrouillonModele(modeleId: string): Promise<VersionDetail> {
  return appeler<VersionDetail>(`${ADMIN}/modeles/${encodeURIComponent(modeleId)}/versions`, {
    method: 'POST',
  });
}

export function getVersionWorkflow(versionId: string): Promise<VersionDetail> {
  return appeler<VersionDetail>(`${ADMIN}/versions/${encodeURIComponent(versionId)}`);
}

export function putVersionWorkflow(
  versionId: string,
  definition: DefinitionWorkflow,
  version: number,
): Promise<VersionDetail> {
  return appeler<VersionDetail>(`${ADMIN}/versions/${encodeURIComponent(versionId)}`, {
    method: 'PUT',
    ...jsonBody({ definition, version }),
  });
}

export function validerVersionWorkflow(versionId: string): Promise<ValidationResponse> {
  return appeler<ValidationResponse>(`${ADMIN}/versions/${encodeURIComponent(versionId)}/valider`, {
    method: 'POST',
  });
}

export function simulerVersionWorkflow(
  versionId: string,
  corps: { demandeur_id: string; beneficiaires?: string[]; variables: Record<string, unknown> },
): Promise<SimulationResponse> {
  return appeler<SimulationResponse>(`${ADMIN}/versions/${encodeURIComponent(versionId)}/simuler`, {
    method: 'POST',
    ...jsonBody(corps),
  });
}

export function publierVersionWorkflow(versionId: string, version: number): Promise<VersionDetail> {
  return appeler<VersionDetail>(`${ADMIN}/versions/${encodeURIComponent(versionId)}/publier`, {
    method: 'POST',
    ...jsonBody({ version }),
  });
}

export function supprimerBrouillonWorkflow(versionId: string): Promise<void> {
  return appeler<void>(`${ADMIN}/versions/${encodeURIComponent(versionId)}`, { method: 'DELETE' });
}

export function comparerVersionsWorkflow(
  versionSourceId: string,
  versionCibleId: string,
): Promise<ComparaisonVersions> {
  return appeler<ComparaisonVersions>(
    `${ADMIN}/versions/${encodeURIComponent(versionSourceId)}/comparer/${encodeURIComponent(versionCibleId)}`,
  );
}

export function getJournalModeleWorkflow(
  modeleId: string,
  page = 0,
  taille = 20,
): Promise<PageWorkflow<JournalModele>> {
  return appeler<PageWorkflow<JournalModele>>(
    `${ADMIN}/modeles/${encodeURIComponent(modeleId)}/journal?page=${page}&taille=${taille}`,
  );
}

// ─── Endpoints Utilisateur / Mes tâches ──────────────────────────────────────

export function getCompteurMesTaches(): Promise<CompteurTaches> {
  return appeler<CompteurTaches>(`${USER}/taches/moi/compteur`);
}

export function getMesTaches(params: {
  statut?: 'A_TRAITER' | 'TRAITEES';
  page?: number;
  taille?: number;
} = {}): Promise<PageWorkflow<TacheListeItem>> {
  const q = new URLSearchParams();
  q.set('statut', params.statut ?? 'A_TRAITER');
  q.set('page', String(params.page ?? 0));
  q.set('taille', String(params.taille ?? 20));
  return appeler<PageWorkflow<TacheListeItem>>(`${USER}/taches/moi?${q}`);
}

export function getDetailTache(tacheId: string): Promise<TacheDetail> {
  return appeler<TacheDetail>(`${USER}/taches/${encodeURIComponent(tacheId)}`);
}

/**
 * Décision sur une tâche (D-W7-10) : distingue HTTP 200 (appliquée) et HTTP 202 (effet en cours de reprise).
 */
export async function postDecisionTache(
  tacheId: string,
  corps: { action: string; commentaire?: string | null; version: number; cle_idempotence: string },
): Promise<ResultatDecision> {
  let res: Response;
  try {
    res = await apiFetch(`${USER}/taches/${encodeURIComponent(tacheId)}/decision`, {
      method: 'POST',
      ...jsonBody(corps),
    });
  } catch {
    throw new WorkflowApiError('Le serveur ne répond pas.', 0);
  }
  if (res.status === 202) {
    const data = (await res.json().catch(() => ({ statut: 'EN_COURS' }))) as { statut: string };
    return { statut: 202, reponse: data };
  }
  const data = await lireReponse<{
    tache_id: string;
    decision: string;
    instance: { statut: string; etape_courante: EtapeCourante | null };
  }>(res);
  return { statut: 200, reponse: data };
}

export function getSuiviObjetWorkflow(objetType: string, objetId: string): Promise<SuiviInstance> {
  return appeler<SuiviInstance>(
    `${USER}/objets/${encodeURIComponent(objetType)}/${encodeURIComponent(objetId)}/suivi`,
  );
}

// ─── Endpoints Supervision, Réaffectation et Délégations (W7b) ───────────────

export function getInstancesWorkflow(params: {
  objet_type?: string;
  statut?: string;
  etape?: string;
  en_retard?: boolean;
  demandeur_id?: string;
  intervenant_id?: string;
  du?: string;
  au?: string;
  page?: number;
  taille?: number;
} = {}): Promise<PageWorkflow<InstanceResume>> {
  const q = new URLSearchParams();
  if (params.objet_type) q.set('objet_type', params.objet_type);
  if (params.statut) q.set('statut', params.statut);
  if (params.etape) q.set('etape', params.etape);
  if (params.en_retard !== undefined) q.set('en_retard', String(params.en_retard));
  if (params.demandeur_id) q.set('demandeur_id', params.demandeur_id);
  if (params.intervenant_id) q.set('intervenant_id', params.intervenant_id);
  if (params.du) q.set('du', params.du);
  if (params.au) q.set('au', params.au);
  q.set('page', String(params.page ?? 0));
  q.set('taille', String(params.taille ?? 20));
  return appeler<PageWorkflow<InstanceResume>>(`${ADMIN}/instances?${q}`);
}

export function getInstanceDetailWorkflow(id: string): Promise<InstanceDetailAdmin> {
  return appeler<InstanceDetailAdmin>(`${ADMIN}/instances/${encodeURIComponent(id)}`);
}

export function reaffecterTacheWorkflow(
  tacheId: string,
  corps: { collaborateur_ids: string[]; motif: string; version: number },
): Promise<ReaffecterTacheResponse> {
  return appeler<ReaffecterTacheResponse>(`${ADMIN}/taches/${encodeURIComponent(tacheId)}/reaffecter`, {
    method: 'POST',
    ...jsonBody(corps),
  });
}

export function getIndicateursWorkflow(du?: string, au?: string): Promise<IndicateursWorkflow> {
  const q = new URLSearchParams();
  if (du) q.set('du', du);
  if (au) q.set('au', au);
  const qs = q.toString();
  return appeler<IndicateursWorkflow>(`${ADMIN}/indicateurs${qs ? `?${qs}` : ''}`);
}

export function getDelegationsAdmin(params: {
  actives?: boolean;
  delegant_id?: string;
  delegue_id?: string;
  page?: number;
  taille?: number;
} = {}): Promise<PageWorkflow<DelegationAdmin>> {
  const q = new URLSearchParams();
  if (params.actives !== undefined) q.set('actives', String(params.actives));
  if (params.delegant_id) q.set('delegant_id', params.delegant_id);
  if (params.delegue_id) q.set('delegue_id', params.delegue_id);
  q.set('page', String(params.page ?? 0));
  q.set('taille', String(params.taille ?? 20));
  return appeler<PageWorkflow<DelegationAdmin>>(`${ADMIN}/delegations?${q}`);
}

export function creerDelegationAdmin(corps: {
  delegant_id: string;
  delegue_id: string;
  du: string;
  au: string;
  objet_types?: string[] | null;
  motif?: string | null;
}): Promise<DelegationAdmin> {
  return appeler<DelegationAdmin>(`${ADMIN}/delegations`, {
    method: 'POST',
    ...jsonBody(corps),
  });
}

export function annulerDelegationAdmin(id: string): Promise<void> {
  return appeler<void>(`${ADMIN}/delegations/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

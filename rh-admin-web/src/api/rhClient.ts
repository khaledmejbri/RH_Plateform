import { apiFetch, clearToken } from './auth';

export async function parseJson<T>(res: Response): Promise<T> {
  const text = await res.text();
  if (!text) return {} as T;
  return JSON.parse(text) as T;
}

export async function handleRhResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new Error('Session expirée');
  }
  if (!res.ok) {
    const body = await parseJson<{ erreur?: string; message?: string }>(res).catch(
      (): { erreur?: string; message?: string } => ({}),
    );
    throw new Error(body.erreur ?? body.message ?? res.statusText);
  }
  return parseJson<T>(res);
}

export function getPlaintesRh(typePlainte?: string, statut?: string) {
  const params = new URLSearchParams();
  if (typePlainte) params.set('type', typePlainte);
  if (statut) params.set('statut', statut);
  const query = params.toString();
  return apiFetch(`/api/rh/v1/plaintes/liste${query ? '?' + query : ''}`).then((r) => handleRhResponse<PlainteRh[]>(r));
}

export function patchPlainteStatut(id: string, body: { statut: string; commentaire_rh?: string }) {
  return apiFetch(`/api/rh/v1/plaintes/${id}/statut`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<PlainteRh>(r));
}

export function getDemandesAdministrativesListe(filtres: { statut?: string; type_demande?: string } = {}) {
  const q = new URLSearchParams();
  if (filtres.statut) q.set('statut', filtres.statut);
  if (filtres.type_demande) q.set('type_demande', filtres.type_demande);
  const query = q.toString();
  return apiFetch(`/api/rh/v1/demandes-administratives/liste${query ? '?' + query : ''}`).then((r) =>
    handleRhResponse<DemandeAdministrative[]>(r),
  );
}

/** Approbation finale RRH (M01 § 6.1) — demande en EN_VALIDATION_RRH. */
export function postDemandeAdministrativeValiderRrh(id: string) {
  return apiFetch(`/api/rh/v1/demandes-administratives/${id}/valider-rrh`, {
    method: 'POST',
  }).then((r) => handleRhResponse<DemandeAdministrative>(r));
}

/** Refus RRH, motif obligatoire (C-M01-03). */
export function postDemandeAdministrativeRefuserRrh(id: string, motif_refus: string) {
  return apiFetch(`/api/rh/v1/demandes-administratives/${id}/refuser-rrh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motif_refus }),
  }).then((r) => handleRhResponse<DemandeAdministrative>(r));
}

export function getDemandeAdministrativeSuivi(id: string) {
  return apiFetch(`/api/rh/v1/demandes-administratives/${id}/suivi`).then((r) =>
    handleRhResponse<DemandeAdministrativeSuivi>(r),
  );
}

export function getDemandeAdministrativeHistorique(id: string) {
  return apiFetch(`/api/rh/v1/demandes-administratives/${id}/historique`).then((r) =>
    handleRhResponse<DemandeAdministrativeHistorique[]>(r),
  );
}

export function getDemandesFormations(statut?: string) {
  const q = new URLSearchParams();
  if (statut) q.set('statut', statut);
  const query = q.toString();
  return apiFetch(`/api/rh/v1/demandes-formations/liste${query ? '?' + query : ''}`).then((r) =>
    handleRhResponse<DemandeFormation[]>(r),
  );
}

export function postFormationIntegrerPlan(id: string, commentaire_rh?: string) {
  return apiFetch(`/api/rh/v1/demandes-formations/${id}/integrer-plan`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ commentaire_rh: commentaire_rh || undefined }),
  }).then((r) => handleRhResponse<DemandeFormation>(r));
}

export function postFormationRefuser(id: string, motif_refus: string) {
  return apiFetch(`/api/rh/v1/demandes-formations/${id}/refuser`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motif_refus }),
  }).then((r) => handleRhResponse<DemandeFormation>(r));
}

export function getDocumentsFileAttente() {
  return apiFetch('/api/rh/v1/demandes-documents-administratifs/file-attente').then((r) =>
    handleRhResponse<DemandeDocument[]>(r),
  );
}

export function postPrendreProchaineDocument() {
  return apiFetch('/api/rh/v1/demandes-documents-administratifs/prendre-prochaine', {
    method: 'POST',
  }).then((r) => handleRhResponse<DemandeDocument>(r));
}

export function postDocumentDisponible(
  id: string,
  reference_livrable: string,
  commentaire_rh?: string,
  justification_derogation_fifo?: string,
) {
  return apiFetch(`/api/rh/v1/demandes-documents-administratifs/${id}/disponible`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      reference_livrable,
      commentaire_rh: commentaire_rh ?? undefined,
      justification_derogation_fifo: justification_derogation_fifo ?? undefined,
    }),
  }).then((r) => handleRhResponse<DemandeDocument>(r));
}

export function postDocumentRejet(
  id: string,
  motif: string,
  justification_derogation_fifo?: string,
) {
  return apiFetch(`/api/rh/v1/demandes-documents-administratifs/${id}/refuser`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      motif,
      justification_derogation_fifo: justification_derogation_fifo ?? undefined,
    }),
  }).then((r) => handleRhResponse<DemandeDocument>(r));
}

export function getCollaborateursPage(page: number, taille: number) {
  const q = new URLSearchParams({ page: String(page), taille: String(taille) });
  return apiFetch(`/api/referentiel/v1/collaborateurs?${q}`).then((r) =>
    handleRhResponse<PageCollaborateurs>(r),
  );
}

export function getUnites() {
  return apiFetch('/api/referentiel/v1/unites').then((r) => handleRhResponse<Unite[]>(r));
}

export function postUnite(body: { code: string; libelle: string; actif?: boolean; parent_identifiant?: string }) {
  return apiFetch('/api/referentiel/v1/unites', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<Unite>(r));
}

export function putUnite(id: string, body: { libelle?: string; parent_identifiant?: string | null; actif?: boolean }) {
  return apiFetch(`/api/referentiel/v1/unites/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<Unite>(r));
}

export function postCollaborateur(body: Record<string, unknown>) {
  return apiFetch('/api/referentiel/v1/collaborateurs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<CollaborateurRow>(r));
}

export function putCollaborateur(id: string, body: Record<string, unknown>) {
  return apiFetch(`/api/referentiel/v1/collaborateurs/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<CollaborateurRow>(r));
}

// Types (champs JSON API)
export type PlainteRh = {
  identifiant: string;
  numero_ticket: string;
  type_plainte: 'INTERNE' | 'EXTERNE';
  auteur_identifiant: string;
  auteur_nom?: string;
  auteur_matricule?: string;
  titre: string;
  description: string;
  statut: 'NOUVEAU' | 'EN_ANALYSE' | 'EN_TRAITEMENT' | 'RESOLU' | 'FERME';
  /** Plainte EXTERNE uniquement (§ 9.2 valider-ro). */
  validation_ro?: 'EN_ATTENTE' | 'VALIDEE' | 'NON_REQUISE' | null;
  commentaire_rh?: string;
  /** URLs des pièces jointes (cf. PlainteRhResponse.pieces_jointes). */
  pieces_jointes: string[];
  transcription_audio?: string;
  log_actions: Array<{
    action?: string;
    acteur_nom?: string;
    ancien_statut?: string;
    nouveau_statut?: string;
    horodatage?: string;
    commentaire?: string;
    acteur_id?: string;
  }>;
  cree_le: string;
  modifie_le?: string;
};

export type DemandeAdministrative = {
  identifiant: string;
  type_demande: string;
  demandeur_identifiant: string;
  /** SCRUM-15 : identité lisible du demandeur. */
  demandeur_nom?: string;
  demandeur_matricule?: string;
  demandeur_unite?: string | null;
  statut: string;
  periode_debut?: string;
  periode_fin?: string;
  motif_refus?: string;
  contenu?: Record<string, unknown>;
  cree_le: string;
};

export type DemandeAdministrativeSuivi = {
  identifiant: string;
  statut: string;
  etape_superieur_requise: boolean;
  valideur_attendu_nom_complet?: string;
  valideur_attendu_matricule?: string;
  message_explication?: string;
  etapes: Array<{ code: string; libelle: string; terminee: boolean; en_cours: boolean }>;
};

export type DemandeAdministrativeHistorique = {
  identifiant: string;
  action: string;
  acteur_identifiant?: string;
  acteur_nom?: string;
  commentaire?: string;
  date_action: string;
};

export type DemandeFormation = {
  identifiant: string;
  demandeur_identifiant: string;
  demandeur_nom?: string;
  origine: 'CHEF_DEPARTEMENT' | 'RESPONSABLE_OPERATIONNEL';
  cible: 'UNITE' | 'COLLABORATEURS';
  unite_cible_identifiant?: string;
  unite_cible_libelle?: string;
  collaborateurs_cibles_identifiants?: string[];
  type_formation: string;
  organisme: string;
  duree_heures: number;
  cout_estime?: number;
  objectifs_pedagogiques: string;
  justification: string;
  statut: 'EN_VALIDATION_RRH' | 'INTEGREE_PLAN' | 'REFUSEE' | 'ANNULEE';
  commentaire_rh?: string;
  cree_le: string;
};

export type DemandeDocument = {
  identifiant: string;
  type_document: string;
  demandeur_identifiant: string;
  /** SCRUM-15 : identité lisible du demandeur. */
  demandeur_nom?: string;
  demandeur_matricule?: string;
  demandeur_unite?: string | null;
  statut: string;
  delai_sla_heures: number;
  rang_dans_file?: number;
  en_retard: boolean;
  est_prochaine_fifo?: boolean;
  reference_livrable?: string;
  commentaire_demandeur?: string;
  departement_libelle?: string;
  justification_derogation_fifo?: string;
  derogation_fifo_par?: string;
  cree_le: string;
};

export const PROFILS_ACCES = [
  'COLLABORATEUR',
  'RO',
  'RESPONSABLE',
  'RH',
  'DIRECTION',
  'ADMIN',
] as const;

export type ProfilAcces = (typeof PROFILS_ACCES)[number];

export const LIBELLES_PROFIL_ACCES: Record<ProfilAcces, string> = {
  COLLABORATEUR: 'Collaborateur',
  RO: 'Responsable opérationnel',
  RESPONSABLE: 'Chef de département',
  RH: 'Responsable RH',
  DIRECTION: 'Direction',
  ADMIN: 'Admin technique',
};

export function libelleProfilAcces(code: string | null | undefined): string {
  if (!code) return '—';
  const key = code.toUpperCase() as ProfilAcces;
  return LIBELLES_PROFIL_ACCES[key] ?? code;
}

export function isProfilAcces(value: string | null | undefined): value is ProfilAcces {
  return !!value && (PROFILS_ACCES as readonly string[]).includes(value.toUpperCase());
}

export type CollaborateurRow = {
  identifiant: string;
  matricule: string;
  prenom: string;
  /** Champ API JSON (`nom`). */
  nom?: string;
  /** Alias historique front ; l’API expose `nom`. */
  name?: string;
  courriel_professionnel?: string;
  poste_libelle?: string;
  fonction?: string;
  qualification_affectation?: string;
  qualite?: string;
  affectation?: string;
  departement_libelle?: string;
  date_recrutement?: string;
  superieur_identifiant?: string;
  unite?: Unite;
  profil_acces?: ProfilAcces;
  /** Matching M07 E2 — optionnel tant que backend non merge. */
  famille_metier_code?: string | null;
  famille_metier_libelle?: string | null;
  niveau_seniorite?: string | null;
  statut: string;
  compte_utilisateur_id?: string;
};

export type FamilleMetierRow = {
  code: string;
  libelle: string;
  actif: boolean;
  systeme?: boolean;
};

export type NiveauSenioriteRow = {
  code: string;
  libelle: string;
};

/** Catalogue familles métier (E2). Fallback seed côté UI si 404. */
export function getFamillesMetier(actifOnly = true) {
  const q = actifOnly ? '?actif=true' : '';
  return apiFetch(`/api/referentiel/v1/familles-metier${q}`).then((r) =>
    handleRhResponse<FamilleMetierRow[]>(r),
  );
}

/** Niveaux de séniorité fermés (E2). */
export function getNiveauxSeniorite() {
  return apiFetch('/api/referentiel/v1/niveaux-seniorite').then((r) =>
    handleRhResponse<NiveauSenioriteRow[]>(r),
  );
}

export function nomCollaborateur(c: Pick<CollaborateurRow, 'nom' | 'name'>): string {
  return (c.nom ?? c.name ?? '').trim();
}

export type PageCollaborateurs = {
  contenu: CollaborateurRow[];
  total_elements: number;
  total_pages: number;
  page: number;
  taille: number;
};

export type Unite = {
  identifiant: string;
  code: string;
  libelle: string;
  parent_identifiant: string | null;
  type_noeud?: string | null;
  titre_poste?: string | null;
  actif: boolean;
};

export type OrganigrammeMembre = {
  identifiant: string;
  matricule: string;
  prenom: string;
  nom: string;
  poste_libelle?: string | null;
  profil_acces?: ProfilAcces | null;
};

export type OrganigrammeNoeud = {
  identifiant: string;
  code: string;
  libelle: string;
  type_noeud: string;
  titre_poste?: string | null;
  parent_identifiant: string | null;
  actif: boolean;
  manager?: OrganigrammeMembre | null;
  membres: OrganigrammeMembre[];
  enfants: OrganigrammeNoeud[];
};

export type Organigramme = {
  racines: OrganigrammeNoeud[];
};

export function getOrganigramme(inclureInactifs = false) {
  const q = inclureInactifs ? '?inclure_inactifs=true' : '';
  return apiFetch(`/api/referentiel/v1/organigramme${q}`).then((r) =>
    handleRhResponse<Organigramme>(r),
  );
}

export function postOrganigrammeNoeud(body: {
  code: string;
  libelle: string;
  type_noeud: string;
  titre_poste?: string;
  parent_identifiant?: string;
  actif?: boolean;
}) {
  return apiFetch('/api/referentiel/v1/organigramme/noeuds', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<OrganigrammeNoeud>(r));
}

export function putOrganigrammeNoeud(
  id: string,
  body: {
    libelle?: string;
    type_noeud?: string;
    titre_poste?: string;
    parent_identifiant?: string;
    detacher_du_parent?: boolean;
    actif?: boolean;
  },
) {
  return apiFetch(`/api/referentiel/v1/organigramme/noeuds/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<OrganigrammeNoeud>(r));
}

export function postOrganigrammeManager(
  noeudId: string,
  body: {
    collaborateur_identifiant: string;
    titre_poste?: string;
  },
) {
  const payload: { collaborateur_identifiant: string; titre_poste?: string } = {
    collaborateur_identifiant: body.collaborateur_identifiant,
  };
  if (body.titre_poste) {
    payload.titre_poste = body.titre_poste;
  }
  return apiFetch(`/api/referentiel/v1/organigramme/noeuds/${noeudId}/manager`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then((r) => handleRhResponse<OrganigrammeNoeud>(r));
}

export function deleteOrganigrammeManager(noeudId: string) {
  return apiFetch(`/api/referentiel/v1/organigramme/noeuds/${noeudId}/manager`, {
    method: 'DELETE',
  }).then((r) => handleRhResponse<OrganigrammeNoeud>(r));
}

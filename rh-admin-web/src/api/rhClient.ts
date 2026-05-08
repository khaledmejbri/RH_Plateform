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
    const body = await parseJson<{ erreur?: string; message?: string }>(res).catch(() => ({}));
    throw new Error(body.erreur ?? body.message ?? res.statusText);
  }
  return parseJson<T>(res);
}

export function getPlaintesRh() {
  return apiFetch('/api/rh/v1/plaintes/liste').then((r) => handleRhResponse<PlainteRh[]>(r));
}

export function patchPlainteStatut(id: string, body: { statut: string; commentaire_rh?: string }) {
  return apiFetch(`/api/rh/v1/plaintes/${id}/statut`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handleRhResponse<PlainteRh>(r));
}

export function getDemandesAdministrativesListe() {
  return apiFetch('/api/rh/v1/demandes-administratives/liste').then((r) =>
    handleRhResponse<DemandeAdministrative[]>(r),
  );
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

export function postDocumentDisponible(id: string, reference_livrable: string, commentaire_rh?: string) {
  return apiFetch(`/api/rh/v1/demandes-documents-administratifs/${id}/disponible`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reference_livrable, commentaire_rh: commentaire_rh ?? undefined }),
  }).then((r) => handleRhResponse<DemandeDocument>(r));
}

export function postDocumentRejet(id: string, motif: string) {
  return apiFetch(`/api/rh/v1/demandes-documents-administratifs/${id}/rejeter`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motif }),
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
  type_plainte: string;
  auteur_collaborateur_identifiant: string;
  titre: string;
  description: string;
  statut: string;
  commentaire_rh?: string;
  cree_le: string;
  modifie_le?: string;
};

export type DemandeAdministrative = {
  identifiant: string;
  type_demande: string;
  demandeur_identifiant: string;
  statut: string;
  periode_debut?: string;
  periode_fin?: string;
  motif_refus?: string;
  contenu?: Record<string, unknown>;
  cree_le: string;
};

export type DemandeDocument = {
  identifiant: string;
  type_document: string;
  demandeur_identifiant: string;
  statut: string;
  delai_sla_heures: number;
  rang_dans_file?: number;
  en_retard: boolean;
  reference_livrable?: string;
  commentaire_demandeur?: string;
  departement_libelle?: string;
  cree_le: string;
};

export type CollaborateurRow = {
  identifiant: string;
  matricule: string;
  prenom: string;
  nom: string;
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
  profil_acces?: string;
  statut: string;
  compte_utilisateur_id?: string;
};

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
  actif: boolean;
};

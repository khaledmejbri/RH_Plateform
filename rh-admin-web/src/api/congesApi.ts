import {
  ApiMetierError, appelerJson, avecParametres, corpsJson, telecharger, type PageContrat,
} from './metierApi';

/** Soldes de congé (back-office), annexe A de l'ADR : B1 à B6 et D4. Les nombres sont des jours (décimaux). */

const BASE = '/api/rh/v1/admin/conges/soldes';

export type DernierMouvement = { date: string; type_libelle: string; nombre: number };

export type SoldeLigne = {
  collaborateur_id: string;
  matricule: string;
  nom_complet: string;
  unite_libelle: string | null;
  statut: string;
  statut_libelle: string;
  acquis: number;
  reserve: number;
  disponible: number;
  dernier_mouvement: DernierMouvement | null;
  version: number;
  est_moi: boolean;
};

export type Mouvement = {
  type: string;
  type_libelle: string;
  nombre: number;
  solde_apres: number;
  cree_le: string;
  auteur_nom: string | null;
  origine: string | null;
  origine_libelle: string | null;
  motif: string | null;
  demande_id: string | null;
  demande_numero: string | null;
  mois_acquisition: string | null;
  mois_acquisition_libelle: string | null;
};

export type FiltresSoldes = {
  q?: string;
  unite_id?: string;
  statut?: string;
  tri?: string;
  sens?: 'asc' | 'desc';
  page?: number;
  taille?: number;
};

export function getSoldes(f: FiltresSoldes): Promise<PageContrat<SoldeLigne>> {
  return appelerJson(avecParametres(BASE, { ...f }));
}

export function exporterSoldes(f: FiltresSoldes): Promise<void> {
  const filtres = { ...f };
  delete filtres.page;
  delete filtres.taille;
  return telecharger(avecParametres(`${BASE}/export`, filtres), 'soldes-conge.csv');
}

export function getSolde(collaborateurId: string): Promise<SoldeLigne> {
  return appelerJson(`${BASE}/${encodeURIComponent(collaborateurId)}`);
}

export function getMouvements(
  collaborateurId: string,
  page = 0,
  taille = 20,
  type = '',
): Promise<PageContrat<Mouvement>> {
  return appelerJson(avecParametres(`${BASE}/${encodeURIComponent(collaborateurId)}/mouvements`, { page, taille, type }));
}

export function exporterMouvements(collaborateurId: string): Promise<void> {
  return telecharger(`${BASE}/${encodeURIComponent(collaborateurId)}/mouvements/export`, 'mouvements-conge.csv');
}

export type CorpsAjustement =
  | { ecart: number; motif: string; version: number }
  | { nouveau_solde: number; motif: string; version: number };

export type ReponseAjustement = { solde: SoldeLigne; mouvement: Mouvement; avertissement: string | null };

export function ajusterSolde(collaborateurId: string, corps: CorpsAjustement): Promise<ReponseAjustement> {
  return appelerJson(`${BASE}/${encodeURIComponent(collaborateurId)}/ajustements`, corpsJson('POST', corps));
}

/** D4 : annulation d'un congé approuvé par un RH autre que le demandeur. */
export function annulerApresApprobation(demandeId: string, motif: string) {
  return appelerJson<Record<string, unknown>>(
    `/api/rh/v1/demandes-administratives/${encodeURIComponent(demandeId)}/annuler-apres-approbation`,
    corpsJson('POST', { motif }),
  );
}

/** Message affichable ; le texte serveur est en français et fait foi. */
export function messageConges(e: unknown): string {
  if (!(e instanceof ApiMetierError)) return 'Une erreur inattendue est survenue. Rien n’a été modifié.';
  if (e.code === 'SOLDE_MODIFIE_ENTRE_TEMPS') return 'Le solde a changé entre-temps. La liste a été rechargée, vérifiez puis recommencez.';
  if (e.code === 'DEMANDE_DEJA_DECIDEE') return 'Cette demande a déjà été décidée. La liste a été rechargée.';
  if (e.code === 'CONGE_DEJA_COMMENCE') return e.message || 'Ce congé a déjà commencé : il ne peut plus être annulé.';
  return e.message;
}

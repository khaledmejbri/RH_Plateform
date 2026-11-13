import { apiFetch, clearToken } from './auth';

/**
 * Appels JSON et téléchargements du lot « soldes de congé et imports » (ADR-soldes-conge-import, annexe A).
 * Erreur : `{ code, erreur, message, ...champs propres }` ; 401 → retour à la connexion.
 */
export class ApiMetierError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
    readonly donnees: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiMetierError';
  }
}

export type PageContrat<T> = {
  contenu: T[];
  total_elements: number;
  total_pages: number;
  page: number;
  taille: number;
};

function sessionExpiree(): never {
  clearToken();
  window.location.href = '/login';
  throw new ApiMetierError('Session expirée', 401);
}

/** Texte de repli quand le serveur n'a pas fourni de message lisible (ex. page HTML d'un proxy). */
function messageParDefaut(status: number): string {
  if (status === 403) return 'Action non autorisée pour votre profil.';
  if (status === 404) return 'Élément introuvable.';
  if (status === 413) return 'Le fichier dépasse 10 Mo.';
  if (status === 415 || status === 400) return 'La requête n’est pas valide.';
  if (status >= 500) return 'Le serveur a rencontré une erreur. Rien n’a été modifié, réessayez plus tard.';
  return 'L’opération a échoué.';
}

async function erreurDepuis(res: Response): Promise<ApiMetierError> {
  let corps: Record<string, unknown> = {};
  try {
    const texte = await res.text();
    if (texte && texte.trim().startsWith('{')) corps = JSON.parse(texte) as Record<string, unknown>;
  } catch {
    corps = {};
  }
  // Un 413 ou 400 de proxy (HTML) n'a pas de corps JSON : texte de repli, jamais la page brute.
  const message = typeof corps.message === 'string' && corps.message
    ? corps.message
    : typeof corps.erreur === 'string' && corps.erreur ? corps.erreur : messageParDefaut(res.status);
  return new ApiMetierError(message, res.status, typeof corps.code === 'string' ? corps.code : null, corps);
}

async function envoyer(url: string, init: RequestInit): Promise<Response> {
  let res: Response;
  try {
    res = await apiFetch(url, init);
  } catch {
    throw new ApiMetierError('Le serveur ne répond pas. Rien n’a été modifié.', 0);
  }
  if (res.status === 401) sessionExpiree();
  if (!res.ok) throw await erreurDepuis(res);
  return res;
}

export async function appelerJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const res = await envoyer(url, init);
  if (res.status === 204) return undefined as T;
  const texte = await res.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
}

export function corpsJson(methode: string, corps: unknown): RequestInit {
  return { method: methode, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) };
}

export function avecParametres(url: string, params: Record<string, string | number | boolean | null | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `${url}?${s}` : url;
}

function nomDepuisEntete(res: Response, defaut: string): string {
  const cd = res.headers.get('Content-Disposition') ?? '';
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  if (!m) return defaut;
  try {
    return decodeURIComponent(m[1]);
  } catch {
    return m[1];
  }
}

/** Télécharge un fichier protégé (jeton) et le propose à l'enregistrement. */
export async function telecharger(url: string, nomParDefaut: string): Promise<void> {
  const res = await envoyer(url, { headers: { Accept: '*/*' } });
  const blob = await res.blob();
  const lien = document.createElement('a');
  const objet = URL.createObjectURL(blob);
  lien.href = objet;
  lien.download = nomDepuisEntete(res, nomParDefaut);
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(objet);
}

export function envoyerFormulaire<T>(url: string, formulaire: FormData): Promise<T> {
  // Pas d'en-tête Content-Type : le navigateur pose la frontière multipart.
  return appelerJson<T>(url, { method: 'POST', body: formulaire });
}

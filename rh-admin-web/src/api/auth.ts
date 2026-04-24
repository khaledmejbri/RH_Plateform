const TOKEN_KEY = 'rh_admin_access_token';

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

export async function signIn(nomUtilisateur: string, motDePasse: string): Promise<void> {
  const res = await fetch('/api/auth/signin', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      nom_utilisateur: nomUtilisateur.trim(),
      mot_de_passe: motDePasse,
    }),
  });
  if (!res.ok) {
    const err = (await res.json().catch(() => ({}))) as { erreur?: string; message?: string };
    throw new Error(err.erreur ?? err.message ?? res.statusText);
  }
  const data = (await res.json()) as {
    jeton_acces?: string;
    access_token?: string;
    accessToken?: string;
  };
  const token = data.jeton_acces ?? data.access_token ?? data.accessToken;
  if (!token) throw new Error('Réponse sans jeton');
  setToken(token);
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(path, { ...init, headers });
}

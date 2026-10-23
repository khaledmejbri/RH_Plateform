/** Décodage payload JWT (sans vérif signature — déjà validée par l'API). */

export type RoleBackoffice = 'RH' | 'DIRECTION' | 'ADMIN';

const CATALOGUE = new Set(['USER', 'RO', 'RESPONSABLE', 'RH', 'DIRECTION', 'ADMIN']);

function normalize(raw: string): string {
  let v = raw.trim().toUpperCase();
  if (v.startsWith('ROLE_')) v = v.slice(5);
  return v;
}

function claimValues(claim: unknown): string[] {
  if (claim == null) return [];
  if (Array.isArray(claim)) return claim.map(String);
  if (typeof claim === 'string') {
    return claim.split(/[\s,]+/).filter(Boolean);
  }
  return [String(claim)];
}

export function decodeJwtPayload(token: string | null): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
    const decoded = JSON.parse(json) as unknown;
    if (decoded && typeof decoded === 'object') return decoded as Record<string, unknown>;
    return null;
  } catch {
    return null;
  }
}

export function rolesFromAccessToken(token: string | null): Set<string> {
  const payload = decodeJwtPayload(token);
  if (!payload) return new Set();
  const extracted = [
    ...claimValues(payload.roles),
    ...claimValues(payload.scope),
  ].map(normalize).filter((r) => CATALOGUE.has(r));
  return new Set(extracted);
}

export function hasAnyRole(token: string | null, ...wanted: string[]): boolean {
  const roles = rolesFromAccessToken(token);
  return wanted.some((w) => roles.has(w.toUpperCase()));
}

export function canAccessBackoffice(token: string | null): boolean {
  return hasAnyRole(token, 'RH', 'DIRECTION', 'ADMIN');
}

export function isDirectionOnly(token: string | null): boolean {
  const roles = rolesFromAccessToken(token);
  return roles.has('DIRECTION') && !roles.has('RH') && !roles.has('ADMIN');
}

export function canWriteBackoffice(token: string | null): boolean {
  return hasAnyRole(token, 'RH', 'ADMIN');
}

/** Identifiant de l'utilisateur connecté (claim `identifiant_utilisateur`). */
export function currentUserId(): string {
  const token = sessionStorage.getItem('rh_admin_access_token');
  const id = decodeJwtPayload(token)?.identifiant_utilisateur;
  if (typeof id !== 'string' || !id) throw new Error('Session invalide : reconnectez-vous.');
  return id;
}

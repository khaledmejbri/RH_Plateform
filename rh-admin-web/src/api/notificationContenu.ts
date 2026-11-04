/**
 * Contenu structuré d'une notification : `TYPE|cle=valeur|...|texte` (convention du backend).
 * Alerte d'écart : `ALERTE_ECART_EVALUATION|alerte=<uuid>|niveau=<NIVEAU>|<texte>` (ADR-M07-alertes-ecart § 7).
 */
export type ContenuNotification = {
  type: string;
  cles: Record<string, string>;
  texte: string;
};

const TYPE_ALERTE_ECART = 'ALERTE_ECART_EVALUATION';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function parseContenuNotification(content: unknown): ContenuNotification | null {
  if (typeof content !== 'string') return null;
  const segments = content.split('|');
  if (segments.length < 2 || !/^[A-Z][A-Z0-9_]+$/.test(segments[0])) return null;
  const cles: Record<string, string> = {};
  let i = 1;
  for (; i < segments.length; i++) {
    const m = /^([a-zA-Z_]+)=(.*)$/.exec(segments[i]);
    if (!m) break;
    cles[m[1]] = m[2];
  }
  return { type: segments[0], cles, texte: segments.slice(i).join('|') };
}

export type AlerteEcartNotification = { alerteId: string; niveau: string | null; texte: string };

/** Infos d'une notification d'alerte d'écart, ou null (autre type ou identifiant invalide). */
export function alerteEcartDeNotification(content: unknown): AlerteEcartNotification | null {
  const c = parseContenuNotification(content);
  if (!c || c.type !== TYPE_ALERTE_ECART) return null;
  const alerteId = c.cles.alerte;
  if (!alerteId || !UUID.test(alerteId)) return null;
  return { alerteId, niveau: c.cles.niveau ?? null, texte: c.texte };
}

export function routeAlerteEcart(alerteId: string): string {
  return `/app/evaluations/alertes-ecart/${encodeURIComponent(alerteId)}`;
}

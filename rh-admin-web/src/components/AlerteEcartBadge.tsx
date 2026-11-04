import {
  LIBELLES_NIVEAU_ECART,
  LIBELLES_STATUT_ALERTE,
  type NiveauEcart,
  type StatutAlerteEcart,
} from '../api/alertesEcartApi';

const ICONES: Record<NiveauEcart, string> = { ALIGNE: '≈', ORANGE: '!', ROUGE: '!!', URGENTE: '!!!' };

/** Niveau d'écart : couleur + symbole + mot écrit (jamais la couleur seule, contraste ≥ 4,5:1). */
export function AlerteEcartBadge({ niveau }: { niveau: NiveauEcart | null | undefined }) {
  if (!niveau) return <span className="text-muted">—</span>;
  const libelle = LIBELLES_NIVEAU_ECART[niveau] ?? niveau;
  return (
    <span className={`badge badge--ecart-${niveau.toLowerCase()}`}>
      <span aria-hidden="true">{ICONES[niveau] ?? '·'}</span> {libelle}
    </span>
  );
}

export function StatutAlerteBadge({ statut }: { statut: StatutAlerteEcart }) {
  const classe = statut === 'TRAITEE' ? 'badge--ecart-aligne' : statut === 'EN_COURS' ? 'badge--info' : 'badge--default';
  return <span className={`badge ${classe}`}>{LIBELLES_STATUT_ALERTE[statut] ?? statut}</span>;
}

import {
  LIBELLES_NIVEAU_ECART,
  LIBELLES_STATUT_ALERTE,
  type NiveauEcart,
  type StatutAlerteEcart,
} from '../api/alertesEcartApi';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';

/** Pastille de couleur (la couleur vient de la classe du badge) suivie du libellé écrit. */
const PASTILLE = '●';

/** Niveau d'écart : couleur + symbole + mot écrit (jamais la couleur seule, contraste ≥ 4,5:1). */
export function AlerteEcartBadge({ niveau }: { niveau: NiveauEcart | null | undefined }) {
  if (!niveau) return <span className="text-muted">—</span>;
  const libelle = LIBELLES_NIVEAU_ECART[niveau] ?? VALEUR_NON_RECONNUE;
  return (
    <span className={`badge badge--ecart-${niveau.toLowerCase()}`}>
      <span aria-hidden="true">{PASTILLE}</span> {libelle}
    </span>
  );
}

export function StatutAlerteBadge({ statut }: { statut: StatutAlerteEcart }) {
  const classe = statut === 'TRAITEE' ? 'badge--ecart-aligne' : statut === 'EN_COURS' ? 'badge--info' : 'badge--default';
  return <span className={`badge ${classe}`}>{LIBELLES_STATUT_ALERTE[statut] ?? VALEUR_NON_RECONNUE}</span>;
}

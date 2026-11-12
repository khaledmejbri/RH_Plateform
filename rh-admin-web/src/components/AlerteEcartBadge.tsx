import {
  type NiveauEcart,
  type StatutAlerteEcart,
} from '../api/alertesEcartApi';
import { libelleNiveauAlerte } from '../utils/libelles';
import { libelleReferentiel, valeurReferentiel } from '../utils/parametresStore';
import PastilleCouleur from './PastilleCouleur';

/** Pastille de couleur suivie du libellé écrit. */
const PASTILLE = '●';

/**
 * Niveau d'écart : couleur de la palette (contraste AA) + symbole + mot écrit, jamais la couleur seule.
 * Libellé et couleur viennent du paramétrage (NIVEAU_ALERTE) ; « Aligné » est la constante « pas d'écart ».
 */
export function AlerteEcartBadge({ niveau }: { niveau: NiveauEcart | null | undefined }) {
  if (!niveau) return <span className="text-muted">—</span>;
  const aligne = niveau.toUpperCase() === 'ALIGNE';
  const couleur = aligne ? 'VERT' : valeurReferentiel('NIVEAU_ALERTE', niveau)?.couleur ?? 'NEUTRE';
  return (
    <PastilleCouleur couleur={couleur}>{`${PASTILLE} ${libelleNiveauAlerte(niveau)}`}</PastilleCouleur>
  );
}

export function StatutAlerteBadge({ statut }: { statut: StatutAlerteEcart }) {
  const classe = statut === 'TRAITEE' ? 'badge--ecart-aligne' : statut === 'EN_COURS' ? 'badge--info' : 'badge--default';
  return <span className={`badge ${classe}`}>{libelleReferentiel('STATUT_ALERTE', statut)}</span>;
}

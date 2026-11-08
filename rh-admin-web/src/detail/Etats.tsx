import type { ReactNode } from 'react';
import { statutHttp } from '../api/detailApi';
import type { EtatChargement } from './useChargement';

export function messageErreur(e: unknown): { texte: string; reessayer: boolean } {
  const s = statutHttp(e);
  if (s === 403) return { texte: 'Vous n’avez pas accès à cette information.', reessayer: false };
  if (s === 404 || s === 400) return { texte: 'Élément introuvable.', reessayer: false };
  if (s === 503 || s === 0 || s === 502) return { texte: 'Service momentanément indisponible.', reessayer: true };
  return { texte: 'Impossible de charger ces informations.', reessayer: true };
}

export function Squelette() {
  return (
    <div className="detail-skel" role="status" aria-busy="true" aria-live="polite">
      <span className="sr-only">Chargement…</span>
      <div /><div /><div />
    </div>
  );
}

export function ErreurOnglet({ erreur, onRetry }: { erreur: unknown; onRetry: () => void }) {
  const m = messageErreur(erreur);
  return (
    <div className="alert alert--error" role="alert">
      {m.texte}{' '}
      {m.reessayer ? <button type="button" className="btn btn--secondary btn--sm" onClick={onRetry}>Réessayer</button> : null}
    </div>
  );
}

export function Vide({ children }: { children: ReactNode }) {
  return <p className="detail-vide">{children}</p>;
}

/** Quatre états d'un onglet : chargement, erreur, vide, données. */
export function Charge<T>({
  etat, recharger, vide, messageVide, children,
}: {
  etat: EtatChargement<T>;
  recharger: () => void;
  vide?: (data: T) => boolean;
  messageVide?: string;
  children: (data: T) => ReactNode;
}) {
  if (etat.statut === 'chargement') return <Squelette />;
  if (etat.statut === 'erreur') return <ErreurOnglet erreur={etat.erreur} onRetry={recharger} />;
  if (vide && vide(etat.data)) return <Vide>{messageVide ?? 'Aucune information.'}</Vide>;
  return <>{children(etat.data)}</>;
}

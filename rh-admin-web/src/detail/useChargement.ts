import { useCallback, useEffect, useRef, useState } from 'react';

export type EtatChargement<T> =
  | { statut: 'chargement' }
  | { statut: 'ok'; data: T }
  | { statut: 'erreur'; erreur: unknown };

/** Charge une donnée ; `actif=false` diffère l'appel (onglet non ouvert). `recharger` relance. */
export function useChargement<T>(charger: () => Promise<T>, deps: ReadonlyArray<unknown>, actif = true) {
  const [etat, setEtat] = useState<EtatChargement<T>>({ statut: 'chargement' });
  const [essai, setEssai] = useState(0);
  const chargerRef = useRef(charger);
  chargerRef.current = charger;

  useEffect(() => {
    if (!actif) return undefined;
    let annule = false;
    setEtat({ statut: 'chargement' });
    chargerRef.current().then(
      (data) => { if (!annule) setEtat({ statut: 'ok', data }); },
      (erreur: unknown) => { if (!annule) setEtat({ statut: 'erreur', erreur }); },
    );
    return () => { annule = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, actif, essai]);

  const recharger = useCallback(() => setEssai((n) => n + 1), []);
  return { etat, recharger };
}

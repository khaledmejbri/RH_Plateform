import { createContext, useContext, useEffect } from 'react';
import type { DetailRef } from './refs';
import { getToken } from '../api/auth';
import { hasAnyRole } from '../auth/jwtRoles';

export type DetailCtx = { ouvrir: (ref: DetailRef) => void };

export const DetailContext = createContext<DetailCtx>({ ouvrir: () => undefined });
export const useDetail = () => useContext(DetailContext);

/** Fournit à la fiche affichée le moyen de définir le titre de la modale / de la page. */
export const TitreContext = createContext<(titre: string) => void>(() => undefined);

export function useTitre(titre: string | null | undefined) {
  const definir = useContext(TitreContext);
  useEffect(() => {
    if (titre) definir(titre);
  }, [titre, definir]);
}

/** Plaintes : RH et ADMIN seulement, jamais DIRECTION (D-N01.a). */
export function peutVoirPlaintes(): boolean {
  return hasAnyRole(getToken(), 'RH', 'ADMIN');
}

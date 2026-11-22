import { createContext, useCallback, useContext, useEffect, useMemo, useRef, type ReactNode } from 'react';
import { useConfirm } from '../components/ConfirmDialog';

/**
 * Garde de navigation (D-W7-05). Le routeur est un `BrowserRouter` : `useBlocker` n'est pas disponible.
 * Un écran qui contient des modifications non enregistrées active la garde ; les liens du menu et la
 * cloche de notifications appellent `confirmerSortie()` avant de quitter la page.
 */
type Garde = {
  /** Vrai si une page a déclaré des modifications non enregistrées. */
  estActive: () => boolean;
  /** Retourne vrai si la sortie est autorisée (garde inactive ou confirmation de l'utilisateur). */
  confirmerSortie: () => Promise<boolean>;
  activer: (actif: boolean) => void;
};

const GardeContext = createContext<Garde | null>(null);

export function GardeNavigationProvider({ children }: { children: ReactNode }) {
  const confirm = useConfirm();
  const actif = useRef(false);

  const activer = useCallback((a: boolean) => {
    actif.current = a;
  }, []);
  const estActive = useCallback(() => actif.current, []);
  const confirmerSortie = useCallback(async () => {
    if (!actif.current) return true;
    return confirm({
      title: 'Quitter sans enregistrer ?',
      message: 'Vous avez des modifications non enregistrées. Elles seront perdues si vous quittez cette page.',
      confirmLabel: 'Quitter sans enregistrer',
      danger: true,
    });
  }, [confirm]);

  const valeur = useMemo(() => ({ estActive, confirmerSortie, activer }), [estActive, confirmerSortie, activer]);
  return <GardeContext.Provider value={valeur}>{children}</GardeContext.Provider>;
}

/** Garde inerte hors fournisseur (tests, pages isolées). */
const GARDE_INERTE: Garde = {
  estActive: () => false,
  confirmerSortie: async () => true,
  activer: () => undefined,
};

export function useGardeNavigation(): Garde {
  return useContext(GardeContext) ?? GARDE_INERTE;
}

/** Active la garde tant que `actif` est vrai ; la désactive au démontage. */
export function useActiverGarde(actif: boolean): void {
  const { activer } = useGardeNavigation();
  useEffect(() => {
    activer(actif);
    return () => activer(false);
  }, [actif, activer]);
}

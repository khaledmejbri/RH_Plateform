import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export type ActionMenu = {
  libelle: string;
  onSelect: () => void;
  desactive?: boolean;
  /** Raison affichée en info-bulle quand l'entrée est grisée. */
  raison?: string;
};

/** Menu « … » d'une ligne de tableau : actions secondaires regroupées (COL-7). */
export default function MenuLigne({ libelle, actions }: { libelle: string; actions: ActionMenu[] }) {
  const [ouvert, setOuvert] = useState(false);
  const racine = useRef<HTMLDivElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);
  const liste = useRef<HTMLUListElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const idMenu = useId();

  useEffect(() => {
    if (!ouvert) return undefined;
    const fermerSiExterieur = (e: MouseEvent) => {
      const cible = e.target as Node;
      if (!racine.current?.contains(cible) && !liste.current?.contains(cible)) setOuvert(false);
    };
    const fermer = () => setOuvert(false);
    const fermerEchap = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOuvert(false);
    };
    document.addEventListener('mousedown', fermerSiExterieur);
    document.addEventListener('keydown', fermerEchap);
    window.addEventListener('scroll', fermer, true);
    window.addEventListener('resize', fermer);
    return () => {
      window.removeEventListener('scroll', fermer, true);
      window.removeEventListener('resize', fermer);
      document.removeEventListener('mousedown', fermerSiExterieur);
      document.removeEventListener('keydown', fermerEchap);
    };
  }, [ouvert]);

  // Position fixe calculée sur le bouton : le menu n'est jamais coupé par un tableau qui défile ;
  // il s'ouvre vers le haut quand la place manque en bas.
  useLayoutEffect(() => {
    if (!ouvert || !bouton.current || !liste.current) {
      setPos(null);
      return;
    }
    const b = bouton.current.getBoundingClientRect();
    const h = liste.current.offsetHeight;
    const l = liste.current.offsetWidth;
    const top = b.bottom + h + 8 > window.innerHeight ? Math.max(8, b.top - h - 4) : b.bottom + 4;
    setPos({ top, left: Math.max(8, Math.min(b.right - l, window.innerWidth - l - 8)) });
  }, [ouvert]);

  return (
    <div ref={racine} style={{ position: 'relative', display: 'inline-block' }}>
      <button
        ref={bouton}
        type="button"
        className="btn btn--ghost btn--sm"
        aria-haspopup="menu"
        aria-expanded={ouvert}
        aria-controls={ouvert ? idMenu : undefined}
        aria-label={libelle}
        title={libelle}
        onClick={() => setOuvert((o) => !o)}
      >
        <span aria-hidden>⋯</span>
      </button>
      {ouvert ? createPortal(
        <ul
          ref={liste}
          id={idMenu}
          role="menu"
          style={{
            position: 'fixed',
            top: pos?.top ?? 0,
            left: pos?.left ?? 0,
            visibility: pos ? 'visible' : 'hidden',
            zIndex: 1000,
            margin: 0,
            padding: 4,
            listStyle: 'none',
            minWidth: 220,
            background: 'var(--color-surface, #fff)',
            border: '1px solid var(--color-border, #d0d5dd)',
            borderRadius: 8,
            boxShadow: '0 6px 20px rgba(0,0,0,.15)',
          }}
        >
          {actions.map((a) => (
            <li key={a.libelle} role="none">
              <button
                type="button"
                role="menuitem"
                className="btn btn--ghost btn--sm"
                style={{ width: '100%', justifyContent: 'flex-start' }}
                disabled={a.desactive}
                title={a.desactive ? a.raison : undefined}
                onClick={() => {
                  setOuvert(false);
                  a.onSelect();
                }}
              >
                {a.libelle}
              </button>
            </li>
          ))}
        </ul>,
        document.body,
      ) : null}
    </div>
  );
}

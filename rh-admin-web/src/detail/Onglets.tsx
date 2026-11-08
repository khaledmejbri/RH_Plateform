import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';

export type OngletDef = { id: string; label: string };

/** Barre d'onglets au clavier (flèches, Début, Fin) et panneau associé. */
export default function Onglets({
  onglets, actif, onChange, children,
}: { onglets: OngletDef[]; actif: string; onChange: (id: string) => void; children: ReactNode }) {
  const base = useId();
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  function touche(e: KeyboardEvent, i: number) {
    let cible = -1;
    if (e.key === 'ArrowRight') cible = (i + 1) % onglets.length;
    else if (e.key === 'ArrowLeft') cible = (i - 1 + onglets.length) % onglets.length;
    else if (e.key === 'Home') cible = 0;
    else if (e.key === 'End') cible = onglets.length - 1;
    if (cible < 0) return;
    e.preventDefault();
    const o = onglets[cible];
    onChange(o.id);
    refs.current[o.id]?.focus();
  }

  return (
    <>
      <div className="detail-tabs" role="tablist" aria-label="Sections">
        {onglets.map((o, i) => (
          <button
            key={o.id}
            ref={(el) => { refs.current[o.id] = el; }}
            type="button"
            role="tab"
            id={`${base}-t-${o.id}`}
            aria-selected={o.id === actif}
            aria-controls={`${base}-p-${o.id}`}
            tabIndex={o.id === actif ? 0 : -1}
            className={`detail-tab${o.id === actif ? ' detail-tab--on' : ''}`}
            onClick={() => onChange(o.id)}
            onKeyDown={(e) => touche(e, i)}
          >
            {o.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-p-${actif}`} aria-labelledby={`${base}-t-${actif}`} className="detail-panel">
        {children}
      </div>
    </>
  );
}

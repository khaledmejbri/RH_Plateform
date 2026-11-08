import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { DetailContext, TitreContext } from './contexte';
import { routeDe, TITRE_PAR_DEFAUT, type DetailRef } from './refs';
import { useDialogA11y } from '../components/useDialogA11y';
import DetailContenu from './contenus/DetailContenu';

type Entree = { ref: DetailRef; onglet?: string; titre?: string };

/** Pile de modales de détail (liens croisés + « Retour »), montée une seule fois dans la coque. */
export function DetailProvider({ children }: { children: ReactNode }) {
  const [pile, setPile] = useState<Entree[]>([]);
  const { pathname } = useLocation();

  const ouvrir = useCallback((ref: DetailRef) => {
    setPile((p) => {
      const haut = p[p.length - 1];
      if (haut && haut.ref.type === ref.type && haut.ref.id === ref.id) return p;
      return [...p, { ref, titre: ref.libelle }];
    });
  }, []);
  const fermer = useCallback(() => setPile([]), []);
  const retour = useCallback(() => setPile((p) => p.slice(0, -1)), []);

  // Navigation (page complète, retour arrière du navigateur) : la modale se ferme.
  useEffect(() => { setPile([]); }, [pathname]);

  const ctx = useMemo(() => ({ ouvrir }), [ouvrir]);
  const haut = pile[pile.length - 1];

  return (
    <DetailContext.Provider value={ctx}>
      {children}
      {haut ? (
        <ModaleDetail
          entree={haut}
          profondeur={pile.length}
          onClose={fermer}
          onRetour={retour}
          onOnglet={(o) => setPile((p) => p.map((e, i) => (i === p.length - 1 ? { ...e, onglet: o } : e)))}
          onTitre={(t) => setPile((p) => p.map((e, i) => (i === p.length - 1 && e.titre !== t ? { ...e, titre: t } : e)))}
        />
      ) : null}
    </DetailContext.Provider>
  );
}

function ModaleDetail({
  entree, profondeur, onClose, onRetour, onOnglet, onTitre,
}: {
  entree: Entree;
  profondeur: number;
  onClose: () => void;
  onRetour: () => void;
  onOnglet: (o: string) => void;
  onTitre: (t: string) => void;
}) {
  const racine = useDialogA11y<HTMLDivElement>(onClose);
  const titreRef = useRef<HTMLHeadingElement>(null);
  const cle = `${entree.ref.type}:${entree.ref.id}`;
  const titre = entree.titre || TITRE_PAR_DEFAUT[entree.ref.type];

  // Focus initial sur le titre, à chaque objet affiché (ouverture et « Retour »).
  useEffect(() => { titreRef.current?.focus(); }, [cle]);

  useEffect(() => {
    const avant = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = avant; };
  }, []);

  return (
    <div className="dmodal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div ref={racine} role="dialog" aria-modal="true" aria-labelledby="dmodal-titre" tabIndex={-1} className="dmodal">
        <header className="dmodal__head">
          {profondeur > 1 ? (
            <button type="button" className="btn btn--ghost btn--sm" onClick={onRetour}>← Retour</button>
          ) : null}
          <h2 id="dmodal-titre" ref={titreRef} tabIndex={-1} className="dmodal__titre">{titre}</h2>
          <Link className="btn btn--secondary btn--sm" to={routeDe(entree.ref)} onClick={onClose}>
            Ouvrir la fiche complète
          </Link>
          <button type="button" className="modal__close" aria-label="Fermer" onClick={onClose}>×</button>
        </header>
        <div className="dmodal__body">
          <TitreContext.Provider value={onTitre}>
            <DetailContenu key={cle} detail={entree.ref} onglet={entree.onglet} onOnglet={onOnglet} />
          </TitreContext.Provider>
        </div>
      </div>
    </div>
  );
}

import type { MouseEvent, ReactNode } from 'react';
import { peutVoirPlaintes, useDetail } from './contexte';
import { routeDe, type DetailRef, type DetailType } from './refs';

/**
 * Nom ou référence cliquable (D-N01.b) : un clic ouvre la modale de détail ; Ctrl/Cmd + clic, clic molette ou
 * Maj + clic ouvrent la page complète (le lien est un vrai lien). Sans identifiant ou sans libellé : texte simple.
 */
export default function DetailLink({
  type, id, libelle, children, className,
}: { type: DetailType; id?: string | null; libelle?: string | null; children?: ReactNode; className?: string }) {
  const { ouvrir } = useDetail();
  const contenu = children ?? libelle;
  if (!id || contenu === null || contenu === undefined || contenu === '') return <>{contenu ?? ''}</>;
  if (type === 'plainte' && !peutVoirPlaintes()) return <>{contenu}</>;
  const ref: DetailRef = { type, id, libelle: libelle ?? undefined };

  function clic(e: MouseEvent<HTMLAnchorElement>) {
    if (e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    ouvrir(ref);
  }

  return (
    <a href={routeDe(ref)} className={`detail-link${className ? ` ${className}` : ''}`} onClick={clic}>
      {contenu}
    </a>
  );
}

/** Lien vers une personne : nom cliquable, matricule en complément. */
export function LienPersonne({
  id, nom, matricule,
}: { id?: string | null; nom?: string | null; matricule?: string | null }) {
  const n = (nom ?? '').trim();
  if (!n) return <>Collaborateur introuvable</>;
  return (
    <>
      <DetailLink type="collaborateur" id={id} libelle={n} />
      {matricule ? <span className="cell-meta"> ({matricule})</span> : null}
    </>
  );
}

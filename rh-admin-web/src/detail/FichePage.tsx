import { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { TitreContext, peutVoirPlaintes } from './contexte';
import { TITRE_PAR_DEFAUT, type DetailType } from './refs';
import DetailContenu from './contenus/DetailContenu';
import AccesRefusePage from '../pages/AccesRefusePage';

const LISTES: Record<DetailType, { label: string; to: string }> = {
  collaborateur: { label: 'Collaborateurs', to: '/app/collaborateurs' },
  unite: { label: 'Organigramme', to: '/app/organigramme' },
  site: { label: 'Sites et bornes', to: '/app/pointage' },
  campagne: { label: 'Évaluations', to: '/app/evaluations' },
  evaluation: { label: 'Évaluations', to: '/app/evaluations' },
  alerte: { label: 'Évaluations', to: '/app/evaluations' },
  demande: { label: 'Demandes administratives', to: '/app/demandes-administratives' },
  document: { label: 'Documents administratifs', to: '/app/documents-administratifs' },
  formation: { label: 'Formations', to: '/app/formations' },
  plainte: { label: 'Plaintes', to: '/app/plaintes' },
  'heures-sup': { label: 'Heures supplémentaires', to: '/app/heures-sup' },
  'pointage-manuel': { label: 'Pointages manuels', to: '/app/pointage?onglet=manuels' },
};

/** Page complète d'une fiche : même contenu que la modale, avec URL propre (onglet dans `?onglet=`). */
export default function FichePage({ type }: { type: DetailType }) {
  const { id = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const [titre, setTitre] = useState('');
  const liste = LISTES[type];
  const onglet = params.get('onglet') ?? undefined;

  useEffect(() => { setTitre(''); }, [id, type]);
  const definir = useCallback((t: string) => setTitre(t), []);
  useEffect(() => {
    document.title = `${titre || TITRE_PAR_DEFAUT[type]} — RH-Évènement`;
  }, [titre, type]);

  if (type === 'plainte' && !peutVoirPlaintes()) return <AccesRefusePage />;

  return (
    <div className="page">
      <nav aria-label="Fil d’Ariane" className="fiche-ariane">
        <Link to={liste.to}>{liste.label}</Link> <span aria-hidden>›</span> <span>{titre || TITRE_PAR_DEFAUT[type]}</span>
      </nav>
      <div className="page__head">
        <h2 className="page__title">{titre || TITRE_PAR_DEFAUT[type]}</h2>
        <Link className="btn btn--secondary btn--sm" to={liste.to}>Retour à la liste</Link>
      </div>
      <TitreContext.Provider value={definir}>
        <div className="panel">
          <DetailContenu
            key={`${type}:${id}`}
            detail={{ type, id }}
            onglet={onglet}
            onOnglet={(o) => setParams((p) => { const n = new URLSearchParams(p); n.set('onglet', o); return n; }, { replace: true })}
          />
        </div>
      </TitreContext.Provider>
    </div>
  );
}

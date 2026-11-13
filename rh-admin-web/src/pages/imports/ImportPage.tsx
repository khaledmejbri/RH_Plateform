import { useNavigate, useSearchParams } from 'react-router-dom';
import { useLectureSeule } from '../../auth/useLectureSeule';
import { PageHeader } from '../../components/ui';
import Onglets from '../../detail/Onglets';
import type { TypeImport } from '../../api/importsApi';
import AssistantImport from './AssistantImport';
import HistoriqueImports from './HistoriqueImports';

const CONFIG: Record<TypeImport, { titre: string; lead: string; liste: string; sortie: string; onglet: string }> = {
  COLLABORATEURS: {
    titre: 'Import des collaborateurs',
    lead: 'Déposez un fichier Excel, vérifiez ce qui sera créé, mis à jour ou refusé ligne par ligne, puis confirmez.',
    liste: '/app/collaborateurs',
    sortie: 'Retour aux fiches',
    onglet: 'Importer des collaborateurs',
  },
  SOLDES: {
    titre: 'Import des soldes de congé',
    lead: 'Déposez un fichier Excel de soldes cibles : chaque différence devient un ajustement avec le motif indiqué.',
    liste: '/app/conges/soldes',
    sortie: 'Retour aux soldes',
    onglet: 'Importer des soldes',
  },
};

/** Page d'import (assistant + historique). La DIRECTION ne voit que l'historique. */
export default function ImportPage({ type }: { type: TypeImport }) {
  const c = CONFIG[type];
  const lectureSeule = useLectureSeule();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const onglets = [
    ...(lectureSeule ? [] : [{ id: 'import', label: c.onglet }]),
    { id: 'historique', label: 'Historique des imports' },
  ];
  const demande = params.get('onglet');
  const actif = onglets.some((o) => o.id === demande) ? (demande as string) : onglets[0].id;

  return (
    <div className="page">
      <PageHeader title={c.titre} lead={lectureSeule ? 'Consultation de l’historique des imports.' : c.lead} />
      <div className="panel">
        <Onglets
          onglets={onglets}
          actif={actif}
          onChange={(id) => setParams((p) => { const s = new URLSearchParams(p); s.set('onglet', id); return s; }, { replace: true })}
        >
          {actif === 'import' && !lectureSeule ? (
            <AssistantImport type={type} libelleSortie={c.sortie} onTermine={() => navigate(c.liste)} />
          ) : (
            <HistoriqueImports typeInitial={type} />
          )}
        </Onglets>
      </div>
    </div>
  );
}

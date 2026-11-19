import { useCallback, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { TYPES_PARAMETRABLES, type TypeParametrable } from '../../api/importParametrageApi';
import { useConfirm } from '../../components/ConfirmDialog';
import Onglets from '../../detail/Onglets';
import ChampsPersoPanel from './ChampsPersoPanel';
import ImportChampsTable from './ImportChampsTable';
import ImportJournalPanel from './ImportJournalPanel';

type Props = { lectureSeule: boolean };

const ONGLETS = [
  ...TYPES_PARAMETRABLES.map((t) => ({ id: t.type as string, label: t.libelle })),
  { id: 'champs-perso', label: 'Champs personnalisés' },
  { id: 'historique', label: 'Historique' },
];

/** Section « Import Excel » de Paramètres : champs des modèles d'import et historique (D-I14 à D-I26). */
export default function ImportExcelPanel({ lectureSeule }: Props) {
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const demande = params.get('onglet') ?? '';
  const onglet = ONGLETS.some((o) => o.id === demande) ? demande : 'collaborateurs';
  const [typeJournal, setTypeJournal] = useState<TypeParametrable>('collaborateurs');
  const modifieRef = useRef(false);
  const surModifie = useCallback((m: boolean) => { modifieRef.current = m; }, []);

  async function changer(id: string) {
    if (id === onglet) return;
    if (modifieRef.current) {
      const ok = await confirm({
        title: 'Quitter sans enregistrer ?',
        message: 'Des modifications de ce tableau ne sont pas enregistrées. Elles seront perdues si vous changez d’onglet.',
        confirmLabel: 'Quitter sans enregistrer',
        danger: true,
      });
      if (!ok) return;
      modifieRef.current = false;
    }
    setParams((p) => {
      const suivant = new URLSearchParams(p);
      suivant.set('onglet', id);
      return suivant;
    });
  }

  const type = TYPES_PARAMETRABLES.find((t) => t.type === onglet);

  return (
    <section aria-labelledby="param-titre-import" className="panel">
      <div className="param-panel__tete">
        <h3 id="param-titre-import">Import Excel</h3>
      </div>
      <Onglets onglets={ONGLETS} actif={onglet} onChange={(id) => void changer(id)}>
        {type ? (
          <ImportChampsTable
            key={type.type}
            type={type.type}
            typeLibelle={type.libelle}
            typeImport={type.typeImport}
            lectureSeule={lectureSeule}
            onModifie={surModifie}
          />
        ) : onglet === 'champs-perso' ? (
          <ChampsPersoPanel lectureSeule={lectureSeule} />
        ) : (
          <div>
            <div style={{ maxWidth: 260, marginBottom: 12 }}>
              <label className="field-label" htmlFor="import-journal-type">Type d’import</label>
              <select id="import-journal-type" className="field-input" value={typeJournal}
                onChange={(e) => setTypeJournal(e.target.value as TypeParametrable)}>
                {TYPES_PARAMETRABLES.map((t) => <option key={t.type} value={t.type}>{t.libelle}</option>)}
              </select>
            </div>
            <ImportJournalPanel type={typeJournal} />
          </div>
        )}
      </Onglets>
    </section>
  );
}

import { useState } from 'react';
import { exporterMouvements, getMouvements, getSolde, messageConges, type Mouvement, type ReponseAjustement } from '../../api/congesApi';
import { useLectureSeule } from '../../auth/useLectureSeule';
import { ChampSelect } from '../../components/BarreFiltres';
import { Icon } from '../../components/ui';
import AjusterSoldeDialog from '../../pages/conges/AjusterSoldeDialog';
import { formatDateHeure, formatJours, formatJoursNombre, formatJoursSigne, TIRET } from '../../utils/format';
import { optionsFiltre } from '../../utils/parametresStore';
import { Charge } from '../Etats';
import DetailLink from '../DetailLink';
import { useChargement } from '../useChargement';
import { Cartes, Tableau } from './commun';

const TAILLE = 20;

function LibelleMouvement({ m }: { m: Mouvement }) {
  return (
    <>
      {m.type_libelle}
      {m.mois_acquisition_libelle ? ` ${m.mois_acquisition_libelle}` : ''}
      {m.demande_id && m.demande_numero ? <> <DetailLink type="demande" id={m.demande_id} libelle={m.demande_numero} /></> : null}
    </>
  );
}

/** Onglet « Congés » de la fiche : solde, mouvements paginés par 20, ajustement (RH / ADMIN). */
export default function CongesOnglet({ id }: { id: string }) {
  const lectureSeule = useLectureSeule();
  const [rev, setRev] = useState(0);
  const [page, setPage] = useState(0);
  const [type, setType] = useState('');
  const [ajuster, setAjuster] = useState(false);
  const [message, setMessage] = useState<{ genre: 'succes' | 'attention' | 'erreur'; texte: string } | null>(null);
  const [export_, setExport] = useState(false);

  const solde = useChargement(() => getSolde(id), [id, rev]);
  const mouvements = useChargement(() => getMouvements(id, page, TAILLE, type), [id, page, type, rev]);

  function apres(r: ReponseAjustement) {
    setAjuster(false);
    setPage(0);
    setMessage({
      genre: r.avertissement ? 'attention' : 'succes',
      texte: `Solde ajusté : ${formatJours(r.solde.acquis)}.${r.avertissement ? ` ${r.avertissement}.` : ''}`,
    });
    setRev((n) => n + 1);
  }

  async function exporter() {
    setExport(true);
    setMessage(null);
    try {
      await exporterMouvements(id);
    } catch (e) {
      setMessage({ genre: 'erreur', texte: messageConges(e) });
    } finally {
      setExport(false);
    }
  }

  return (
    <>
      <Charge etat={solde.etat} recharger={solde.recharger}>
        {(s) => (
          <>
            <Cartes items={[
              { label: 'Solde acquis', valeur: formatJours(s.acquis) },
              { label: 'Réservé (demandes en cours)', valeur: formatJours(s.reserve) },
              { label: 'Solde disponible', valeur: formatJours(s.disponible) },
            ]} />
            <div className="import__actions" style={{ justifyContent: 'flex-start' }}>
              {!lectureSeule && !s.est_moi ? (
                <button type="button" className="btn btn--primary btn--sm" onClick={() => { setMessage(null); setAjuster(true); }}>Ajuster</button>
              ) : null}
              <button type="button" className="btn btn--secondary btn--sm" disabled={export_} onClick={() => void exporter()}>
                <Icon name="download" size={14} />
                {export_ ? 'Export…' : 'Exporter les mouvements'}
              </button>
            </div>
            {ajuster ? (
              <AjusterSoldeDialog
                solde={s}
                onClose={() => setAjuster(false)}
                onAjuste={apres}
                onConflit={() => {
                  setAjuster(false);
                  setMessage({ genre: 'attention', texte: 'Le solde a changé entre-temps. Les données ont été rechargées, vérifiez puis recommencez.' });
                  setRev((n) => n + 1);
                }}
              />
            ) : null}
          </>
        )}
      </Charge>

      {message ? (
        <div className={`alert ${message.genre === 'succes' ? 'alert--success' : message.genre === 'attention' ? 'alert--warning' : 'alert--error'}`}
          role={message.genre === 'erreur' ? 'alert' : 'status'}>
          {message.texte}
        </div>
      ) : null}

      <h3 className="detail-h3">Mouvements</h3>
      <div style={{ maxWidth: 260 }}>
        <ChampSelect id="conges-type" libelle="Type de mouvement" valeur={type} toutesLibelle="Tous les types"
          options={optionsFiltre('TYPE_MOUVEMENT_SOLDE')} onChange={(v) => { setType(v); setPage(0); }} />
      </div>
      <Charge etat={mouvements.etat} recharger={mouvements.recharger} vide={(p) => p.contenu.length === 0} messageVide="Aucun mouvement">
        {(p) => (
          <>
            <Tableau colonnes={['Date', 'Mouvement', 'Nombre', 'Solde après', 'Auteur', 'Motif']}>
              {p.contenu.map((m, i) => (
                <tr key={`${m.cree_le}-${i}`}>
                  <td>{formatDateHeure(m.cree_le)}</td>
                  <td><LibelleMouvement m={m} /></td>
                  <td>{formatJoursSigne(m.nombre)}</td>
                  <td>{formatJoursNombre(m.solde_apres)}</td>
                  <td>{m.auteur_nom ?? TIRET}</td>
                  <td>{m.motif ?? TIRET}</td>
                </tr>
              ))}
            </Tableau>
            {p.total_pages > 1 ? (
              <div className="pager">
                <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
                <span>Page {page + 1} sur {p.total_pages}</span>
                <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= p.total_pages} onClick={() => setPage(page + 1)}>Suivant</button>
              </div>
            ) : null}
          </>
        )}
      </Charge>
    </>
  );
}

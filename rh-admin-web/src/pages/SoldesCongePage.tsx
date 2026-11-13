import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  exporterSoldes,
  getSoldes,
  messageConges,
  type FiltresSoldes,
  type ReponseAjustement,
  type SoldeLigne,
} from '../api/congesApi';
import type { PageContrat } from '../api/metierApi';
import { getUnites, type Unite } from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { EmptyState, Icon, LoadingState, PageHeader, StatusBadge } from '../components/ui';
import DetailLink from '../detail/DetailLink';
import { useFiltresUrl } from '../utils/filtres';
import { formatDate, formatJoursNombre, formatJoursSigne, TIRET } from '../utils/format';
import { optionsFiltre, useParametres } from '../utils/parametresStore';
import AjusterSoldeDialog from './conges/AjusterSoldeDialog';

const TAILLE = 20;
const FILTRES_DEFAUT = {
  q: '', direction: '', departement: '', service: '', statut: 'ACTIF', tri: 'nom', sens: 'asc', page: '0',
};

const COLONNES_TRI: Array<{ tri: string; label: string }> = [
  { tri: 'nom', label: 'Collaborateur' },
  { tri: 'matricule', label: 'Matricule' },
  { tri: 'unite', label: 'Unité' },
  { tri: 'acquis', label: 'Acquis' },
  { tri: 'reserve', label: 'Réservé' },
  { tri: 'disponible', label: 'Disponible' },
  { tri: 'dernier_mouvement', label: 'Dernier mouvement' },
];

type Etat =
  | { statut: 'chargement' }
  | { statut: 'erreur'; message: string }
  | { statut: 'ok'; page: PageContrat<SoldeLigne> };

export default function SoldesCongePage() {
  useParametres();
  const lectureSeule = useLectureSeule();
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const page = Math.max(0, Number(f.page) || 0);
  const [unites, setUnites] = useState<Unite[]>([]);
  const [recherche, setRecherche] = useState(f.q);
  const [etat, setEtat] = useState<Etat>({ statut: 'chargement' });
  const [ajuster, setAjuster] = useState<SoldeLigne | null>(null);
  const [message, setMessage] = useState<{ genre: 'succes' | 'attention' | 'erreur'; texte: string } | null>(null);
  const [export_, setExport] = useState(false);

  useEffect(() => { void getUnites().then(setUnites).catch(() => {}); }, []);

  // Le serveur filtre : l'unité la plus précise choisie (service > département > direction) inclut ses descendants.
  const uniteId = f.service || f.departement || f.direction;
  const requete: FiltresSoldes = {
    q: f.q.trim(), unite_id: uniteId, statut: f.statut, tri: f.tri, sens: f.sens === 'desc' ? 'desc' : 'asc', page, taille: TAILLE,
  };
  const cle = JSON.stringify(requete);

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      setEtat({ statut: 'ok', page: await getSoldes(JSON.parse(cle) as FiltresSoldes) });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageConges(e) });
    }
  }, [cle]);

  useEffect(() => { void charger(); }, [charger]);

  // Recherche : on attend une courte pause de frappe avant d'interroger le serveur.
  useEffect(() => {
    if (recherche === f.q) return undefined;
    const t = window.setTimeout(() => filtres.definir({ q: recherche, page: '0' }), 350);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recherche]);

  function trier(tri: string) {
    if (f.tri === tri) filtres.definir({ sens: f.sens === 'asc' ? 'desc' : 'asc', page: '0' });
    else filtres.definir({ tri, sens: 'asc', page: '0' });
  }

  async function exporter() {
    setExport(true);
    setMessage(null);
    try {
      await exporterSoldes(requete);
    } catch (e) {
      setMessage({ genre: 'erreur', texte: messageConges(e) });
    } finally {
      setExport(false);
    }
  }

  function apresAjustement(r: ReponseAjustement) {
    setAjuster(null);
    setMessage({
      genre: r.avertissement ? 'attention' : 'succes',
      texte: `Solde ajusté : ${formatJoursNombre(r.solde.acquis)} ${Math.abs(r.solde.acquis) >= 2 ? 'jours' : 'jour'}.${r.avertissement ? ` ${r.avertissement}.` : ''}`,
    });
    // Mise à jour immédiate de la ligne, sans rechargement complet.
    setEtat((e) => e.statut === 'ok'
      ? { statut: 'ok', page: { ...e.page, contenu: e.page.contenu.map((l) => (l.collaborateur_id === r.solde.collaborateur_id ? r.solde : l)) } }
      : e);
  }

  async function apresConflit() {
    setAjuster(null);
    await charger();
    setMessage({ genre: 'attention', texte: 'Le solde a changé entre-temps. La liste a été rechargée, vérifiez puis recommencez.' });
  }

  const sortOf = (tri: string): 'ascending' | 'descending' | 'none' =>
    f.tri === tri ? (f.sens === 'desc' ? 'descending' : 'ascending') : 'none';

  const nbFiltres = [f.q, f.direction, f.departement, f.service].filter(Boolean).length + (f.statut !== FILTRES_DEFAUT.statut ? 1 : 0);
  const total = etat.statut === 'ok' ? etat.page.total_elements : 0;
  const pages = etat.statut === 'ok' ? Math.max(1, etat.page.total_pages) : 1;

  return (
    <div className="page">
      <PageHeader
        title="Soldes de congé"
        lead="Solde acquis, jours réservés par les demandes en cours et solde disponible de chaque collaborateur."
        actions={
          <>
            <button type="button" className="btn btn--secondary" onClick={() => void exporter()} disabled={export_ || etat.statut !== 'ok'}>
              <Icon name="download" />
              {export_ ? 'Export…' : 'Exporter (CSV)'}
            </button>
            {!lectureSeule ? (
              <Link to="/app/conges/soldes/import" className="btn btn--primary">
                <Icon name="plus" />
                Importer des soldes
              </Link>
            ) : null}
          </>
        }
      />

      <BarreFiltres
        id="soldes"
        recherche={recherche}
        onRecherche={setRecherche}
        libelleRecherche="Collaborateur"
        placeholder="Nom, prénom ou matricule…"
        nbResultats={total}
        nbActifs={nbFiltres}
        onReinitialiser={() => { setRecherche(''); filtres.reinitialiser(); }}
      >
        <FiltresUnite
          idPrefixe="soldes"
          unites={unites}
          selection={{ direction: f.direction, departement: f.departement, service: f.service }}
          onChange={(sel) => filtres.definir({ ...sel, page: '0' })}
        />
        <ChampSelect
          id="soldes-statut"
          libelle="Statut"
          valeur={f.statut}
          sansTous
          options={[{ valeur: 'TOUS', libelle: 'Tous les statuts' }, ...optionsFiltre('STATUT_COLLABORATEUR', ['ACTIF'])]}
          onChange={(v) => filtres.definir({ statut: v, page: '0' })}
        />
      </BarreFiltres>

      {message ? (
        <div className={`alert ${message.genre === 'succes' ? 'alert--success' : message.genre === 'attention' ? 'alert--warning' : 'alert--error'}`}
          role={message.genre === 'erreur' ? 'alert' : 'status'}>
          {message.texte}
        </div>
      ) : null}

      <div className="panel panel--flush">
        {etat.statut === 'chargement' ? <LoadingState label="Chargement des soldes…" /> : null}
        {etat.statut === 'erreur' ? (
          <div className="alert alert--error" role="alert">
            {etat.message}{' '}
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
          </div>
        ) : null}
        {etat.statut === 'ok' && etat.page.contenu.length === 0 ? (
          nbFiltres > 0
            ? <AucunResultat onReinitialiser={() => { setRecherche(''); filtres.reinitialiser(); }} />
            : <EmptyState icon="calendar" title="Aucun solde à afficher">Aucun collaborateur n’a encore de solde de congé.</EmptyState>
        ) : null}
        {etat.statut === 'ok' && etat.page.contenu.length > 0 ? (
          <div className="table-wrap">
            <table className="data-table">
              <caption className="sr-only">Soldes de congé des collaborateurs</caption>
              <thead>
                <tr>
                  {COLONNES_TRI.map((c) => (
                    <th key={c.tri} scope="col" aria-sort={sortOf(c.tri)}>
                      <button type="button" className="btn btn--ghost btn--sm" onClick={() => trier(c.tri)}>
                        {c.label}{f.tri === c.tri ? (f.sens === 'desc' ? ' ▼' : ' ▲') : ''}
                      </button>
                    </th>
                  ))}
                  {!lectureSeule ? <th scope="col"><span className="sr-only">Actions</span></th> : null}
                </tr>
              </thead>
              <tbody>
                {etat.page.contenu.map((l) => (
                  <tr key={l.collaborateur_id}>
                    <td>
                      <DetailLink type="collaborateur" id={l.collaborateur_id} libelle={l.nom_complet} />
                      {l.statut !== 'ACTIF' ? <> <StatusBadge referentiel="STATUT_COLLABORATEUR" statut={l.statut} label={l.statut_libelle} /></> : null}
                    </td>
                    <td className="cell-meta">{l.matricule}</td>
                    <td>{l.unite_libelle ?? TIRET}</td>
                    <td>{formatJoursNombre(l.acquis)}</td>
                    <td>{formatJoursNombre(l.reserve)}</td>
                    <td><strong>{formatJoursNombre(l.disponible)}</strong></td>
                    <td className="cell-meta">
                      {l.dernier_mouvement
                        ? `${l.dernier_mouvement.type_libelle} ${formatJoursSigne(l.dernier_mouvement.nombre)} · ${formatDate(l.dernier_mouvement.date)}`
                        : TIRET}
                    </td>
                    {!lectureSeule ? (
                      <td style={{ textAlign: 'right' }}>
                        {l.est_moi ? null : (
                          <button type="button" className="btn btn--sm btn--secondary" aria-label={`Ajuster le solde de ${l.nom_complet}`}
                            onClick={() => { setMessage(null); setAjuster(l); }}>
                            Ajuster
                          </button>
                        )}
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>

      {etat.statut === 'ok' && pages > 1 ? (
        <div className="pager">
          <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => filtres.definir({ page: String(page - 1) })}>Précédent</button>
          <span>Page {page + 1} sur {pages}</span>
          <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => filtres.definir({ page: String(page + 1) })}>Suivant</button>
        </div>
      ) : null}

      {ajuster ? (
        <AjusterSoldeDialog solde={ajuster} onClose={() => setAjuster(null)} onAjuste={apresAjustement} onConflit={() => void apresConflit()} />
      ) : null}
    </div>
  );
}

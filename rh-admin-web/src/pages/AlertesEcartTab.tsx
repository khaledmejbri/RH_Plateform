import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  AlerteEcartApiError,
  getAlertesEcart,
  getCompteurAlertesEcart,
  LIBELLES_NIVEAU_ECART,
  LIBELLES_SENS_GLOBAL,
  LIBELLES_STATUT_ALERTE,
  type CompteurAlertes,
  type NiveauEcart,
  type PageAlertes,
  type StatutAlerteEcart,
} from '../api/alertesEcartApi';
import { campaignApi, type EvaluationCampaign } from '../api/evaluationApi';
import { getUnites, type Unite } from '../api/rhClient';
import { AlerteEcartBadge, StatutAlerteBadge } from '../components/AlerteEcartBadge';
import { EmptyState, LoadingState } from '../components/ui';
import { formatDate, formatPersonne } from '../utils/format';
import { routeAlerteEcart } from '../api/notificationContenu';
import BarreFiltres, { AucunResultat, ChampSelect } from '../components/BarreFiltres';
import { correspond, useFiltresUrl } from '../utils/filtres';

const TAILLE = 20;
const FILTRES_DEFAUT = { q: '', statut: '', niveau: '', campagne: '', unite: '' };

/**
 * Filtre « Non closes » (ouvertes + en cours), utilisé par la carte de Suivi & scores (D-E12.c) :
 * le serveur ne filtre qu'un statut à la fois, on lit donc toutes les pages sans statut et on écarte les
 * alertes traitées. L'ordre de l'API est conservé.
 */
async function chargerNonCloses(
  f: { niveau?: string; campagneId?: string; uniteId?: string },
  page: number,
): Promise<PageAlertes> {
  const tout: PageAlertes['elements'] = [];
  for (let p = 0; p < 20; p += 1) {
    const r = await getAlertesEcart(f, p, 100);
    tout.push(...r.elements);
    if (r.elements.length === 0 || tout.length >= r.total) break;
  }
  const ouvertes = tout.filter((a) => a.statut !== 'TRAITEE');
  return { elements: ouvertes.slice(page * TAILLE, (page + 1) * TAILLE), total: ouvertes.length, page, taille: TAILLE };
}

function libelleCompteur(c: CompteurAlertes | null): string {
  if (!c) return '';
  if (c.nonCloses === 0) return 'Aucune alerte non close';
  const urgentes = c.urgentesNonCloses;
  return `${c.nonCloses} non close${c.nonCloses > 1 ? 's' : ''} (dont ${urgentes} urgente${urgentes > 1 ? 's' : ''})`;
}

/** Onglet « Alertes d'écart » : l'ordre renvoyé par l'API est conservé tel quel (jamais retrié). */
export default function AlertesEcartTab() {
  const filtres = useFiltresUrl(FILTRES_DEFAUT, 'a_');
  const location = useLocation();
  const { q, statut, niveau, campagne: campagneId, unite: uniteId } = filtres.valeurs;
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageAlertes | null>(null);
  const [compteur, setCompteur] = useState<CompteurAlertes | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [refuse, setRefuse] = useState(false);
  const [campagnes, setCampagnes] = useState<EvaluationCampaign[]>([]);
  const [unites, setUnites] = useState<Unite[]>([]);

  useEffect(() => {
    campaignApi.list().then(setCampagnes).catch(() => setCampagnes([]));
    getUnites().then(setUnites).catch(() => setUnites([]));
  }, []);

  const charger = useCallback(async () => {
    setLoading(true);
    setErreur(null);
    setRefuse(false);
    try {
      const [p, c] = await Promise.all([
        statut === 'NON_CLOSES' ? chargerNonCloses({ niveau, campagneId, uniteId }, page) : getAlertesEcart({ statut, niveau, campagneId, uniteId }, page, TAILLE),
        getCompteurAlertesEcart().catch(() => null),
      ]);
      setData(p);
      setCompteur(c);
    } catch (e) {
      setData(null);
      if (e instanceof AlerteEcartApiError && e.status === 403) setRefuse(true);
      else setErreur(e instanceof Error ? e.message : 'Chargement impossible');
    } finally {
      setLoading(false);
    }
  }, [statut, niveau, campagneId, uniteId, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

  const filtresActifs = filtres.nbActifs > 0;
  const changer = (changement: Partial<typeof FILTRES_DEFAUT>) => {
    filtres.definir(changement);
    setPage(0);
  };
  const reinitialiser = () => {
    filtres.reinitialiser();
    setPage(0);
  };
  // Statut, niveau, campagne et unité sont filtrés par le serveur (ordre de l'API conservé) ;
  // la recherche collaborateur / responsable ne porte que sur la page affichée (le serveur n'a pas ce paramètre).
  const elements = useMemo(
    () =>
      (data?.elements ?? []).filter((a) =>
        correspond([a.collaborateurNom, a.collaborateurMatricule, a.collaborateurUniteLibelle, a.superieurNom, a.campagneNom], q),
      ),
    [data, q],
  );

  if (refuse) {
    return (
      <EmptyState icon="lock" title="Accès réservé">
        Les alertes d’écart sont réservées aux profils RH, Direction et Administrateur.
      </EmptyState>
    );
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / TAILLE)) : 1;

  return (
    <div>
      <p className="text-sm" role="status" aria-live="polite">
        <strong>{libelleCompteur(compteur)}</strong>
      </p>

      <BarreFiltres
        id="alertes"
        recherche={q}
        onRecherche={(v) => filtres.definir({ q: v })}
        libelleRecherche="Collaborateur"
        placeholder="Nom, matricule, responsable…"
        nbResultats={elements.length}
        nbTotal={data?.elements.length}
        nbActifs={filtres.nbActifs}
        onReinitialiser={reinitialiser}
        note={q && data && data.total > data.elements.length ? 'recherche limitée à la page affichée' : undefined}
      >
        <ChampSelect
          id="alertes-statut"
          libelle="Statut"
          valeur={statut}
          toutesLibelle="Tous les statuts"
          options={[
            { valeur: 'NON_CLOSES', libelle: 'Non closes (ouvertes + en cours)' },
            ...(Object.keys(LIBELLES_STATUT_ALERTE) as StatutAlerteEcart[]).map((x) => ({ valeur: x, libelle: LIBELLES_STATUT_ALERTE[x] })),
          ]}
          onChange={(v) => changer({ statut: v })}
        />
        <ChampSelect
          id="alertes-niveau"
          libelle="Niveau"
          valeur={niveau}
          toutesLibelle="Tous les niveaux"
          options={(['URGENTE', 'ROUGE', 'ORANGE', 'ALIGNE'] as NiveauEcart[]).map((x) => ({ valeur: x, libelle: LIBELLES_NIVEAU_ECART[x] }))}
          onChange={(v) => changer({ niveau: v })}
        />
        <ChampSelect
          id="alertes-campagne"
          libelle="Campagne"
          valeur={campagneId}
          toutesLibelle="Toutes les campagnes"
          options={campagnes.map((c) => ({ valeur: c.identifiant, libelle: c.nom }))}
          onChange={(v) => changer({ campagne: v })}
        />
        <ChampSelect
          id="alertes-unite"
          libelle="Unité"
          valeur={uniteId}
          toutesLibelle="Toutes les unités"
          options={unites.map((u) => ({ valeur: u.identifiant, libelle: u.libelle }))}
          onChange={(v) => changer({ unite: v })}
        />
      </BarreFiltres>

      {loading ? (
        <LoadingState label="Chargement des alertes…" />
      ) : erreur ? (
        <div className="alert alert--error" role="alert">
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : !data || data.elements.length === 0 ? (
        <EmptyState icon="inbox" title="Aucune alerte d’écart">
          {filtresActifs ? 'Aucune alerte ne correspond à ces filtres.' : 'Aucun écart d’étoiles n’a été détecté pour le moment.'}
        </EmptyState>
      ) : elements.length === 0 ? (
        <AucunResultat onReinitialiser={reinitialiser} />
      ) : (
        <>
          <div className="table-container">
            <div className="table-wrap"><table className="table">
              <thead>
                <tr>
                  <th scope="col">Niveau</th>
                  <th scope="col">Collaborateur</th>
                  <th scope="col">Campagne</th>
                  <th scope="col">Responsable</th>
                  <th scope="col">Sens</th>
                  <th scope="col">Compétences</th>
                  <th scope="col">Détectée le</th>
                  <th scope="col">Statut</th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {elements.map((a) => (
                  <tr key={a.identifiant}>
                    <td>
                      <AlerteEcartBadge niveau={a.niveau} />
                    </td>
                    <td>
                      <div>{formatPersonne(a.collaborateurNom, a.collaborateurMatricule)}</div>
                      <div className="text-muted text-sm">{a.collaborateurUniteLibelle ?? ''}</div>
                    </td>
                    <td>{a.campagneNom ?? '—'}</td>
                    <td>{a.superieurNom ?? '—'}</td>
                    <td>{a.sensGlobal ? LIBELLES_SENS_GLOBAL[a.sensGlobal] : '—'}</td>
                    <td>
                      {a.nbCompetencesEnEcart} en écart, max {a.ecartMax} ★
                      {a.notesModifieesApresDetection ? (
                        <div className="text-muted text-sm">Notes modifiées après détection</div>
                      ) : null}
                    </td>
                    <td>{formatDate(a.detecteeLe)}</td>
                    <td>
                      <StatutAlerteBadge statut={a.statut} />
                    </td>
                    <td>
                      <Link className="btn btn--secondary btn--sm" to={routeAlerteEcart(a.identifiant)} state={{ retourListe: location.search }}>
                        Voir le détail
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table></div>
          </div>
          {totalPages > 1 ? (
            <div className="pager">
              <button type="button" className="btn btn--secondary btn--sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>
                Précédent
              </button>
              <span className="small">
                Page {page + 1} sur {totalPages} ({data.total} alertes)
              </span>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
              >
                Suivant
              </button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

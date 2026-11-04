import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
import { EmptyState, LoadingState, formatDate } from '../components/ui';
import { routeAlerteEcart } from '../api/notificationContenu';

const TAILLE = 20;

function libelleCompteur(c: CompteurAlertes | null): string {
  if (!c) return '';
  if (c.nonCloses === 0) return 'Aucune alerte non close';
  const urgentes = c.urgentesNonCloses;
  return `${c.nonCloses} non close${c.nonCloses > 1 ? 's' : ''} (dont ${urgentes} urgente${urgentes > 1 ? 's' : ''})`;
}

/** Onglet « Alertes d'écart » : l'ordre renvoyé par l'API est conservé tel quel (jamais retrié). */
export default function AlertesEcartTab() {
  const [statut, setStatut] = useState('');
  const [niveau, setNiveau] = useState('');
  const [campagneId, setCampagneId] = useState('');
  const [uniteId, setUniteId] = useState('');
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
        getAlertesEcart({ statut, niveau, campagneId, uniteId }, page, TAILLE),
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

  const filtresActifs = !!(statut || niveau || campagneId || uniteId);
  const changer = (fn: () => void) => {
    fn();
    setPage(0);
  };
  const reinitialiser = () => {
    setStatut('');
    setNiveau('');
    setCampagneId('');
    setUniteId('');
    setPage(0);
  };

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

      <div className="panel mb-md">
        <div className="form-grid">
          <label>
            Statut
            <select value={statut} onChange={(e) => changer(() => setStatut(e.target.value))}>
              <option value="">Tous les statuts</option>
              {(Object.keys(LIBELLES_STATUT_ALERTE) as StatutAlerteEcart[]).map((s) => (
                <option key={s} value={s}>
                  {LIBELLES_STATUT_ALERTE[s]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Niveau
            <select value={niveau} onChange={(e) => changer(() => setNiveau(e.target.value))}>
              <option value="">Tous les niveaux</option>
              {(['URGENTE', 'ROUGE', 'ORANGE', 'ALIGNE'] as NiveauEcart[]).map((n) => (
                <option key={n} value={n}>
                  {LIBELLES_NIVEAU_ECART[n]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Campagne
            <select value={campagneId} onChange={(e) => changer(() => setCampagneId(e.target.value))}>
              <option value="">Toutes les campagnes</option>
              {campagnes.map((c) => (
                <option key={c.identifiant} value={c.identifiant}>
                  {c.nom}
                </option>
              ))}
            </select>
          </label>
          <label>
            Unité
            <select value={uniteId} onChange={(e) => changer(() => setUniteId(e.target.value))}>
              <option value="">Toutes les unités</option>
              {unites.map((u) => (
                <option key={u.identifiant} value={u.identifiant}>
                  {u.libelle}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="mt-md">
          <button type="button" className="btn btn--secondary btn--sm" disabled={!filtresActifs} onClick={reinitialiser}>
            Réinitialiser
          </button>
        </div>
      </div>

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
      ) : (
        <>
          <div className="table-container">
            <table className="table">
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
                {data.elements.map((a) => (
                  <tr key={a.identifiant}>
                    <td>
                      <AlerteEcartBadge niveau={a.niveau} />
                    </td>
                    <td>
                      <div>{a.collaborateurNom ?? '—'}</div>
                      <div className="text-muted text-sm">
                        {[a.collaborateurMatricule, a.collaborateurUniteLibelle].filter(Boolean).join(' · ')}
                      </div>
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
                      <Link className="btn btn--secondary btn--sm" to={routeAlerteEcart(a.identifiant)}>
                        Voir le détail
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 ? (
            <div className="pager">
              <button type="button" className="btn btn--secondary btn--sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>
                Précédent
              </button>
              <span className="small">
                Page {page + 1} / {totalPages} ({data.total} alertes)
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

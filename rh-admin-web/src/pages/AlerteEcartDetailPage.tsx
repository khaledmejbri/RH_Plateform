import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlerteEcartApiError,
  getAlerteEcart,
  libelleConclusion,
  LIBELLES_EVENEMENT,
  LIBELLES_SENS_GLOBAL,
  LIBELLES_STATUT_ALERTE,
  messageErreurAction,
  postClotureAlerte,
  postPriseEnChargeAlerte,
  type AlerteEcartDetail,
  type ConclusionAlerteEcart,
} from '../api/alertesEcartApi';
import { AlerteEcartBadge, StatutAlerteBadge } from '../components/AlerteEcartBadge';
import { ClotureAlerteDialog } from '../components/ClotureAlerteDialog';
import { EmptyState, LoadingState, formatDateTime } from '../components/ui';
import { useLectureSeule } from '../auth/useLectureSeule';

const RETOUR_LISTE = '/app/evaluations?onglet=alertes';

export default function AlerteEcartDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const lectureSeule = useLectureSeule();
  const [alerte, setAlerte] = useState<AlerteEcartDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [introuvable, setIntrouvable] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'error' | 'success'; texte: string } | null>(null);
  const [cloture, setCloture] = useState(false);
  const [busy, setBusy] = useState(false);

  const charger = useCallback(
    async (silencieux = false) => {
      if (!silencieux) setLoading(true);
      setErreur(null);
      setIntrouvable(false);
      try {
        setAlerte(await getAlerteEcart(id));
      } catch (e) {
        if (e instanceof AlerteEcartApiError && [400, 403, 404].includes(e.status)) {
          setAlerte(null);
          setIntrouvable(true);
        } else if (!silencieux) {
          setAlerte(null);
          setErreur(e instanceof Error ? e.message : 'Chargement impossible');
        }
      } finally {
        setLoading(false);
      }
    },
    [id],
  );

  useEffect(() => {
    void charger();
  }, [charger]);

  /** 403 / 404 / 409 sur une action : message, puis rechargement ou retour à la liste. */
  function gererErreurBloquante(e: AlerteEcartApiError) {
    setCloture(false);
    setMessage({ type: 'error', texte: messageErreurAction(e) });
    if (e.status === 404) {
      navigate(RETOUR_LISTE, { replace: true });
    } else {
      void charger(true);
    }
  }

  async function prendreEnCharge() {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      setAlerte(await postPriseEnChargeAlerte(id));
      setMessage({ type: 'success', texte: 'Alerte prise en charge.' });
    } catch (e) {
      if (e instanceof AlerteEcartApiError && [403, 404, 409].includes(e.status)) gererErreurBloquante(e);
      else setMessage({ type: 'error', texte: messageErreurAction(e) });
    } finally {
      setBusy(false);
    }
  }

  async function clore(conclusion: ConclusionAlerteEcart, commentaire: string) {
    const maj = await postClotureAlerte(id, conclusion, commentaire);
    setAlerte(maj);
    setCloture(false);
    setMessage({ type: 'success', texte: 'Alerte clôturée.' });
  }

  if (loading) return <div className="page"><LoadingState label="Chargement de l’alerte…" /></div>;

  if (introuvable) {
    return (
      <div className="page">
        <EmptyState
          icon="alert"
          title="Alerte introuvable ou inaccessible"
          action={<Link className="btn btn--secondary" to={RETOUR_LISTE}>Retour aux alertes</Link>}
        >
          Cette alerte n’existe pas ou vous n’avez pas le droit de la consulter.
        </EmptyState>
      </div>
    );
  }

  if (erreur || !alerte) {
    return (
      <div className="page">
        <div className="alert alert--error" role="alert">
          {erreur ?? 'Chargement impossible'}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
        <Link to={RETOUR_LISTE}>Retour aux alertes</Link>
      </div>
    );
  }

  const peutAgir = !lectureSeule && alerte.peutTraiter;
  const concerne = !lectureSeule && !alerte.peutTraiter && alerte.motifNonTraitable === 'CONCERNE';
  const peutPrendre = peutAgir && alerte.statut === 'OUVERTE';
  const peutClore = peutAgir && (alerte.statut === 'OUVERTE' || alerte.statut === 'EN_COURS');

  return (
    <div className="page">
      <p>
        <Link to={RETOUR_LISTE}>← Retour aux alertes d’écart</Link>
      </p>
      <div className="page__header">
        <h1 className="page__title">
          Alerte d’écart — {alerte.collaborateurNom ?? 'collaborateur'}
        </h1>
        <p className="page__subtitle">
          {alerte.campagneNom ?? 'Campagne'} · détectée le {formatDateTime(alerte.detecteeLe)}
        </p>
      </div>

      {lectureSeule ? (
        <div className="alert alert--info" role="status">
          Consultation uniquement — vous pouvez lire cette alerte sans la traiter.
        </div>
      ) : null}
      {concerne ? (
        <div className="alert alert--warning" role="status">
          Vous êtes concerné par cette évaluation : vous ne pouvez pas traiter cette alerte.
        </div>
      ) : null}
      {message ? (
        <div className={`alert alert--${message.type === 'error' ? 'error' : 'success'}`} role={message.type === 'error' ? 'alert' : 'status'}>
          {message.texte}
        </div>
      ) : null}

      <div className="panel">
        <div className="detail-badges">
          <AlerteEcartBadge niveau={alerte.niveau} />
          <StatutAlerteBadge statut={alerte.statut} />
          {alerte.notesModifieesApresDetection ? (
            <span className="badge badge--default">Notes modifiées après détection</span>
          ) : null}
        </div>
        <dl className="detail-dl mt-md">
          <div><dt>Collaborateur</dt><dd>{alerte.collaborateurNom ?? '—'}{alerte.collaborateurMatricule ? ` (${alerte.collaborateurMatricule})` : ''}</dd></div>
          <div><dt>Unité</dt><dd>{alerte.collaborateurUniteLibelle ?? '—'}</dd></div>
          <div><dt>Responsable</dt><dd>{alerte.superieurNom ?? '—'}</dd></div>
          <div><dt>Niveau à la détection</dt><dd><AlerteEcartBadge niveau={alerte.niveauDetection} /></dd></div>
          <div>
            <dt>Sens</dt>
            <dd>
              {alerte.sensGlobal ? (alerte.libelleSensGlobal ?? LIBELLES_SENS_GLOBAL[alerte.sensGlobal]) : '—'}
              {alerte.aideSensGlobal ? <div className="text-muted text-sm">{alerte.aideSensGlobal}</div> : null}
            </dd>
          </div>
          {alerte.conclusion ? (
            <div><dt>Conclusion</dt><dd>{libelleConclusion(alerte.conclusion, alerte.conclusionLibelle)}</dd></div>
          ) : null}
        </dl>

        {peutPrendre || peutClore ? (
          <div className="form-actions">
            {peutPrendre ? (
              <button type="button" className="btn btn--secondary" disabled={busy} onClick={() => void prendreEnCharge()}>
                {busy ? 'Envoi…' : 'Prendre en charge'}
              </button>
            ) : null}
            {peutClore ? (
              <button type="button" className="btn btn--primary" disabled={busy} onClick={() => { setMessage(null); setCloture(true); }}>
                Clore
              </button>
            ) : null}
          </div>
        ) : null}
      </div>

      <h2 className="section-title mt-md">Compétences en écart</h2>
      <div className="table-container">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Compétence</th>
              <th scope="col">À la détection</th>
              <th scope="col">Actuellement</th>
              <th scope="col">Niveau</th>
              <th scope="col">Lecture</th>
            </tr>
          </thead>
          <tbody>
            {alerte.lignes.map((l) => {
              const modifiee =
                l.etoilesCollaborateurActuelles !== l.etoilesCollaborateurDetection ||
                l.etoilesRoActuelles !== l.etoilesRoDetection;
              return (
                <tr key={l.questionId}>
                  <td>{l.competence}</td>
                  <td>
                    Collaborateur {l.etoilesCollaborateurDetection} ★, Responsable {l.etoilesRoDetection} ★, écart {l.ecartDetection}
                  </td>
                  <td>
                    Collaborateur {l.etoilesCollaborateurActuelles} ★, Responsable {l.etoilesRoActuelles} ★, écart {l.ecartActuel}
                    {modifiee ? <div className="text-muted text-sm">Modifiée depuis la détection</div> : null}
                  </td>
                  <td><AlerteEcartBadge niveau={l.niveauActuel} /></td>
                  <td>
                    {l.libelleSens ?? '—'}
                    {l.aideSens ? <div className="text-muted text-sm">{l.aideSens}</div> : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <h2 className="section-title mt-md">Historique</h2>
      {alerte.historique.length === 0 ? (
        <p className="text-muted">Aucun événement.</p>
      ) : (
        <ol className="historique-alerte">
          {alerte.historique.map((h) => (
            <li key={h.identifiant}>
              <div>
                <strong>{LIBELLES_EVENEMENT[h.typeEvenement] ?? h.typeEvenement}</strong>
                {' — '}
                {h.auteurSysteme ? 'SYSTEME' : (h.auteurLibelle ?? '—')}
                <span className="text-muted"> · {formatDateTime(h.le)}</span>
              </div>
              {h.statutAvant !== h.statutApres && h.statutApres ? (
                <div className="text-sm">
                  Statut : {h.statutAvant ? LIBELLES_STATUT_ALERTE[h.statutAvant] : '—'} → {LIBELLES_STATUT_ALERTE[h.statutApres]}
                </div>
              ) : null}
              {h.niveauApres && h.niveauAvant !== h.niveauApres ? (
                <div className="text-sm">
                  Niveau : {h.niveauAvant ? <AlerteEcartBadge niveau={h.niveauAvant} /> : '—'} → <AlerteEcartBadge niveau={h.niveauApres} />
                </div>
              ) : null}
              {h.conclusion ? (
                <div className="text-sm">Conclusion : {libelleConclusion(h.conclusion, h.conclusionLibelle)}</div>
              ) : null}
              {h.commentaire ? <div className="text-sm historique-alerte__commentaire">{h.commentaire}</div> : null}
            </li>
          ))}
        </ol>
      )}

      {cloture ? (
        <ClotureAlerteDialog
          onClose={() => setCloture(false)}
          onSubmit={clore}
          onErreurBloquante={gererErreurBloquante}
        />
      ) : null}
    </div>
  );
}

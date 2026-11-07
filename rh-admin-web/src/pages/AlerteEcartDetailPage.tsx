import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  AlerteEcartApiError,
  getAlerteEcart,
  libelleConclusion,
  LECTURE_NEUTRE,
  LIBELLES_EVENEMENT,
  LIBELLES_SENS_ECART,
  LIBELLES_SENS_GLOBAL,
  LIBELLES_STATUT_ALERTE,
  messageErreurAction,
  postClotureAlerte,
  postPriseEnChargeAlerte,
  type AlerteEcartDetail,
  type ConclusionAlerteEcart,
  type SensEcart,
} from '../api/alertesEcartApi';
import { AlerteEcartBadge, StatutAlerteBadge } from '../components/AlerteEcartBadge';
import { ClotureAlerteDialog } from '../components/ClotureAlerteDialog';
import { EmptyState, LoadingState } from '../components/ui';
import { COLLABORATEUR_INTROUVABLE, formatDateHeure, formatPersonne } from '../utils/format';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';
import { useConfirm } from '../components/ConfirmDialog';
import { useLectureSeule } from '../auth/useLectureSeule';

const RETOUR_LISTE_DEFAUT = '/app/evaluations?onglet=alertes';

/** Retour à la liste des alertes avec ses filtres (onglet et `a_*` transmis par le lien de la liste). */
function retourListe(etat: unknown): string {
  const recherche = (etat as { retourListe?: unknown } | null)?.retourListe;
  return typeof recherche === 'string' && recherche.startsWith('?') ? `/app/evaluations${recherche}` : RETOUR_LISTE_DEFAUT;
}

export default function AlerteEcartDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const lienRetour = retourListe(useLocation().state);
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
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
      navigate(lienRetour, { replace: true });
    } else {
      void charger(true);
    }
  }

  const nomAlerte = () => formatPersonne(alerte?.collaborateurNom, alerte?.collaborateurMatricule);

  async function prendreEnCharge() {
    if (busy) return;
    if (!(await confirm({ message: `Prendre en charge l’alerte d’écart de ${nomAlerte()} ?`, confirmLabel: 'Prendre en charge' }))) return;
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
          action={<Link className="btn btn--secondary" to={lienRetour}>Retour aux alertes</Link>}
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
        <Link to={lienRetour}>Retour aux alertes</Link>
      </div>
    );
  }

  // Lecture neutre : une phrase par sens présent, affichée une fois (et non répétée par compétence).
  const lecturesDistinctes = Array.from(new Set(
    alerte.lignes
      .map((l) => l.sensActuel ?? l.sensDetection)
      .filter((s): s is SensEcart => !!s)
      .map((s) => LECTURE_NEUTRE[s]),
  ));
  const peutAgir = !lectureSeule && alerte.peutTraiter;
  const concerne = !lectureSeule && !alerte.peutTraiter && alerte.motifNonTraitable === 'CONCERNE';
  const peutPrendre = peutAgir && alerte.statut === 'OUVERTE';
  const peutClore = peutAgir && (alerte.statut === 'OUVERTE' || alerte.statut === 'EN_COURS');

  return (
    <div className="page">
      <p>
        <Link to={lienRetour}>← Retour aux alertes d’écart</Link>
      </p>
      <div className="page__header">
        <h1 className="page__title">
          Alerte d’écart — {alerte.collaborateurNom ?? COLLABORATEUR_INTROUVABLE}
        </h1>
        <p className="page__subtitle">
          {alerte.campagneNom ?? 'Campagne'} · détectée le {formatDateHeure(alerte.detecteeLe)}
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
          <div><dt>Collaborateur</dt><dd>{formatPersonne(alerte.collaborateurNom, alerte.collaborateurMatricule)}</dd></div>
          <div><dt>Unité</dt><dd>{alerte.collaborateurUniteLibelle ?? '—'}</dd></div>
          <div><dt>Responsable</dt><dd>{alerte.superieurNom ?? '—'}</dd></div>
          <div><dt>Niveau à la détection</dt><dd><AlerteEcartBadge niveau={alerte.niveauDetection} /></dd></div>
          <div>
            <dt>Sens</dt>
            <dd>
              {alerte.sensGlobal ? LIBELLES_SENS_GLOBAL[alerte.sensGlobal] : '—'}
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
              <th scope="col">Note du responsable</th>
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
                  <td>{sensCourt(l.sensActuel ?? l.sensDetection)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {lecturesDistinctes.length > 0 ? (
        <div className="callout mt-sm">
          <p className="callout__title">Lecture</p>
          {lecturesDistinctes.map((t) => <p key={t}>{t}</p>)}
        </div>
      ) : null}

      <h2 className="section-title mt-md">Historique</h2>
      {alerte.historique.length === 0 ? (
        <p className="text-muted">Aucun événement.</p>
      ) : (
        <ol className="historique-alerte">
          {alerte.historique.map((h) => (
            <li key={h.identifiant}>
              <div>
                <strong>{h.typeEvenementLibelle ?? LIBELLES_EVENEMENT[h.typeEvenement] ?? VALEUR_NON_RECONNUE}</strong>
                {' — '}
                {h.auteurSysteme ? 'Système (automatique)' : (h.auteurLibelle ?? '—')}
                <span className="text-muted"> · {formatDateHeure(h.le)}</span>
              </div>
              {h.statutAvant !== h.statutApres && h.statutApres ? (
                <div className="text-sm">
                  {h.statutAvant
                    ? <>Statut : {h.statutAvantLibelle ?? LIBELLES_STATUT_ALERTE[h.statutAvant]} → {h.statutApresLibelle ?? LIBELLES_STATUT_ALERTE[h.statutApres]}</>
                    : <>Création : {h.statutApresLibelle ?? LIBELLES_STATUT_ALERTE[h.statutApres]}</>}
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
          confirmer={() => confirm({ message: `Clore l’alerte d’écart de ${nomAlerte()} ? Cette action est définitive.`, confirmLabel: 'Clore l’alerte', danger: true })}
          onClose={() => setCloture(false)}
          onSubmit={clore}
          onErreurBloquante={gererErreurBloquante}
        />
      ) : null}
    </div>
  );
}

function sensCourt(sens: SensEcart | null | undefined): string {
  return sens ? LIBELLES_SENS_ECART[sens] : '—';
}

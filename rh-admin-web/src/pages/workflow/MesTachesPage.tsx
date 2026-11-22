import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  getDetailTache,
  getMesTaches,
  getSuiviObjetWorkflow,
  messageErreurWorkflow,
  postDecisionTache,
  WorkflowApiError,
  type PageWorkflow,
  type TacheDetail,
  type TacheListeItem,
} from '../../api/workflowApi';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import { motifValide, regleMotif } from '../../workflow/decision';
import { EVENEMENT_RAFRAICHIR_TACHES } from '../../workflow/evenements';
import {
  libelleActionDecision,
  libelleRaisonSaut,
  libelleResultatEtape,
  libelleStatutEtapeSuivi,
} from '../../workflow/libelles';

/** Pages de 100 tâches parcourues au plus pour retrouver la tâche d'une demande ciblée. */
const PAGES_RECHERCHE_MAX = 20;

function genererCleIdempotence(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `wf-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

const libelleAction = libelleActionDecision;

export default function MesTachesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filtreObjetType = searchParams.get('objet_type') ?? '';
  const filtreObjetId = searchParams.get('objet_id') ?? '';

  const [onglet, setOnglet] = useState<'A_TRAITER' | 'TRAITEES'>('A_TRAITER');
  const [page, setPage] = useState(0);
  const [donnees, setDonnees] = useState<PageWorkflow<TacheListeItem> | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [messageGlobal, setMessageGlobal] = useState<string | null>(null);

  // Panneau de détail et décision
  const [detail, setDetail] = useState<TacheDetail | null>(null);
  const [detailChargement, setDetailChargement] = useState(false);
  const [detailErreur, setDetailErreur] = useState<string | null>(null);
  const [tacheEnEchec, setTacheEnEchec] = useState<string | null>(null);
  // Demande ciblée par l'adresse (?objet_type=…&objet_id=…) : recherche de la tâche de l'utilisateur.
  const [cible, setCible] = useState<{ etat: 'recherche' | 'introuvable' | 'erreur'; message?: string } | null>(null);
  const [tentativeCible, setTentativeCible] = useState(0);
  const cibleTraitee = useRef('');
  const minuteur = useRef<number | null>(null);
  const [cleIdempotence, setCleIdempotence] = useState('');
  const [actionChoisie, setActionChoisie] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [envoiDecision, setEnvoiDecision] = useState(false);

  const chargerListe = useCallback(async (statut: 'A_TRAITER' | 'TRAITEES', p: number) => {
    setChargement(true);
    setErreur(null);
    try {
      const res = await getMesTaches({ statut, page: p, taille: 20 });
      setDonnees(res);
      setPage(p);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => {
    void chargerListe(onglet, 0);
    window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
  }, [onglet, chargerListe]);

  const ouvrirDetail = useCallback(async (tacheId: string, garderSaisie = false) => {
    setDetailChargement(true);
    setDetailErreur(null);
    setTacheEnEchec(null);
    try {
      const det = await getDetailTache(tacheId);
      setDetail(det);
      if (!garderSaisie) {
        // D-W7-10 : clé d'idempotence générée à l'ouverture du formulaire, réutilisée si nouvel essai
        setCleIdempotence(genererCleIdempotence());
        setActionChoisie(det.actions_permises[0] ?? '');
        setCommentaire('');
      }
    } catch (e) {
      setDetail(null);
      if (e instanceof WorkflowApiError && e.status === 409 && e.code === 'TACHE_DEJA_TRAITEE') {
        setDetailErreur('Cette tâche a déjà été traitée.');
        void chargerListe(onglet, page);
        window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
      } else if (e instanceof WorkflowApiError && (e.status === 403 || e.status === 404)) {
        setErreur(messageErreurWorkflow(e));
        void chargerListe(onglet, page);
      } else {
        // Erreur réseau ou serveur : message et « Réessayer ».
        setDetailErreur(messageErreurWorkflow(e));
        setTacheEnEchec(tacheId);
      }
    } finally {
      setDetailChargement(false);
    }
  }, [chargerListe, onglet, page]);

  // Demande ciblée : on retrouve la tâche courante de l'utilisateur sans filtrer la page chargée.
  useEffect(() => {
    if (!filtreObjetType || !filtreObjetId) {
      setCible(null);
      cibleTraitee.current = '';
      return undefined;
    }
    const cle = `${filtreObjetType}/${filtreObjetId}`;
    if (cibleTraitee.current === cle) return undefined; // une seule ouverture automatique par demande
    let annule = false;
    setCible({ etat: 'recherche' });
    (async () => {
      try {
        const suivi = await getSuiviObjetWorkflow(filtreObjetType, filtreObjetId);
        let p = 0;
        for (;;) {
          const res = await getMesTaches({ statut: 'A_TRAITER', page: p, taille: 100 });
          if (annule) return;
          const trouvee = res.elements.find((t) => t.instance_id === suivi.instance_id);
          if (trouvee) {
            cibleTraitee.current = cle;
            setCible(null);
            setOnglet('A_TRAITER');
            await ouvrirDetail(trouvee.tache_id);
            return;
          }
          p += 1;
          if (p * res.taille >= res.total || p >= PAGES_RECHERCHE_MAX) break;
        }
        cibleTraitee.current = cle;
        setCible({ etat: 'introuvable', message: 'Aucune tâche à traiter pour cette demande.' });
      } catch (e) {
        if (annule) return;
        if (e instanceof WorkflowApiError && (e.status === 403 || e.status === 404)) {
          cibleTraitee.current = cle;
          setCible({ etat: 'introuvable', message: 'Aucune tâche à traiter pour cette demande.' });
        } else {
          setCible({ etat: 'erreur', message: messageErreurWorkflow(e) });
        }
      }
    })();
    return () => {
      annule = true;
    };
    // ouvrirDetail change à chaque page chargée : la recherche ne doit dépendre que de la demande ciblée.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtreObjetType, filtreObjetId, tentativeCible]);

  useEffect(
    () => () => {
      if (minuteur.current !== null) window.clearTimeout(minuteur.current);
    },
    [],
  );

  const regle = regleMotif(actionChoisie, Boolean(detail?.commentaire_obligatoire));
  const motifObligatoire = regle.obligatoire;
  const longueurCommentaire = commentaire.trim().length;
  const motifInvalide = !motifValide(commentaire, regle);

  async function soumettreDecision(e: React.FormEvent) {
    e.preventDefault();
    if (!detail || !actionChoisie || motifInvalide) return;
    setEnvoiDecision(true);
    setDetailErreur(null);
    setMessageGlobal(null);
    try {
      const res = await postDecisionTache(detail.tache_id, {
        action: actionChoisie,
        commentaire: commentaire.trim() || null,
        version: detail.version,
        cle_idempotence: cleIdempotence,
      });
      if (res.statut === 202) {
        cibleTraitee.current = '';
        setMessageGlobal('Décision en cours d’enregistrement : elle apparaîtra dans quelques instants.');
        setDetail(null);
        if (minuteur.current !== null) window.clearTimeout(minuteur.current);
        minuteur.current = window.setTimeout(() => {
          minuteur.current = null;
          void chargerListe(onglet, page);
          window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
        }, 10_000);
      } else {
        cibleTraitee.current = ''; // une nouvelle tâche sur la même demande pourra se rouvrir
        setMessageGlobal(`Décision « ${libelleAction(actionChoisie)} » enregistrée.`);
        setDetail(null);
        await chargerListe(onglet, page);
        window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
      }
    } catch (err) {
      if (err instanceof WorkflowApiError) {
        if (err.status === 409 && err.code === 'TACHE_DEJA_TRAITEE') {
          setDetail(null);
          setDetailErreur('Cette tâche a déjà été traitée.');
          await chargerListe(onglet, page);
          window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
        } else if (err.status === 409) {
          await ouvrirDetail(detail.tache_id, true);
          setDetailErreur(
            `${messageErreurWorkflow(err)} La tâche a été rechargée ; vérifiez son état avant de recommencer.`,
          );
        } else if (err.status === 422) {
          // Message du serveur affiché tel quel (ex. solde insuffisant), formulaire et clé conservés
          setDetailErreur(err.message);
        } else if (err.status === 403 || err.status === 404) {
          setDetail(null);
          setErreur(messageErreurWorkflow(err));
          await chargerListe(onglet, page);
        } else {
          setDetailErreur(messageErreurWorkflow(err));
        }
      } else {
        setDetailErreur(messageErreurWorkflow(err));
      }
    } finally {
      setEnvoiDecision(false);
    }
  }

  const elements = donnees?.elements ?? [];
  const totalPages = donnees ? Math.max(1, Math.ceil(donnees.total / donnees.taille)) : 1;

  return (
    <div className="page">
      <PageHeader
        title="Mes tâches"
        lead="Demandes en attente de votre décision et historique de vos tâches traitées."
      />

      {messageGlobal ? <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>{messageGlobal}</div> : null}
      {erreur ? (
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerListe(onglet, page)}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {cible ? (
        <div
          className={`alert alert--${cible.etat === 'erreur' ? 'error' : 'info'}`}
          role={cible.etat === 'erreur' ? 'alert' : 'status'}
          style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}
        >
          <span>{cible.etat === 'recherche' ? 'Recherche de la tâche de cette demande…' : cible.message}</span>
          <span style={{ display: 'flex', gap: 8 }}>
            {cible.etat === 'erreur' ? (
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  cibleTraitee.current = '';
                  setTentativeCible((n) => n + 1);
                }}
              >
                Réessayer
              </button>
            ) : null}
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => setSearchParams({})}>
              Fermer
            </button>
          </span>
        </div>
      ) : null}
      {detailErreur && !detail ? (
        <ErrorAlert>
          {detailErreur}{' '}
          {tacheEnEchec ? (
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void ouvrirDetail(tacheEnEchec)}>
              Réessayer
            </button>
          ) : null}
        </ErrorAlert>
      ) : null}

      <div className="tabs" role="tablist" aria-label="Onglets Mes tâches" style={{ marginBottom: 16 }}>
        <button
          type="button"
          role="tab"
          aria-selected={onglet === 'A_TRAITER'}
          className={`tab${onglet === 'A_TRAITER' ? ' tab--active' : ''}`}
          onClick={() => {
            setDetail(null);
            setDetailErreur(null);
            setOnglet('A_TRAITER');
          }}
        >
          À traiter
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={onglet === 'TRAITEES'}
          className={`tab${onglet === 'TRAITEES' ? ' tab--active' : ''}`}
          onClick={() => {
            setDetail(null);
            setDetailErreur(null);
            setOnglet('TRAITEES');
          }}
        >
          Traitées (90 jours)
        </button>
      </div>

      {chargement ? <LoadingState /> : null}

      {!chargement && elements.length === 0 ? (
        <EmptyState title={onglet === 'A_TRAITER' ? 'Aucune tâche à traiter' : 'Aucune tâche traitée récemment'}>
          {onglet === 'A_TRAITER'
            ? 'Vous n’avez aucune demande en attente de validation.'
            : 'Les décisions prises au cours des 90 derniers jours apparaîtront ici.'}
        </EmptyState>
      ) : null}

      {!chargement && elements.length > 0 ? (
        <div className="card" style={{ marginBottom: 16 }}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Objet</th>
                  <th>Étape</th>
                  <th>Demandeur</th>
                  <th>Résumé</th>
                  <th>Échéance</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {elements.map((t) => (
                  <tr key={t.tache_id}>
                    <td>
                      <strong>{t.objet_libelle}</strong>
                      {t.objet_numero ? <div className="small muted">{t.objet_numero}</div> : null}
                    </td>
                    <td>
                      {t.libelle_etape}
                      {t.pour_nom ? <div className="small muted">Pour le compte de {t.pour_nom}</div> : null}
                    </td>
                    <td>{t.demandeur_nom}</td>
                    <td className="small">
                      {t.resume ? (
                        <>
                          {t.resume.affichage_type ? <span>{String(t.resume.affichage_type)} </span> : null}
                          {t.resume.affichage_periode ? <span>{String(t.resume.affichage_periode)} </span> : null}
                          {t.resume.affichage_duree ? <span>({String(t.resume.affichage_duree)})</span> : null}
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      {t.echeance_le ? (
                        <span className={t.en_retard ? 'status status--danger' : ''}>
                          {formatDateHeure(t.echeance_le)}
                          {t.en_retard ? ' (en retard)' : ''}
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        onClick={() => void ouvrirDetail(t.tache_id)}
                      >
                        {onglet === 'A_TRAITER' ? 'Traiter' : 'Consulter'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 ? (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={page === 0}
                onClick={() => void chargerListe(onglet, page - 1)}
              >
                Précédent
              </button>
              <span className="small muted">Page {page + 1} / {totalPages}</span>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={page + 1 >= totalPages}
                onClick={() => void chargerListe(onglet, page + 1)}
              >
                Suivant
              </button>
            </div>
          ) : null}
        </div>
      ) : null}

      {detailChargement ? <div className="card"><LoadingState label="Chargement du détail de la tâche…" /></div> : null}

      {detail && !detailChargement ? (
        <section className="card" aria-label="Détail de la tâche">
          {detailErreur ? <ErrorAlert>{detailErreur}</ErrorAlert> : null}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <div>
              <h3 style={{ margin: 0 }}>
                {detail.objet_libelle} {detail.objet_numero ? `— ${detail.objet_numero}` : ''}
              </h3>
              <p className="small muted" style={{ margin: 0 }}>
                Étape : {detail.libelle} · Demandeur : {detail.demandeur_nom}
                {detail.echeance_le ? ` · Échéance : ${formatDateHeure(detail.echeance_le)}` : ''}
              </p>
            </div>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDetail(null)}>
              Fermer
            </button>
          </div>

          {detail.resume && Object.keys(detail.resume).length > 0 ? (
            <div style={{ marginBottom: 14, padding: 10, borderRadius: 8, background: 'var(--bg-subtle, #f8fafc)' }}>
              <strong>Résumé de la demande</strong>
              <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '4px 12px', margin: '6px 0 0' }} className="small">
                {detail.resume.affichage_type ? (<><dt className="muted">Type</dt><dd style={{ margin: 0 }}>{String(detail.resume.affichage_type)}</dd></>) : null}
                {detail.resume.affichage_periode ? (<><dt className="muted">Période</dt><dd style={{ margin: 0 }}>{String(detail.resume.affichage_periode)}</dd></>) : null}
                {detail.resume.affichage_duree ? (<><dt className="muted">Durée</dt><dd style={{ margin: 0 }}>{String(detail.resume.affichage_duree)}</dd></>) : null}
              </dl>
            </div>
          ) : null}

          {detail.suivi && detail.suivi.etapes.length > 0 ? (
            <div style={{ marginBottom: 16 }}>
              <h4 style={{ marginBottom: 6 }}>Suivi du circuit</h4>
              <ol style={{ margin: 0, paddingLeft: 18 }} className="small">
                {detail.suivi.etapes.map((et) => (
                  <li key={et.code} style={{ marginBottom: 4 }}>
                    <strong>{et.libelle}</strong> — {libelleStatutEtapeSuivi(et.statut)}
                    {et.resultat ? ` (${libelleResultatEtape(et.resultat)})` : ''}
                    {et.statut === 'SAUTEE' && et.raison ? ` : ${libelleRaisonSaut(et.raison)}` : ''}
                    {et.decideur_nom ? ` par ${et.decideur_nom}` : ''}
                    {et.pour_nom ? ` (pour le compte de ${et.pour_nom})` : ''}
                    {et.date_decision ? ` le ${formatDateHeure(et.date_decision)}` : ''}
                    {et.motif ? ` — « ${et.motif} »` : ''}
                  </li>
                ))}
              </ol>
            </div>
          ) : null}

          {detail.statut === 'A_TRAITER' && detail.actions_permises.length > 0 ? (
            <form onSubmit={(e) => void soumettreDecision(e)} style={{ borderTop: '1px solid var(--border, #e5e7eb)', paddingTop: 14 }}>
              <h4 style={{ marginBottom: 8 }}>Prendre une décision</h4>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', margin: '10px 0' }}>
                {detail.actions_permises.map((act) => (
                  <label key={act} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <input
                      type="radio"
                      name="wf-decision-action"
                      value={act}
                      checked={actionChoisie === act}
                      onChange={() => setActionChoisie(act)}
                    />
                    <span>{libelleAction(act)}</span>
                  </label>
                ))}
              </div>

              <div>
                <label className="field-label" htmlFor="wf-decision-commentaire">
                  Commentaire / motif{motifObligatoire ? ` (obligatoire, ${regle.min} à ${regle.max} caractères)` : ' (facultatif)'}
                </label>
                <textarea
                  id="wf-decision-commentaire"
                  className="field-input field-input--area"
                  rows={3}
                  maxLength={regle.max}
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                />
                <p className={`small ${motifInvalide ? 'field-error' : 'muted'}`}>
                  {longueurCommentaire} / {regle.max} caractères
                  {motifInvalide && motifObligatoire && longueurCommentaire < regle.min
                    ? ` — saisissez au moins ${regle.min} caractères.`
                    : ''}
                </p>
              </div>

              <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                <button
                  type="submit"
                  className={actionChoisie === 'REFUSER' ? 'btn btn--danger' : 'btn btn--primary'}
                  disabled={envoiDecision || !actionChoisie || motifInvalide}
                  aria-busy={envoiDecision}
                >
                  {envoiDecision ? 'Envoi…' : `Confirmer : ${libelleAction(actionChoisie)}`}
                </button>
              </div>
            </form>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

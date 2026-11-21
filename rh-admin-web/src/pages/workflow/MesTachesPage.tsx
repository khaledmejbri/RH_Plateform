import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  getDetailTache,
  getMesTaches,
  messageErreurWorkflow,
  postDecisionTache,
  WorkflowApiError,
  type PageWorkflow,
  type TacheDetail,
  type TacheListeItem,
} from '../../api/workflowApi';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';

export const EVENEMENT_RAFRAICHIR_TACHES = 'sirh:wf-taches-refresh';

function genererCleIdempotence(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `wf-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function libelleAction(action: string): string {
  switch (action) {
    case 'APPROUVER':
      return 'Approuver';
    case 'REFUSER':
      return 'Refuser';
    case 'DEMANDER_COMPLEMENT':
      return 'Demander un complément';
    case 'FAIT':
      return 'Marquer comme fait';
    default:
      return action;
  }
}

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

  const elementsFiltres = useMemo(() => {
    const items = donnees?.elements ?? [];
    return items.filter((t) => {
      if (filtreObjetType && t.objet_type !== filtreObjetType) return false;
      if (filtreObjetId && t.objet_id !== filtreObjetId) return false;
      return true;
    });
  }, [donnees, filtreObjetType, filtreObjetId]);

  const ouvrirDetail = useCallback(async (tacheId: string, garderSaisie = false) => {
    setDetailChargement(true);
    setDetailErreur(null);
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
      if (e instanceof WorkflowApiError && (e.status === 403 || e.status === 404)) {
        setDetail(null);
        setErreur(messageErreurWorkflow(e));
        void chargerListe(onglet, page);
      } else {
        setDetailErreur(messageErreurWorkflow(e));
      }
    } finally {
      setDetailChargement(false);
    }
  }, [chargerListe, onglet, page]);

  // Si un filtre objet_id correspond exactement à une tâche à traiter, on peut l'ouvrir directement
  useEffect(() => {
    if (filtreObjetId && !detail && elementsFiltres.length === 1 && onglet === 'A_TRAITER') {
      void ouvrirDetail(elementsFiltres[0].tache_id);
    }
  }, [filtreObjetId, detail, elementsFiltres, onglet, ouvrirDetail]);

  const motifObligatoire =
    actionChoisie === 'REFUSER' ||
    actionChoisie === 'DEMANDER_COMPLEMENT' ||
    Boolean(detail?.commentaire_obligatoire);
  const longueurCommentaire = commentaire.trim().length;
  const motifInvalide = motifObligatoire && longueurCommentaire < 5;

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
        setMessageGlobal('Décision en cours d’enregistrement : elle apparaîtra dans quelques instants.');
        setDetail(null);
        window.setTimeout(() => {
          void chargerListe(onglet, page);
          window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
        }, 10_000);
      } else {
        setMessageGlobal(`Décision « ${libelleAction(actionChoisie)} » enregistrée.`);
        setDetail(null);
        await chargerListe(onglet, page);
        window.dispatchEvent(new Event(EVENEMENT_RAFRAICHIR_TACHES));
      }
    } catch (err) {
      if (err instanceof WorkflowApiError) {
        if (err.status === 409) {
          setDetailErreur(
            `${messageErreurWorkflow(err)} La tâche a été rechargée ; vérifiez son état avant de recommencer.`,
          );
          await ouvrirDetail(detail.tache_id, true);
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

      {(filtreObjetType || filtreObjetId) ? (
        <div className="alert alert--info" role="status" style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>
            Filtre actif{filtreObjetType ? ` — type : ${filtreObjetType}` : ''}{filtreObjetId ? ' (demande ciblée)' : ''}.
          </span>
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => setSearchParams({})}>
            Tout afficher
          </button>
        </div>
      ) : null}

      <div className="tabs" role="tablist" aria-label="Onglets Mes tâches" style={{ marginBottom: 16 }}>
        <button
          type="button"
          role="tab"
          aria-selected={onglet === 'A_TRAITER'}
          className={`tab${onglet === 'A_TRAITER' ? ' tab--active' : ''}`}
          onClick={() => {
            setDetail(null);
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
            setOnglet('TRAITEES');
          }}
        >
          Traitées (90 jours)
        </button>
      </div>

      {chargement ? <LoadingState /> : null}

      {!chargement && elementsFiltres.length === 0 ? (
        <EmptyState title={onglet === 'A_TRAITER' ? 'Aucune tâche à traiter' : 'Aucune tâche traitée récemment'}>
          {onglet === 'A_TRAITER'
            ? 'Vous n’avez aucune demande en attente de validation.'
            : 'Les décisions prises au cours des 90 derniers jours apparaîtront ici.'}
        </EmptyState>
      ) : null}

      {!chargement && elementsFiltres.length > 0 ? (
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
                {elementsFiltres.map((t) => (
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
                    <strong>{et.libelle}</strong> — {et.statut}
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
              {detailErreur ? <ErrorAlert>{detailErreur}</ErrorAlert> : null}

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
                  Commentaire / motif{motifObligatoire ? ' (obligatoire, 5 caractères min.)' : ' (facultatif)'}
                </label>
                <textarea
                  id="wf-decision-commentaire"
                  className="field-input field-input--area"
                  rows={3}
                  maxLength={1000}
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                />
                {motifInvalide ? (
                  <p className="small field-error">
                    Saisissez au moins 5 caractères ({longueurCommentaire} / 5).
                  </p>
                ) : null}
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

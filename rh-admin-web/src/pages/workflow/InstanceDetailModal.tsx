import { useCallback, useEffect, useMemo, useState } from 'react';
import type { CollaborateurRow } from '../../api/rhClient';
import {
  getInstanceDetailWorkflow,
  messageErreurWorkflow,
  reaffecterTacheWorkflow,
  WorkflowApiError,
  type CatalogueWorkflow,
  type InstanceDetailAdmin,
  type TacheAdmin,
} from '../../api/workflowApi';
import FiltreCollaborateur, { libelleCollaborateur } from '../../components/FiltreCollaborateur';
import { ErrorAlert, LoadingState } from '../../components/ui';
import { DialogPanel } from '../../components/useDialogA11y';
import DetailLink from '../../detail/DetailLink';
import { typeDepuisNotification } from '../../detail/refs';
import { formatDate, formatDateHeure, formatNombre, TIRET } from '../../utils/format';
import {
  libelleDecisionTache,
  libelleRaisonSaut,
  libelleResultatEtape,
  libelleStatutEtapeSuivi,
  libelleStatutInstance,
  libelleStatutTache,
  libelleTypeTache,
  toneStatutInstance,
  toneStatutTache,
} from '../../workflow/libelles';
import {
  formatEvenementHistorique,
  formatOrigineCandidat,
  REAFFECTATION_COLLABS_MAX,
  REAFFECTATION_MOTIF_MAX,
  REAFFECTATION_MOTIF_MIN,
  validerReaffectation,
} from '../../workflow/supervision';

type Props = {
  instanceId: string;
  lectureSeule: boolean;
  catalogue: CatalogueWorkflow | null;
  collaborateurs: ReadonlyArray<CollaborateurRow>;
  collabChargement: boolean;
  onClose: () => void;
  onModifie: () => void;
};

function formatValeurVariable(v: unknown): string {
  if (v === null || v === undefined || v === '') return TIRET;
  if (typeof v === 'boolean') return v ? 'Oui' : 'Non';
  if (typeof v === 'number') return formatNombre(v, 2);
  if (typeof v === 'string') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return formatDate(v);
    if (/^\d{4}-\d{2}-\d{2}T/.test(v)) return formatDateHeure(v);
    return v;
  }
  if (Array.isArray(v)) return v.map((x) => formatValeurVariable(x)).join(', ');
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

export default function InstanceDetailModal({
  instanceId,
  lectureSeule,
  catalogue,
  collaborateurs,
  collabChargement,
  onClose,
  onModifie,
}: Props) {
  const [detail, setDetail] = useState<InstanceDetailAdmin | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [messageSucces, setMessageSucces] = useState<string | null>(null);

  // État du formulaire de réaffectation inline (D-W7b-03)
  const [reaffectTacheId, setReaffectTacheId] = useState<string | null>(null);
  const [selectionIds, setSelectionIds] = useState<string[]>([]);
  const [motif, setMotif] = useState('');
  const [resetKey, setResetKey] = useState(0);
  const [envoiReaffect, setEnvoiReaffect] = useState(false);
  const [reaffectErreur, setReaffectErreur] = useState<string | null>(null);

  const chargerDetail = useCallback(async (garderSaisie = false) => {
    setChargement(true);
    setErreur(null);
    try {
      const res = await getInstanceDetailWorkflow(instanceId);
      setDetail(res);
      if (!garderSaisie) {
        setReaffectTacheId(null);
        setSelectionIds([]);
        setMotif('');
        setReaffectErreur(null);
      } else if (reaffectTacheId) {
        const t = res.taches.find((x) => x.id === reaffectTacheId);
        if (!t || t.statut !== 'A_TRAITER') {
          setReaffectTacheId(null);
        }
      }
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, [instanceId, reaffectTacheId]);

  useEffect(() => {
    void chargerDetail(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instanceId]);

  const indexCollabs = useMemo(
    () => new Map(collaborateurs.map((c) => [c.identifiant, c])),
    [collaborateurs],
  );

  const collaborateursEligibles = useMemo(() => {
    const selectionSet = new Set(selectionIds);
    return collaborateurs.filter(
      (c) =>
        c.statut?.toUpperCase() === 'ACTIF' &&
        c.identifiant !== detail?.demandeur_id &&
        !selectionSet.has(c.identifiant),
    );
  }, [collaborateurs, selectionIds, detail?.demandeur_id]);

  const libellesEtapes = useMemo(
    () => new Map((detail?.etapes ?? []).map((e) => [e.etape_code, e.libelle])),
    [detail?.etapes],
  );

  const libellesVariables = useMemo(() => {
    const map = new Map<string, string>();
    for (const vc of catalogue?.variables_communes ?? []) {
      map.set(vc.nom, vc.libelle);
    }
    const typeCat = catalogue?.types.find((t) => t.objet_type === detail?.objet_type);
    for (const v of typeCat?.variables ?? []) {
      map.set(v.nom, v.libelle);
    }
    return map;
  }, [catalogue, detail?.objet_type]);

  function ouvrirReaffectation(t: TacheAdmin) {
    if (lectureSeule || t.statut !== 'A_TRAITER') return;
    setReaffectTacheId(t.id);
    setSelectionIds([]);
    setMotif('');
    setReaffectErreur(null);
    setMessageSucces(null);
    setResetKey((k) => k + 1);
  }

  function ajouterCollaborateur(cid: string) {
    if (!cid) return;
    setSelectionIds((prev) => {
      if (prev.includes(cid) || prev.length >= REAFFECTATION_COLLABS_MAX) return prev;
      return [...prev, cid];
    });
    setResetKey((k) => k + 1);
  }

  function retirerCollaborateur(cid: string) {
    setSelectionIds((prev) => prev.filter((x) => x !== cid));
  }

  const validationReaffect = validerReaffectation(selectionIds, motif);

  async function soumettreReaffectation(e: React.FormEvent, t: TacheAdmin) {
    e.preventDefault();
    if (lectureSeule || t.statut !== 'A_TRAITER' || !validationReaffect.valide) return;
    setEnvoiReaffect(true);
    setReaffectErreur(null);
    setMessageSucces(null);
    try {
      const etaitBloquee = detail?.statut === 'BLOQUEE';
      const rep = await reaffecterTacheWorkflow(t.id, {
        collaborateur_ids: selectionIds,
        motif: motif.trim(),
        version: t.version,
      });
      setMessageSucces(
        etaitBloquee && rep.statut_instance === 'EN_COURS'
          ? 'Tâche réaffectée et instance de workflow débloquée.'
          : 'Tâche réaffectée avec succès.',
      );
      await chargerDetail(false);
      onModifie();
    } catch (err) {
      if (err instanceof WorkflowApiError && err.status === 409) {
        // Garde la saisie (collaborateurs et motif) et recharge le détail (D-W7b-03).
        const msg =
          err.code === 'TACHE_DEJA_TRAITEE'
            ? 'Cette tâche a déjà été traitée entre-temps. Le détail a été rechargé.'
            : `${messageErreurWorkflow(err)} Le détail a été rechargé ; vérifiez puis recommencez.`;
        await chargerDetail(true);
        setReaffectErreur(msg);
        onModifie();
      } else {
        setReaffectErreur(messageErreurWorkflow(err));
      }
    } finally {
      setEnvoiReaffect(false);
    }
  }

  const titreModale = detail
    ? `${detail.objet_libelle}${detail.objet_numero ? ` — ${detail.objet_numero}` : ''}`
    : 'Détail de l’instance';

  const typeObjetDetail = detail ? typeDepuisNotification(detail.objet_type) : null;
  const entriesVariables = detail?.variables ? Object.entries(detail.variables) : [];

  return (
    <div
      className="dmodal-backdrop"
      style={{ zIndex: 80 }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !envoiReaffect) onClose();
      }}
    >
      <DialogPanel onClose={() => { if (!envoiReaffect) onClose(); }} label={titreModale} className="dmodal">
        <header className="dmodal__head">
          <h2 className="dmodal__titre">{titreModale}</h2>
          {detail && typeObjetDetail ? (
            <DetailLink
              type={typeObjetDetail}
              id={detail.objet_id}
              libelle={detail.objet_numero ?? detail.objet_libelle}
              className="btn btn--secondary btn--sm"
            >
              Ouvrir la demande
            </DetailLink>
          ) : null}
          <button
            type="button"
            className="modal__close"
            aria-label="Fermer"
            disabled={envoiReaffect}
            onClick={onClose}
          >
            ×
          </button>
        </header>

        <div className="dmodal__body">
          {chargement && !detail ? <LoadingState label="Chargement du détail de l’instance…" /> : null}

          {erreur ? (
            <ErrorAlert>
              {erreur}{' '}
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerDetail(false)}>
                Réessayer
              </button>
            </ErrorAlert>
          ) : null}

          {messageSucces ? (
            <div className="alert alert--success" role="status">
              {messageSucces}
            </div>
          ) : null}

          {reaffectErreur && !reaffectTacheId ? (
            <ErrorAlert>{reaffectErreur}</ErrorAlert>
          ) : null}

          {detail ? (
            <>
              {detail.statut === 'BLOQUEE' ? (
                <div className="alert alert--warning" role="status">
                  {lectureSeule
                    ? 'Cette instance est bloquée en attente d’une réaffectation par un gestionnaire RH.'
                    : 'Cette instance est bloquée (aucun intervenant disponible ou action automatique refusée). Réaffectez la tâche à traiter ci-dessous pour relancer le circuit.'}
                </div>
              ) : null}

              <dl className="detail-champs">
                <div>
                  <dt>Statut</dt>
                  <dd>
                    <span className={`status status--${toneStatutInstance(detail.statut)}`}>
                      {libelleStatutInstance(detail.statut)}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Demandeur</dt>
                  <dd>
                    <DetailLink type="collaborateur" id={detail.demandeur_id} libelle={detail.demandeur_nom} />
                  </dd>
                </div>
                <div>
                  <dt>Modèle appliqué</dt>
                  <dd>
                    <span className="cell-code">{detail.modele_code}</span> · v{detail.modele_version_numero}
                  </dd>
                </div>
                <div>
                  <dt>Étape courante</dt>
                  <dd>{detail.etape_courante_libelle ?? TIRET}</dd>
                </div>
                <div>
                  <dt>Démarrée le</dt>
                  <dd>{formatDateHeure(detail.demarre_le)}</dd>
                </div>
                <div>
                  <dt>Terminée le</dt>
                  <dd>{detail.termine_le ? formatDateHeure(detail.termine_le) : TIRET}</dd>
                </div>
                {detail.resultat_motif ? (
                  <div>
                    <dt>Motif de clôture</dt>
                    <dd>{detail.resultat_motif}</dd>
                  </div>
                ) : null}
              </dl>

              <h3 className="detail-h3">Frise des étapes</h3>
              {detail.etapes.length === 0 ? (
                <p className="detail-vide">Aucune étape exécutée.</p>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th scope="col">#</th>
                        <th scope="col">Étape</th>
                        <th scope="col">Statut</th>
                        <th scope="col">Résultat / Raison</th>
                        <th scope="col">Activée le</th>
                        <th scope="col">Terminée le</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.etapes.map((et) => (
                        <tr key={et.id}>
                          <td>{et.ordre}</td>
                          <td>
                            <strong>{et.libelle}</strong>
                            <div className="small muted">{et.etape_code}</div>
                          </td>
                          <td>{libelleStatutEtapeSuivi(et.statut)}</td>
                          <td>
                            {et.resultat ? libelleResultatEtape(et.resultat) : ''}
                            {et.raison_saut ? libelleRaisonSaut(et.raison_saut) : ''}
                            {et.nb_renvois > 0 ? `${et.resultat || et.raison_saut ? ' · ' : ''}${et.nb_renvois} renvoi(s)` : ''}
                            {!et.resultat && !et.raison_saut && et.nb_renvois === 0 ? TIRET : ''}
                          </td>
                          <td>{et.active_le ? formatDateHeure(et.active_le) : TIRET}</td>
                          <td>{et.terminee_le ? formatDateHeure(et.terminee_le) : TIRET}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              <h3 className="detail-h3">Tâches et intervenants</h3>
              {detail.taches.length === 0 ? (
                <p className="detail-vide">Aucune tâche enregistrée.</p>
              ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                  {detail.taches.map((t) => {
                    const enReaffectation = reaffectTacheId === t.id && !lectureSeule && t.statut === 'A_TRAITER';
                    return (
                      <section key={t.id} className="card" aria-label={`Tâche ${t.libelle}`}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10, flexWrap: 'wrap', marginBottom: 8 }}>
                          <div>
                            <h4 style={{ margin: 0 }}>
                              {t.libelle}{' '}
                              <span className="small muted">
                                ({libelleTypeTache(t.type)} · {t.obligatoire ? 'Obligatoire' : 'Facultative'})
                              </span>
                            </h4>
                            <div className="small muted" style={{ marginTop: 4, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                              <span>Échéance : {t.echeance_le ? formatDateHeure(t.echeance_le) : TIRET}</span>
                              <span>· Relances : {t.nb_relances}</span>
                              {t.prochaine_relance_le ? <span>(prochaine : {formatDateHeure(t.prochaine_relance_le)})</span> : null}
                              {t.escalade_le ? (
                                <span>· Escaladée le {formatDateHeure(t.escalade_le)}</span>
                              ) : t.escalade_a_faire_le ? (
                                <span>· Escalade prévue le {formatDateHeure(t.escalade_a_faire_le)}</span>
                              ) : null}
                            </div>
                          </div>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                            <span className={`status status--${toneStatutTache(t.statut)}`}>
                              {libelleStatutTache(t.statut)}
                            </span>
                            {t.en_retard ? <span className="status status--danger">En retard</span> : null}
                            {!lectureSeule && t.statut === 'A_TRAITER' && !enReaffectation ? (
                              <button
                                type="button"
                                className="btn btn--primary btn--sm"
                                onClick={() => ouvrirReaffectation(t)}
                              >
                                Réaffecter
                              </button>
                            ) : null}
                          </div>
                        </div>

                        {t.decision ? (
                          <div className="callout" style={{ marginBottom: 10 }}>
                            <p className="callout__title">
                              Décision : {libelleDecisionTache(t.decision)}
                            </p>
                            <p>
                              Par {t.decideur_nom ?? 'Système'}
                              {t.pour_nom ? ` (pour le compte de ${t.pour_nom})` : ''}
                              {t.decide_le ? ` le ${formatDateHeure(t.decide_le)}` : ''}
                              {t.commentaire ? ` — « ${t.commentaire} »` : ''}
                            </p>
                          </div>
                        ) : null}

                        {t.candidats.length === 0 ? (
                          <p className="detail-vide">Aucun candidat affecté à cette tâche.</p>
                        ) : (
                          <div className="table-wrap">
                            <table className="data-table">
                              <thead>
                                <tr>
                                  <th scope="col">Intervenant</th>
                                  <th scope="col">Origine</th>
                                  <th scope="col">Ajouté le</th>
                                  <th scope="col">État</th>
                                </tr>
                              </thead>
                              <tbody>
                                {t.candidats.map((c) => (
                                  <tr key={`${c.collaborateur_id}-${c.ajoute_le ?? ''}`} style={c.retire_le ? { opacity: 0.7 } : undefined}>
                                    <td>
                                      <DetailLink type="collaborateur" id={c.collaborateur_id} libelle={c.nom} />
                                    </td>
                                    <td>{formatOrigineCandidat(c.origine, c.pour_nom)}</td>
                                    <td>{c.ajoute_le ? formatDateHeure(c.ajoute_le) : TIRET}</td>
                                    <td>
                                      {c.retire_le ? (
                                        <span className="status status--neutral">
                                          Retiré le {formatDateHeure(c.retire_le)}
                                        </span>
                                      ) : (
                                        <span className="status status--success">Actif</span>
                                      )}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}

                        {enReaffectation ? (
                          <form
                            onSubmit={(e) => void soumettreReaffectation(e, t)}
                            style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)' }}
                          >
                            <h5 style={{ margin: '0 0 8px' }}>Réaffecter cette tâche</h5>
                            {reaffectErreur ? <ErrorAlert>{reaffectErreur}</ErrorAlert> : null}

                            {selectionIds.length > 0 ? (
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 10 }} aria-label="Collaborateurs sélectionnés">
                                {selectionIds.map((cid) => {
                                  const col = indexCollabs.get(cid);
                                  const nom = col ? libelleCollaborateur(col) : 'Collaborateur sélectionné';
                                  return (
                                    <span key={cid} className="chip chip--info">
                                      <span>{nom}</span>
                                      <button
                                        type="button"
                                        className="btn btn--ghost btn--sm"
                                        style={{ minHeight: 20, padding: '0 4px', border: 'none' }}
                                        aria-label={`Retirer ${nom}`}
                                        disabled={envoiReaffect}
                                        onClick={() => retirerCollaborateur(cid)}
                                      >
                                        ×
                                      </button>
                                    </span>
                                  );
                                })}
                              </div>
                            ) : null}

                            <div style={{ marginBottom: 10, maxWidth: 420 }}>
                              {selectionIds.length < REAFFECTATION_COLLABS_MAX ? (
                                <FiltreCollaborateur
                                  key={resetKey}
                                  id={`reaffect-collab-${t.id}`}
                                  libelle={`Nouveaux intervenants (${selectionIds.length}/${REAFFECTATION_COLLABS_MAX})`}
                                  collaborateurs={collaborateursEligibles}
                                  chargement={collabChargement}
                                  valeur=""
                                  onChange={ajouterCollaborateur}
                                />
                              ) : (
                                <p className="small muted">Nombre maximal de {REAFFECTATION_COLLABS_MAX} collaborateurs atteint.</p>
                              )}
                            </div>

                            <div style={{ marginBottom: 10 }}>
                              <label className="field-label" htmlFor={`reaffect-motif-${t.id}`}>
                                Motif de la réaffectation (obligatoire, {REAFFECTATION_MOTIF_MIN} à {REAFFECTATION_MOTIF_MAX} caractères)
                              </label>
                              <textarea
                                id={`reaffect-motif-${t.id}`}
                                className="field-input field-input--area"
                                rows={3}
                                maxLength={REAFFECTATION_MOTIF_MAX}
                                disabled={envoiReaffect}
                                value={motif}
                                onChange={(e) => setMotif(e.target.value)}
                              />
                              <p className={`small ${validationReaffect.erreurs.motif ? 'field-error' : 'muted'}`}>
                                {motif.trim().length} / {REAFFECTATION_MOTIF_MAX} caractères
                                {motif.trim().length < REAFFECTATION_MOTIF_MIN
                                  ? ` — saisissez au moins ${REAFFECTATION_MOTIF_MIN} caractères.`
                                  : ''}
                              </p>
                            </div>

                            <div style={{ display: 'flex', gap: 8 }}>
                              <button
                                type="submit"
                                className="btn btn--primary btn--sm"
                                disabled={envoiReaffect || !validationReaffect.valide}
                                aria-busy={envoiReaffect}
                              >
                                {envoiReaffect ? 'Réaffectation…' : 'Confirmer la réaffectation'}
                              </button>
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                disabled={envoiReaffect}
                                onClick={() => {
                                  setReaffectTacheId(null);
                                  setReaffectErreur(null);
                                }}
                              >
                                Annuler
                              </button>
                            </div>
                          </form>
                        ) : null}
                      </section>
                    );
                  })}
                </div>
              )}

              <h3 className="detail-h3">Variables du dossier</h3>
              {entriesVariables.length === 0 ? (
                <p className="detail-vide">Aucune variable enregistrée.</p>
              ) : (
                <dl className="detail-champs">
                  {entriesVariables.map(([cle, val]) => (
                    <div key={cle}>
                      <dt>{libellesVariables.get(cle) ?? cle}</dt>
                      <dd>{formatValeurVariable(val)}</dd>
                    </div>
                  ))}
                </dl>
              )}

              <h3 className="detail-h3">Historique</h3>
              {detail.historique.length === 0 ? (
                <p className="detail-vide">Aucun événement enregistré.</p>
              ) : (
                <ul className="detail-liste">
                  {detail.historique.map((h) => (
                    <li key={h.id}>
                      <span className="cell-meta">{formatDateHeure(h.horodatage)}</span> —{' '}
                      {formatEvenementHistorique(h, libellesEtapes)}
                      {h.acteur_nom ? ` · ${h.acteur_nom}` : ''}
                      {h.pour_nom ? ` (pour le compte de ${h.pour_nom})` : ''}
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : null}
        </div>
      </DialogPanel>
    </div>
  );
}

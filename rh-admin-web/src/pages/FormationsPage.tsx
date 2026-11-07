import { useEffect, useMemo, useState } from 'react';
import {
  getDemandesFormations,
  getTousCollaborateurs,
  getUnites,
  postFormationIntegrerPlan,
  postFormationRefuser,
  type CollaborateurRow,
  type DemandeFormation,
  type Unite,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';

import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { appartientSelection, correspond, indexerUnites, libelleDansSelection, unitesRetenues, useFiltresUrl } from '../utils/filtres';
import { DialogPanel } from '../components/useDialogA11y';
import { EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge } from '../components/ui';
import { formatDateHeure, formatNombre, indexerPersonnes, personneParId, COLLABORATEUR_INTROUVABLE } from '../utils/format';
import { libelle } from '../utils/libelles';

type StatutFilter = '' | 'EN_VALIDATION_RRH' | 'INTEGREE_PLAN' | 'REFUSEE' | 'ANNULEE';

const FILTRES_DEFAUT = { q: '', direction: '', departement: '', service: '', statut: 'EN_VALIDATION_RRH' };

export default function FormationsPage() {
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
  const [rows, setRows] = useState<DemandeFormation[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const statut = f.statut as StatutFilter;
  const [unites, setUnites] = useState<Unite[]>([]);
  const [collabs, setCollabs] = useState<CollaborateurRow[]>([]);
  const [collabsErr, setCollabsErr] = useState(false);
  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
    void getTousCollaborateurs().then(setCollabs).catch(() => setCollabsErr(true));
  }, []);
  const [selectedFormation, setSelectedFormation] = useState<DemandeFormation | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [action, setAction] = useState<{ kind: 'integrer' | 'refuser'; demande: DemandeFormation } | null>(null);
  const [actionText, setActionText] = useState('');
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getDemandesFormations(statut || undefined));
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [statut]);

  const indexUnites = useMemo(() => indexerUnites(unites), [unites]);
  const retenues = useMemo(() => unitesRetenues(unites, f), [unites, f]);
  const personnes = useMemo(() => indexerPersonnes(collabs), [collabs]);
  /** Demandeur : nom fourni par l'API, sinon résolu par la liste des collaborateurs, sinon « introuvable ». */
  const demandeur = (d: DemandeFormation) =>
    d.demandeur_nom && d.demandeur_nom.trim() ? d.demandeur_nom : personneParId(personnes, d.demandeur_identifiant);
  const uniteDemandeur = useMemo(
    () => new Map(collabs.map((c) => [c.identifiant, c.unite?.identifiant])),
    [collabs],
  );
  // Statut : serveur ; recherche et unité (unité du demandeur ou unité ciblée) : client.
  const affichees = useMemo(
    () =>
      rows.filter(
        (d) =>
          correspond(
            [d.numero, demandeur(d), d.type_formation, d.organisme, d.unite_cible_libelle, d.objectifs_pedagogiques],
            f.q,
          ) &&
          (retenues === null ||
            appartientSelection(indexUnites, uniteDemandeur.get(d.demandeur_identifiant), f) ||
            libelleDansSelection(retenues, d.unite_cible_libelle)),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows, f, retenues, indexUnites, uniteDemandeur, personnes],
  );

  async function confirmAction() {
    if (!action) return;
    const text = actionText.trim();
    if (action.kind === 'refuser' && !text) {
      setActionErr('Le motif de refus est obligatoire.');
      return;
    }
    const ok = await confirm({
      message: `${action.kind === 'integrer' ? 'Accepter' : 'Refuser'} la demande de formation « ${action.demande.type_formation} » de ${demandeur(action.demande)} ?`,
      confirmLabel: action.kind === 'integrer' ? 'Accepter' : 'Refuser',
      danger: action.kind === 'refuser',
    });
    if (!ok) return;
    setActing(true);
    setActionErr(null);
    try {
      if (action.kind === 'integrer') {
        await postFormationIntegrerPlan(action.demande.identifiant, text || undefined);
        setMsg('Demande acceptée.');
      } else {
        await postFormationRefuser(action.demande.identifiant, text);
        setMsg('Demande refusée.');
      }
      setAction(null);
      await load();
    } catch (e) {
      setActionErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setActing(false);
    }
  }

  function openAction(kind: 'integrer' | 'refuser', demande: DemandeFormation) {
    setAction({ kind, demande });
    setActionText('');
    setActionErr(null);
    setMsg(null);
  }

  function viewDetail(d: DemandeFormation) {
    setSelectedFormation(d);
    setShowDetailModal(true);
  }

  function closeDetailModal() {
    setShowDetailModal(false);
    setSelectedFormation(null);
  }

  const cibleLabel = (d: DemandeFormation) => {
    if (d.cible === 'UNITE') return d.unite_cible_libelle ?? 'Unité';
    const ids = d.collaborateurs_cibles_identifiants ?? [];
    if (ids.length === 0) return 'Aucun collaborateur';
    const noms = ids.map((id) => personnes.get(id)?.nom ?? COLLABORATEUR_INTROUVABLE);
    return noms.length <= 2 ? noms.join(', ') : `${noms.slice(0, 2).join(', ')} et ${noms.length - 2} autre${noms.length - 2 > 1 ? 's' : ''}`;
  };

  return (
    <div className="page">
      <PageHeader
        title="Formations"
        lead="Demandes de formation à valider par la RH."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      <BarreFiltres
        id="formations"
        recherche={f.q}
        onRecherche={(v) => filtres.definir({ q: v })}
        libelleRecherche="Demandeur ou formation"
        placeholder="Numéro, nom, formation, organisme…"
        nbResultats={affichees.length}
        nbTotal={rows.length}
        nbActifs={filtres.nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="formations-statut"
          libelle="Statut"
          valeur={f.statut}
          toutesLibelle="Tous les statuts"
          options={[
            { valeur: 'EN_VALIDATION_RRH', libelle: 'En attente de validation RH' },
            { valeur: 'INTEGREE_PLAN', libelle: 'Acceptées' },
            { valeur: 'REFUSEE', libelle: 'Refusées' },
            { valeur: 'ANNULEE', libelle: 'Annulées' },
          ]}
          onChange={(v) => filtres.definir({ statut: v })}
        />
        <FiltresUnite idPrefixe="formations" unites={unites} selection={f} onChange={(sel) => filtres.definir(sel)} />
      </BarreFiltres>
      {collabsErr ? (
        <div className="alert alert--warning" role="status">
          Liste des collaborateurs indisponible : le filtre par unité ne porte que sur l'unité ciblée, pas sur l'unité du demandeur.
        </div>
      ) : null}

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des demandes de formation…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="graduation" title="Aucune demande de formation">
            Aucune demande ne correspond à ce statut.
          </EmptyState>
        ) : affichees.length === 0 ? (
          <AucunResultat onReinitialiser={filtres.reinitialiser} />
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Demandes de formation</caption>
              <thead>
                <tr>
                  <th scope="col">Numéro</th>
                  <th scope="col">Formation</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col">Cible</th>
                  <th scope="col">Budget</th>
                  <th scope="col">Statut</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {affichees.map((d) => (
                  <tr key={d.identifiant}>
                    <td data-label="Numéro" className="cell-meta">{d.numero ?? ''}</td>
                    <td data-label="Formation" className="cell-main">
                      <div className="cell-main__title">{d.type_formation}</div>
                      <div className="cell-main__sub">{d.organisme} · {formatNombre(d.duree_heures)} h</div>
                    </td>
                    <td data-label="Demandeur">
                      <div>{demandeur(d)}</div>
                      <div className="cell-meta">{libelle('origineFormation', d.origine)}</div>
                    </td>
                    <td data-label="Cible">{cibleLabel(d)}</td>
                    <td data-label="Budget" style={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>{formatTnd(d.cout_estime)}</td>
                    <td data-label="Statut"><StatusBadge statut={d.statut} label={labelStatut(d.statut)} /></td>
                    <td data-label="Actions">
                      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                        <button type="button" className="btn btn--sm btn--ghost" onClick={() => viewDetail(d)}>
                          <Icon name="eye" size={14} />
                          Détails
                        </button>
                        {!lectureSeule && d.statut === 'EN_VALIDATION_RRH' ? (
                          <>
                            <button type="button" className="btn btn--sm btn--secondary" onClick={() => openAction('refuser', d)}>
                              Refuser
                            </button>
                            <button type="button" className="btn btn--sm btn--primary" onClick={() => openAction('integrer', d)}>
                              Accepter
                            </button>
                          </>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showDetailModal && selectedFormation && (
        <div className="modal-backdrop" role="presentation" onClick={closeDetailModal}>
          <DialogPanel label="Détails de la formation" className="modal modal--md" onClose={closeDetailModal}>
            <div className="modal__head">
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>{selectedFormation.type_formation}</h3>
                <span className="cell-meta">{[selectedFormation.numero, selectedFormation.organisme].filter(Boolean).join(' · ')}</span>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={closeDetailModal}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <dl className="detail-grid">
                <div><dt>Durée</dt><dd>{formatNombre(selectedFormation.duree_heures)} heures</dd></div>
                <div><dt>Budget</dt><dd>{formatTnd(selectedFormation.cout_estime)}</dd></div>
                <div><dt>Demandeur</dt><dd>{demandeur(selectedFormation)}</dd></div>
                <div><dt>Origine</dt><dd>{libelle('origineFormation', selectedFormation.origine)}</dd></div>
                <div><dt>Cible</dt><dd>{cibleLabel(selectedFormation)}</dd></div>
                <div><dt>Statut</dt><dd><StatusBadge statut={selectedFormation.statut} label={labelStatut(selectedFormation.statut)} /></dd></div>
                <div><dt>Créée le</dt><dd>{formatDateHeure(selectedFormation.cree_le)}</dd></div>
              </dl>

              {selectedFormation.objectifs_pedagogiques && (
                <>
                  <p className="section-title">Objectifs pédagogiques</p>
                  <p className="small" style={{ marginTop: 0 }}>{selectedFormation.objectifs_pedagogiques}</p>
                </>
              )}
              {selectedFormation.justification && (
                <>
                  <p className="section-title">Justification</p>
                  <p className="small" style={{ marginTop: 0 }}>{selectedFormation.justification}</p>
                </>
              )}
              {selectedFormation.commentaire_rh && (
                <div className="callout">
                  <p className="callout__title">Commentaire RH</p>
                  <p>{selectedFormation.commentaire_rh}</p>
                </div>
              )}
            </div>

            <div className="modal__actions">
              <button type="button" className="btn btn--secondary" onClick={closeDetailModal}>Fermer</button>
            </div>
          </DialogPanel>
        </div>
      )}

      {action && (
        <div className="modal-backdrop" role="presentation" onClick={() => setAction(null)}>
          <DialogPanel
            label={action.kind === 'integrer' ? 'Accepter la demande' : 'Refuser la demande'}
            className="modal"
            onClose={() => setAction(null)}
          >
            <h3 className="modal__title">
              {action.kind === 'integrer' ? 'Accepter la demande' : 'Refuser la demande'}
            </h3>
            <p className="small muted" style={{ marginTop: 0 }}>
              {action.demande.type_formation} · {demandeur(action.demande)}
            </p>
            <label htmlFor="formation-action-text" className="field-label">
              {action.kind === 'integrer' ? 'Commentaire RH (optionnel)' : 'Motif du refus (obligatoire)'}
            </label>
            <textarea
              id="formation-action-text"
              className="field-input field-input--area"
              rows={4}
              value={actionText}
              onChange={(e) => setActionText(e.target.value)}
              aria-invalid={!!actionErr}
              aria-describedby={actionErr ? 'formation-action-err' : undefined}
            />
            {actionErr ? (
              <p id="formation-action-err" className="alert alert--error" role="alert">{actionErr}</p>
            ) : null}
            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setAction(null)}>Annuler</button>
              <button
                type="button"
                className={`btn ${action.kind === 'refuser' ? 'btn--danger' : 'btn--primary'}`}
                disabled={acting}
                aria-busy={acting}
                onClick={() => void confirmAction()}
              >
                {acting ? 'Envoi…' : action.kind === 'integrer' ? 'Accepter' : 'Refuser'}
              </button>
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}

function labelStatut(statut: string) {
  return libelle('statut', statut);
}

function formatTnd(v?: number | null) {
  return v === null || v === undefined ? '—' : `${formatNombre(v, 2)} TND`;
}

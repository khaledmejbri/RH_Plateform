import { useEffect, useState } from 'react';
import {
  getDemandesFormations,
  postFormationIntegrerPlan,
  postFormationRefuser,
  type DemandeFormation,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { DialogPanel } from '../components/useDialogA11y';
import { EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, formatDateTime } from '../components/ui';

type StatutFilter = '' | 'EN_VALIDATION_RRH' | 'INTEGREE_PLAN' | 'REFUSEE' | 'ANNULEE';

export default function FormationsPage() {
  const lectureSeule = useLectureSeule();
  const [rows, setRows] = useState<DemandeFormation[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [statut, setStatut] = useState<StatutFilter>('EN_VALIDATION_RRH');
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

  async function confirmAction() {
    if (!action) return;
    const text = actionText.trim();
    if (action.kind === 'refuser' && !text) {
      setActionErr('Le motif de refus est obligatoire.');
      return;
    }
    setActing(true);
    setActionErr(null);
    try {
      if (action.kind === 'integrer') {
        await postFormationIntegrerPlan(action.demande.identifiant, text || undefined);
        setMsg('Demande intégrée au plan annuel.');
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

  const cibleLabel = (d: DemandeFormation) =>
    d.cible === 'UNITE'
      ? d.unite_cible_libelle ?? 'Unité'
      : `${d.collaborateurs_cibles_identifiants?.length ?? 0} collaborateur(s)`;

  return (
    <div className="page">
      <PageHeader
        title="Formations"
        lead="Validation RH des demandes et intégration au plan annuel."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      <div className="toolbar-filters">
        <div>
          <label htmlFor="formations-statut" className="field-label">Statut</label>
          <select id="formations-statut" className="field-input" value={statut} onChange={(e) => setStatut(e.target.value as StatutFilter)}>
            <option value="">Tous les statuts</option>
            <option value="EN_VALIDATION_RRH">En attente RH</option>
            <option value="INTEGREE_PLAN">Intégrées au plan</option>
            <option value="REFUSEE">Refusées</option>
            <option value="ANNULEE">Annulées</option>
          </select>
        </div>
      </div>

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des demandes de formation…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="graduation" title="Aucune demande de formation">
            Aucune demande ne correspond à ce statut.
          </EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Demandes de formation</caption>
              <thead>
                <tr>
                  <th scope="col">Formation</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col">Cible</th>
                  <th scope="col">Budget</th>
                  <th scope="col">Statut</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.identifiant}>
                    <td data-label="Formation" className="cell-main">
                      <div className="cell-main__title">{d.type_formation}</div>
                      <div className="cell-main__sub">{d.organisme} · {d.duree_heures} h</div>
                    </td>
                    <td data-label="Demandeur">
                      <div>{d.demandeur_nom ?? d.demandeur_identifiant}</div>
                      <div className="cell-meta">{d.origine === 'CHEF_DEPARTEMENT' ? 'Chef de département' : 'Responsable opérationnel'}</div>
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
                              Intégrer au plan
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
                <span className="cell-meta">{selectedFormation.organisme}</span>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={closeDetailModal}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <dl className="detail-grid">
                <div><dt>Durée</dt><dd>{selectedFormation.duree_heures} heures</dd></div>
                <div><dt>Budget</dt><dd>{formatTnd(selectedFormation.cout_estime)}</dd></div>
                <div><dt>Demandeur</dt><dd>{selectedFormation.demandeur_nom ?? selectedFormation.demandeur_identifiant}</dd></div>
                <div><dt>Origine</dt><dd>{selectedFormation.origine === 'CHEF_DEPARTEMENT' ? 'Chef de département' : 'Responsable opérationnel'}</dd></div>
                <div><dt>Cible</dt><dd>{cibleLabel(selectedFormation)}</dd></div>
                <div><dt>Statut</dt><dd><StatusBadge statut={selectedFormation.statut} label={labelStatut(selectedFormation.statut)} /></dd></div>
                <div><dt>Créée le</dt><dd>{formatDateTime(selectedFormation.cree_le)}</dd></div>
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
            label={action.kind === 'integrer' ? 'Intégrer au plan annuel' : 'Refuser la demande'}
            className="modal"
            onClose={() => setAction(null)}
          >
            <h3 className="modal__title">
              {action.kind === 'integrer' ? 'Intégrer au plan annuel' : 'Refuser la demande'}
            </h3>
            <p className="small muted" style={{ marginTop: 0 }}>
              {action.demande.type_formation} · {action.demande.demandeur_nom ?? action.demande.demandeur_identifiant}
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
                {acting ? 'Envoi…' : action.kind === 'integrer' ? 'Intégrer' : 'Refuser'}
              </button>
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}

function labelStatut(statut: string) {
  switch (statut) {
    case 'INTEGREE_PLAN': return 'Intégrée au plan';
    case 'REFUSEE': return 'Refusée';
    case 'ANNULEE': return 'Annulée';
    default: return 'En attente RH';
  }
}

function formatTnd(v?: number | null) {
  return `${(v ?? 0).toLocaleString('fr-FR')} TND`;
}

import { useEffect, useState } from 'react';
import {
  getDocumentsFileAttente,
  postDocumentDisponible,
  postPrendreProchaineDocument,
  postDocumentRejet,
  type DemandeDocument,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { DialogPanel } from '../components/useDialogA11y';
import { Chip, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, formatDateTime } from '../components/ui';

export default function DocumentsAdministratifsPage() {
  const lectureSeule = useLectureSeule();
  const [rows, setRows]                   = useState<DemandeDocument[]>([]);
  const [loading, setLoading]             = useState(true);
  const [err, setErr]                     = useState<string | null>(null);
  const [msg, setMsg]                     = useState<string | null>(null);
  const [pickedDemande, setPickedDemande] = useState<DemandeDocument | null>(null);
  const [actionMode, setActionMode]       = useState<'info' | 'validate' | 'reject' | null>(null);
  const [ref, setRef]                     = useState('');
  const [comment, setComment]             = useState('');

  // ── FIFO override state ──
  const [fifoJustification, setFifoJustification] = useState('');
  const [showFifoWarning, setShowFifoWarning]     = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try { setRows(await getDocumentsFileAttente()); }
    catch (e) { setErr(e instanceof Error ? e.message : 'Erreur'); }
    finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  /** Hors ordre FIFO = encore en attente et pas la prochaine (pas une demande déjà en traitement). */
  function isOutOfFifoOrder(d: DemandeDocument | null): boolean {
    if (!d) return false;
    if (d.statut === 'EN_TRAITEMENT_RH') return false;
    if (d.statut !== 'EN_ATTENTE_FILE') return false;
    return d.est_prochaine_fifo === false;
  }

  async function prendreProchaine() {
    setMsg(null); setErr(null);
    try {
      const d = await postPrendreProchaineDocument();
      setPickedDemande(d);
      setActionMode('info');
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : 'Erreur'); }
  }

  async function marquerDisponible() {
    if (!pickedDemande || !ref.trim()) return;
    setErr(null);
    try {
      await postDocumentDisponible(
        pickedDemande.identifiant,
        ref.trim(),
        comment.trim() || undefined,
        showFifoWarning ? fifoJustification.trim() || undefined : undefined,
      );
      closeModal();
      setMsg('Document marqué comme disponible.');
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : 'Erreur'); }
  }

  async function rejeterDemande() {
    if (!pickedDemande || !comment.trim()) {
      setErr('Veuillez saisir un motif de refus.');
      return;
    }
    setErr(null);
    try {
      await postDocumentRejet(
        pickedDemande.identifiant,
        comment.trim(),
        showFifoWarning ? fifoJustification.trim() || undefined : undefined,
      );
      closeModal();
      setMsg('Demande refusée.');
      await load();
    } catch (e) { setErr(e instanceof Error ? e.message : 'Erreur'); }
  }

  function closeModal() {
    setPickedDemande(null);
    setActionMode(null);
    setRef('');
    setComment('');
    setFifoJustification('');
    setShowFifoWarning(false);
    setErr(null);
  }

  function openInfo(d: DemandeDocument) {
    setPickedDemande(d);
    setActionMode('info');
    setFifoJustification('');
    const outOfOrder = isOutOfFifoOrder(d);
    setShowFifoWarning(outOfOrder);
  }

  /** Is the action button (validate/reject confirmation) disabled due to FIFO? */
  const fifoBlocked = showFifoWarning && !fifoJustification.trim();

  /** Prevent taking a new request if one is already being processed */
  const hasPendingTraitement = rows.some(r => r.statut === 'EN_TRAITEMENT_RH');

  /** Une autre demande est en cours : on ne peut pas traiter celle-ci. */
  const blockedByOther = hasPendingTraitement && pickedDemande?.statut !== 'EN_TRAITEMENT_RH';

  return (
    <div className="page">
      <PageHeader
        title="Documents administratifs"
        lead="Traitement des attestations, bulletins et courriers RH."
        actions={
          <>
            <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
              <Icon name="refresh" />
              Actualiser
            </button>
            {!lectureSeule ? (
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => void prendreProchaine()}
                disabled={hasPendingTraitement || loading}
                aria-describedby={hasPendingTraitement ? 'docs-pending-hint' : undefined}
              >
                <Icon name={hasPendingTraitement ? 'lock' : 'plus'} />
                {hasPendingTraitement ? 'Traitement en cours' : 'Prendre la prochaine demande'}
              </button>
            ) : null}
          </>
        }
      />

      {err && !pickedDemande ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      <div className="callout" id="docs-pending-hint">
        <p className="callout__title"><Icon name="info" size={14} /> Ordre FIFO actif</p>
        <p className="muted">
          Les demandes sont traitées dans l’ordre d’arrivée : la demande marquée « Suivante » est la prochaine à prendre.
          {hasPendingTraitement ? ' Terminez la demande en cours avant d’en prendre une nouvelle.' : ''}
        </p>
      </div>

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement de la file…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="file" title="Aucune demande en attente">
            La file est vide : les nouvelles demandes de documents apparaîtront ici.
          </EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">File d’attente des demandes de documents</caption>
              <thead>
                <tr>
                  <th scope="col">Rang</th>
                  <th scope="col">Demandée le</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col">Type de document</th>
                  <th scope="col">Statut</th>
                  <th scope="col">SLA</th>
                  <th scope="col">Retard</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => {
                  const isNext = d.est_prochaine_fifo === true;
                  const enCours = d.statut === 'EN_TRAITEMENT_RH';
                  return (
                    <tr key={d.identifiant} className={isNext ? 'row--next' : undefined}>
                      <td data-label="Rang">
                        {d.rang_dans_file != null ? (
                          <span className="rank">
                            <span className={`rank__num${isNext ? ' rank__num--next' : ''}`}>{d.rang_dans_file}</span>
                            {enCours ? <span className="status status--info">En cours</span> : null}
                            {isNext ? <span className="status status--success">Suivante</span> : null}
                          </span>
                        ) : (
                          <span className="cell-meta">—</span>
                        )}
                      </td>
                      <td data-label="Demandée le" className="cell-meta">{formatDateTime(d.cree_le)}</td>
                      <td data-label="Demandeur">
                        <div>{d.demandeur_nom ?? '—'}</div>
                        {d.demandeur_matricule ? <div className="cell-meta">{d.demandeur_matricule}</div> : null}
                      </td>
                      <td data-label="Type"><Chip tone="info" icon="file">{libelleTypeDocument(d.type_document)}</Chip></td>
                      <td data-label="Statut"><StatusBadge statut={d.statut} label={docStatusLabel(d.statut)} /></td>
                      <td data-label="SLA" className="cell-meta">{d.delai_sla_heures} h</td>
                      <td data-label="Retard">
                        {d.en_retard
                          ? <StatusBadge statut="REJETEE" label="En retard" />
                          : <span className="cell-meta">—</span>}
                      </td>
                      <td data-label="Action" style={{ textAlign: 'right' }}>
                        <button type="button" className="btn btn--sm btn--secondary" onClick={() => openInfo(d)}>
                          Voir le détail
                          <Icon name="chevronRight" size={14} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {pickedDemande && actionMode && (
        <div className="modal-backdrop" role="presentation" onClick={closeModal}>
          <DialogPanel label="Traitement de la demande" className="modal modal--md" onClose={closeModal}>
            <div className="modal__head">
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>
                  {actionMode === 'validate' ? 'Valider le document' : actionMode === 'reject' ? 'Refuser la demande' : 'Détail de la demande'}
                </h3>
                <code className="cell-code">{pickedDemande.identifiant.slice(0, 8)}…</code>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={closeModal}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              {showFifoWarning && (
                <div className="alert alert--warning" role="alert" style={{ display: 'block' }}>
                  <p className="callout__title" style={{ color: 'inherit' }}>
                    <Icon name="alert" size={14} /> Ordre FIFO non respecté
                  </p>
                  <p style={{ margin: '0 0 10px' }}>
                    Des demandes plus anciennes attendent encore. Justifiez pourquoi celle-ci doit passer en priorité.
                  </p>
                  <label htmlFor="documentsadministratifs-f1" className="field-label">Justification de la dérogation (obligatoire)</label>
                  <textarea
                    id="documentsadministratifs-f1"
                    className="field-input field-input--area"
                    rows={3}
                    value={fifoJustification}
                    onChange={(e) => setFifoJustification(e.target.value)}
                    placeholder="Ex. contrat requis aujourd’hui, besoin administratif critique…"
                    aria-invalid={!fifoJustification.trim()}
                    style={{ marginBottom: 0 }}
                  />
                </div>
              )}

              <dl className="detail-grid">
                <div><dt>Type de document</dt><dd><Chip tone="info" icon="file">{libelleTypeDocument(pickedDemande.type_document)}</Chip></dd></div>
                <div><dt>Statut</dt><dd><StatusBadge statut={pickedDemande.statut} label={docStatusLabel(pickedDemande.statut)} /></dd></div>
                <div>
                  <dt>Demandeur</dt>
                  <dd>
                    {pickedDemande.demandeur_nom ?? '—'}
                    {pickedDemande.demandeur_matricule ? <span className="cell-meta"> · {pickedDemande.demandeur_matricule}</span> : null}
                  </dd>
                </div>
                <div><dt>Département</dt><dd>{pickedDemande.departement_libelle ?? 'Non renseigné'}</dd></div>
                <div><dt>Date de la demande</dt><dd>{formatDateTime(pickedDemande.cree_le)}</dd></div>
                <div>
                  <dt>Rang FIFO</dt>
                  <dd>{pickedDemande.rang_dans_file != null ? `${pickedDemande.rang_dans_file}${pickedDemande.est_prochaine_fifo ? ' (suivante)' : ''}` : '—'}</dd>
                </div>
                <div><dt>SLA</dt><dd>{pickedDemande.delai_sla_heures} h</dd></div>
                <div>
                  <dt>Retard</dt>
                  <dd>{pickedDemande.en_retard ? <StatusBadge statut="REJETEE" label="En retard" /> : 'Non'}</dd>
                </div>
              </dl>

              {pickedDemande.commentaire_demandeur && (
                <div className="callout">
                  <p className="callout__title">Motif de la demande</p>
                  <p>{pickedDemande.commentaire_demandeur}</p>
                </div>
              )}
              {pickedDemande.justification_derogation_fifo && (
                <div className="callout">
                  <p className="callout__title">Dérogation FIFO appliquée</p>
                  <p>{pickedDemande.justification_derogation_fifo}</p>
                </div>
              )}

              {actionMode === 'validate' && (
                <>
                  <label htmlFor="documentsadministratifs-f2" className="field-label">Référence du document (obligatoire)</label>
                  <input id="documentsadministratifs-f2" className="field-input" value={ref} onChange={(e) => setRef(e.target.value)}
                    placeholder="Ex. numéro, lien, chemin…" autoFocus />
                  <label htmlFor="documentsadministratifs-f3" className="field-label">Commentaire (optionnel)</label>
                  <textarea id="documentsadministratifs-f3" className="field-input field-input--area" rows={3}
                    value={comment} onChange={(e) => setComment(e.target.value)} />
                </>
              )}

              {actionMode === 'reject' && (
                <>
                  <label htmlFor="documentsadministratifs-f4" className="field-label">Motif du refus (obligatoire)</label>
                  <textarea id="documentsadministratifs-f4" className="field-input field-input--area" rows={3}
                    value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Expliquez la raison du refus…" autoFocus />
                </>
              )}
            </div>

            {err ? <ErrorAlert>{err}</ErrorAlert> : null}
            {!lectureSeule && actionMode === 'info' && blockedByOther ? (
              <p className="cell-meta" style={{ whiteSpace: 'normal' }}>
                <Icon name="lock" size={12} /> Terminez d’abord la demande en cours de traitement.
              </p>
            ) : null}

            <div className="modal__actions" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
              {actionMode === 'info' ? (
                <>
                  <div>
                    {!lectureSeule ? (
                      <button type="button" className="btn btn--ghost" disabled={blockedByOther} onClick={() => setActionMode('reject')}>
                        Refuser
                      </button>
                    ) : null}
                  </div>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <button type="button" className="btn btn--secondary" onClick={closeModal}>Fermer</button>
                    {!lectureSeule ? (
                      <button type="button" className="btn btn--primary" disabled={blockedByOther} onClick={() => setActionMode('validate')}>
                        Valider le document
                      </button>
                    ) : null}
                  </div>
                </>
              ) : null}

              {!lectureSeule && actionMode === 'validate' ? (
                <>
                  <button type="button" className="btn btn--secondary" onClick={() => setActionMode('info')}>Retour</button>
                  <button type="button" className="btn btn--primary" disabled={!ref.trim() || fifoBlocked} onClick={() => void marquerDisponible()}>
                    {fifoBlocked ? 'Justification requise' : 'Confirmer la validation'}
                  </button>
                </>
              ) : null}

              {!lectureSeule && actionMode === 'reject' ? (
                <>
                  <button type="button" className="btn btn--secondary" onClick={() => setActionMode('info')}>Retour</button>
                  <button type="button" className="btn btn--danger" disabled={!comment.trim() || fifoBlocked} onClick={() => void rejeterDemande()}>
                    {fifoBlocked ? 'Justification requise' : 'Confirmer le refus'}
                  </button>
                </>
              ) : null}
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}

/** Libellés métier § 7.1 (SCRUM-15) — le code brut reste affiché pour un type inconnu. */
const LIBELLES_TYPE_DOCUMENT: Record<string, string> = {
  ATTESTATION_TRAVAIL: 'Attestation de travail',
  ATTESTATION_SALAIRE: 'Attestation de salaire',
  BULLETIN_PAIE: 'Bulletin de paie',
  ATTESTATION_CNSS: 'Attestation CNSS',
  FEUILLE_POINTAGE_MENSUELLE: 'Feuille de pointage mensuelle',
  DOCUMENT_INTERNE: 'Document interne',
  AUTRE: 'Autre',
};

function libelleTypeDocument(code: string): string {
  return LIBELLES_TYPE_DOCUMENT[code] ?? code;
}

function docStatusLabel(statut: string): string | undefined {
  switch (statut.toUpperCase()) {
    case 'EN_TRAITEMENT_RH': return 'En traitement';
    case 'EN_ATTENTE_FILE': return 'En attente';
    case 'DISPONIBLE': return 'Disponible';
    case 'REJETEE': return 'Refusée';
    default: return undefined;
  }
}

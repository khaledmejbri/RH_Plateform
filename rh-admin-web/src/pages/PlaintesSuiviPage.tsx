import { useEffect, useState } from 'react';
import { getPlaintesRh, patchPlainteStatut, type PlainteRh } from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { DialogPanel } from '../components/useDialogA11y';
import { Chip, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, formatDateTime } from '../components/ui';

/**
 * CDC v2 §M04 corrections :
 * - Filtres par type (INTERNE / EXTERNE) et statut
 * - Affichage numero_ticket, pieces_jointes, log_actions
 * - Transitions statut validées (choix limités selon statut courant)
 * - Commentaire RH obligatoire affiché si RESOLU ou FERME
 * - Badge INTERNE / EXTERNE avec couleur distincte
 */

type TypePlainte = 'INTERNE' | 'EXTERNE' | '';
type StatutPlainte = 'NOUVEAU' | 'EN_ANALYSE' | 'EN_TRAITEMENT' | 'RESOLU' | 'FERME' | '';

/** CDC §M04 : transitions autorisées côté front (miroir de la validation back). */
const TRANSITIONS: Record<string, string[]> = {
  NOUVEAU:        ['EN_ANALYSE'],
  EN_ANALYSE:     ['EN_TRAITEMENT'],
  EN_TRAITEMENT:  ['RESOLU'],
  RESOLU:         ['FERME'],
  FERME:          [],
};

export default function PlaintesSuiviPage() {
  const lectureSeule = useLectureSeule();
  const [rows, setRows]         = useState<PlainteRh[]>([]);
  const [loading, setLoading]   = useState(true);
  const [err, setErr]           = useState<string | null>(null);
  const [filterType, setFilterType]     = useState<TypePlainte>('');
  const [filterStatut, setFilterStatut] = useState<StatutPlainte>('');

  const [edit, setEdit]         = useState<PlainteRh | null>(null);
  /** Détail consultable par tous (Direction, plaintes clôturées) ; formulaire de transition seulement si RH + transition possible. */
  const peutTraiter = !!edit && !lectureSeule && (TRANSITIONS[edit.statut]?.length ?? 0) > 0;
  const [newStatut, setNewStatut] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [saving, setSaving]     = useState(false);
  const [saveErr, setSaveErr]   = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getPlaintesRh(filterType || undefined, filterStatut || undefined));
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur chargement');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [filterType, filterStatut]);

  function openEdit(p: PlainteRh) {
    const opts = TRANSITIONS[p.statut] ?? [];
    setEdit(p);
    setNewStatut(opts[0] ?? p.statut);
    setCommentaire(p.commentaire_rh ?? '');
    setSaveErr(null);
  }

  async function saveEdit() {
    if (!edit) return;
    const commentaireRequired = newStatut === 'RESOLU' || newStatut === 'FERME';
    if (commentaireRequired && !commentaire.trim()) {
      setSaveErr('Un commentaire RH est obligatoire pour ce statut.');
      return;
    }
    setSaving(true);
    setSaveErr(null);
    try {
      await patchPlainteStatut(edit.identifiant, {
        statut: newStatut,
        commentaire_rh: commentaire.trim() || undefined,
      });
      setEdit(null);
      void load();
    } catch (e) {
      setSaveErr(e instanceof Error ? e.message : 'Erreur enregistrement');
    } finally {
      setSaving(false);
    }
  }

  const STATUT_LABELS: Record<string, string> = {
    NOUVEAU: 'Nouveau', EN_ANALYSE: 'En analyse',
    EN_TRAITEMENT: 'En traitement', RESOLU: 'Résolu', FERME: 'Fermé',
  };

  return (
    <div className="page">
      <PageHeader
        title="Suivi des plaintes"
        lead="Interne → RH uniquement · Externe → RH, Services techniques et Direction E&S."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <div className="toolbar-filters">
        <div>
          <label htmlFor="plaintessuivi-f1" className="field-label">Type</label>
          <select id="plaintessuivi-f1" className="field-input"
            value={filterType} onChange={(e) => setFilterType(e.target.value as TypePlainte)}>
            <option value="">Tous les types</option>
            <option value="INTERNE">Interne</option>
            <option value="EXTERNE">Externe</option>
          </select>
        </div>
        <div>
          <label htmlFor="plaintessuivi-f2" className="field-label">Statut</label>
          <select id="plaintessuivi-f2" className="field-input"
            value={filterStatut} onChange={(e) => setFilterStatut(e.target.value as StatutPlainte)}>
            <option value="">Tous les statuts</option>
            {Object.entries(STATUT_LABELS).map(([v, l]) => (
              <option key={v} value={v}>{l}</option>
            ))}
          </select>
        </div>
        <span className="cell-meta" aria-live="polite">
          {rows.length} plainte{rows.length !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des plaintes…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="inbox" title="Aucune plainte">Aucune plainte ne correspond à ces filtres.</EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Plaintes</caption>
              <thead>
                <tr>
                  <th scope="col">Ticket</th>
                  <th scope="col">Date</th>
                  <th scope="col">Type</th>
                  <th scope="col">Auteur</th>
                  <th scope="col">Titre</th>
                  <th scope="col">PJ</th>
                  <th scope="col">Statut</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.identifiant}>
                    <td data-label="Ticket"><code className="cell-code">{p.numero_ticket}</code></td>
                    <td data-label="Date" className="cell-meta">{formatDateTime(p.cree_le)}</td>
                    <td data-label="Type"><TypeChip type={p.type_plainte} /></td>
                    <td data-label="Auteur">
                      <div>{p.auteur_nom ?? '—'}</div>
                      {p.auteur_matricule ? <div className="cell-meta">{p.auteur_matricule}</div> : null}
                    </td>
                    <td data-label="Titre" className="cell-main">
                      <div className="cell-main__title">{p.titre}</div>
                      <div className="cell-main__sub">{p.description}</div>
                    </td>
                    <td data-label="PJ">
                      {(p.pieces_jointes?.length ?? 0) > 0 ? (
                        <span className="cell-meta" title={`${p.pieces_jointes.length} fichier(s)`}>
                          <Icon name="paperclip" size={14} /> {p.pieces_jointes.length}
                        </span>
                      ) : (
                        <span className="cell-meta">—</span>
                      )}
                    </td>
                    <td data-label="Statut"><StatusBadge statut={p.statut} /></td>
                    <td data-label="Action" className="cell-actions">
                      <button type="button" className="btn btn--sm btn--secondary" onClick={() => openEdit(p)}>
                        {!lectureSeule && (TRANSITIONS[p.statut]?.length ?? 0) > 0 ? 'Traiter' : 'Voir le détail'}
                        <Icon name="chevronRight" size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {edit && (
        <div className="modal-backdrop" role="presentation" onClick={() => setEdit(null)}>
          <DialogPanel label={`Traiter — ${edit.titre}`} className="modal modal--md" onClose={() => setEdit(null)}>
            <div className="modal__head">
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>{edit.titre}</h3>
                <code className="cell-code">{edit.numero_ticket}</code>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={() => setEdit(null)}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                <TypeChip type={edit.type_plainte} />
                <StatusBadge statut={edit.statut} />
              </div>

              <dl className="detail-grid">
                <div>
                  <dt>Auteur</dt>
                  <dd>{edit.auteur_nom ?? '—'}{edit.auteur_matricule ? ` (${edit.auteur_matricule})` : ''}</dd>
                </div>
                <div><dt>Déposée le</dt><dd>{formatDateTime(edit.cree_le)}</dd></div>
                {edit.type_plainte === 'EXTERNE' ? (
                  <div>
                    <dt>Validation RO</dt>
                    <dd><StatusBadge statut={VALIDATION_RO[edit.validation_ro ?? '']?.statut ?? 'EN_ATTENTE_VALIDATION'}
                      label={VALIDATION_RO[edit.validation_ro ?? '']?.label ?? '—'} /></dd>
                  </div>
                ) : null}
              </dl>

              <p className="small" style={{ marginTop: 0 }}>{edit.description}</p>

              {(edit.pieces_jointes?.length ?? 0) > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <p className="section-title">Pièces jointes ({edit.pieces_jointes.length})</p>
                  <div className="attachments">
                    {edit.pieces_jointes.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="attachment">
                        <Icon name="paperclip" size={14} />
                        Fichier {i + 1}
                      </a>
                    ))}
                  </div>
                </div>
              )}

              {edit.transcription_audio && (
                <div className="callout callout--info">
                  <p className="callout__title"><Icon name="mic" size={14} /> Transcription vocale</p>
                  <p>{edit.transcription_audio}</p>
                </div>
              )}

              {peutTraiter ? (<>
              <label htmlFor="plaintessuivi-f3" className="field-label">Nouveau statut</label>
              <select id="plaintessuivi-f3" className="field-input" value={newStatut} onChange={(e) => setNewStatut(e.target.value)}
                aria-describedby="plainte-transition-help">
                {(TRANSITIONS[edit.statut] ?? []).map((s) => (
                  <option key={s} value={s}>{STATUT_LABELS[s] ?? s}</option>
                ))}
              </select>
              <p id="plainte-transition-help" className="cell-meta" style={{ marginTop: -8, marginBottom: 14 }}>
                Transition : {STATUT_LABELS[edit.statut]} → {STATUT_LABELS[newStatut] ?? newStatut}
              </p>

              <label htmlFor="plaintessuivi-f4" className="field-label">
                Commentaire RH{' '}
                {(newStatut === 'RESOLU' || newStatut === 'FERME')
                  ? <span style={{ color: 'var(--color-destructive)' }}>(obligatoire)</span>
                  : <span className="muted">(optionnel)</span>}
              </label>
              <textarea id="plaintessuivi-f4"
                className="field-input field-input--area"
                rows={4}
                value={commentaire}
                onChange={(e) => setCommentaire(e.target.value)}
                placeholder="Message visible dans le suivi collaborateur…"
                aria-invalid={!!saveErr}
              />
              </>) : null}

              {/* § 9.2 : journal d'actions — qui a changé le statut, quand, commentaire (SCRUM-13). */}
              <div style={{ marginBottom: 8 }}>
                <p className="section-title">Journal</p>
                <ol className="timeline">
                  <li>
                    <strong>Dépôt de la plainte</strong>
                    <span className="timeline__meta">{formatDateTime(edit.cree_le)}{edit.auteur_nom ? ` · ${edit.auteur_nom}` : ''}</span>
                  </li>
                  {(edit.log_actions ?? []).map((log, i) => (
                    <li key={i}>
                      <strong>
                        {log.action === 'VALIDATION_RO' ? 'Validation RO · ' : ''}
                        {STATUT_LABELS[log.ancien_statut ?? ''] ?? log.ancien_statut} → {STATUT_LABELS[log.nouveau_statut ?? ''] ?? log.nouveau_statut}
                      </strong>
                      <span className="timeline__meta">
                        {log.horodatage ? formatDateTime(log.horodatage) : ''}
                        {' · '}{log.acteur_nom ?? (log.acteur_id === 'SYSTEME' || !log.acteur_id ? 'Acteur non tracé' : log.acteur_id)}
                      </span>
                      {log.commentaire && <div className="muted">{log.commentaire}</div>}
                    </li>
                  ))}
                </ol>
              </div>
            </div>

            {saveErr ? <ErrorAlert>{saveErr}</ErrorAlert> : null}

            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setEdit(null)}>{peutTraiter ? 'Annuler' : 'Fermer'}</button>
              {peutTraiter ? (
                <button type="button" className="btn btn--primary" disabled={saving} aria-busy={saving} onClick={() => void saveEdit()}>
                  {saving ? 'Enregistrement…' : 'Confirmer la transition'}
                </button>
              ) : null}
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}

const VALIDATION_RO: Record<string, { statut: string; label: string }> = {
  EN_ATTENTE: { statut: 'EN_ATTENTE_VALIDATION', label: 'En attente du RO' },
  VALIDEE: { statut: 'VALIDE', label: 'Validée par le RO' },
  NON_REQUISE: { statut: 'TERMINEE', label: 'Prise en charge directe RH' },
};

function TypeChip({ type }: { type: string }) {
  return type === 'EXTERNE'
    ? <Chip tone="warning" icon="building">Externe</Chip>
    : <Chip tone="info" icon="lock">Interne</Chip>;
}

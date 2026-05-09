import { useEffect, useState } from 'react';
import { getPlaintesList, patchPlainteStatut, type PlainteRh } from '../api/rhClient';

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
  const [rows, setRows]         = useState<PlainteRh[]>([]);
  const [loading, setLoading]   = useState(true);
  const [err, setErr]           = useState<string | null>(null);
  const [filterType, setFilterType]     = useState<TypePlainte>('');
  const [filterStatut, setFilterStatut] = useState<StatutPlainte>('');

  const [edit, setEdit]         = useState<PlainteRh | null>(null);
  const [newStatut, setNewStatut] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [saving, setSaving]     = useState(false);
  const [saveErr, setSaveErr]   = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getPlaintesList(filterType || undefined, filterStatut || undefined));
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
      {/* En-tête */}
      <div className="page__head" style={{ marginBottom: '1rem' }}>
        <div>
          <h2 className="page__title">Suivi des plaintes</h2>
          <p className="page__lead">
            Interne → RH uniquement · Externe → RH + Services Techniques + Direction E&S
          </p>
        </div>
        <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
          Actualiser
        </button>
      </div>

      {err && <div className="alert alert--error">{err}</div>}

      {/* Filtres */}
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <div>
          <label className="field-label" style={{ marginBottom: 4 }}>Type</label>
          <select className="field-input" style={{ minWidth: 160 }}
            value={filterType} onChange={(e) => setFilterType(e.target.value as TypePlainte)}>
            <option value="">Tous les types</option>
            <option value="INTERNE">🔒 Interne</option>
            <option value="EXTERNE">🌐 Externe</option>
          </select>
        </div>
        <div>
          <label className="field-label" style={{ marginBottom: 4 }}>Statut</label>
          <select className="field-input" style={{ minWidth: 180 }}
            value={filterStatut} onChange={(e) => setFilterStatut(e.target.value as StatutPlainte)}>
            <option value="">Tous les statuts</option>
            {Object.entries(STATUT_LABELS).map(([v, l]) => (
              <option key={v} value={v}>{l}</option>
            ))}
          </select>
        </div>
        <div style={{ alignSelf: 'flex-end' }}>
          <span className="muted" style={{ fontSize: 13 }}>
            {rows.length} plainte{rows.length !== 1 ? 's' : ''}
          </span>
        </div>
      </div>

      <div className="panel">
        {loading ? (
          <p className="muted">Chargement…</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Titre</th>
                  <th>PJ</th>
                  <th>Statut</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.identifiant}>
                    <td style={{ fontFamily: 'monospace', fontSize: 12, fontWeight: 600, color: '#2563eb' }}>
                      {p.numero_ticket}
                    </td>
                    <td className="muted">{formatDate(p.cree_le)}</td>
                    <td>
                      <span style={{
                        display: 'inline-block', padding: '3px 10px', borderRadius: 999,
                        fontSize: 11, fontWeight: 700,
                        background: p.type_plainte === 'EXTERNE' ? '#fff7ed' : '#eff6ff',
                        color: p.type_plainte === 'EXTERNE' ? '#ea580c' : '#1d4ed8',
                      }}>
                        {p.type_plainte === 'EXTERNE' ? '🌐 Externe' : '🔒 Interne'}
                      </span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>{p.titre}</div>
                      <div className="muted" style={{ fontSize: 11, marginTop: 2 }}>
                        {p.description.slice(0, 80)}{p.description.length > 80 ? '…' : ''}
                      </div>
                    </td>
                    <td>
                      {p.pieces_jointes.length > 0 ? (
                        <span title={`${p.pieces_jointes.length} fichier(s)`} style={{ fontSize: 13 }}>
                          📎 {p.pieces_jointes.length}
                        </span>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td>
                      <span className={`pill pill--${pillVariant(p.statut)}`}>
                        {STATUT_LABELS[p.statut] ?? p.statut}
                      </span>
                    </td>
                    <td>
                      {(TRANSITIONS[p.statut]?.length ?? 0) > 0 ? (
                        <button type="button" className="btn btn--sm btn--ghost" onClick={() => openEdit(p)}>
                          Traiter →
                        </button>
                      ) : (
                        <span className="muted" style={{ fontSize: 12 }}>Clôturé</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 && <p className="muted pad">Aucune plainte pour ces filtres.</p>}
          </div>
        )}
      </div>

      {/* Modal traitement */}
      {edit && (
        <div className="modal-backdrop" role="presentation" onClick={() => setEdit(null)}>
          <div className="modal" role="dialog" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>Traiter — {edit.titre}</h3>
                <span style={{
                  fontSize: 11, fontFamily: 'monospace', color: '#2563eb', fontWeight: 700
                }}>{edit.numero_ticket}</span>
              </div>
              <span style={{
                padding: '3px 10px', borderRadius: 999, fontSize: 11, fontWeight: 700,
                background: edit.type_plainte === 'EXTERNE' ? '#fff7ed' : '#eff6ff',
                color: edit.type_plainte === 'EXTERNE' ? '#ea580c' : '#1d4ed8',
              }}>
                {edit.type_plainte === 'EXTERNE' ? '🌐 Externe' : '🔒 Interne'}
              </span>
            </div>

            <p className="muted small" style={{ marginBottom: 12 }}>{edit.description}</p>

            {/* Pièces jointes */}
            {edit.pieces_jointes.length > 0 && (
              <div style={{ marginBottom: 14 }}>
                <p style={{ fontSize: 12, fontWeight: 600, margin: '0 0 6px', color: '#64748b' }}>
                  Pièces jointes ({edit.pieces_jointes.length})
                </p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {edit.pieces_jointes.map((url, i) => (
                    <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                      style={{ fontSize: 12, padding: '3px 8px', background: '#f1f5f9', borderRadius: 6, color: '#2563eb' }}>
                      📎 Fichier {i + 1}
                    </a>
                  ))}
                </div>
              </div>
            )}

            {/* Transcription audio */}
            {edit.transcription_audio && (
              <div style={{ background: '#f5f3ff', border: '1px solid #e9d5ff', borderRadius: 10, padding: '10px 14px', marginBottom: 14 }}>
                <p style={{ fontSize: 11, fontWeight: 700, color: '#7c3aed', margin: '0 0 4px' }}>🎙 Transcription vocale</p>
                <p style={{ fontSize: 13, margin: 0 }}>{edit.transcription_audio}</p>
              </div>
            )}

            {/* Transition statut */}
            <label className="field-label">Nouveau statut</label>
            <select className="field-input" value={newStatut} onChange={(e) => setNewStatut(e.target.value)}>
              {(TRANSITIONS[edit.statut] ?? []).map((s) => (
                <option key={s} value={s}>{STATUT_LABELS[s] ?? s}</option>
              ))}
            </select>
            <p className="muted" style={{ fontSize: 12, marginTop: 4 }}>
              Transition autorisée : {STATUT_LABELS[edit.statut]} → {STATUT_LABELS[newStatut] ?? newStatut}
            </p>

            <label className="field-label">
              Commentaire RH {(newStatut === 'RESOLU' || newStatut === 'FERME') ? <span style={{ color: '#dc2626' }}>*</span> : '(optionnel)'}
            </label>
            <textarea
              className="field-input field-input--area"
              rows={4}
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
              placeholder="Message visible dans le suivi collaborateur…"
            />

            {/* Log d'actions */}
            {edit.log_actions.length > 0 && (
              <div style={{ marginBottom: 14 }}>
                <p style={{ fontSize: 12, fontWeight: 600, margin: '0 0 6px', color: '#64748b' }}>
                  Historique des actions
                </p>
                <div style={{ maxHeight: 140, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {edit.log_actions.map((log, i) => (
                    <div key={i} style={{ fontSize: 12, padding: '6px 10px', background: '#f8fafc', borderRadius: 8, border: '1px solid #e2e8f0' }}>
                      <span style={{ fontWeight: 600 }}>{log.ancien_statut} → {log.nouveau_statut}</span>
                      <span className="muted" style={{ marginLeft: 8 }}>{formatDate(log.horodatage)}</span>
                      {log.commentaire && <div style={{ marginTop: 2, color: '#475569' }}>{log.commentaire}</div>}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {saveErr && <div className="alert alert--error" style={{ marginBottom: 12 }}>{saveErr}</div>}

            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setEdit(null)}>Annuler</button>
              <button type="button" className="btn btn--primary" disabled={saving} onClick={() => void saveEdit()}>
                {saving ? 'Enregistrement…' : 'Confirmer la transition'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }); }
  catch { return iso; }
}

function pillVariant(s: string) {
  if (s === 'RESOLU' || s === 'FERME') return 'ok';
  if (s === 'NOUVEAU') return 'new';
  return 'prog';
}

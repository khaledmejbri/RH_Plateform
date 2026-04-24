import { useEffect, useState } from 'react';
import { getPlaintesRh, patchPlainteStatut, type PlainteRh } from '../api/rhClient';

const STATUTS = ['NOUVEAU', 'EN_ANALYSE', 'EN_TRAITEMENT', 'RESOLU', 'FERME'] as const;

export default function PlaintesSuiviPage() {
  const [rows, setRows] = useState<PlainteRh[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [edit, setEdit] = useState<PlainteRh | null>(null);
  const [statut, setStatut] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getPlaintesRh());
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function openEdit(p: PlainteRh) {
    setEdit(p);
    setStatut(p.statut);
    setCommentaire(p.commentaire_rh ?? '');
  }

  async function saveEdit() {
    if (!edit) return;
    setSaving(true);
    try {
      await patchPlainteStatut(edit.identifiant, {
        statut,
        commentaire_rh: commentaire || undefined,
      });
      setEdit(null);
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur enregistrement');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Suivi des plaintes</h2>
          <p className="page__lead">Liste des plaintes — traitement réservé au rôle RH.</p>
        </div>
        <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
          Actualiser
        </button>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}

      <div className="panel">
        {loading ? (
          <p className="muted">Chargement…</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Titre</th>
                  <th>Statut</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.identifiant}>
                    <td className="muted">{formatDate(p.cree_le)}</td>
                    <td>
                      <span className="tag">{p.type_plainte}</span>
                    </td>
                    <td>{p.titre}</td>
                    <td>
                      <span className={`pill pill--${pillVariant(p.statut)}`}>{p.statut}</span>
                    </td>
                    <td>
                      <button type="button" className="btn btn--sm btn--ghost" onClick={() => openEdit(p)}>
                        Traiter
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 ? <p className="muted pad">Aucune plainte.</p> : null}
          </div>
        )}
      </div>

      {edit ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setEdit(null)}>
          <div className="modal" role="dialog" onClick={(e) => e.stopPropagation()}>
            <h3 className="modal__title">Mise à jour — {edit.titre}</h3>
            <p className="muted small">{edit.description}</p>
            <label className="field-label">Statut</label>
            <select className="field-input" value={statut} onChange={(e) => setStatut(e.target.value)}>
              {STATUTS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            <label className="field-label">Commentaire RH</label>
            <textarea
              className="field-input field-input--area"
              rows={4}
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
              placeholder="Message visible dans le suivi…"
            />
            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setEdit(null)}>
                Annuler
              </button>
              <button type="button" className="btn btn--primary" disabled={saving} onClick={() => void saveEdit()}>
                {saving ? 'Enregistrement…' : 'Enregistrer'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function formatDate(iso: string) {
  try {
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  } catch {
    return iso;
  }
}

function pillVariant(s: string) {
  if (s === 'RESOLU' || s === 'FERME') return 'ok';
  if (s === 'NOUVEAU') return 'new';
  return 'prog';
}

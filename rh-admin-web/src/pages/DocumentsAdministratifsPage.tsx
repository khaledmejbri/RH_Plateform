import { useEffect, useState } from 'react';
import {
  getDocumentsFileAttente,
  postDocumentDisponible,
  postPrendreProchaineDocument,
  postDocumentRejet,
  type DemandeDocument,
} from '../api/rhClient';

export default function DocumentsAdministratifsPage() {
  const [rows, setRows]                   = useState<DemandeDocument[]>([]);
  const [loading, setLoading]             = useState(true);
  const [err, setErr]                     = useState<string | null>(null);
  const [msg, setMsg]                     = useState<string | null>(null);
  const [pickedDemande, setPickedDemande] = useState<DemandeDocument | null>(null);
  const [actionMode, setActionMode]       = useState<'info' | 'validate' | 'reject' | null>(null);
  const [ref, setRef]                     = useState('');
  const [comment, setComment]             = useState('');

  async function load() {
    setLoading(true);
    setErr(null);
    try { setRows(await getDocumentsFileAttente()); }
    catch (e) { setErr(e instanceof Error ? e.message : 'Erreur'); }
    finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

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
      await postDocumentDisponible(pickedDemande.identifiant, ref.trim(), comment.trim() || undefined);
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
      await postDocumentRejet(pickedDemande.identifiant, comment.trim());
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
    setErr(null);
  }

  function openInfo(d: DemandeDocument) {
    setPickedDemande(d);
    setActionMode('info');
  }

  function formatDate(iso: string) {
    try { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }); }
    catch { return iso; }
  }

  function statusMeta(statut: string): { label: string; color: string; bg: string } {
    switch (statut.toUpperCase()) {
      case 'EN_TRAITEMENT_RH': return { label: 'En traitement', color: '#1e40af', bg: '#eff6ff' };
      case 'EN_ATTENTE':       return { label: 'En attente',    color: '#92400e', bg: '#fffbeb' };
      case 'DISPONIBLE':       return { label: 'Disponible',    color: '#065f46', bg: '#f0fdf4' };
      case 'REJETE':           return { label: 'Refusé',        color: '#991b1b', bg: '#fef2f2' };
      default:                 return { label: statut,           color: '#374151', bg: '#f3f4f6' };
    }
  }

  return (
    <div className="page">
      {/* ── Header ── */}
      <div className="page__head">
        <div>
          <h2 className="page__title">Documents administratifs</h2>
          <p className="page__lead">Traitement des attestations, bulletins et courriers RH.</p>
        </div>
        <div className="page__head-actions">
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            Actualiser
          </button>
          <button type="button" className="btn btn--primary" onClick={() => void prendreProchaine()}>
            Prendre la prochaine demande
          </button>
        </div>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      {/* ── Table ── */}
      <div style={cardStyle}>
        {loading ? (
          <div style={{ padding: '60px 20px', textAlign: 'center' }}>
            <div style={spinnerStyle} />
            <p style={{ color: '#6b7280', marginTop: 16 }}>Chargement…</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  {['Rang', 'Type de document', 'Statut', 'SLA', 'Retard', ''].map(h => (
                    <th key={h} style={thStyle}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((d, i) => {
                  const s = statusMeta(d.statut);
                  return (
                    <tr key={d.identifiant}
                      style={{ borderBottom: i < rows.length - 1 ? '1px solid #f1f5f9' : 'none' }}
                      onMouseEnter={e => (e.currentTarget.style.background = '#f9fafb')}
                      onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                      <td style={tdStyle}>
                        <span style={{ fontWeight: 600, color: '#374151' }}>
                          {d.rang_dans_file ?? (d.statut === 'EN_TRAITEMENT_RH' ? '—' : '—')}
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <span style={{ ...tagStyle, background: '#eff6ff', color: '#1e40af' }}>
                          {d.type_document}
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <span style={{ ...tagStyle, background: s.bg, color: s.color }}>
                          {s.label}
                        </span>
                      </td>
                      <td style={{ ...tdStyle, color: '#6b7280' }}>{d.delai_sla_heures}h</td>
                      <td style={tdStyle}>
                        {d.en_retard
                          ? <span style={{ ...tagStyle, background: '#fef2f2', color: '#dc2626' }}>En retard</span>
                          : <span style={{ color: '#d1d5db' }}>—</span>}
                      </td>
                      <td style={{ ...tdStyle, textAlign: 'right' }}>
                        <button type="button"
                          style={btnInfoStyle}
                          onClick={() => openInfo(d)}>
                          Voir le détail
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {rows.length === 0 && (
              <div style={{ padding: '60px 20px', textAlign: 'center' }}>
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5" style={{ marginBottom: 12 }}>
                  <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <p style={{ color: '#9ca3af', fontWeight: 500 }}>Aucune demande en attente</p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Modal ── */}
      {pickedDemande && actionMode && (
        <div style={backdropStyle} onClick={closeModal}>
          <div style={modalStyle} onClick={e => e.stopPropagation()}>

            {/* Modal header */}
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 24 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#0f172a' }}>
                  Détail de la demande
                </h3>
                <p style={{ margin: '4px 0 0', fontSize: 12, color: '#94a3b8' }}>
                  #{pickedDemande.identifiant}
                </p>
              </div>
              <button onClick={closeModal} style={closeBtnStyle}>✕</button>
            </div>

            {/* Info section */}
            <div style={infoGridStyle}>
              <InfoRow label="Type de document" value={pickedDemande.type_document} highlight />
              <InfoRow label="Demandeur" value={pickedDemande.demandeur_identifiant} />
              <InfoRow label="Département" value={pickedDemande.departement_libelle ?? 'Non renseigné'} />
              <InfoRow label="Date de la demande" value={formatDate(pickedDemande.cree_le)} />
              {pickedDemande.commentaire_demandeur && (
                <InfoRow label="Motif de la demande" value={pickedDemande.commentaire_demandeur} full />
              )}
              <InfoRow label="SLA" value={`${pickedDemande.delai_sla_heures}h`} />
              <InfoRow label="Retard" value={pickedDemande.en_retard ? 'Oui' : 'Non'} danger={pickedDemande.en_retard} />
            </div>

            {/* Action zone — only shown when explicitly triggered */}
            {actionMode === 'validate' && (
              <div style={{ marginTop: 20 }}>
                <label style={labelStyle}>Référence du document *</label>
                <input style={inputStyle} value={ref} onChange={e => setRef(e.target.value)} placeholder="Ex. numéro, lien, chemin…" />
                <label style={{ ...labelStyle, marginTop: 12 }}>Commentaire (optionnel)</label>
                <textarea style={{ ...inputStyle, height: 80, resize: 'vertical' }} value={comment} onChange={e => setComment(e.target.value)} />
              </div>
            )}

            {actionMode === 'reject' && (
              <div style={{ marginTop: 20 }}>
                <label style={labelStyle}>Motif de refus *</label>
                <textarea style={{ ...inputStyle, height: 80, resize: 'vertical' }} value={comment} onChange={e => setComment(e.target.value)} placeholder="Expliquez la raison du refus…" />
              </div>
            )}

            {err ? <div style={{ marginTop: 12, padding: '10px 14px', background: '#fef2f2', color: '#dc2626', borderRadius: 10, fontSize: 13 }}>{err}</div> : null}

            {/* Footer actions */}
            <div style={{ display: 'flex', gap: 10, marginTop: 24, flexWrap: 'wrap' }}>
              {actionMode === 'info' && (
                <>
                  <button style={{ ...footerBtnStyle, background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca' }}
                    onClick={() => setActionMode('reject')}>
                    Refuser
                  </button>
                  <div style={{ flex: 1 }} />
                  <button style={{ ...footerBtnStyle, background: 'white', border: '1px solid #e2e8f0', color: '#374151' }}
                    onClick={closeModal}>
                    Fermer
                  </button>
                  <button style={{ ...footerBtnStyle, background: '#1e40af', color: 'white' }}
                    onClick={() => setActionMode('validate')}>
                    Valider le document
                  </button>
                </>
              )}
              {actionMode === 'validate' && (
                <>
                  <button style={{ ...footerBtnStyle, background: 'white', border: '1px solid #e2e8f0', color: '#374151' }}
                    onClick={() => setActionMode('info')}>
                    Retour
                  </button>
                  <div style={{ flex: 1 }} />
                  <button style={{ ...footerBtnStyle, background: '#1e40af', color: 'white' }}
                    disabled={!ref.trim()}
                    onClick={() => void marquerDisponible()}>
                    Confirmer la validation
                  </button>
                </>
              )}
              {actionMode === 'reject' && (
                <>
                  <button style={{ ...footerBtnStyle, background: 'white', border: '1px solid #e2e8f0', color: '#374151' }}
                    onClick={() => setActionMode('info')}>
                    Retour
                  </button>
                  <div style={{ flex: 1 }} />
                  <button style={{ ...footerBtnStyle, background: '#dc2626', color: 'white' }}
                    disabled={!comment.trim()}
                    onClick={() => void rejeterDemande()}>
                    Confirmer le refus
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function InfoRow({ label, value, highlight, full, danger }: {
  label: string; value: string; highlight?: boolean; full?: boolean; danger?: boolean;
}) {
  return (
    <div style={{ gridColumn: full ? '1 / -1' : 'span 1' }}>
      <p style={{ margin: 0, fontSize: 11, fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4 }}>
        {label}
      </p>
      <p style={{
        margin: 0, fontSize: 14, fontWeight: highlight ? 700 : 500,
        color: danger ? '#dc2626' : highlight ? '#1e40af' : '#1e293b',
        background: highlight ? '#eff6ff' : 'transparent',
        padding: highlight ? '4px 10px' : 0,
        borderRadius: highlight ? 8 : 0,
        display: highlight ? 'inline-block' : 'block',
      }}>
        {value}
      </p>
    </div>
  );
}

// ── Styles ───────────────────────────────────────────────────────────────────

const cardStyle: React.CSSProperties = {
  background: 'white',
  borderRadius: 16,
  border: '1px solid #e2e8f0',
  overflow: 'hidden',
};

const thStyle: React.CSSProperties = {
  padding: '14px 20px',
  textAlign: 'left',
  fontWeight: 600,
  color: '#475569',
  fontSize: 12,
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
};

const tdStyle: React.CSSProperties = {
  padding: '14px 20px',
  verticalAlign: 'middle',
};

const tagStyle: React.CSSProperties = {
  display: 'inline-block',
  padding: '5px 12px',
  borderRadius: 8,
  fontSize: 12,
  fontWeight: 600,
};

const btnInfoStyle: React.CSSProperties = {
  padding: '7px 16px',
  borderRadius: 8,
  border: '1px solid #e2e8f0',
  background: 'white',
  color: '#374151',
  fontSize: 13,
  fontWeight: 500,
  cursor: 'pointer',
};

const backdropStyle: React.CSSProperties = {
  position: 'fixed', inset: 0,
  background: 'rgba(15,23,42,0.4)',
  backdropFilter: 'blur(4px)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000, padding: 20,
};

const modalStyle: React.CSSProperties = {
  background: 'white',
  borderRadius: 20,
  padding: 28,
  width: '100%',
  maxWidth: 540,
  boxShadow: '0 20px 60px rgba(0,0,0,0.18)',
  maxHeight: '90vh',
  overflowY: 'auto',
};

const infoGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: '16px 24px',
  background: '#f8fafc',
  borderRadius: 12,
  padding: 20,
  border: '1px solid #e2e8f0',
};

const closeBtnStyle: React.CSSProperties = {
  width: 32, height: 32, borderRadius: '50%',
  border: 'none', background: '#f1f5f9',
  color: '#64748b', cursor: 'pointer', fontSize: 14,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 600,
  color: '#475569', marginBottom: 6,
  textTransform: 'uppercase', letterSpacing: '0.04em',
};

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 14px',
  border: '1px solid #e2e8f0', borderRadius: 10,
  fontSize: 14, color: '#1e293b',
  background: '#f8fafc', boxSizing: 'border-box',
  outline: 'none',
};

const footerBtnStyle: React.CSSProperties = {
  padding: '10px 20px', borderRadius: 10,
  border: 'none', fontWeight: 600, fontSize: 14,
  cursor: 'pointer',
};

const spinnerStyle: React.CSSProperties = {
  width: 36, height: 36, margin: '0 auto',
  border: '3px solid #e2e8f0',
  borderTopColor: '#1e40af',
  borderRadius: '50%',
  animation: 'spin 0.8s linear infinite',
};

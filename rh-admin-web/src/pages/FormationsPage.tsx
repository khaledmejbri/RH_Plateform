import { useEffect, useState } from 'react';
import {
  getDemandesFormations,
  postFormationIntegrerPlan,
  postFormationRefuser,
  type DemandeFormation,
} from '../api/rhClient';

type StatutFilter = '' | 'EN_VALIDATION_RRH' | 'INTEGREE_PLAN' | 'REFUSEE' | 'ANNULEE';

export default function FormationsPage() {
  const [rows, setRows] = useState<DemandeFormation[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [statut, setStatut] = useState<StatutFilter>('EN_VALIDATION_RRH');
  const [selectedFormation, setSelectedFormation] = useState<DemandeFormation | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);

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

  async function integrer(d: DemandeFormation) {
    const commentaire = window.prompt('Commentaire RH (optionnel)') ?? undefined;
    try {
      await postFormationIntegrerPlan(d.identifiant, commentaire);
      setMsg('Demande intégrée au plan annuel.');
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    }
  }

  async function refuser(d: DemandeFormation) {
    const motif = window.prompt('Motif de refus');
    if (!motif?.trim()) return;
    try {
      await postFormationRefuser(d.identifiant, motif.trim());
      setMsg('Demande refusée.');
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    }
  }

  function viewDetail(d: DemandeFormation) {
    setSelectedFormation(d);
    setShowDetailModal(true);
  }

  function closeDetailModal() {
    setShowDetailModal(false);
    setSelectedFormation(null);
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Formations</h2>
          <p className="page__lead">Validation RH des demandes et integration au plan annuel.</p>
        </div>
        <div className="page__head-actions">
          <select className="input" value={statut} onChange={e => setStatut(e.target.value as StatutFilter)}>
            <option value="">Tous les statuts</option>
            <option value="EN_VALIDATION_RRH">En attente RH</option>
            <option value="INTEGREE_PLAN">Integrees au plan</option>
            <option value="REFUSEE">Refusees</option>
            <option value="ANNULEE">Annulees</option>
          </select>
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            Actualiser
          </button>
        </div>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      <div style={cardStyle}>
        {loading ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748b' }}>Chargement...</div>
        ) : rows.length === 0 ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#94a3b8' }}>Aucune demande de formation.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  {['Formation', 'Demandeur', 'Cible', 'Budget', 'Statut', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {rows.map(d => (
                  <tr key={d.identifiant} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={tdStyle}>
                      <strong>{d.type_formation}</strong>
                      <div style={muted}>{d.organisme} - {d.duree_heures}h</div>
                      <div style={{ ...muted, marginTop: 6 }}>{d.objectifs_pedagogiques}</div>
                    </td>
                    <td style={tdStyle}>
                      <div>{d.demandeur_nom ?? d.demandeur_identifiant}</div>
                      <div style={muted}>{d.origine === 'CHEF_DEPARTEMENT' ? 'Chef departement' : 'RO'}</div>
                    </td>
                    <td style={tdStyle}>
                      {d.cible === 'UNITE'
                        ? d.unite_cible_libelle ?? 'Unite'
                        : `${d.collaborateurs_cibles_identifiants?.length ?? 0} collaborateur(s)`}
                    </td>
                    <td style={tdStyle}>{d.cout_estime ?? 0}</td>
                    <td style={tdStyle}><span style={statusStyle(d.statut)}>{labelStatut(d.statut)}</span></td>
                    <td style={{ ...tdStyle, textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                        <button className="btn btn--ghost" onClick={() => viewDetail(d)}>Détails</button>
                        {d.statut === 'EN_VALIDATION_RRH' ? (
                          <>
                            <button className="btn btn--ghost" onClick={() => void refuser(d)}>Refuser</button>
                            <button className="btn btn--primary" onClick={() => void integrer(d)}>Intégrer</button>
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

      {/* Detail Modal */}
      {showDetailModal && selectedFormation && (
        <div style={modalOverlayStyle} onClick={closeDetailModal}>
          <div style={modalContentStyle} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 20 }}>Détails de la formation</h3>
              <button onClick={closeDetailModal} style={{ background: 'none', border: 'none', fontSize: 24, cursor: 'pointer' }}>×</button>
            </div>
            
            <div style={{ display: 'grid', gap: 12 }}>
              <InfoRow label="Formation" value={selectedFormation.type_formation} />
              <InfoRow label="Organisme" value={selectedFormation.organisme} />
              <InfoRow label="Durée" value={`${selectedFormation.duree_heures} heures`} />
              <InfoRow label="Budget" value={`${selectedFormation.cout_estime ?? 0} TND`} />
              <InfoRow label="Demandeur" value={selectedFormation.demandeur_nom ?? selectedFormation.demandeur_identifiant} />
              <InfoRow label="Origine" value={selectedFormation.origine === 'CHEF_DEPARTEMENT' ? 'Chef département' : 'RO'} />
              <InfoRow label="Cible" value={
                selectedFormation.cible === 'UNITE'
                  ? selectedFormation.unite_cible_libelle ?? 'Unité'
                  : `${selectedFormation.collaborateurs_cibles_identifiants?.length ?? 0} collaborateur(s)`
              } />
              <InfoRow label="Statut" value={<span style={statusStyle(selectedFormation.statut)}>{labelStatut(selectedFormation.statut)}</span>} />
              
              {selectedFormation.objectifs_pedagogiques && (
                <div>
                  <div style={labelStyle}>Objectifs pédagogiques</div>
                  <div style={{ fontSize: 14, color: '#475569', marginTop: 4 }}>{selectedFormation.objectifs_pedagogiques}</div>
                </div>
              )}
              
              {selectedFormation.justification && (
                <div>
                  <div style={labelStyle}>Justification</div>
                  <div style={{ fontSize: 14, color: '#475569', marginTop: 4 }}>{selectedFormation.justification}</div>
                </div>
              )}
              
              {selectedFormation.commentaire_rh && (
                <div>
                  <div style={labelStyle}>Commentaire RH</div>
                  <div style={{ fontSize: 14, color: '#475569', marginTop: 4, padding: 12, background: '#f8fafc', borderRadius: 8 }}>
                    {selectedFormation.commentaire_rh}
                  </div>
                </div>
              )}
              
              <div>
                <div style={labelStyle}>Date de création</div>
                <div style={{ fontSize: 14, color: '#64748b', marginTop: 4 }}>
                  {new Date(selectedFormation.cree_le).toLocaleString('fr-FR')}
                </div>
              </div>
            </div>

            <div style={{ marginTop: 24, paddingTop: 16, borderTop: '1px solid #e2e8f0', textAlign: 'right' }}>
              <button className="btn btn--primary" onClick={closeDetailModal}>Fermer</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function labelStatut(statut: string) {
  switch (statut) {
    case 'INTEGREE_PLAN': return 'Integree au plan';
    case 'REFUSEE': return 'Refusee';
    case 'ANNULEE': return 'Annulee';
    default: return 'En attente RH';
  }
}

function statusStyle(statut: string): React.CSSProperties {
  const meta = statut === 'INTEGREE_PLAN'
    ? ['#dcfce7', '#166534']
    : statut === 'REFUSEE'
      ? ['#fee2e2', '#991b1b']
      : statut === 'ANNULEE'
        ? ['#f1f5f9', '#475569']
        : ['#dbeafe', '#1e40af'];
  return { background: meta[0], color: meta[1], borderRadius: 999, padding: '5px 10px', fontWeight: 700, fontSize: 12 };
}

const cardStyle: React.CSSProperties = {
  background: 'white',
  borderRadius: 16,
  border: '1px solid #e2e8f0',
  overflow: 'hidden',
};

const thStyle: React.CSSProperties = {
  padding: '14px 18px',
  textAlign: 'left',
  color: '#475569',
  fontWeight: 700,
  fontSize: 12,
};

const tdStyle: React.CSSProperties = {
  padding: '14px 18px',
  verticalAlign: 'top',
};

const muted: React.CSSProperties = {
  color: '#64748b',
  fontSize: 12,
};

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '150px 1fr', gap: 8, alignItems: 'start' }}>
      <div style={labelStyle}>{label}</div>
      <div style={{ fontSize: 14, color: '#1e293b' }}>{value}</div>
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  fontSize: 13,
  fontWeight: 600,
  color: '#64748b',
};

const modalOverlayStyle: React.CSSProperties = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  background: 'rgba(0, 0, 0, 0.5)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 1000,
};

const modalContentStyle: React.CSSProperties = {
  background: 'white',
  borderRadius: 16,
  padding: 24,
  maxWidth: 600,
  width: '90%',
  maxHeight: '80vh',
  overflowY: 'auto',
  boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)',
};

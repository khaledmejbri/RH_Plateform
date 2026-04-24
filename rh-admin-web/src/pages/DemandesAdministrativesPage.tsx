import { useEffect, useState } from 'react';
import { getDemandesAdministrativesListe, type DemandeAdministrative } from '../api/rhClient';

export default function DemandesAdministrativesPage() {
  const [rows, setRows] = useState<DemandeAdministrative[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let c = false;
    void (async () => {
      try {
        const data = await getDemandesAdministrativesListe();
        if (!c) setRows(data);
      } catch (e) {
        if (!c) setErr(e instanceof Error ? e.message : 'Erreur');
      } finally {
        if (!c) setLoading(false);
      }
    })();
    return () => {
      c = true;
    };
  }, []);

  function getStatusColor(statut: string): string {
    switch (statut.toUpperCase()) {
      case 'APPROUVEE':
      case 'VALIDEE':
        return '#10b981';
      case 'REJETEE':
      case 'REFUSEE':
        return '#ef4444';
      case 'EN_ATTENTE_VALIDATION':
      case 'EN_COURS':
        return '#f59e0b';
      default:
        return '#64748b';
    }
  }

  function getStatusIcon(statut: string): string {
    switch (statut.toUpperCase()) {
      case 'APPROUVEE':
      case 'VALIDEE':
        return '✓';
      case 'REJETEE':
      case 'REFUSEE':
        return '✕';
      case 'EN_ATTENTE_VALIDATION':
      case 'EN_COURS':
        return '⏳';
      default:
        return '•';
    }
  }

  function formatDate(iso: string): string {
    try {
      return new Date(iso).toLocaleString('fr-FR', { 
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return iso;
    }
  }

  function formatTypeDemande(type: string): string {
    return type
      .replaceAll('_', ' ')
      .toLowerCase()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Demandes administratives</h2>
          <p className="page__lead">Consultation des congés et autorisations - Informations détaillées.</p>
        </div>
      </div>
      
      {err ? (
        <div className="alert alert--error" style={{
          padding: '16px 20px',
          background: '#fef2f2',
          border: '1px solid #fecaca',
          borderRadius: '12px',
          color: '#991b1b',
          marginBottom: '20px'
        }}>
          <strong>Erreur:</strong> {err}
        </div>
      ) : null}
      
      <div className="panel" style={{
        background: 'white',
        borderRadius: '16px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.06)',
        overflow: 'hidden'
      }}>
        {loading ? (
          <div style={{ padding: '60px 20px', textAlign: 'center' }}>
            <div style={{
              width: '40px',
              height: '40px',
              border: '3px solid #e5e7eb',
              borderTopColor: '#3b82f6',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
              margin: '0 auto 16px'
            }} />
            <p className="muted">Chargement des demandes...</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{
              width: '100%',
              borderCollapse: 'collapse',
              fontSize: '14px'
            }}>
              <thead>
                <tr style={{
                  background: 'linear-gradient(to right, #f9fafb, #f3f4f6)',
                  borderBottom: '2px solid #e5e7eb'
                }}>
                  <th style={{ padding: '16px 20px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>Date de création</th>
                  <th style={{ padding: '16px 20px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>Type de demande</th>
                  <th style={{ padding: '16px 20px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>Statut</th>
                  <th style={{ padding: '16px 20px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>Période</th>
                  <th style={{ padding: '16px 20px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>Demandeur</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d, index) => {
                  const statusColor = getStatusColor(d.statut);
                  return (
                    <tr 
                      key={d.identifiant}
                      style={{
                        borderBottom: index < rows.length - 1 ? '1px solid #f3f4f6' : 'none',
                        transition: 'background 0.2s',
                        cursor: 'pointer'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = '#f9fafb'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'white'}
                    >
                      <td style={{ padding: '16px 20px', color: '#6b7280', fontSize: '13px' }}>
                        {formatDate(d.cree_le)}
                      </td>
                      <td style={{ padding: '16px 20px' }}>
                        <span style={{
                          display: 'inline-block',
                          padding: '6px 12px',
                          background: '#eff6ff',
                          color: '#1e40af',
                          borderRadius: '8px',
                          fontSize: '13px',
                          fontWeight: '600'
                        }}>
                          {formatTypeDemande(d.type_demande)}
                        </span>
                      </td>
                      <td style={{ padding: '16px 20px' }}>
                        <span style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '6px 12px',
                          background: `${statusColor}15`,
                          color: statusColor,
                          borderRadius: '8px',
                          fontSize: '13px',
                          fontWeight: '600',
                          border: `1px solid ${statusColor}30`
                        }}>
                          <span>{getStatusIcon(d.statut)}</span>
                          <span>{d.statut}</span>
                        </span>
                      </td>
                      <td style={{ padding: '16px 20px', color: '#6b7280', fontSize: '13px' }}>
                        {d.periode_debut && d.periode_fin ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span>📅</span>
                            <span>{d.periode_debut} → {d.periode_fin}</span>
                          </span>
                        ) : (
                          <span style={{ color: '#d1d5db' }}>-</span>
                        )}
                      </td>
                      <td style={{ padding: '16px 20px' }}>
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '10px'
                        }}>
                          <div style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: 'white',
                            fontSize: '12px',
                            fontWeight: '700'
                          }}>
                            {d.demandeur_identifiant.substring(0, 2).toUpperCase()}
                          </div>
                          <code style={{
                            fontSize: '12px',
                            color: '#6b7280',
                            background: '#f3f4f6',
                            padding: '4px 8px',
                            borderRadius: '6px'
                          }}>
                            {d.demandeur_identifiant}
                          </code>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {rows.length === 0 ? (
              <div style={{ padding: '60px 20px', textAlign: 'center' }}>
                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5" style={{ marginBottom: '16px' }}>
                  <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <p style={{ color: '#9ca3af', fontSize: '15px', fontWeight: '500', margin: '0 0 8px' }}>
                  Aucune demande administrative
                </p>
                <p style={{ color: '#d1d5db', fontSize: '13px', margin: 0 }}>
                  Les demandes apparaîtront ici lorsqu'elles seront soumises
                </p>
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}

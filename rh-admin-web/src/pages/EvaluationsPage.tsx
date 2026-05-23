import { useEffect, useMemo, useState } from 'react';
import {
  campaignApi,
  evaluationApi,
  templateApi,
  type CampaignAnalytics,
  type EvaluationAnalytics,
  type EvaluationCampaign,
  type EvaluationItem,
  type EvaluationTemplate
} from '../api/evaluationApi';
import EnhancedTemplatesTab from './EnhancedTemplatesTab';

type Tab = 'campaigns' | 'templates' | 'competencies' | 'evaluations';

const currentUserId = '0a4f7069-0737-4207-97dd-7a46a45f5429';

export default function EvaluationsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('campaigns');

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Gestion des évaluations</h1>
        <p className="page__subtitle">Campagnes, templates dynamiques, affectations et analytics RH.</p>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'campaigns' ? 'tab--active' : ''}`} onClick={() => setActiveTab('campaigns')}>
          Campagnes
        </button>
        <button className={`tab ${activeTab === 'templates' ? 'tab--active' : ''}`} onClick={() => setActiveTab('templates')}>
          Évaluation générale
        </button>
        <button className={`tab ${activeTab === 'competencies' ? 'tab--active' : ''}`} onClick={() => setActiveTab('competencies')}>
          Compétences
        </button>
        <button className={`tab ${activeTab === 'evaluations' ? 'tab--active' : ''}`} onClick={() => setActiveTab('evaluations')}>
          Analytics
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'campaigns' && <CampaignsTab />}
        {activeTab === 'templates' && <EnhancedTemplatesTab templateType="GENERIC" />}
        {activeTab === 'competencies' && <EnhancedTemplatesTab templateType="TECHNICAL" />}
        {activeTab === 'evaluations' && <EvaluationsTab />}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// CAMPAIGNS TAB
// ──────────────────────────────────────────────────────────────────────────────

function CampaignsTab() {
  const [campaigns, setCampaigns] = useState<EvaluationCampaign[]>([]);
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [analytics, setAnalytics] = useState<Record<string, CampaignAnalytics>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [assignModal, setAssignModal] = useState<EvaluationCampaign | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [formData, setFormData] = useState({
    nom: '',
    description: '',
    type: 'ANNUELLE' as 'ANNUELLE' | 'SEMESTRIELLE',
    annee: new Date().getFullYear(),
    moisDebut: 6,
    moisFin: 12,
    templateGeneralId: '',
    templateTechniqueId: ''
  });

  useEffect(() => { loadData(); }, []);

  const generalTemplates = templates.filter(t => t.type === 'GENERIC' && t.statut === 'PUBLISHED');
  const competencyTemplates = templates.filter(t => t.type === 'TECHNICAL' && t.statut === 'PUBLISHED');

  const loadData = async () => {
    try {
      setLoading(true);
      const [campaignData, templateData] = await Promise.all([
        campaignApi.list(),
        templateApi.listV2()
      ]);
      setCampaigns(campaignData);
      setTemplates(templateData);

      const analyticsEntries = await Promise.all(
        campaignData.map(async campaign => {
          try {
            return [campaign.identifiant, await campaignApi.analytics(campaign.identifiant)] as const;
          } catch {
            return [campaign.identifiant, undefined] as const;
          }
        })
      );
      const validEntries = analyticsEntries.filter((entry): entry is [string, CampaignAnalytics] => entry[1] !== undefined);
      setAnalytics(Object.fromEntries(validEntries));
    } finally {
      setLoading(false);
    }
  };

  const createCampaign = async (event: React.FormEvent) => {
    event.preventDefault();
    setActionError(null);
    try {
      setLoading(true);
      const campaign = await campaignApi.create({
        nom: formData.nom,
        description: formData.description,
        type: formData.type,
        annee: formData.annee,
        moisDebut: formData.moisDebut,
        moisFin: formData.moisFin,
        creePar: currentUserId
      });
      if (formData.templateGeneralId || formData.templateTechniqueId) {
        await campaignApi.assignTemplates(
          campaign.identifiant,
          formData.templateGeneralId || undefined,
          formData.templateTechniqueId || undefined
        );
      }
      setShowForm(false);
      setFormData({
        nom: '', description: '', type: 'ANNUELLE',
        annee: new Date().getFullYear(), moisDebut: 6, moisFin: 12,
        templateGeneralId: '', templateTechniqueId: ''
      });
      await loadData();
    } catch (error: any) {
      setActionError(error?.response?.data?.message || error.message || 'Erreur lors de la création');
    } finally {
      setLoading(false);
    }
  };

  const handleActivate = async (campaign: EvaluationCampaign) => {
    setActionError(null);
    try {
      await campaignApi.activate(campaign.identifiant);
      await loadData();
    } catch (error: any) {
      setActionError(`Erreur activation: ${error?.response?.data?.message || error.message}`);
    }
  };

  const handleTerminate = async (campaign: EvaluationCampaign) => {
    if (!confirm(`Terminer la campagne "${campaign.nom}" ? Cette action est irréversible.`)) return;
    setActionError(null);
    try {
      await campaignApi.terminate(campaign.identifiant);
      await loadData();
    } catch (error: any) {
      setActionError(`Erreur: ${error?.response?.data?.message || error.message}`);
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h2>Campagnes ({campaigns.length})</h2>
        <button className="btn btn--primary" onClick={() => { setShowForm(!showForm); setActionError(null); }}>
          {showForm ? 'Annuler' : '+ Nouvelle campagne'}
        </button>
      </div>

      {actionError && (
        <div className="card" style={{ background: '#fef2f2', border: '1px solid #fca5a5', marginBottom: '1rem' }}>
          <p style={{ color: '#dc2626', margin: 0 }}>⚠️ {actionError}</p>
        </div>
      )}

      {showForm && (
        <div className="card">
          <h3>Créer une campagne</h3>
          <form onSubmit={createCampaign}>
            <div className="form-grid">
              <div className="form-group">
                <label>Nom *</label>
                <input
                  value={formData.nom}
                  onChange={e => setFormData({ ...formData, nom: e.target.value })}
                  placeholder="Ex: Évaluation annuelle 2025"
                  required
                />
              </div>
              <div className="form-group">
                <label>Type</label>
                <select value={formData.type} onChange={e => setFormData({ ...formData, type: e.target.value as any })}>
                  <option value="ANNUELLE">Annuelle</option>
                  <option value="SEMESTRIELLE">Semestrielle</option>
                </select>
              </div>
              <div className="form-group">
                <label>Année</label>
                <input type="number" value={formData.annee} onChange={e => setFormData({ ...formData, annee: Number(e.target.value) })} />
              </div>
              <div className="form-group">
                <label>Mois début</label>
                <select value={formData.moisDebut} onChange={e => setFormData({ ...formData, moisDebut: Number(e.target.value) })}>
                  {[1,2,3,4,5,6,7,8,9,10,11,12].map(m => <option key={m} value={m}>{getMonthName(m)}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Mois fin</label>
                <select value={formData.moisFin} onChange={e => setFormData({ ...formData, moisFin: Number(e.target.value) })}>
                  {[1,2,3,4,5,6,7,8,9,10,11,12].map(m => <option key={m} value={m}>{getMonthName(m)}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Template général</label>
                <select value={formData.templateGeneralId} onChange={e => setFormData({ ...formData, templateGeneralId: e.target.value })}>
                  <option value="">— Aucun —</option>
                  {generalTemplates.map(t => <option key={t.identifiant} value={t.identifiant}>{t.nom}</option>)}
                </select>
                {generalTemplates.length === 0 && (
                  <small style={{ color: 'var(--muted)' }}>Aucun template général publié. Créez-en un dans l'onglet "Évaluation générale".</small>
                )}
              </div>
              <div className="form-group">
                <label>Template compétences</label>
                <select value={formData.templateTechniqueId} onChange={e => setFormData({ ...formData, templateTechniqueId: e.target.value })}>
                  <option value="">— Aucun —</option>
                  {competencyTemplates.map(t => <option key={t.identifiant} value={t.identifiant}>{t.nom}</option>)}
                </select>
                {competencyTemplates.length === 0 && (
                  <small style={{ color: 'var(--muted)' }}>Aucun template technique publié. Créez-en un dans l'onglet "Compétences".</small>
                )}
              </div>
              <div className="form-group full-width">
                <label>Description</label>
                <textarea rows={3} value={formData.description} onChange={e => setFormData({ ...formData, description: e.target.value })} />
              </div>
            </div>
            <div className="form-actions">
              <button className="btn btn--primary" type="submit" disabled={loading}>
                {loading ? 'Création...' : 'Créer la campagne'}
              </button>
              <button className="btn btn--ghost" type="button" onClick={() => setShowForm(false)}>Annuler</button>
            </div>
          </form>
        </div>
      )}

      {loading && campaigns.length === 0 && <div className="loading">Chargement...</div>}

      {!loading && campaigns.length === 0 && !showForm && (
        <div className="empty-state full-width">
          <h3>Aucune campagne</h3>
          <p>Créez votre première campagne d'évaluation pour commencer.</p>
          <button className="btn btn--primary" onClick={() => setShowForm(true)}>+ Nouvelle campagne</button>
        </div>
      )}

      <div className="grid grid--2">
        {campaigns.map(campaign => (
          <CampaignCard
            key={campaign.identifiant}
            campaign={campaign}
            analytics={analytics[campaign.identifiant]}
            onActivate={() => handleActivate(campaign)}
            onTerminate={() => handleTerminate(campaign)}
            onAssignTemplates={() => setAssignModal(campaign)}
          />
        ))}
      </div>

      {assignModal && (
        <AssignTemplatesModal
          campaign={assignModal}
          generalTemplates={generalTemplates}
          competencyTemplates={competencyTemplates}
          onClose={() => setAssignModal(null)}
          onSaved={() => { setAssignModal(null); loadData(); }}
        />
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// CAMPAIGN CARD
// ──────────────────────────────────────────────────────────────────────────────

interface CampaignCardProps {
  campaign: EvaluationCampaign;
  analytics?: CampaignAnalytics;
  onActivate: () => void;
  onTerminate: () => void;
  onAssignTemplates: () => void;
}

function CampaignCard({ campaign, analytics, onActivate, onTerminate, onAssignTemplates }: CampaignCardProps) {
  const [expanded, setExpanded] = useState(false);

  const hasTemplates = campaign.templateGeneral || campaign.templateTechnique;

  return (
    <div className="card">
      <div className="card__header">
        <h3 style={{ fontSize: '1rem' }}>{campaign.nom}</h3>
        <StatusBadge status={campaign.statut} />
      </div>

      <p className="text-muted" style={{ fontSize: '0.85rem', margin: '0.5rem 0' }}>
        {campaign.description || 'Aucune description'}
      </p>

      <div className="template-meta">
        <div><strong>Fréquence:</strong> {campaign.type === 'ANNUELLE' ? 'Annuelle' : 'Semestrielle'}</div>
        <div><strong>Période:</strong> {getMonthName(campaign.moisDebut)} – {getMonthName(campaign.moisFin)} {campaign.annee}</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <strong>Général:</strong>
          {campaign.templateGeneral
            ? <span className="badge badge--success" style={{ fontSize: '0.75rem' }}>{campaign.templateGeneral.nom}</span>
            : <span className="badge badge--default" style={{ fontSize: '0.75rem' }}>Non assigné</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <strong>Compétences:</strong>
          {campaign.templateTechnique
            ? <span className="badge badge--success" style={{ fontSize: '0.75rem' }}>{campaign.templateTechnique.nom}</span>
            : <span className="badge badge--default" style={{ fontSize: '0.75rem' }}>Non assigné</span>}
        </div>
      </div>

      {analytics && (
        <div className="stats-grid stats-grid--compact" style={{ marginTop: '0.75rem' }}>
          <MiniStat label="Complétion" value={`${analytics.completionPercentage}%`} />
          <MiniStat label="Score moyen" value={`${analytics.averageFinalScore || 0}/5`} />
          <MiniStat label="Évaluations" value={String(analytics.evaluationCount)} />
        </div>
      )}

      <div className="form-actions" style={{ marginTop: '1rem', flexWrap: 'wrap' }}>
        {/* Assign templates button - always visible */}
        <button className="btn btn--sm" onClick={onAssignTemplates} title="Assigner des templates">
          📋 {hasTemplates ? 'Modifier templates' : 'Assigner templates'}
        </button>

        {campaign.statut === 'PLANIFIEE' && (
          <button className="btn btn--success btn--sm" onClick={onActivate} title="Activer la campagne pour qu'elle soit visible sur mobile">
            ▶ Activer
          </button>
        )}
        {campaign.statut === 'ACTIVE' && (
          <>
            <span className="badge badge--success" style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}>● En cours</span>
            <button className="btn btn--warning btn--sm" onClick={onTerminate}>
              ⏹ Terminer
            </button>
          </>
        )}
        {campaign.statut === 'TERMINEE' && (
          <span className="badge badge--default" style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}>Terminée</span>
        )}

        <button className="btn btn--ghost btn--sm" onClick={() => setExpanded(!expanded)}>
          {expanded ? '▲ Réduire' : '▼ Détails'}
        </button>
      </div>

      {expanded && (
        <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--bg)', borderRadius: 'var(--radius-sm)' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
            <div><strong>ID:</strong> <code style={{ fontSize: '0.75rem' }}>{campaign.identifiant.slice(0, 8)}…</code></div>
            <div><strong>Créé le:</strong> {new Date(campaign.creeLe).toLocaleDateString('fr-FR')}</div>
            {campaign.dateDebut && <div><strong>Début:</strong> {new Date(campaign.dateDebut).toLocaleDateString('fr-FR')}</div>}
            {campaign.dateFin && <div><strong>Fin:</strong> {new Date(campaign.dateFin).toLocaleDateString('fr-FR')}</div>}
          </div>
          {!hasTemplates && campaign.statut === 'PLANIFIEE' && (
            <div style={{ marginTop: '0.75rem', padding: '0.75rem', background: '#fffbeb', borderRadius: '8px', border: '1px solid #fde68a' }}>
              <p style={{ margin: 0, fontSize: '0.82rem', color: '#92400e' }}>
                ⚠️ <strong>Aucun template assigné.</strong> La campagne ne peut pas être activée sans templates. Cliquez sur "Assigner templates" pour continuer.
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// ASSIGN TEMPLATES MODAL
// ──────────────────────────────────────────────────────────────────────────────

interface AssignTemplatesModalProps {
  campaign: EvaluationCampaign;
  generalTemplates: EvaluationTemplate[];
  competencyTemplates: EvaluationTemplate[];
  onClose: () => void;
  onSaved: () => void;
}

function AssignTemplatesModal({ campaign, generalTemplates, competencyTemplates, onClose, onSaved }: AssignTemplatesModalProps) {
  const [generalId, setGeneralId] = useState(campaign.templateGeneral?.identifiant || '');
  const [techniqueId, setTechniqueId] = useState(campaign.templateTechnique?.identifiant || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    setError(null);
    try {
      setSaving(true);
      await campaignApi.assignTemplates(
        campaign.identifiant,
        generalId || undefined,
        techniqueId || undefined
      );
      onSaved();
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Erreur lors de l\'assignation');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: '520px', width: '90%' }}>
        <div style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 className="modal__title" style={{ margin: 0 }}>Assigner des templates</h3>
            <button className="btn btn--ghost btn--sm" onClick={onClose}>✕</button>
          </div>

          <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'var(--bg)', borderRadius: '8px' }}>
            <p style={{ margin: 0, fontSize: '0.9rem' }}>
              <strong>Campagne:</strong> {campaign.nom}
              <span className="badge badge--info" style={{ marginLeft: '0.5rem', fontSize: '0.75rem' }}>{campaign.statut}</span>
            </p>
          </div>

          {error && (
            <div style={{ padding: '0.75rem', background: '#fef2f2', borderRadius: '8px', marginBottom: '1rem', color: '#dc2626', fontSize: '0.875rem' }}>
              ⚠️ {error}
            </div>
          )}

          <div className="form-group" style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', marginBottom: '0.4rem', fontWeight: 600 }}>
              📝 Template général (évaluation comportementale)
            </label>
            <select
              value={generalId}
              onChange={e => setGeneralId(e.target.value)}
              style={{ width: '100%' }}
            >
              <option value="">— Aucun —</option>
              {generalTemplates.map(t => (
                <option key={t.identifiant} value={t.identifiant}>
                  {t.nom} ({t.questions.length} questions)
                </option>
              ))}
            </select>
            {generalTemplates.length === 0 && (
              <small style={{ color: 'var(--muted)' }}>
                Aucun template général publié disponible. Créez-en un dans l'onglet "Évaluation générale".
              </small>
            )}
          </div>

          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <label style={{ display: 'block', marginBottom: '0.4rem', fontWeight: 600 }}>
              🔧 Template compétences (évaluation technique)
            </label>
            <select
              value={techniqueId}
              onChange={e => setTechniqueId(e.target.value)}
              style={{ width: '100%' }}
            >
              <option value="">— Aucun —</option>
              {competencyTemplates.map(t => (
                <option key={t.identifiant} value={t.identifiant}>
                  {t.nom}{t.role ? ` — ${t.role}` : ''}{t.niveauSeniorite ? ` (${t.niveauSeniorite})` : ''}
                </option>
              ))}
            </select>
            {competencyTemplates.length === 0 && (
              <small style={{ color: 'var(--muted)' }}>
                Aucun template technique publié disponible. Créez-en un dans l'onglet "Compétences".
              </small>
            )}
          </div>

          <div className="modal__actions">
            <button className="btn btn--ghost" onClick={onClose} disabled={saving}>Annuler</button>
            <button className="btn btn--primary" onClick={handleSave} disabled={saving}>
              {saving ? 'Enregistrement…' : '✓ Sauvegarder'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// EVALUATIONS (ANALYTICS) TAB
// ──────────────────────────────────────────────────────────────────────────────

function EvaluationsTab() {
  const [evaluations, setEvaluations] = useState<EvaluationItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<EvaluationAnalytics | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [filterStatut, setFilterStatut] = useState('');

  useEffect(() => { loadEvaluations(); }, []);

  const loadEvaluations = async () => {
    try {
      setLoading(true);
      setEvaluations(await evaluationApi.list());
    } finally {
      setLoading(false);
    }
  };

  const filtered = filterStatut ? evaluations.filter(e => e.statut === filterStatut) : evaluations;

  const stats = useMemo(() => {
    const completed = evaluations.filter(e => e.statut === 'VALIDEE').length;
    const inProgress = evaluations.filter(e => e.statut !== 'VALIDEE').length;
    return {
      total: evaluations.length,
      completed,
      inProgress,
      completion: evaluations.length ? Math.round((completed / evaluations.length) * 100) : 0
    };
  }, [evaluations]);

  const openAnalytics = async (id: string) => {
    if (selectedId === id) { setSelectedId(null); setAnalytics(null); return; }
    setSelectedId(id);
    setAnalytics(null);
    setAnalyticsLoading(true);
    try {
      setAnalytics(await evaluationApi.analytics(id));
    } catch {
      setAnalytics(null);
    } finally {
      setAnalyticsLoading(false);
    }
  };

  if (loading) return <div className="loading">Chargement…</div>;

  return (
    <div>
      <div className="stats-grid">
        <MiniStat label="Total évaluations" value={String(stats.total)} />
        <MiniStat label="En cours" value={String(stats.inProgress)} />
        <MiniStat label="Validées" value={String(stats.completed)} />
        <MiniStat label="Taux complétion" value={`${stats.completion}%`} />
      </div>

      <div className="toolbar" style={{ marginTop: '1.5rem' }}>
        <h2>Évaluations</h2>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <select value={filterStatut} onChange={e => setFilterStatut(e.target.value)} style={{ width: 'auto' }}>
            <option value="">Tous les statuts</option>
            <option value="EN_ATTENTE_VALIDATION_CROISEE">En attente</option>
            <option value="VALIDEE_COLLABORATEUR">Validée collab.</option>
            <option value="VALIDEE_SUPERIEUR">Validée manager</option>
            <option value="VALIDEE">Validée</option>
          </select>
          <button className="btn btn--ghost btn--sm" onClick={loadEvaluations}>↺ Actualiser</button>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="empty-state full-width">
          <h3>Aucune évaluation</h3>
          <p>Les évaluations apparaissent ici une fois qu'une campagne est activée et assignée aux collaborateurs.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="table">
            <thead>
              <tr>
                <th>Campagne</th>
                <th>Collaborateur</th>
                <th>Manager</th>
                <th>Étape</th>
                <th>Statut</th>
                <th>Score</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(evaluation => (
                <>
                  <tr key={evaluation.identifiant} style={{ background: selectedId === evaluation.identifiant ? 'var(--bg-glow)' : undefined }}>
                    <td><strong>{evaluation.campaignNom}</strong></td>
                    <td><code style={{ fontSize: '0.75rem' }}>{evaluation.collaborateurIdentifiant.slice(0, 8)}…</code></td>
                    <td><code style={{ fontSize: '0.75rem' }}>{evaluation.superieurIdentifiant.slice(0, 8)}…</code></td>
                    <td><span style={{ fontSize: '0.8rem' }}>{etapeLabel(evaluation.etapeActuelle)}</span></td>
                    <td><StatusBadge status={evaluation.statut} /></td>
                    <td>{evaluation.scoreSur20 ? <strong>{evaluation.scoreSur20}/20</strong> : <span className="text-muted">—</span>}</td>
                    <td>
                      <button className="btn btn--sm" onClick={() => openAnalytics(evaluation.identifiant)}>
                        {selectedId === evaluation.identifiant ? '▲ Fermer' : '📊 Analyser'}
                      </button>
                    </td>
                  </tr>
                  {selectedId === evaluation.identifiant && (
                    <tr key={`analytics-${evaluation.identifiant}`}>
                      <td colSpan={7} style={{ padding: '0' }}>
                        {analyticsLoading ? (
                          <div style={{ padding: '1rem', textAlign: 'center' }}>Chargement analytics…</div>
                        ) : analytics ? (
                          <AnalyticsPanel analytics={analytics} />
                        ) : (
                          <div style={{ padding: '1rem', color: 'var(--muted)' }}>Aucune donnée analytics disponible.</div>
                        )}
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function AnalyticsPanel({ analytics }: { analytics: EvaluationAnalytics }) {
  return (
    <div style={{ padding: '1.25rem', background: 'var(--bg)', borderTop: '1px solid var(--border)' }}>
      <div className="stats-grid stats-grid--compact">
        <MiniStat label="Auto-éval." value={`${analytics.selfAverage}/5`} />
        <MiniStat label="Manager" value={`${analytics.managerAverage}/5`} />
        <MiniStat label="Score final" value={`${analytics.finalScore}/5`} />
        <MiniStat label="Écart" value={`${analytics.discrepancyPercentage}%`} />
      </div>

      <div className="grid grid--2" style={{ marginTop: '1rem' }}>
        <div>
          <h4 style={{ marginBottom: '0.5rem' }}>Sections</h4>
          <div className="questions-list">
            {analytics.sections.map(section => (
              <div key={section.section} className="question-item">
                <div className="question-content">
                  <div className="question-label">{section.section}</div>
                  <div className="heatbar">
                    <span style={{ width: `${Math.min(100, section.managerAverage * 20)}%` }} />
                  </div>
                  <div className="question-meta">
                    <span>Self {section.selfAverage}</span>
                    <span>Manager {section.managerAverage}</span>
                    <span>Gap {section.gap}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div>
          {analytics.recommendations.length > 0 && (
            <>
              <h4 style={{ marginBottom: '0.5rem' }}>Recommandations</h4>
              <ul className="insight-list">
                {analytics.recommendations.map(item => <li key={item}>{item}</li>)}
              </ul>
            </>
          )}
          {analytics.gaps.filter(g => g.severity === 'HIGH' || g.severity === 'CRITICAL').length > 0 && (
            <>
              <h4 style={{ marginBottom: '0.5rem' }}>Écarts élevés</h4>
              <ul className="insight-list">
                {analytics.gaps
                  .filter(g => g.severity === 'HIGH' || g.severity === 'CRITICAL')
                  .map(g => <li key={g.questionId}>{g.label}: {g.selfScore} vs {g.managerScore}</li>)}
              </ul>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// SHARED COMPONENTS
// ──────────────────────────────────────────────────────────────────────────────

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat-card">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const config: Record<string, { label: string; className: string }> = {
    PLANIFIEE: { label: 'Planifiée', className: 'badge--info' },
    ACTIVE: { label: 'Active', className: 'badge--success' },
    TERMINEE: { label: 'Terminée', className: 'badge--default' },
    EN_ATTENTE_VALIDATION_CROISEE: { label: 'En cours', className: 'badge--warning' },
    VALIDEE_COLLABORATEUR: { label: 'Collab. validé', className: 'badge--info' },
    VALIDEE_SUPERIEUR: { label: 'Manager validé', className: 'badge--warning' },
    VALIDEE: { label: 'Validée', className: 'badge--success' }
  };
  const meta = config[status] || { label: status, className: 'badge--default' };
  return <span className={`badge ${meta.className}`}>{meta.label}</span>;
}

function getMonthName(month: number): string {
  const months = ['', 'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
  return months[month] || '';
}

function etapeLabel(etape?: string): string {
  if (!etape) return '—';
  return { EVALUATION_GENERALE: 'Étape 1 – Générale', EVALUATION_TECHNIQUE: 'Étape 2 – Technique' }[etape] ?? etape;
}

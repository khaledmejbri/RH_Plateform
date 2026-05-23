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
        <h1 className="page__title">Gestion des evaluations</h1>
        <p className="page__subtitle">Campagnes, templates dynamiques, affectations et analytics RH.</p>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'campaigns' ? 'tab--active' : ''}`} onClick={() => setActiveTab('campaigns')}>
          Campagnes
        </button>
        <button className={`tab ${activeTab === 'templates' ? 'tab--active' : ''}`} onClick={() => setActiveTab('templates')}>
          Evaluation generale
        </button>
        <button className={`tab ${activeTab === 'competencies' ? 'tab--active' : ''}`} onClick={() => setActiveTab('competencies')}>
          Competences
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

function CampaignsTab() {
  const [campaigns, setCampaigns] = useState<EvaluationCampaign[]>([]);
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [analytics, setAnalytics] = useState<Record<string, CampaignAnalytics>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
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

  useEffect(() => {
    loadData();
  }, []);

  const generalTemplates = templates.filter(template => template.type === 'GENERIC' && template.statut === 'PUBLISHED');
  const competencyTemplates = templates.filter(template => template.type === 'TECHNICAL' && template.statut === 'PUBLISHED');

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
      setAnalytics(Object.fromEntries(analyticsEntries.filter(([, value]) => value)));
    } finally {
      setLoading(false);
    }
  };

  const createCampaign = async (event: React.FormEvent) => {
    event.preventDefault();
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
        await campaignApi.assignTemplates(campaign.identifiant, formData.templateGeneralId, formData.templateTechniqueId);
      }
      setShowForm(false);
      setFormData({
        nom: '',
        description: '',
        type: 'ANNUELLE',
        annee: new Date().getFullYear(),
        moisDebut: 6,
        moisFin: 12,
        templateGeneralId: '',
        templateTechniqueId: ''
      });
      await loadData();
    } catch (error: any) {
      alert(error?.response?.data?.message || error.message || 'Erreur lors de la creation');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h2>Campagnes</h2>
        <button className="btn btn--primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Annuler' : '+ Nouvelle campagne'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          <h3>Creer une campagne</h3>
          <form onSubmit={createCampaign}>
            <div className="form-grid">
              <div className="form-group">
                <label>Nom</label>
                <input value={formData.nom} onChange={e => setFormData({ ...formData, nom: e.target.value })} required />
              </div>
              <div className="form-group">
                <label>Type</label>
                <select value={formData.type} onChange={e => setFormData({ ...formData, type: e.target.value as any })}>
                  <option value="ANNUELLE">Annuelle</option>
                  <option value="SEMESTRIELLE">Semestrielle</option>
                </select>
              </div>
              <div className="form-group">
                <label>Annee</label>
                <input type="number" value={formData.annee} onChange={e => setFormData({ ...formData, annee: Number(e.target.value) })} />
              </div>
              <div className="form-group">
                <label>Mois debut</label>
                <select value={formData.moisDebut} onChange={e => setFormData({ ...formData, moisDebut: Number(e.target.value) })}>
                  <option value={6}>Juin</option>
                  <option value={12}>Decembre</option>
                </select>
              </div>
              <div className="form-group">
                <label>Mois fin</label>
                <select value={formData.moisFin} onChange={e => setFormData({ ...formData, moisFin: Number(e.target.value) })}>
                  <option value={6}>Juin</option>
                  <option value={12}>Decembre</option>
                </select>
              </div>
              <div className="form-group">
                <label>Template general</label>
                <select value={formData.templateGeneralId} onChange={e => setFormData({ ...formData, templateGeneralId: e.target.value })}>
                  <option value="">Aucun</option>
                  {generalTemplates.map(template => <option key={template.identifiant} value={template.identifiant}>{template.nom}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Template competences</label>
                <select value={formData.templateTechniqueId} onChange={e => setFormData({ ...formData, templateTechniqueId: e.target.value })}>
                  <option value="">Aucun</option>
                  {competencyTemplates.map(template => <option key={template.identifiant} value={template.identifiant}>{template.nom}</option>)}
                </select>
              </div>
              <div className="form-group full-width">
                <label>Description</label>
                <textarea rows={3} value={formData.description} onChange={e => setFormData({ ...formData, description: e.target.value })} />
              </div>
            </div>
            <div className="form-actions">
              <button className="btn btn--primary" disabled={loading}>Creer</button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid--2">
        {campaigns.map(campaign => (
          <div key={campaign.identifiant} className="card">
            <div className="card__header">
              <h3>{campaign.nom}</h3>
              <StatusBadge status={campaign.statut} />
            </div>
            <p className="text-muted">{campaign.description || 'Aucune description'}</p>
            <div className="template-meta">
              <div><strong>Frequence:</strong> {campaign.type === 'ANNUELLE' ? 'Annuelle' : 'Semestrielle'}</div>
              <div><strong>Periode:</strong> {getMonthName(campaign.moisDebut)} - {getMonthName(campaign.moisFin)} {campaign.annee}</div>
              <div><strong>General:</strong> {campaign.templateGeneral?.nom || 'Non assigne'}</div>
              <div><strong>Competences:</strong> {campaign.templateTechnique?.nom || 'Non assigne'}</div>
            </div>
            {analytics[campaign.identifiant] && (
              <div className="stats-grid stats-grid--compact">
                <MiniStat label="Completion" value={`${analytics[campaign.identifiant].completionPercentage}%`} />
                <MiniStat label="Score moyen" value={`${analytics[campaign.identifiant].averageFinalScore || 0}/5`} />
              </div>
            )}
            <div className="form-actions">
              {campaign.statut === 'PLANIFIEE' && (
                <button className="btn btn--success btn--sm" onClick={async () => { await campaignApi.activate(campaign.identifiant); loadData(); }}>
                  Activer
                </button>
              )}
              {campaign.statut === 'ACTIVE' && (
                <button className="btn btn--warning btn--sm" onClick={async () => { await campaignApi.terminate(campaign.identifiant); loadData(); }}>
                  Terminer
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function EvaluationsTab() {
  const [evaluations, setEvaluations] = useState<EvaluationItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<EvaluationAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadEvaluations();
  }, []);

  const loadEvaluations = async () => {
    try {
      setLoading(true);
      setEvaluations(await evaluationApi.list());
    } finally {
      setLoading(false);
    }
  };

  const stats = useMemo(() => {
    const completed = evaluations.filter(evaluation => evaluation.statut === 'VALIDEE').length;
    return {
      total: evaluations.length,
      completed,
      completion: evaluations.length ? Math.round((completed / evaluations.length) * 100) : 0
    };
  }, [evaluations]);

  const openAnalytics = async (id: string) => {
    setSelectedId(id);
    setAnalytics(await evaluationApi.analytics(id));
  };

  if (loading) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="stats-grid">
        <MiniStat label="Evaluations" value={String(stats.total)} />
        <MiniStat label="Finalisees" value={String(stats.completed)} />
        <MiniStat label="Completion" value={`${stats.completion}%`} />
      </div>

      <div className="table-container">
        <table className="table">
          <thead>
            <tr>
              <th>Campagne</th>
              <th>Collaborateur</th>
              <th>Manager</th>
              <th>Etape</th>
              <th>Statut</th>
              <th>Score</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {evaluations.map(evaluation => (
              <tr key={evaluation.identifiant}>
                <td><strong>{evaluation.campaignNom}</strong></td>
                <td>{evaluation.collaborateurIdentifiant}</td>
                <td>{evaluation.superieurIdentifiant}</td>
                <td>{evaluation.etapeActuelle}</td>
                <td><StatusBadge status={evaluation.statut} /></td>
                <td>{evaluation.scoreSur20 ? `${evaluation.scoreSur20}/20` : '-'}</td>
                <td>
                  <button className="btn btn--sm" onClick={() => openAnalytics(evaluation.identifiant)}>
                    Analyser
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selectedId && analytics && (
        <div className="card">
          <div className="card__header">
            <h3>Analyse automatique</h3>
            <button className="btn btn--ghost btn--sm" onClick={() => setSelectedId(null)}>Fermer</button>
          </div>
          <div className="stats-grid">
            <MiniStat label="Self average" value={`${analytics.selfAverage}/5`} />
            <MiniStat label="Manager average" value={`${analytics.managerAverage}/5`} />
            <MiniStat label="Final score" value={`${analytics.finalScore}/5`} />
            <MiniStat label="Discrepancy" value={`${analytics.discrepancyPercentage}%`} />
          </div>

          <div className="grid grid--2">
            <div>
              <h4>Skill heatmap</h4>
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
              <h4>Recommendations</h4>
              <ul className="insight-list">
                {analytics.recommendations.map(item => <li key={item}>{item}</li>)}
              </ul>
              <h4>Gaps eleves</h4>
              <ul className="insight-list">
                {analytics.gaps.filter(gap => gap.severity === 'HIGH' || gap.severity === 'CRITICAL').map(gap => (
                  <li key={gap.questionId}>{gap.label}: {gap.selfScore} vs {gap.managerScore}</li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

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
    PLANIFIEE: { label: 'Planifiee', className: 'badge--info' },
    ACTIVE: { label: 'Active', className: 'badge--success' },
    TERMINEE: { label: 'Terminee', className: 'badge--default' },
    EN_ATTENTE_VALIDATION_CROISEE: { label: 'En cours', className: 'badge--warning' },
    VALIDEE_COLLABORATEUR: { label: 'Collab valide', className: 'badge--info' },
    VALIDEE_SUPERIEUR: { label: 'Manager valide', className: 'badge--warning' },
    VALIDEE: { label: 'Validee', className: 'badge--success' }
  };
  const meta = config[status] || { label: status, className: 'badge--default' };
  return <span className={`badge ${meta.className}`}>{meta.label}</span>;
}

function getMonthName(month: number): string {
  const months = ['', 'Janvier', 'Fevrier', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Aout', 'Septembre', 'Octobre', 'Novembre', 'Decembre'];
  return months[month] || '';
}

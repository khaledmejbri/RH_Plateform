import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  campaignApi,
  evaluationApi,
  templateApi,
  type CampaignAnalytics,
  type EvaluationAnalytics,
  type EvaluationCampaign,
  type EvaluationItem,
  type EvaluationTemplate,
} from '../api/evaluationApi';
import { libelleNiveauSeniorite, profilMetierLabel } from '../api/evaluationCatalog';
import { DiscrepancyLabel, EvalAlertBadge } from '../components/EvalAlertBadge';
import EnhancedTemplatesTab from './EnhancedTemplatesTab';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { currentUserId } from '../auth/jwtRoles';
import { useDialogA11y } from '../components/useDialogA11y';

type Tab = 'campaigns' | 'templates' | 'evaluations';
type TemplateSubTab = 'GENERIC' | 'TECHNICAL';

/** Fenêtres Must E1-R02 : juin / décembre uniquement. */
const CAMPAGNE_MOIS = [6, 12] as const;


export default function EvaluationsPage() {
  const lectureSeule = useLectureSeule();
  const [activeTab, setActiveTab] = useState<Tab>('campaigns');
  const [templateSub, setTemplateSub] = useState<TemplateSubTab>('GENERIC');

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Évaluations RH</h1>
        <p className="page__subtitle">
          {lectureSeule
            ? 'Consultation des campagnes, questionnaires et scores (lecture seule).'
            : 'Campagnes, questionnaires par profil métier et suivi des scores.'}
        </p>
      </div>

      {lectureSeule && (
        <div className="alert alert--info" role="status" style={{ marginBottom: '1rem' }}>
          Consultation seule — vous visualisez campagnes, questionnaires et scores sans pouvoir les modifier.
        </div>
      )}

      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'campaigns'}
          className={`tab ${activeTab === 'campaigns' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('campaigns')}
        >
          Campagnes
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'templates'}
          className={`tab ${activeTab === 'templates' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('templates')}
        >
          Templates
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'evaluations'}
          className={`tab ${activeTab === 'evaluations' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('evaluations')}
        >
          Suivi & scores
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'campaigns' && <CampaignsTab lectureSeule={lectureSeule} />}
        {activeTab === 'templates' && (
          <div>
            <div className="tabs tabs--sub" role="tablist" style={{ marginBottom: '1rem' }}>
              <button
                type="button"
                className={`tab ${templateSub === 'GENERIC' ? 'tab--active' : ''}`}
                onClick={() => setTemplateSub('GENERIC')}
              >
                Généraux
              </button>
              <button
                type="button"
                className={`tab ${templateSub === 'TECHNICAL' ? 'tab--active' : ''}`}
                onClick={() => setTemplateSub('TECHNICAL')}
              >
                Techniques (par profil)
              </button>
            </div>
            <EnhancedTemplatesTab templateType={templateSub} lectureSeule={lectureSeule} />
          </div>
        )}
        {activeTab === 'evaluations' && <EvaluationsTab />}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// CAMPAIGNS TAB
// ──────────────────────────────────────────────────────────────────────────────

function CampaignsTab({ lectureSeule = false }: { lectureSeule?: boolean }) {
  const confirm = useConfirm();
  const [campaigns, setCampaigns] = useState<EvaluationCampaign[]>([]);
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [analytics, setAnalytics] = useState<Record<string, CampaignAnalytics>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [assignModal, setAssignModal] = useState<EvaluationCampaign | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [formData, setFormData] = useState({
    nom: '',
    description: '',
    type: 'ANNUELLE' as 'ANNUELLE' | 'SEMESTRIELLE',
    annee: new Date().getFullYear(),
    moisDebut: 6,
    moisFin: 12,
    templateGeneralId: '',
    templateTechniqueId: '',
  });

  useEffect(() => { void loadData(); }, []);

  const generalTemplates = templates.filter(t => t.type === 'GENERIC' && t.statut === 'PUBLISHED');
  const competencyTemplates = templates.filter(t => t.type === 'TECHNICAL' && t.statut === 'PUBLISHED');

  const loadData = async () => {
    try {
      setLoading(true);
      const [campaignData, templateData] = await Promise.all([
        campaignApi.list(),
        templateApi.listV2(),
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
        }),
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
    setActionSuccess(null);
    try {
      setLoading(true);
      const campaign = await campaignApi.create({
        nom: formData.nom,
        description: formData.description,
        type: formData.type,
        annee: formData.annee,
        moisDebut: formData.moisDebut,
        moisFin: formData.moisFin,
        creePar: currentUserId(),
      });
      if (formData.templateGeneralId || formData.templateTechniqueId) {
        await campaignApi.assignTemplates(
          campaign.identifiant,
          formData.templateGeneralId || undefined,
          formData.templateTechniqueId || undefined,
        );
      }
      setShowForm(false);
      setFormData({
        nom: '', description: '', type: 'ANNUELLE',
        annee: new Date().getFullYear(), moisDebut: 6, moisFin: 12,
        templateGeneralId: '', templateTechniqueId: '',
      });
      setActionSuccess('Campagne créée.');
      await loadData();
    } catch (error: unknown) {
      const ax = error as { response?: { data?: { message?: string } }; message?: string };
      setActionError(ax?.response?.data?.message || ax.message || 'Erreur lors de la création');
    } finally {
      setLoading(false);
    }
  };

  const handleActivate = async (campaign: EvaluationCampaign) => {
    if (!campaign.templateGeneral) {
      setActionError('Impossible d’activer : publiez et assignez d’abord un template général.');
      return;
    }
    setActionError(null);
    setActionSuccess(null);
    try {
      const result = await campaignApi.activate(campaign.identifiant);
      const parts = [
        result.evaluationsCreees != null ? `${result.evaluationsCreees} évaluation(s) créée(s)` : null,
        result.profilsIncomplets ? `${result.profilsIncomplets} profil(s) métier incomplet(s)` : null,
        result.ignoresManagerManquant ? `${result.ignoresManagerManquant} sans manager` : null,
      ].filter(Boolean);
      setActionSuccess(parts.length ? `Campagne activée — ${parts.join(' · ')}` : 'Campagne activée.');
      await loadData();
    } catch (error: unknown) {
      const ax = error as { response?: { data?: { message?: string } }; message?: string };
      setActionError(`Activation : ${ax?.response?.data?.message || ax.message}`);
    }
  };

  const handleTerminate = async (campaign: EvaluationCampaign) => {
    const ok = await confirm({
      title: 'Terminer la campagne',
      message: `Terminer la campagne « ${campaign.nom} » ? Cette action est irréversible.`,
      confirmLabel: 'Terminer',
      danger: true,
    });
    if (!ok) return;
    setActionError(null);
    try {
      await campaignApi.terminate(campaign.identifiant);
      await loadData();
    } catch (error: unknown) {
      const ax = error as { response?: { data?: { message?: string } }; message?: string };
      setActionError(ax?.response?.data?.message || ax.message || 'Erreur');
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h2>Campagnes ({campaigns.length})</h2>
        {!lectureSeule ? (
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => { setShowForm(!showForm); setActionError(null); }}
          >
            {showForm ? 'Annuler' : '+ Nouvelle campagne'}
          </button>
        ) : null}
      </div>

      {actionError && (
        <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
          {actionError}
        </div>
      )}
      {actionSuccess && (
        <div className="alert alert--success" role="status" style={{ marginBottom: '1rem' }}>
          {actionSuccess}
        </div>
      )}

      {!lectureSeule && showForm && (
        <div className="card">
          <h3>Créer une campagne</h3>
          <form onSubmit={createCampaign}>
            <div className="form-grid">
              <div className="form-group">
                <label htmlFor="camp-nom">Nom *</label>
                <input
                  id="camp-nom"
                  value={formData.nom}
                  onChange={e => setFormData({ ...formData, nom: e.target.value })}
                  placeholder="Ex: Campagne annuelle 2026"
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="camp-type">Type *</label>
                <select
                  id="camp-type"
                  value={formData.type}
                  onChange={e => setFormData({ ...formData, type: e.target.value as 'ANNUELLE' | 'SEMESTRIELLE' })}
                  aria-describedby="camp-type-help"
                >
                  <option value="ANNUELLE">Annuelle</option>
                  <option value="SEMESTRIELLE">Semestrielle</option>
                </select>
                <small id="camp-type-help" className="text-muted">
                  Bilan mi-parcours (semestriel) ou bilan annuel compétences et objectifs.
                </small>
              </div>
              <div className="form-group">
                <label htmlFor="camp-annee">Année</label>
                <input
                  id="camp-annee"
                  type="number"
                  value={formData.annee}
                  onChange={e => setFormData({ ...formData, annee: Number(e.target.value) })}
                />
              </div>
              <div className="form-group">
                <label htmlFor="camp-debut">Mois début</label>
                <select
                  id="camp-debut"
                  value={formData.moisDebut}
                  onChange={e => setFormData({ ...formData, moisDebut: Number(e.target.value) })}
                  aria-describedby="camp-cal-help"
                >
                  {CAMPAGNE_MOIS.map(m => (
                    <option key={m} value={m}>{getMonthName(m)}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="camp-fin">Mois fin</label>
                <select
                  id="camp-fin"
                  value={formData.moisFin}
                  onChange={e => setFormData({ ...formData, moisFin: Number(e.target.value) })}
                >
                  {CAMPAGNE_MOIS.map(m => (
                    <option key={m} value={m}>{getMonthName(m)}</option>
                  ))}
                </select>
                <small id="camp-cal-help" className="text-muted">
                  Fenêtres habituelles : juin / décembre (S1, S2, annuelle).
                </small>
              </div>
              <div className="form-group">
                <label htmlFor="camp-gen">Template général</label>
                <select
                  id="camp-gen"
                  value={formData.templateGeneralId}
                  onChange={e => setFormData({ ...formData, templateGeneralId: e.target.value })}
                >
                  <option value="">— Aucun —</option>
                  {generalTemplates.map(t => (
                    <option key={t.identifiant} value={t.identifiant}>{t.nom}</option>
                  ))}
                </select>
                {generalTemplates.length === 0 && (
                  <small className="text-muted">Aucun template général publié. Créez-en un dans Templates → Généraux.</small>
                )}
              </div>
              <div className="form-group">
                <label htmlFor="camp-tech">Template technique campagne (fallback)</label>
                <select
                  id="camp-tech"
                  value={formData.templateTechniqueId}
                  onChange={e => setFormData({ ...formData, templateTechniqueId: e.target.value })}
                >
                  <option value="">— Aucun —</option>
                  {competencyTemplates.map(t => (
                    <option key={t.identifiant} value={t.identifiant}>
                      {t.nom}
                      {t.familleMetierCode || t.niveauSeniorite
                        ? ` — ${profilMetierLabel(t.familleMetierCode, t.niveauSeniorite) || libelleNiveauSeniorite(t.niveauSeniorite)}`
                        : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group full-width">
                <label htmlFor="camp-desc">Description</label>
                <textarea
                  id="camp-desc"
                  rows={3}
                  value={formData.description}
                  onChange={e => setFormData({ ...formData, description: e.target.value })}
                />
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
          <p>
            {lectureSeule
              ? 'Aucune campagne à consulter.'
              : 'Créez la première campagne annuelle ou semestrielle.'}
          </p>
          {!lectureSeule ? (
            <button type="button" className="btn btn--primary" onClick={() => setShowForm(true)}>
              + Nouvelle campagne
            </button>
          ) : null}
        </div>
      )}

      <div className="grid grid--2">
        {campaigns.map(campaign => (
          <CampaignCard
            key={campaign.identifiant}
            campaign={campaign}
            analytics={analytics[campaign.identifiant]}
            lectureSeule={lectureSeule}
            onActivate={() => void handleActivate(campaign)}
            onTerminate={() => void handleTerminate(campaign)}
            onAssignTemplates={() => setAssignModal(campaign)}
          />
        ))}
      </div>

      {!lectureSeule && assignModal && (
        <AssignTemplatesModal
          campaign={assignModal}
          generalTemplates={generalTemplates}
          competencyTemplates={competencyTemplates}
          onClose={() => setAssignModal(null)}
          onSaved={() => { setAssignModal(null); void loadData(); }}
        />
      )}
    </div>
  );
}

interface CampaignCardProps {
  campaign: EvaluationCampaign;
  analytics?: CampaignAnalytics;
  lectureSeule?: boolean;
  onActivate: () => void;
  onTerminate: () => void;
  onAssignTemplates: () => void;
}

function CampaignCard({ campaign, analytics, lectureSeule = false, onActivate, onTerminate, onAssignTemplates }: CampaignCardProps) {
  const [expanded, setExpanded] = useState(false);
  const hasTemplates = campaign.templateGeneral || campaign.templateTechnique;
  const isActive = campaign.statut === 'ACTIVE';

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
        <div><strong>Fréquence :</strong> {campaign.type === 'ANNUELLE' ? 'Annuelle' : 'Semestrielle'}</div>
        <div><strong>Période :</strong> {getMonthName(campaign.moisDebut)} – {getMonthName(campaign.moisFin)} {campaign.annee}</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
          <strong>Général :</strong>
          {campaign.templateGeneral
            ? <span className="badge badge--success" style={{ fontSize: '0.75rem' }}>{campaign.templateGeneral.nom}</span>
            : <span className="badge badge--default" style={{ fontSize: '0.75rem' }}>Non assigné</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
          <strong>Technique (fallback) :</strong>
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
        {!lectureSeule ? (
          <>
            {!isActive && (
              <button type="button" className="btn btn--sm" onClick={onAssignTemplates}>
                {hasTemplates ? 'Modifier templates' : 'Assigner templates'}
              </button>
            )}
            {isActive && (
              <span className="text-muted text-sm" title="Les questionnaires des évaluations déjà créées ne changent plus (instantané conservé).">
                Templates figés (campagne active)
              </span>
            )}

            {campaign.statut === 'PLANIFIEE' && (
              <button type="button" className="btn btn--success btn--sm" onClick={onActivate}>
                Activer
              </button>
            )}
            {campaign.statut === 'ACTIVE' && (
              <>
                <span className="badge badge--success" style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}>En cours</span>
                <button type="button" className="btn btn--warning btn--sm" onClick={onTerminate}>
                  Terminer
                </button>
              </>
            )}
          </>
        ) : (
          campaign.statut === 'ACTIVE' ? (
            <span className="badge badge--success" style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}>En cours</span>
          ) : null
        )}
        {campaign.statut === 'TERMINEE' && (
          <span className="badge badge--default" style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}>Terminée</span>
        )}

        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Réduire' : 'Détails'}
        </button>
      </div>

      {expanded && (
        <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--bg)', borderRadius: 'var(--radius-sm)' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
            <div><strong>Créé le :</strong> {new Date(campaign.creeLe).toLocaleDateString('fr-FR')}</div>
            {campaign.dateDebut && <div><strong>Début :</strong> {new Date(campaign.dateDebut).toLocaleDateString('fr-FR')}</div>}
            {campaign.dateFin && <div><strong>Fin :</strong> {new Date(campaign.dateFin).toLocaleDateString('fr-FR')}</div>}
          </div>
          {!campaign.templateGeneral && campaign.statut === 'PLANIFIEE' && !lectureSeule && (
            <div className="alert alert--info" style={{ marginTop: '0.75rem' }}>
              Impossible d’activer : publiez et assignez d’abord un template général.
            </div>
          )}
        </div>
      )}
    </div>
  );
}

interface AssignTemplatesModalProps {
  campaign: EvaluationCampaign;
  generalTemplates: EvaluationTemplate[];
  competencyTemplates: EvaluationTemplate[];
  onClose: () => void;
  onSaved: () => void;
}

function AssignTemplatesModal({ campaign, generalTemplates, competencyTemplates, onClose, onSaved }: AssignTemplatesModalProps) {
  const dialogRef = useDialogA11y(onClose);
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
        techniqueId || undefined,
      );
      onSaved();
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string; code?: string } }; message?: string };
      const msg = ax?.response?.data?.message || ax.message || 'Erreur lors de l’assignation';
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={e => e.target === e.currentTarget && onClose()}>
      <div ref={dialogRef} tabIndex={-1} className="modal" style={{ maxWidth: '520px', width: '90%' }} role="dialog" aria-modal="true" aria-labelledby="assign-tpl-title">
        <div style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 id="assign-tpl-title" className="modal__title" style={{ margin: 0 }}>Assigner des templates</h3>
            <button type="button" className="btn btn--ghost btn--sm" onClick={onClose} aria-label="Fermer">✕</button>
          </div>

          <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'var(--bg)', borderRadius: '8px' }}>
            <p style={{ margin: 0, fontSize: '0.9rem' }}>
              <strong>Campagne :</strong> {campaign.nom}
              <span className="badge badge--info" style={{ marginLeft: '0.5rem', fontSize: '0.75rem' }}>{campaign.statut}</span>
            </p>
          </div>

          {error && (
            <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>{error}</div>
          )}

          <div className="form-group" style={{ marginBottom: '1rem' }}>
            <label htmlFor="assign-gen" style={{ display: 'block', marginBottom: '0.4rem', fontWeight: 600 }}>
              Template général (évaluation comportementale)
            </label>
            <select id="assign-gen" value={generalId} onChange={e => setGeneralId(e.target.value)} style={{ width: '100%' }}>
              <option value="">— Aucun —</option>
              {generalTemplates.map(t => (
                <option key={t.identifiant} value={t.identifiant}>
                  {t.nom} ({t.questions.length} questions)
                </option>
              ))}
            </select>
          </div>

          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <label htmlFor="assign-tech" style={{ display: 'block', marginBottom: '0.4rem', fontWeight: 600 }}>
              Template technique campagne (fallback)
            </label>
            <select id="assign-tech" value={techniqueId} onChange={e => setTechniqueId(e.target.value)} style={{ width: '100%' }}>
              <option value="">— Aucun —</option>
              {competencyTemplates.map(t => (
                <option key={t.identifiant} value={t.identifiant}>
                  {t.nom}
                  {t.familleMetierCode || t.niveauSeniorite
                    ? ` — ${profilMetierLabel(t.familleMetierCode, t.niveauSeniorite)}`
                    : ''}
                </option>
              ))}
            </select>
          </div>

          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose} disabled={saving}>Annuler</button>
            <button type="button" className="btn btn--primary" onClick={() => void handleSave()} disabled={saving}>
              {saving ? 'Enregistrement…' : 'Sauvegarder'}
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filterStatut, setFilterStatut] = useState('');
  const [filterAlerte, setFilterAlerte] = useState('');
  const [filterProfilIncomplet, setFilterProfilIncomplet] = useState(false);

  useEffect(() => { void loadEvaluations(); }, [filterProfilIncomplet]);

  const loadEvaluations = async () => {
    try {
      setLoading(true);
      setLoadError(null);
      setEvaluations(await evaluationApi.list({
        profilIncomplet: filterProfilIncomplet || undefined,
      }));
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  };

  const filtered = evaluations.filter(e => {
    if (filterStatut && e.statut !== filterStatut) return false;
    if (filterAlerte && e.couleurAlerte !== filterAlerte) return false;
    return true;
  });

  const stats = useMemo(() => {
    const completed = evaluations.filter(e => e.statut === 'VALIDEE').length;
    const inProgress = evaluations.filter(e => e.statut !== 'VALIDEE').length;
    const orange = evaluations.filter(e => e.couleurAlerte === 'ORANGE').length;
    const rouge = evaluations.filter(e => e.couleurAlerte === 'ROUGE').length;
    return {
      total: evaluations.length,
      completed,
      inProgress,
      completion: evaluations.length ? Math.round((completed / evaluations.length) * 100) : 0,
      orange,
      rouge,
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
      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadEvaluations()}>Réessayer</button>
        </div>
      )}

      <div className="stats-grid">
        <MiniStat label="Total évaluations" value={String(stats.total)} />
        <MiniStat label="En cours" value={String(stats.inProgress)} />
        <MiniStat label="Validées" value={String(stats.completed)} />
        <MiniStat label="Taux complétion" value={`${stats.completion}%`} />
        <MiniStat label="Alertes orange" value={String(stats.orange)} />
        <MiniStat label="Alertes rouge" value={String(stats.rouge)} />
      </div>

      <div className="toolbar" style={{ marginTop: '1.5rem' }}>
        <h2>Évaluations</h2>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            value={filterStatut}
            onChange={e => setFilterStatut(e.target.value)}
            style={{ width: 'auto' }}
            aria-label="Filtrer par statut"
          >
            <option value="">Tous les statuts</option>
            <option value="EN_ATTENTE_VALIDATION_CROISEE">En attente</option>
            <option value="VALIDEE_COLLABORATEUR">Validée collab.</option>
            <option value="VALIDEE_SUPERIEUR">Validée manager</option>
            <option value="VALIDEE">Validée</option>
          </select>
          <select
            value={filterAlerte}
            onChange={e => setFilterAlerte(e.target.value)}
            style={{ width: 'auto' }}
            aria-label="Filtrer par alerte"
          >
            <option value="">Toutes les alertes</option>
            <option value="VERT">Vert</option>
            <option value="ORANGE">Orange</option>
            <option value="ROUGE">Rouge</option>
          </select>
          <label className="text-sm" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <input
              type="checkbox"
              checked={filterProfilIncomplet}
              onChange={e => setFilterProfilIncomplet(e.target.checked)}
            />
            Profil métier incomplet
          </label>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadEvaluations()}>
            Actualiser
          </button>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="empty-state full-width">
          <h3>Aucune évaluation</h3>
          <p>Les évaluations apparaissent ici une fois qu’une campagne est activée.</p>
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
                <th>Alerte</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(evaluation => (
                <Fragment key={evaluation.identifiant}>
                  <tr style={{ background: selectedId === evaluation.identifiant ? 'var(--bg-glow)' : undefined }}>
                    <td>
                      <strong>{evaluation.campaignNom}</strong>
                      {evaluation.profilMetierIncomplet && (
                        <div>
                          <span className="badge badge--warning" style={{ fontSize: '0.7rem' }}>
                            Profil métier incomplet
                          </span>
                        </div>
                      )}
                    </td>
                    <td>{personLabel(evaluation.collaborateurNom, evaluation.collaborateurMatricule, evaluation.collaborateurIdentifiant)}</td>
                    <td>{personLabel(evaluation.superieurNom, undefined, evaluation.superieurIdentifiant)}</td>
                    <td><span style={{ fontSize: '0.8rem' }}>{etapeLabel(evaluation.etapeActuelle)}</span></td>
                    <td><StatusBadge status={evaluation.statut} /></td>
                    <td>
                      {evaluation.scoreSur20 != null ? (
                        <div>
                          <strong>{evaluation.scoreSur20}/20</strong>
                          {evaluation.appreciation && (
                            <div className="text-muted text-sm">{evaluation.appreciation}</div>
                          )}
                        </div>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                    <td>
                      <EvalAlertBadge couleur={evaluation.couleurAlerte} compact />
                    </td>
                    <td>
                      <button type="button" className="btn btn--sm" onClick={() => void openAnalytics(evaluation.identifiant)}>
                        {selectedId === evaluation.identifiant ? 'Fermer' : 'Analyser'}
                      </button>
                    </td>
                  </tr>
                  {selectedId === evaluation.identifiant && (
                    <tr>
                      <td colSpan={8} style={{ padding: '0' }}>
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
                </Fragment>
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
      <p className="text-muted text-sm" style={{ marginTop: 0 }}>
        Score final : 70 % notes manager + 30 % auto-évaluation (si notes manager présentes).
      </p>
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
                  .map(g => (
                    <li key={g.questionId}>
                      {g.label}: {g.selfScore} vs {g.managerScore}{' '}
                      <DiscrepancyLabel severity={g.severity} />
                    </li>
                  ))}
              </ul>
            </>
          )}
        </div>
      </div>
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
    PLANIFIEE: { label: 'Planifiée', className: 'badge--info' },
    ACTIVE: { label: 'Active', className: 'badge--success' },
    TERMINEE: { label: 'Terminée', className: 'badge--default' },
    ANNULEE: { label: 'Annulée', className: 'badge--default' },
    EN_ATTENTE_VALIDATION_CROISEE: { label: 'En cours', className: 'badge--warning' },
    VALIDEE_COLLABORATEUR: { label: 'Collab. validé', className: 'badge--info' },
    VALIDEE_SUPERIEUR: { label: 'Manager validé', className: 'badge--warning' },
    VALIDEE: { label: 'Validée', className: 'badge--success' },
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
  return { EVALUATION_GENERALE: '1/2 — Générale', EVALUATION_TECHNIQUE: '2/2 — Technique' }[etape] ?? etape;
}

function personLabel(nom?: string, matricule?: string, id?: string): ReactNode {
  if (nom) {
    return (
      <div>
        <div>{nom}</div>
        {matricule && <div className="text-muted text-sm">{matricule}</div>}
      </div>
    );
  }
  if (!id) return <span className="text-muted">—</span>;
  return <code style={{ fontSize: '0.75rem' }} title={id}>{id.slice(0, 8)}…</code>;
}

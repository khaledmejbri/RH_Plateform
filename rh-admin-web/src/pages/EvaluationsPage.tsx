import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  campaignApi,
  evaluationApi,
  LIBELLES_TYPE_CAMPAGNE,
  parametresApi,
  lireSansTemplateTechnique,
  templateApi,
  type CampaignAnalytics,
  type CampaignType,
  type EvaluationParametres,
  type EvaluationAnalytics,
  type EvaluationCampaign,
  type EvaluationItem,
  type EvaluationTemplate,
} from '../api/evaluationApi';
import { libelleNiveauSeniorite, profilMetierLabel } from '../api/evaluationCatalog';
import { getAlertesOuvertesParNiveau, LIBELLES_NIVEAU_ECART } from '../api/alertesEcartApi';
import { DiscrepancyLabel, EvalAlertBadge } from '../components/EvalAlertBadge';
import EnhancedTemplatesTab from './EnhancedTemplatesTab';
import AlertesEcartTab from './AlertesEcartTab';
import { AlerteEcartBadge } from '../components/AlerteEcartBadge';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { useDialogA11y } from '../components/useDialogA11y';
import { EmptyState, Icon, LoadingState, StatusBadge as Status } from '../components/ui';
import { COLLABORATEUR_INTROUVABLE, formatDate, formatNombre, formatNote, formatPersonne, formatPourcentage, jourIso, TIRET } from '../utils/format';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';
import { getTousCollaborateurs, getUnites, nomCollaborateur, type CollaborateurRow, type Unite } from '../api/rhClient';
import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { appartientSelection, correspond, indexerUnites, pluriel, selectionActive, useFiltresUrl } from '../utils/filtres';
import DetailLink, { LienPersonne } from '../detail/DetailLink';

type Tab = 'campaigns' | 'templates' | 'evaluations' | 'alertes';
type TemplateSubTab = 'GENERIC' | 'TECHNICAL';

/** Mois de lancement libre (D-E02, SCRUM-41). */
const CAMPAGNE_MOIS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;
const JOUR_DEBUT_DEFAUT = 20;

const formatJour = (d: Date) => d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });

const jourFr = formatDate;

/** Jour courant (AAAA-MM-JJ) en Africa/Tunis, comme le serveur. Sert à l'affichage seulement : le serveur décide (422). */
function aujourdhuiTunis(): string {
  return jourIso();
}

/** Les mots de la sortie serveur gardent leurs accents (D-E14 : « évolution »). */
function accentuer(texte: string): string {
  return texte.replace(/evolution/g, 'évolution').replace(/Evolution/g, 'Évolution');
}

/**
 * Aperçu du calendrier, aligné sur le serveur : début = jour (20 par défaut) du mois choisi,
 * dernier jour = début + durée. Renvoie null si le jour n'existe pas dans le mois.
 */
function apercuCalendrier(annee: number, mois: number, jour: number | null, duree: number | null) {
  const j = jour ?? JOUR_DEBUT_DEFAUT;
  const debut = new Date(annee, mois - 1, j);
  if (debut.getMonth() !== mois - 1 || !duree || duree < 1) return null;
  const fin = new Date(debut);
  fin.setDate(fin.getDate() + duree);
  return { debut, fin };
}


export default function EvaluationsPage() {
  const lectureSeule = useLectureSeule();
  const [searchParams, setSearchParams] = useSearchParams();
  const ongletUrl = searchParams.get('onglet') as Tab | null;
  const [activeTab, setActiveTabEtat] = useState<Tab>(
    ongletUrl && ['campaigns', 'templates', 'evaluations', 'alertes'].includes(ongletUrl) ? ongletUrl : 'campaigns',
  );
  /** L'onglet est mémorisé dans l'URL avec les filtres. */
  function setActiveTab(t: Tab) {
    setActiveTabEtat(t);
    setSearchParams(
      (prev) => {
        const suivant = new URLSearchParams(prev);
        if (t === 'campaigns') suivant.delete('onglet');
        else suivant.set('onglet', t);
        return suivant;
      },
      { replace: true },
    );
  }
  const [templateSub, setTemplateSub] = useState<TemplateSubTab>('GENERIC');

  /** D-E12.c : ouvre l'onglet Alertes déjà filtré (alertes non closes, niveau et campagne éventuels). */
  function ouvrirAlertes(filtre: { niveau?: string; campagneId?: string }) {
    setActiveTabEtat('alertes');
    setSearchParams(
      (prev) => {
        const suivant = new URLSearchParams(prev);
        for (const k of ['a_statut', 'a_niveau', 'a_campagne', 'a_unite', 'a_q']) suivant.delete(k);
        suivant.set('onglet', 'alertes');
        suivant.set('a_statut', 'NON_CLOSES');
        if (filtre.niveau) suivant.set('a_niveau', filtre.niveau);
        if (filtre.campagneId) suivant.set('a_campagne', filtre.campagneId);
        return suivant;
      },
      { replace: true },
    );
  }

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
        <div className="alert alert--info" role="status">
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
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'alertes'}
          className={`tab ${activeTab === 'alertes' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('alertes')}
        >
          Alertes d'écart
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'campaigns' && <CampaignsTab lectureSeule={lectureSeule} />}
        {activeTab === 'templates' && (
          <div>
            <div className="tabs tabs--sub" role="tablist">
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
        {activeTab === 'evaluations' && <EvaluationsTab onOuvrirAlertes={ouvrirAlertes} />}
        {activeTab === 'alertes' && <AlertesEcartTab />}
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
    type: 'ANNUELLE' as CampaignType,
    annee: new Date().getFullYear(),
    moisDebut: 12,
    jourDebut: '',
    dureeJours: '',
    templateGeneralId: '',
    templateTechniqueId: '',
  });

  const [parametres, setParametres] = useState<EvaluationParametres | null>(null);
  const [dureeSociete, setDureeSociete] = useState('');

  useEffect(() => { void loadData(); }, []);
  useEffect(() => {
    parametresApi.get()
      .then(p => { setParametres(p); setDureeSociete(String(p.dureeCampagneJours)); })
      .catch(() => setParametres(null));
  }, []);

  const dureeEffective = formData.dureeJours ? Number(formData.dureeJours) : parametres?.dureeCampagneJours ?? null;
  const apercu = apercuCalendrier(
    formData.annee,
    formData.moisDebut,
    formData.jourDebut ? Number(formData.jourDebut) : null,
    dureeEffective,
  );

  const enregistrerDureeSociete = async () => {
    setActionError(null);
    setActionSuccess(null);
    try {
      const p = await parametresApi.update(Number(dureeSociete));
      setParametres(p);
      setActionSuccess(`Durée par défaut des campagnes : ${p.dureeCampagneJours} jours.`);
    } catch (error: unknown) {
      const ax = error as { response?: { data?: { message?: string } }; message?: string };
      setActionError(ax?.response?.data?.message || ax.message || 'Enregistrement impossible');
    }
  };

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
        ...(formData.jourDebut ? { jourDebut: Number(formData.jourDebut) } : {}),
        ...(formData.dureeJours ? { dureeJours: Number(formData.dureeJours) } : {}),
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
        annee: new Date().getFullYear(), moisDebut: 12, jourDebut: '', dureeJours: '',
        templateGeneralId: '', templateTechniqueId: '',
      });
      setActionSuccess(
        `Campagne créée : du ${formatDate(campaign.dateDebut)} au ${formatDate(campaign.dateFin)}. `
        + 'Elle sera lancée automatiquement à sa date de début (template général requis).',
      );
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
    const okActiver = await confirm({
      message: `Activer la campagne « ${campaign.nom} » ? Les évaluations seront créées pour les collaborateurs concernés.`,
      confirmLabel: 'Activer',
    });
    if (!okActiver) return;
    setActionError(null);
    setActionSuccess(null);
    try {
      const result = await campaignApi.activate(campaign.identifiant);
      const parts = [
        result.evaluationsCreees != null ? `${result.evaluationsCreees} évaluation(s) créée(s)` : null,
        result.profilsIncomplets ? `${result.profilsIncomplets} profil(s) métier incomplet(s)` : null,
        (lireSansTemplateTechnique(result) ?? 0) > 0
          ? `${pluriel(lireSansTemplateTechnique(result) ?? 0, 'évaluation')} sans questionnaire technique`
          : null,
        result.ignoresManagerManquant ? `${result.ignoresManagerManquant} sans responsable` : null,
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
        <div className="alert alert--error" role="alert">
          {actionError}
        </div>
      )}
      {actionSuccess && (
        <div className="alert alert--success" role="status">
          {actionSuccess}
        </div>
      )}

      {parametres && (
        <div className="card">
          <h3>Durée des campagnes</h3>
          <p className="text-muted">
            Durée par défaut d’une campagne, à partir de son jour de commencement. Elle est pré-remplie à la création
            et reste modifiable pour une campagne donnée (par ex. 10, 20 ou 30 jours pour 1 mois).
          </p>
          {lectureSeule ? (
            <p><strong>{parametres.dureeCampagneJours} jours</strong></p>
          ) : (
            <div className="inline-row">
              <label htmlFor="duree-societe">Durée par défaut (jours)</label>
              <input
                id="duree-societe"
                type="number"
                min={parametres.dureeMinJours}
                max={parametres.dureeMaxJours}
                value={dureeSociete}
                onChange={e => setDureeSociete(e.target.value)}
                style={{ width: '6rem' }}
              />
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={!dureeSociete || Number(dureeSociete) === parametres.dureeCampagneJours}
                onClick={() => void enregistrerDureeSociete()}
              >
                Enregistrer
              </button>
            </div>
          )}
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
                  onChange={e => setFormData({ ...formData, type: e.target.value as CampaignType })}
                  aria-describedby="camp-type-help"
                >
                  {(Object.keys(LIBELLES_TYPE_CAMPAGNE) as CampaignType[]).map(t => (
                    <option key={t} value={t}>{LIBELLES_TYPE_CAMPAGNE[t]}</option>
                  ))}
                </select>
                <small id="camp-type-help" className="text-muted">
                  Une seule campagne du même type par année, semestre ou trimestre.
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
                <label htmlFor="camp-debut">Mois de lancement *</label>
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
                <label htmlFor="camp-jour">Jour de commencement</label>
                <input
                  id="camp-jour"
                  type="number"
                  min={1}
                  max={31}
                  value={formData.jourDebut}
                  onChange={e => setFormData({ ...formData, jourDebut: e.target.value })}
                  placeholder={`Par défaut le ${JOUR_DEBUT_DEFAUT}`}
                />
              </div>
              <div className="form-group">
                <label htmlFor="camp-duree">Durée (jours)</label>
                <input
                  id="camp-duree"
                  type="number"
                  min={parametres?.dureeMinJours ?? 1}
                  max={parametres?.dureeMaxJours ?? 120}
                  value={formData.dureeJours}
                  onChange={e => setFormData({ ...formData, dureeJours: e.target.value })}
                  placeholder={parametres ? `Par défaut ${parametres.dureeCampagneJours} jours` : ''}
                />
              </div>
              <div className="form-group full-width">
                <small id="camp-cal-help" className={apercu ? 'text-muted' : 'text-danger'}>
                  {apercu
                    ? `Du ${formatJour(apercu.debut)} au ${formatJour(apercu.fin)} inclus. `
                      + `Les évaluations apparaîtront automatiquement sur le mobile des collaborateurs le ${formatJour(apercu.debut)}.`
                    : `Le jour ${formData.jourDebut || JOUR_DEBUT_DEFAUT} n’existe pas en ${getMonthName(formData.moisDebut).toLowerCase()} ${formData.annee}.`}
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
                <label htmlFor="camp-tech">Questionnaire technique par défaut</label>
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
              <button className="btn btn--primary" type="submit" disabled={loading || !apercu}>
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
              : 'Créez la première campagne annuelle, semestrielle ou trimestrielle.'}
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
  // D-E11 : affichage « Planifiée · lancement le … » ; le serveur reste juge (422 CAMPAGNE_NON_COMMENCEE).
  const commenceLe = campaign.dateDebut?.slice(0, 10) ?? '';
  const pasEncoreCommencee = campaign.statut === 'PLANIFIEE' && !!commenceLe && commenceLe > aujourdhuiTunis();
  const sansTechnique = campaign.evaluationsSansTemplateTechnique ?? 0;

  return (
    <div className="card">
      <div className="card__header">
        <h3 className="struct-title__name"><DetailLink type="campagne" id={campaign.identifiant} libelle={campaign.nom} /></h3>
        <StatusBadge
          status={campaign.statut}
          label={campaign.statut === 'PLANIFIEE' ? `Planifiée · lancement le ${jourFr(campaign.dateDebut)}` : undefined}
        />
      </div>

      <p className="cell-meta" style={{ whiteSpace: 'normal', margin: '0.5rem 0' }}>
        {campaign.description || 'Aucune description'}
      </p>

      <div className="template-meta">
        <div><strong>Fréquence :</strong> {LIBELLES_TYPE_CAMPAGNE[campaign.type] ?? campaign.type}</div>
        <div>
          <strong>Période :</strong> du {formatDate(campaign.dateDebut)} au {formatDate(campaign.dateFin)}
          {campaign.dureeJours ? ` (${campaign.dureeJours} jours)` : ''}
        </div>
        {campaign.statut === 'PLANIFIEE' && (
          <div className="cell-meta">
            Lancement automatique à cette date{pasEncoreCommencee ? ' ; activation manuelle impossible avant' : ''}
            {campaign.templateGeneral ? '' : ' — associez un template général avant cette date'}
          </div>
        )}
        <div className="inline-row">
          <strong>Général :</strong>
          {campaign.templateGeneral
            ? <span className="badge badge--success">{campaign.templateGeneral.nom}</span>
            : <span className="badge badge--default">Aucun</span>}
        </div>
        <div className="inline-row">
          <strong>Questionnaire technique par défaut :</strong>
          {campaign.templateTechnique
            ? <span className="badge badge--success">{campaign.templateTechnique.nom}</span>
            : <span className="badge badge--default">Aucun</span>}
        </div>
      </div>

      {sansTechnique > 0 && (
        <div className="alert alert--warning mt-sm" role="status">
          {pluriel(sansTechnique, 'évaluation')} sans questionnaire technique
        </div>
      )}

      {analytics && (
        <div className="stats-grid stats-grid--compact mt-sm">
          <MiniStat label="Complétion" value={formatPourcentage(analytics.completionPercentage)} />
          <MiniStat label="Score moyen" value={analytics.averageFinalScore ? formatNote(analytics.averageFinalScore) : 'Non évaluée'} />
          <MiniStat label="Évaluations" value={formatNombre(analytics.evaluationCount, 0)} />
        </div>
      )}

      <div className="form-actions form-actions--wrap">
        {!lectureSeule ? (
          <>
            {!isActive && (
              <button type="button" className="btn btn--sm" onClick={onAssignTemplates}>
                {hasTemplates ? 'Modifier les questionnaires' : 'Assigner les questionnaires'}
              </button>
            )}
            {isActive && (
              <span className="text-muted text-sm" title="Les questionnaires des évaluations déjà créées ne changent plus (instantané conservé).">
                Questionnaires verrouillés (campagne en cours)
              </span>
            )}

            {campaign.statut === 'PLANIFIEE' && (
              <button
                type="button"
                className="btn btn--success btn--sm"
                onClick={onActivate}
                disabled={pasEncoreCommencee}
                title={pasEncoreCommencee ? `Activation possible à partir du ${jourFr(campaign.dateDebut)}` : undefined}
              >
                Activer
              </button>
            )}
            {campaign.statut === 'ACTIVE' && (
              <>
                <Status statut="ACTIVE" label="En cours" />
                <button type="button" className="btn btn--warning btn--sm" onClick={onTerminate}>
                  Terminer
                </button>
              </>
            )}
          </>
        ) : (
          campaign.statut === 'ACTIVE' ? (
            <Status statut="ACTIVE" label="En cours" />
          ) : null
        )}
        {campaign.statut === 'TERMINEE' && (
          <Status statut="TERMINEE" />
        )}

        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Réduire' : 'Détails'}
        </button>
      </div>

      {expanded && (
        <div className="callout mt-md">
          <dl className="detail-grid" style={{ marginBottom: 0 }}>
            <div><dt>Créée le</dt><dd>{formatDate(campaign.creeLe)}</dd></div>
            {campaign.dateDebut && <div><dt>Début</dt><dd>{formatDate(campaign.dateDebut)}</dd></div>}
            {campaign.dateFin && <div><dt>Fin</dt><dd>{formatDate(campaign.dateFin)}</dd></div>}
          </dl>
          {pasEncoreCommencee && !lectureSeule && (
            <div className="alert alert--info mt-sm">
              La campagne commence le {jourFr(campaign.dateDebut)} : elle ne peut pas être activée avant cette date
              et se lancera automatiquement ce jour-là.
            </div>
          )}
          {!campaign.templateGeneral && campaign.statut === 'PLANIFIEE' && !lectureSeule && (
            <div className="alert alert--info mt-sm">
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
      <div ref={dialogRef} tabIndex={-1} className="modal modal--md" role="dialog" aria-modal="true" aria-labelledby="assign-tpl-title">
        <div>
          <div className="modal__head">
            <h3 id="assign-tpl-title" className="modal__title" style={{ margin: 0 }}>Assigner des templates</h3>
            <button type="button" className="modal__close" onClick={onClose} aria-label="Fermer">
              <Icon name="x" size={18} />
            </button>
          </div>

          <div className="callout inline-row">
            <strong>{campaign.nom}</strong>
            <Status statut={campaign.statut} />
          </div>

          {error && (
            <div className="alert alert--error" role="alert">{error}</div>
          )}

          <div className="form-group mb-md">
            <label htmlFor="assign-gen">
              Template général (évaluation comportementale)
            </label>
            <select id="assign-gen" value={generalId} onChange={e => setGeneralId(e.target.value)}>
              <option value="">— Aucun —</option>
              {generalTemplates.map(t => (
                <option key={t.identifiant} value={t.identifiant}>
                  {t.nom} ({t.questions.length} questions)
                </option>
              ))}
            </select>
          </div>

          <div className="form-group mb-lg">
            <label htmlFor="assign-tech">
              Questionnaire technique par défaut
            </label>
            <select id="assign-tech" value={techniqueId} onChange={e => setTechniqueId(e.target.value)}>
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

const FILTRES_EVAL_DEFAUT = { q: '', direction: '', departement: '', service: '', statut: '', alerte: '', campagne: '', profilIncomplet: '' };

function EvaluationsTab({ onOuvrirAlertes }: { onOuvrirAlertes: (filtre: { niveau?: string; campagneId?: string }) => void }) {
  const lectureSeule = useLectureSeule();
  const [evaluations, setEvaluations] = useState<EvaluationItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<EvaluationAnalytics | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const filtres = useFiltresUrl(FILTRES_EVAL_DEFAUT, 'e_');
  const f = filtres.valeurs;
  const filterStatut = f.statut;
  const filterAlerte = f.alerte;
  const filterProfilIncomplet = f.profilIncomplet === '1';
  const [unites, setUnites] = useState<Unite[]>([]);
  const [collabs, setCollabs] = useState<CollaborateurRow[]>([]);
  const [collabsErr, setCollabsErr] = useState(false);
  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
    void getTousCollaborateurs().then(setCollabs).catch(() => setCollabsErr(true));
  }, []);
  const indexUnites = useMemo(() => indexerUnites(unites), [unites]);
  const uniteCollab = useMemo(() => new Map(collabs.map((c) => [c.identifiant, c.unite?.identifiant])), [collabs]);
  // La liste admin ne renvoie que les identifiants : nom et matricule résolus depuis les fiches (EVA-8, U03).
  const evaluationsNommees = useMemo(() => {
    const fiches = new Map(collabs.map((c) => [c.identifiant, c]));
    const nom = (c?: CollaborateurRow) => (c ? `${c.prenom} ${nomCollaborateur(c)}`.trim() : undefined);
    return evaluations.map((e) => {
      const collab = fiches.get(e.collaborateurIdentifiant);
      const sup = e.superieurIdentifiant ? fiches.get(e.superieurIdentifiant) : undefined;
      return {
        ...e,
        collaborateurNom: e.collaborateurNom || nom(collab),
        collaborateurMatricule: e.collaborateurMatricule || collab?.matricule,
        superieurNom: e.superieurNom || nom(sup),
      };
    });
  }, [evaluations, collabs]);

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

  // Campagne par défaut (TRV-8) : l'ACTIVE la plus récente, sinon toutes les non annulées.
  // '*' dans l'URL = « Toutes les campagnes » choisi explicitement ; une valeur dans l'URL prime sur le défaut.
  const [campagnes, setCampagnes] = useState<EvaluationCampaign[]>([]);
  const [campagnesChargees, setCampagnesChargees] = useState(false);
  useEffect(() => {
    campaignApi.list().then(setCampagnes).catch(() => setCampagnes([])).finally(() => setCampagnesChargees(true));
  }, []);
  const campagneParDefaut = useMemo(
    () => campagnes
      .filter(c => c.statut === 'ACTIVE')
      .sort((a, b) => (b.dateDebut || '').localeCompare(a.dateDebut || ''))[0]?.nom ?? '',
    [campagnes],
  );
  const campagneChoisie = f.campagne === '*' ? '' : (f.campagne || campagneParDefaut);
  const toutesExplicite = f.campagne === '*';
  const nomsAnnulees = useMemo(() => new Set(campagnes.filter(c => c.statut === 'ANNULEE').map(c => c.nom)), [campagnes]);
  const masquerAnnulees = !f.campagne && !campagneChoisie;

  // Profil incomplet : serveur ; le reste côté client (liste complète renvoyée).
  const filtered = evaluationsNommees.filter(e => {
    if (filterStatut && e.statut !== filterStatut) return false;
    if (filterAlerte === 'NON_EVALUEE' ? couleurEffective(e) !== undefined : filterAlerte && couleurEffective(e) !== filterAlerte) return false;
    if (campagneChoisie && e.campaignNom !== campagneChoisie) return false;
    if (masquerAnnulees && nomsAnnulees.has(e.campaignNom)) return false;
    if (!correspond([e.collaborateurNom, e.collaborateurMatricule, e.superieurNom, e.campaignNom], f.q)) return false;
    if (!collabsErr && selectionActive(f) && !appartientSelection(indexUnites, uniteCollab.get(e.collaborateurIdentifiant), f)) return false;
    return true;
  });
  const campagnesNoms = Array.from(new Set(evaluations.map(e => e.campaignNom).filter(Boolean))).sort();

  // Les compteurs suivent la campagne sélectionnée (toutes si aucune).
  const duPerimetre = useMemo(
    () => evaluations.filter(e => (!campagneChoisie || e.campaignNom === campagneChoisie)
      && !(masquerAnnulees && nomsAnnulees.has(e.campaignNom))),
    [evaluations, campagneChoisie, masquerAnnulees, nomsAnnulees],
  );
  const stats = useMemo(() => {
    const completed = duPerimetre.filter(e => e.statut === 'VALIDEE').length;
    const inProgress = duPerimetre.filter(e => e.statut !== 'VALIDEE').length;
    // D-E12.b : codage couleur compté seulement sur les évaluations notées par le responsable.
    const vigilance = duPerimetre.filter(e => couleurEffective(e) === 'ORANGE').length;
    const planAction = duPerimetre.filter(e => couleurEffective(e) === 'ROUGE').length;
    return {
      total: duPerimetre.length,
      completed,
      inProgress,
      completion: duPerimetre.length ? Math.round((completed / duPerimetre.length) * 100) : 0,
      vigilance,
      planAction,
    };
  }, [duPerimetre]);

  // D-E12.c : alertes d'écart ouvertes (OUVERTE + EN_COURS) par niveau, pour la campagne sélectionnée.
  const [alertesOuvertes, setAlertesOuvertes] = useState<Record<NiveauAlerte, number> | 'erreur' | null>(null);
  const campagneIdChoisie = campagneChoisie ? campagnes.find(c => c.nom === campagneChoisie)?.identifiant : undefined;
  const attendCampagne = !campagnesChargees || (!!campagneChoisie && campagnes.length === 0);
  useEffect(() => {
    if (attendCampagne) return;
    let annule = false;
    setAlertesOuvertes(null);
    void (async () => {
      try {
        const r = await getAlertesOuvertesParNiveau(campagneIdChoisie);
        if (!annule) setAlertesOuvertes({ ORANGE: r.ORANGE, ROUGE: r.ROUGE, URGENTE: r.URGENTE });
      } catch {
        if (!annule) setAlertesOuvertes('erreur');
      }
    })();
    return () => { annule = true; };
  }, [campagneIdChoisie, attendCampagne]);
  const totalAlertesOuvertes = alertesOuvertes && alertesOuvertes !== 'erreur'
    ? alertesOuvertes.ORANGE + alertesOuvertes.ROUGE + alertesOuvertes.URGENTE
    : null;

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
        <MiniStat label="Taux de complétion" value={formatPourcentage(stats.completion)} />
        <CarteStatLien
          label="Alertes d’écart ouvertes"
          value={alertesOuvertes === 'erreur' ? '—' : totalAlertesOuvertes === null ? '…' : String(totalAlertesOuvertes)}
          detail={alertesOuvertes && alertesOuvertes !== 'erreur'
            ? NIVEAUX_ALERTE.map(n => `${LIBELLES_NIVEAU_ECART[n]} ${alertesOuvertes[n]}`).join(' · ')
            : alertesOuvertes === 'erreur' ? 'Compteur indisponible' : undefined}
          onClick={() => onOuvrirAlertes({ campagneId: campagneIdChoisie })}
          aide="Ouvrir l’onglet Alertes d’écart (alertes non closes)"
        />
        <CarteStatLien
          label="Vigilance"
          value={String(stats.vigilance)}
          onClick={() => filtres.definir({ alerte: 'ORANGE' })}
          aide="Filtrer la liste sur « Vigilance »"
        />
        <CarteStatLien
          label="Plan d’action requis"
          value={String(stats.planAction)}
          onClick={() => filtres.definir({ alerte: 'ROUGE' })}
          aide="Filtrer la liste sur « Plan d’action requis »"
        />
      </div>

      <div className="toolbar mt-lg">
        <h2>Évaluations</h2>
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadEvaluations()}>
          Actualiser
        </button>
      </div>

      <BarreFiltres
        id="eval"
        recherche={f.q}
        onRecherche={(v) => filtres.definir({ q: v })}
        libelleRecherche="Collaborateur ou responsable"
        placeholder="Nom, matricule, campagne…"
        nbResultats={filtered.length}
        nbTotal={evaluations.length}
        nbActifs={filtres.nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="eval-statut"
          libelle="Statut"
          valeur={f.statut}
          toutesLibelle="Tous les statuts"
          options={[
            { valeur: 'EN_ATTENTE_VALIDATION_CROISEE', libelle: 'En attente' },
            { valeur: 'VALIDEE_COLLABORATEUR', libelle: 'Validée par le collaborateur' },
            { valeur: 'VALIDEE_SUPERIEUR', libelle: 'Validée par le responsable' },
            { valeur: 'VALIDEE', libelle: 'Validée' },
          ]}
          onChange={(v) => filtres.definir({ statut: v })}
        />
        <ChampSelect
          id="eval-alerte"
          libelle="Codage couleur"
          valeur={f.alerte}
          toutesLibelle="Tous"
          options={[
            { valeur: 'VERT', libelle: 'Conforme' },
            { valeur: 'ORANGE', libelle: 'Vigilance' },
            { valeur: 'ROUGE', libelle: 'Plan d’action requis' },
            { valeur: 'NON_EVALUEE', libelle: 'Non évaluée' },
          ]}
          onChange={(v) => filtres.definir({ alerte: v })}
        />
        <ChampSelect
          id="eval-campagne"
          libelle="Campagne"
          valeur={toutesExplicite ? '*' : campagneChoisie || '*'}
          sansTous
          options={[{ valeur: '*', libelle: 'Toutes les campagnes' }, ...campagnesNoms.map(n => ({ valeur: n, libelle: n }))]}
          onChange={(v) => filtres.definir({ campagne: v })}
        />
        <FiltresUnite idPrefixe="eval" unites={unites} selection={f} onChange={(sel) => filtres.definir(sel)} />
        <label className="text-sm inline-row" style={{ alignSelf: 'end' }}>
          <input
            type="checkbox"
            checked={filterProfilIncomplet}
            onChange={e => filtres.definir({ profilIncomplet: e.target.checked ? '1' : '' })}
          />
          Profil métier incomplet
        </label>
      </BarreFiltres>
      {collabsErr ? (
        <div className="alert alert--warning" role="status">
          Liste des collaborateurs indisponible : le filtre par direction, département ou service n'est pas appliqué.
        </div>
      ) : null}

      {filtered.length === 0 && evaluations.length > 0 ? (
        <AucunResultat onReinitialiser={filtres.reinitialiser} />
      ) : filtered.length === 0 ? (
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
                <th>Responsable</th>
                <th>Étape</th>
                <th>Statut</th>
                <th>Score</th>
                <th>Codage couleur</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(evaluation => (
                <Fragment key={evaluation.identifiant}>
                  <tr className={selectedId === evaluation.identifiant ? 'is-selected' : undefined}>
                    <td>
                      <strong><DetailLink type="campagne" id={evaluation.campagneId} libelle={evaluation.campaignNom} /></strong>
                      <div className="cell-meta"><DetailLink type="evaluation" id={evaluation.identifiant} libelle={`Évaluation de ${evaluation.collaborateurNom ?? 'collaborateur'}`}>Voir l’évaluation</DetailLink></div>
                      {evaluation.profilMetierIncomplet && (
                        <div>
                          <span
                            className="badge badge--warning"
                            style={{ fontSize: '0.7rem' }}
                            title="Famille ou niveau non renseigné sur la fiche au lancement de la campagne"
                          >
                            Profil métier incomplet
                          </span>
                          <div className="text-muted text-sm">
                            Famille ou niveau non renseigné sur la fiche au lancement de la campagne.{' '}
                            {(evaluation.collaborateurMatricule || evaluation.collaborateurNom) ? (
                              <Link
                                to={`/app/collaborateurs?q=${encodeURIComponent(evaluation.collaborateurMatricule || evaluation.collaborateurNom || '')}`}
                              >
                                {lectureSeule ? 'Voir la fiche' : 'Compléter la fiche'}
                              </Link>
                            ) : null}
                          </div>
                        </div>
                      )}
                    </td>
                    <td>{evaluation.collaborateurNom ? <LienPersonne id={evaluation.collaborateurIdentifiant} nom={evaluation.collaborateurNom} matricule={evaluation.collaborateurMatricule} /> : personLabel(evaluation.collaborateurNom, evaluation.collaborateurMatricule, COLLABORATEUR_INTROUVABLE)}</td>
                    <td>{evaluation.superieurNom ? <LienPersonne id={evaluation.superieurIdentifiant} nom={evaluation.superieurNom} /> : personLabel(evaluation.superieurNom, undefined, evaluation.superieurIdentifiant ? COLLABORATEUR_INTROUVABLE : undefined)}</td>
                    <td className="cell-meta">{etapeLabel(evaluation.etapeActuelle)}</td>
                    <td><StatusBadge status={evaluation.statut} /></td>
                    <td>
                      {evaluation.scoreSur20 != null ? (
                        <div>
                          <strong>{formatNombre(evaluation.scoreSur20)} / 20</strong>
                          {evaluation.appreciation && (
                            <div className="text-muted text-sm">{evaluation.appreciation}</div>
                          )}
                        </div>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                    <td>
                      <EvalAlertBadge couleur={couleurEffective(evaluation)} compact />
                    </td>
                    <td>
                      <button type="button" className="btn btn--sm" onClick={() => void openAnalytics(evaluation.identifiant)}>
                        {selectedId === evaluation.identifiant ? 'Fermer' : 'Analyser'}
                      </button>
                    </td>
                  </tr>
                  {selectedId === evaluation.identifiant && (
                    <tr>
                      <td colSpan={8} className="p-0">
                        {analyticsLoading ? (
                          <LoadingState label="Chargement des analyses…" />
                        ) : analytics ? (
                          <AnalyticsPanel analytics={analytics} />
                        ) : (
                          <EmptyState icon="info" title="Aucune donnée d’analyse disponible" />
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
  // D-E14 : aucun score ni recommandation sans note. On se fie aux champs serveur s'ils existent,
  // sinon à la présence d'au moins une note par question (une note vaut au moins 1).
  const aNotesCollab = analytics.aNotesCollaborateur ?? analytics.gaps.some(g => (g.selfScore ?? 0) > 0);
  const aNotesResp = analytics.aNotesResponsable ?? analytics.gaps.some(g => (g.managerScore ?? 0) > 0);
  if (!aNotesCollab && !aNotesResp) {
    return (
      <div className="analytics-panel">
        <EmptyState icon="info" title="Aucune note pour le moment">
          Les scores et les recommandations apparaîtront dès les premières notes.
        </EmptyState>
      </div>
    );
  }
  return (
    <div className="analytics-panel">
      {aNotesResp ? (
        <p className="text-muted text-sm mt-0">
          Score final : 70 % notes du responsable + 30 % auto-évaluation (si le responsable a noté).
        </p>
      ) : (
        <p className="text-muted text-sm mt-0">
          Seule l’auto-évaluation du collaborateur est notée : score consolidé et recommandations disponibles après la première note du responsable.
        </p>
      )}
      <div className="stats-grid stats-grid--compact">
        {aNotesCollab ? <MiniStat label="Auto-évaluation" value={formatNote(analytics.selfAverage)} /> : null}
        {aNotesResp ? <MiniStat label="Responsable" value={formatNote(analytics.managerAverage)} /> : null}
        {aNotesResp ? <MiniStat label="Score final" value={formatNote(analytics.finalScore)} /> : null}
        {aNotesResp ? <MiniStat label="Écart" value={formatPourcentage(analytics.discrepancyPercentage)} /> : null}
      </div>

      <div className="grid grid--2 mt-md">
        <div>
          <h4 className="section-title">Sections</h4>
          <div className="questions-list">
            {analytics.sections.map(section => (
              <div key={section.section} className="question-item">
                <div className="question-content">
                  <div className="question-label">{section.section}</div>
                  <div className="heatbar">
                    <span style={{ width: `${Math.min(100, section.managerAverage * 20)}%` }} />
                  </div>
                  <div className="question-meta">
                    <span>Collaborateur {formatNombre(section.selfAverage, 2)}</span>
                    {aNotesResp ? <span>Responsable {formatNombre(section.managerAverage, 2)}</span> : null}
                    {aNotesResp ? <span>Écart {formatNombre(section.gap, 2)}</span> : null}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div>
          {aNotesResp && analytics.recommendations.length > 0 && (
            <>
              <h4 className="section-title">Recommandations</h4>
              <ul className="insight-list">
                {analytics.recommendations.map(item => <li key={item}>{accentuer(item)}</li>)}
              </ul>
            </>
          )}
          {aNotesResp && analytics.gaps.some(g => g.competenceTechnique) && (
            <>
              <h4 className="section-title">Compétences techniques</h4>
              <ul className="insight-list">
                {analytics.gaps
                  .filter(g => g.competenceTechnique)
                  .map(g => (
                    <li key={g.questionId}>
                      {g.label} : {g.selfScore ?? TIRET} contre {g.managerScore ?? TIRET}{' '}
                      <AlerteEcartBadge niveau={g.niveauEcart} />
                    </li>
                  ))}
              </ul>
            </>
          )}
          {aNotesResp && analytics.gaps.filter(g => !g.competenceTechnique && (g.severity === 'HIGH' || g.severity === 'CRITICAL')).length > 0 && (
            <>
              <h4 className="section-title">Écarts élevés</h4>
              <ul className="insight-list">
                {analytics.gaps
                  .filter(g => !g.competenceTechnique && (g.severity === 'HIGH' || g.severity === 'CRITICAL'))
                  .map(g => (
                    <li key={g.questionId}>
                      {g.label} : {g.selfScore ?? TIRET} contre {g.managerScore ?? TIRET}{' '}
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

const NIVEAUX_ALERTE = ['ORANGE', 'ROUGE', 'URGENTE'] as const;
type NiveauAlerte = typeof NIVEAUX_ALERTE[number];

/** D-E12.b : « Non évaluée » tant que le responsable n'a noté aucune compétence. */
function couleurEffective(e: EvaluationItem) {
  return e.aNoteResponsable === false ? undefined : e.couleurAlerte;
}

/** Carte de chiffre cliquable (D-E12.c) : bouton, libellé et détail lisibles au clavier. */
function CarteStatLien({ label, value, detail, onClick, aide }: {
  label: string; value: string; detail?: string; onClick: () => void; aide?: string;
}) {
  return (
    <button
      type="button"
      className="stat-card"
      onClick={onClick}
      title={aide}
      style={{ cursor: 'pointer', textAlign: 'inherit', font: 'inherit', color: 'inherit' }}
    >
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {detail ? <div className="stat-label text-sm">{detail}</div> : null}
    </button>
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

function StatusBadge({ status, label }: { status: string; label?: string }) {
  const config: Record<string, { label: string; className: string }> = {
    PLANIFIEE: { label: 'Planifiée', className: 'badge--info' },
    ACTIVE: { label: 'Active', className: 'badge--success' },
    TERMINEE: { label: 'Terminée', className: 'badge--default' },
    ANNULEE: { label: 'Annulée', className: 'badge--default' },
    EN_ATTENTE_VALIDATION_CROISEE: { label: 'En cours', className: 'badge--warning' },
    VALIDEE_COLLABORATEUR: { label: 'Validée par le collaborateur', className: 'badge--info' },
    VALIDEE_SUPERIEUR: { label: 'Validée par le responsable', className: 'badge--warning' },
    VALIDEE: { label: 'Validée', className: 'badge--success' },
  };
  const meta = config[status] || { label: VALEUR_NON_RECONNUE, className: 'badge--default' };
  return <span className={`badge ${meta.className}`}>{label ?? meta.label}</span>;
}

function getMonthName(month: number): string {
  const months = ['', 'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
  return months[month] || '';
}

function etapeLabel(etape?: string): string {
  if (!etape) return '—';
  return {
    EVALUATION_GENERALE: 'Étape 1 sur 2 — Questions générales',
    EVALUATION_TECHNIQUE: 'Étape 2 sur 2 — Questions techniques',
  }[etape] ?? VALEUR_NON_RECONNUE;
}

/** Personne d'un tableau : nom (matricule) ; `repli` (jamais un identifiant) si le nom est introuvable. */
function personLabel(nom?: string, matricule?: string, repli?: string): ReactNode {
  if (nom) return formatPersonne(nom, matricule);
  if (!repli) return <span className="text-muted">—</span>;
  return <span className="text-muted">{repli}</span>;
}

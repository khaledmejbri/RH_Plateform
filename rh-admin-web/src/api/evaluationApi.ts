import axios from 'axios';
import { getToken } from './auth';
import {
  appreciationFromScore,
  normalizeCouleurAlerte,
  normalizeNiveauSeniorite,
  type CouleurAlerte,
} from './evaluationCatalog';

const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type TemplateType = 'GENERIC' | 'TECHNICAL';
type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
type QuestionType =
  | 'TEXT'
  | 'PARAGRAPH'
  | 'MULTIPLE_CHOICE'
  | 'CHECKBOX'
  | 'RATING'
  | 'SCALE'
  | 'DATE'
  | 'NUMBER';

/** Fréquences (D-E02, SCRUM-41 : TRIMESTRIELLE ajoutée). */
export type CampaignType = 'ANNUELLE' | 'SEMESTRIELLE' | 'TRIMESTRIELLE';

export const LIBELLES_TYPE_CAMPAGNE: Record<CampaignType, string> = {
  ANNUELLE: 'Annuelle',
  SEMESTRIELLE: 'Semestrielle',
  TRIMESTRIELLE: 'Trimestrielle',
};

export interface EvaluationCampaign {
  identifiant: string;
  nom: string;
  description?: string;
  type: CampaignType;
  statut: 'PLANIFIEE' | 'ACTIVE' | 'TERMINEE' | 'ANNULEE';
  annee: number;
  moisDebut: number;
  moisFin: number;
  dateDebut: string;
  dateFin: string;
  /** Jour de commencement (SCRUM-41) ; absent pour les campagnes antérieures. */
  jourDebut?: number | null;
  /** Durée en jours (SCRUM-41). */
  dureeJours?: number | null;
  creeLe: string;
  templateGeneral?: { identifiant: string; nom: string };
  templateTechnique?: { identifiant: string; nom: string };
}

export interface CreateCampaignRequest {
  nom: string;
  description?: string;
  type: CampaignType;
  annee: number;
  /** Mois de lancement, 1 à 12 (SCRUM-41). */
  moisDebut: number;
  /** Jour de commencement ; absent → le 20. */
  jourDebut?: number;
  /** Durée en jours ; absente → paramètre société. La date de fin est calculée par le serveur. */
  dureeJours?: number;
  /* Créateur lu par le serveur dans le JWT (SCRUM-26) : pas de creePar dans le corps. */
}

/** Paramètres société des évaluations (SCRUM-41). */
export interface EvaluationParametres {
  dureeCampagneJours: number;
  jourDebutDefaut: number;
  dureeMinJours: number;
  dureeMaxJours: number;
  modifieLe?: string | null;
}

export interface ActivateCampaignResult {
  campagneId?: string;
  statut?: string;
  evaluationsCreees?: number;
  ignoresDejaExistantes?: number;
  ignoresManagerManquant?: number;
  profilsIncomplets?: number;
}

export interface EvaluationQuestion {
  identifiant: string;
  libelle: string;
  description?: string;
  typeQuestion: QuestionType;
  ordre: number;
  obligatoire: boolean;
  optionsReponses: string[];
  valeurMinimale?: number;
  valeurMaximale?: number;
  sectionCode?: string;
  sectionLibelle?: string;
  poids?: number;
  labelsEchelle?: string[];
  placeholder?: string;
  actif?: boolean;
}

export interface CreateQuestionRequest {
  libelle: string;
  description?: string;
  typeQuestion: QuestionType;
  ordre: number;
  obligatoire?: boolean;
  optionsReponses?: string[];
  valeurMinimale?: number;
  valeurMaximale?: number;
  sectionCode?: string;
  sectionLibelle?: string;
  poids?: number;
  labelsEchelle?: string[];
  uniteMesure?: string;
  placeholder?: string;
  regexPattern?: string;
  minLongueur?: number;
  maxLongueur?: number;
}

export interface EvaluationTemplate {
  identifiant: string;
  nom: string;
  description?: string;
  type: TemplateType;
  statut: TemplateStatus;
  version: number;
  /** Clé matching E3 — famille catalogue. */
  familleMetierCode?: string;
  niveauSeniorite?: string;
  /** @deprecated lecture legacy → familleMetierCode */
  role?: string;
  domaine?: string;
  actif: boolean;
  creeLe: string;
  modifieLe?: string;
  creePar?: string;
  publieLe?: string;
  publiePar?: string;
  questions: EvaluationQuestion[];
}

export interface CreateTemplateRequest {
  nom: string;
  description?: string;
  type: TemplateType;
  /** Snake cible BA ; aussi envoyé en camel + alias `role` pour backend actuel. */
  famille_metier_code?: string;
  familleMetierCode?: string;
  niveau_seniorite?: string;
  niveauSeniorite?: string;
  /** @deprecated alias écriture → famille_metier_code */
  role?: string;
  domaine?: string;
  questions?: CreateQuestionRequest[];
}

export interface TechnicalTemplate {
  identifiant: string;
  nom: string;
  description?: string;
  niveauSeniorite: string;
  role: string;
  domaine?: string;
  actif: boolean;
  creeLe: string;
  creePar?: string;
}

export interface CreateTechnicalTemplateRequest {
  nom: string;
  description?: string;
  niveauSeniorite: string;
  role: string;
  domaine?: string;
  creePar: string;
}

export interface TechnicalQuestion {
  identifiant: string;
  competence: string;
  description?: string;
  niveauxPermis: string;
  ordre: number;
  actif: boolean;
}

export interface EvaluationItem {
  identifiant: string;
  campaignNom: string;
  collaborateurIdentifiant: string;
  superieurIdentifiant: string;
  collaborateurNom?: string;
  collaborateurMatricule?: string;
  superieurNom?: string;
  statut: string;
  etapeActuelle: string;
  scoreSur20?: number;
  appreciation?: string;
  couleurAlerte?: CouleurAlerte;
  profilMetierIncomplet?: boolean;
  creeLe: string;
}

export interface EvaluationAnalytics {
  selfAverage: number;
  managerAverage: number;
  finalScore: number;
  totalSelfScore: number;
  totalManagerScore: number;
  averageGap: number;
  discrepancyPercentage: number;
  gaps: Array<{
    questionId: string;
    label: string;
    section: string;
    selfScore?: number;
    managerScore?: number;
    gap: number;
    severity: 'ALIGNED' | 'MODERATE' | 'HIGH' | 'CRITICAL';
  }>;
  sections: Array<{
    section: string;
    selfAverage: number;
    managerAverage: number;
    gap: number;
  }>;
  strengths: string[];
  improvementAreas: string[];
  recommendations: string[];
}

export interface CampaignAnalytics {
  campaignId: string;
  evaluationCount: number;
  completedCount: number;
  averageFinalScore: number;
  completionPercentage: number;
}

function idOf(value: any): string {
  return value?.identifiant ?? value?.id ?? '';
}

function normalizeQuestion(value: any): EvaluationQuestion {
  const rawOptions = value?.optionsReponses ?? value?.options;
  return {
    identifiant: idOf(value),
    libelle: value?.libelle ?? value?.intitule ?? '',
    description: value?.description,
    typeQuestion: value?.typeQuestion ?? value?.type ?? 'TEXT',
    ordre: value?.ordre ?? 0,
    obligatoire: Boolean(value?.obligatoire),
    optionsReponses: Array.isArray(rawOptions)
      ? rawOptions
      : typeof rawOptions === 'string' && rawOptions.length > 0
        ? rawOptions.split(',').map((option) => option.trim()).filter(Boolean)
        : [],
    valeurMinimale: value?.valeurMinimale != null ? Number(value.valeurMinimale) : undefined,
    valeurMaximale: value?.valeurMaximale != null ? Number(value.valeurMaximale) : undefined,
    sectionCode: value?.sectionCode,
    sectionLibelle: value?.sectionLibelle,
    poids: value?.poids != null ? Number(value.poids) : undefined,
    labelsEchelle: Array.isArray(value?.labelsEchelle) ? value.labelsEchelle : undefined,
    actif: value?.actif
  };
}

function resolveFamilleCode(value: any): string | undefined {
  const raw =
    value?.familleMetierCode
    ?? value?.famille_metier_code
    ?? value?.role
    ?? value?.roleMetier;
  return typeof raw === 'string' && raw.trim() ? raw.trim().toUpperCase() : undefined;
}

function normalizeTemplate(value: any): EvaluationTemplate {
  const familleMetierCode = resolveFamilleCode(value);
  return {
    identifiant: idOf(value),
    nom: value?.nom ?? '',
    description: value?.description,
    type: value?.type ?? 'GENERIC',
    statut: value?.statut ?? 'DRAFT',
    version: value?.version ?? 1,
    familleMetierCode,
    niveauSeniorite: normalizeNiveauSeniorite(
      value?.niveauSeniorite ?? value?.niveau_seniorite
    ),
    role: value?.role,
    domaine: value?.domaine,
    actif: value?.actif ?? true,
    creeLe: value?.creeLe ?? new Date().toISOString(),
    modifieLe: value?.modifieLe,
    creePar: value?.creePar,
    publieLe: value?.publieLe,
    publiePar: value?.publiePar,
    questions: Array.isArray(value?.questions) ? value.questions.map(normalizeQuestion) : []
  };
}

function normalizeEvaluationItem(value: any): EvaluationItem {
  const scoreSur20 = value?.scoreSur20 != null ? Number(value.scoreSur20) : undefined;
  const appreciation =
    value?.appreciation
    ?? value?.appreciationLibelle
    ?? appreciationFromScore(scoreSur20);
  return {
    identifiant: idOf(value),
    campaignNom: value?.campaignNom ?? value?.campagneNom ?? '',
    collaborateurIdentifiant: String(value?.collaborateurIdentifiant ?? ''),
    superieurIdentifiant: String(value?.superieurIdentifiant ?? ''),
    collaborateurNom: value?.collaborateurNom,
    collaborateurMatricule: value?.collaborateurMatricule,
    superieurNom: value?.superieurNom,
    statut: value?.statut ?? '',
    etapeActuelle: value?.etapeActuelle ?? '',
    scoreSur20,
    appreciation,
    couleurAlerte: normalizeCouleurAlerte(value?.couleurAlerte ?? value?.couleur_alerte),
    profilMetierIncomplet: Boolean(value?.profilMetierIncomplet ?? value?.profil_metier_incomplet),
    creeLe: value?.creeLe ?? new Date().toISOString(),
  };
}

function normalizeCampaign(value: any): EvaluationCampaign {
  return {
    ...value,
    identifiant: idOf(value),
    templateGeneral: value?.templateGeneral,
    templateTechnique: value?.templateTechnique
  };
}

export const campaignApi = {
  async list(): Promise<EvaluationCampaign[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/campaigns');
    return response.data.map(normalizeCampaign);
  },
  async create(data: CreateCampaignRequest): Promise<EvaluationCampaign> {
    const response = await api.post('/api/rh/v1/admin/evaluations/campaigns', data);
    return normalizeCampaign(response.data);
  },
  async activate(id: string): Promise<ActivateCampaignResult> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/campaigns/${id}/activate`);
    return (response.data && typeof response.data === 'object') ? response.data : {};
  },
  async terminate(id: string): Promise<void> {
    await api.post(`/api/rh/v1/admin/evaluations/campaigns/${id}/terminate`);
  },
  async assignTemplates(campaignId: string, templateGeneralId?: string, templateTechniqueId?: string): Promise<void> {
    await api.post(`/api/rh/v1/admin/evaluations/campaigns/${campaignId}/assign-templates`, {
      templateGeneralId,
      templateTechniqueId
    });
  },
  async analytics(campaignId: string): Promise<CampaignAnalytics> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/campaigns/${campaignId}/analytics`);
    return response.data;
  }
};

export const parametresApi = {
  async get(): Promise<EvaluationParametres> {
    const response = await api.get('/api/rh/v1/admin/evaluations/parametres');
    return response.data;
  },
  async update(dureeCampagneJours: number): Promise<EvaluationParametres> {
    const response = await api.put('/api/rh/v1/admin/evaluations/parametres', { dureeCampagneJours });
    return response.data;
  },
};

export type TemplateListFilters = {
  type?: string;
  statut?: string;
  familleMetierCode?: string;
  niveauSeniorite?: string;
};

export const templateApi = {
  async list(): Promise<EvaluationTemplate[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/templates');
    return response.data.map(normalizeTemplate);
  },
  async createV2(data: CreateTemplateRequest, userId: string): Promise<EvaluationTemplate> {
    const famille = data.famille_metier_code ?? data.familleMetierCode ?? data.role;
    const niveau = normalizeNiveauSeniorite(data.niveau_seniorite ?? data.niveauSeniorite);
    // Compat backend actuel (`role` + `niveauSeniorite`) + contrat cible BA.
    const body: CreateTemplateRequest = {
      ...data,
      famille_metier_code: famille,
      familleMetierCode: famille,
      niveau_seniorite: niveau,
      niveauSeniorite: niveau,
      role: data.type === 'TECHNICAL' ? famille : undefined,
    };
    const response = await api.post(`/api/rh/v1/admin/evaluations/v2?userId=${userId}`, body);
    return normalizeTemplate(response.data);
  },
  async listV2(typeOrFilters?: string | TemplateListFilters, statut?: string): Promise<EvaluationTemplate[]> {
    const filters: TemplateListFilters =
      typeof typeOrFilters === 'string' || typeOrFilters == null
        ? { type: typeOrFilters, statut }
        : typeOrFilters;
    const params = new URLSearchParams();
    if (filters.type) params.append('type', filters.type);
    if (filters.statut) params.append('statut', filters.statut);
    if (filters.familleMetierCode) {
      params.append('famille_metier_code', filters.familleMetierCode);
      params.append('role', filters.familleMetierCode);
    }
    if (filters.niveauSeniorite) {
      params.append('niveau_seniorite', filters.niveauSeniorite);
      params.append('niveauSeniorite', filters.niveauSeniorite);
    }
    const suffix = params.toString() ? `?${params.toString()}` : '';
    const response = await api.get(`/api/rh/v1/admin/evaluations/v2${suffix}`);
    return response.data.map(normalizeTemplate);
  },
  async getByIdV2(templateId: string): Promise<EvaluationTemplate> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/v2/${templateId}`);
    return normalizeTemplate(response.data);
  },
  async publish(templateId: string, userId: string): Promise<EvaluationTemplate> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/v2/${templateId}/publish?userId=${userId}`);
    return normalizeTemplate(response.data);
  },
  async archive(templateId: string): Promise<void> {
    await api.post(`/api/rh/v1/admin/evaluations/v2/${templateId}/archive`);
  },
  async addQuestionV2(templateId: string, data: CreateQuestionRequest): Promise<EvaluationQuestion> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/v2/${templateId}/questions`, data);
    return normalizeQuestion(response.data);
  },
  async reorderQuestions(templateId: string, questionIds: string[]): Promise<void> {
    await api.post(`/api/rh/v1/admin/evaluations/v2/${templateId}/questions/reorder`, questionIds);
  },
  async listTechnical(): Promise<TechnicalTemplate[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/technical-templates');
    return response.data;
  },
  async createTechnical(data: CreateTechnicalTemplateRequest): Promise<TechnicalTemplate> {
    const response = await api.post('/api/rh/v1/admin/evaluations/technical-templates', data);
    return response.data;
  },
  async getTechnicalQuestions(templateId: string): Promise<TechnicalQuestion[]> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/technical-templates/${templateId}/questions`);
    return response.data;
  },
  async addTechnicalQuestion(templateId: string, data: Omit<TechnicalQuestion, 'identifiant' | 'actif'>): Promise<TechnicalQuestion> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/technical-templates/${templateId}/questions`, data);
    return response.data;
  }
};

export const technicalTemplateApi = {
  getQuestions: templateApi.getTechnicalQuestions,
  addQuestion: templateApi.addTechnicalQuestion
};

export const evaluationApi = {
  async list(opts?: { campagneId?: string; profilIncomplet?: boolean }): Promise<EvaluationItem[]> {
    const params = new URLSearchParams();
    if (opts?.campagneId) params.append('campagneId', opts.campagneId);
    if (opts?.profilIncomplet) params.append('profilIncomplet', 'true');
    const suffix = params.toString() ? `?${params.toString()}` : '';
    const response = await api.get(`/api/rh/v1/admin/evaluations${suffix}`);
    return response.data.map(normalizeEvaluationItem);
  },
  async getById(id: string): Promise<EvaluationItem> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/${id}`);
    return normalizeEvaluationItem(response.data);
  },
  async getByCollaborateur(collaborateurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/collaborateur/${collaborateurId}`);
    return response.data.map(normalizeEvaluationItem);
  },
  async getBySuperieur(superieurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/superieur/${superieurId}`);
    return response.data.map(normalizeEvaluationItem);
  },
  async analytics(id: string): Promise<EvaluationAnalytics> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/${id}/analytics`);
    return response.data;
  }
};

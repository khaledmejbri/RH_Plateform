import axios from 'axios';
import { getToken } from './auth';

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

export interface EvaluationCampaign {
  identifiant: string;
  nom: string;
  description?: string;
  type: 'ANNUELLE' | 'SEMESTRIELLE';
  statut: 'PLANIFIEE' | 'ACTIVE' | 'TERMINEE';
  annee: number;
  moisDebut: number;
  moisFin: number;
  dateDebut: string;
  dateFin: string;
  creeLe: string;
  templateGeneral?: { identifiant: string; nom: string };
  templateTechnique?: { identifiant: string; nom: string };
}

export interface CreateCampaignRequest {
  nom: string;
  description?: string;
  type: 'ANNUELLE' | 'SEMESTRIELLE';
  annee: number;
  moisDebut: number;
  moisFin: number;
  creePar: string;
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
  niveauSeniorite?: string;
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
  niveauSeniorite?: string;
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
  statut: string;
  etapeActuelle: string;
  scoreSur20?: number;
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

function normalizeTemplate(value: any): EvaluationTemplate {
  return {
    identifiant: idOf(value),
    nom: value?.nom ?? '',
    description: value?.description,
    type: value?.type ?? 'GENERIC',
    statut: value?.statut ?? 'DRAFT',
    version: value?.version ?? 1,
    niveauSeniorite: value?.niveauSeniorite,
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
  async activate(id: string): Promise<void> {
    await api.post(`/api/rh/v1/admin/evaluations/campaigns/${id}/activate`);
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

export const templateApi = {
  async list(): Promise<EvaluationTemplate[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/templates');
    return response.data.map(normalizeTemplate);
  },
  async createV2(data: CreateTemplateRequest, userId: string): Promise<EvaluationTemplate> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/v2?userId=${userId}`, data);
    return normalizeTemplate(response.data);
  },
  async listV2(type?: string, statut?: string): Promise<EvaluationTemplate[]> {
    const params = new URLSearchParams();
    if (type) params.append('type', type);
    if (statut) params.append('statut', statut);
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
  async list(): Promise<EvaluationItem[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations');
    return response.data.map((item: any) => ({ ...item, identifiant: idOf(item) }));
  },
  async getById(id: string): Promise<EvaluationItem> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/${id}`);
    return { ...response.data, identifiant: idOf(response.data) };
  },
  async getByCollaborateur(collaborateurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/collaborateur/${collaborateurId}`);
    return response.data.map((item: any) => ({ ...item, identifiant: idOf(item) }));
  },
  async getBySuperieur(superieurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/superieur/${superieurId}`);
    return response.data.map((item: any) => ({ ...item, identifiant: idOf(item) }));
  },
  async analytics(id: string): Promise<EvaluationAnalytics> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/${id}/analytics`);
    return response.data;
  }
};

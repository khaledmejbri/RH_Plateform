import axios from 'axios';
import { getToken } from './auth';

const api = axios.create({
  baseURL: '', // Use Vite proxy
  headers: {
    'Content-Type': 'application/json'
  }
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  console.log('[API Request]', config.method?.toUpperCase(), config.url);
  return config;
});

// Log errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('[API Error]', error.response?.status, error.config?.url, error.message);
    if (error.response?.status === 403) {
      console.error('403 Forbidden - Check authentication and security config');
    }
    return Promise.reject(error);
  }
);

// ========== Campaign API ==========

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
  creePar?: string;
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

export const campaignApi = {
  async list(): Promise<EvaluationCampaign[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/campaigns');
    return response.data;
  },

  async create(data: CreateCampaignRequest): Promise<EvaluationCampaign> {
    const response = await api.post('/api/rh/v1/admin/evaluations/campaigns', data);
    return response.data;
  },

  async activate(id: string): Promise<EvaluationCampaign> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/campaigns/${id}/activate`);
    return response.data;
  },

  async terminate(id: string): Promise<EvaluationCampaign> {
    const response = await api.post(`/api/rh/v1/admin/evaluations/campaigns/${id}/terminate`);
    return response.data;
  },

  async assignTemplates(
    campaignId: string,
    templateGeneralId?: string,
    templateTechniqueId?: string
  ): Promise<EvaluationCampaign> {
    const response = await api.post(
      `/api/rh/v1/admin/evaluations/campaigns/${campaignId}/assign-templates`,
      { templateGeneralId, templateTechniqueId }
    );
    return response.data;
  }
};

// ========== General Template API ==========

export interface EvaluationTemplate {
  identifiant: string;
  nom: string;
  description?: string;
  actif: boolean;
  creeLe: string;
  creePar?: string;
}

export interface CreateTemplateRequest {
  nom: string;
  description?: string;
  creePar: string;
}

export interface EvaluationQuestion {
  identifiant: string;
  libelle: string;
  typeQuestion: 'TEXTE_LIBRE' | 'ECHELLE' | 'CHOIX_MULTIPLE';
  ordre: number;
  obligatoire: boolean;
  optionsReponses?: string;
  valeurMinimale?: number;
  valeurMaximale?: number;
  actif: boolean;
}

export interface CreateQuestionRequest {
  libelle: string;
  typeQuestion: 'TEXTE_LIBRE' | 'ECHELLE' | 'CHOIX_MULTIPLE';
  ordre: number;
  obligatoire: boolean;
  optionsReponses?: string;
  valeurMinimale?: number;
  valeurMaximale?: number;
}

export const templateApi = {
  async list(): Promise<EvaluationTemplate[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/templates');
    return response.data;
  },

  async create(data: CreateTemplateRequest): Promise<EvaluationTemplate> {
    const response = await api.post('/api/rh/v1/admin/evaluations/templates', data);
    return response.data;
  },

  async delete(id: string): Promise<void> {
    await api.delete(`/api/rh/v1/admin/evaluations/templates/${id}`);
  },

  async getQuestions(templateId: string): Promise<EvaluationQuestion[]> {
    const response = await api.get(
      `/api/rh/v1/admin/evaluations/templates/${templateId}/questions`
    );
    return response.data;
  },

  async addQuestion(
    templateId: string,
    data: CreateQuestionRequest
  ): Promise<EvaluationQuestion> {
    const response = await api.post(
      `/api/rh/v1/admin/evaluations/templates/${templateId}/questions`,
      data
    );
    return response.data;
  },

  async deleteQuestion(templateId: string, questionId: string): Promise<void> {
    await api.delete(
      `/api/rh/v1/admin/evaluations/templates/${templateId}/questions/${questionId}`
    );
  },

  // Technical templates
  async listTechnical(): Promise<TechnicalTemplate[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations/technical-templates');
    return response.data;
  },

  async createTechnical(data: CreateTechnicalTemplateRequest): Promise<TechnicalTemplate> {
    const response = await api.post('/api/rh/v1/admin/evaluations/technical-templates', data);
    return response.data;
  },

  async getTechnicalQuestions(templateId: string): Promise<TechnicalQuestion[]> {
    const response = await api.get(
      `/api/rh/v1/admin/evaluations/technical-templates/${templateId}/questions`
    );
    return response.data;
  },

  async addTechnicalQuestion(
    templateId: string,
    data: {
      competence: string;
      description?: string;
      niveauxPermis: string;
      ordre: number;
    }
  ): Promise<TechnicalQuestion> {
    const response = await api.post(
      `/api/rh/v1/admin/evaluations/technical-templates/${templateId}/questions`,
      data
    );
    return response.data;
  }
};

// ========== Technical Template API ==========

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

export const technicalTemplateApi = {
  async getQuestions(templateId: string): Promise<TechnicalQuestion[]> {
    const response = await api.get(
      `/api/rh/v1/evaluations/technical-templates/${templateId}/questions`
    );
    return response.data;
  },

  async addQuestion(
    templateId: string,
    data: {
      competence: string;
      description?: string;
      niveauxPermis: string;
      ordre: number;
    }
  ): Promise<TechnicalQuestion> {
    const response = await api.post(
      `/api/rh/v1/evaluations/technical-templates/${templateId}/questions`,
      data
    );
    return response.data;
  }
};

// ========== Evaluations API ==========

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

export const evaluationApi = {
  async list(): Promise<EvaluationItem[]> {
    const response = await api.get('/api/rh/v1/admin/evaluations');
    return response.data;
  },

  async getById(id: string): Promise<EvaluationItem> {
    const response = await api.get(`/api/rh/v1/admin/evaluations/${id}`);
    return response.data;
  },

  async getByCollaborateur(collaborateurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(
      `/api/rh/v1/admin/evaluations/collaborateur/${collaborateurId}`
    );
    return response.data;
  },

  async getBySuperieur(superieurId: string): Promise<EvaluationItem[]> {
    const response = await api.get(
      `/api/rh/v1/admin/evaluations/superieur/${superieurId}`
    );
    return response.data;
  }
};

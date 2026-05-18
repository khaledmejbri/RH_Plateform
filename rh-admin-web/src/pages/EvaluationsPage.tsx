import { useState, useEffect } from 'react';
import {
  evaluationApi,
  campaignApi,
  templateApi,
  type EvaluationCampaign,
  type EvaluationTemplate,
  type EvaluationQuestion,
  type TechnicalTemplate,
  type TechnicalQuestion,
  type EvaluationItem
} from '../api/evaluationApi';

type Tab = 'campaigns' | 'templates' | 'technical' | 'evaluations';

export default function EvaluationsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('campaigns');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Gestion des Évaluations</h1>
        <p className="page__subtitle">Campagnes, templates et suivi des évaluations annuelles</p>
      </div>

      <div className="tabs">
        <button
          className={`tab ${activeTab === 'campaigns' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('campaigns')}
        >
          📅 Campagnes
        </button>
        <button
          className={`tab ${activeTab === 'templates' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('templates')}
        >
          📝 Templates Généraux
        </button>
        <button
          className={`tab ${activeTab === 'technical' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('technical')}
        >
          🔧 Templates Techniques
        </button>
        <button
          className={`tab ${activeTab === 'evaluations' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('evaluations')}
        >
          📊 Évaluations
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'campaigns' && <CampaignsTab />}
        {activeTab === 'templates' && <TemplatesTab />}
        {activeTab === 'technical' && <TechnicalTemplatesTab />}
        {activeTab === 'evaluations' && <EvaluationsTab />}
      </div>
    </div>
  );
}

function CampaignsTab() {
  const [campaigns, setCampaigns] = useState<EvaluationCampaign[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    nom: '',
    description: '',
    type: 'ANNUELLE',
    annee: new Date().getFullYear(),
    moisDebut: 6,
    moisFin: 6,
    templateGeneralId: '',
    templateTechniqueId: ''
  });

  useEffect(() => {
    loadCampaigns();
  }, []);

  const loadCampaigns = async () => {
    try {
      setLoading(true);
      const data = await campaignApi.list();
      setCampaigns(data);
    } catch (err: any) {
      console.error('Error loading campaigns:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await campaignApi.create({
        nom: formData.nom,
        description: formData.description,
        type: formData.type as any,
        annee: formData.annee,
        moisDebut: formData.moisDebut,
        moisFin: formData.moisFin,
        creePar: '0a4f7069-0737-4207-97dd-7a46a45f5429' // TODO: Get from auth
      });
      setShowForm(false);
      setFormData({
        nom: '',
        description: '',
        type: 'ANNUELLE',
        annee: new Date().getFullYear(),
        moisDebut: 6,
        moisFin: 6,
        templateGeneralId: '',
        templateTechniqueId: ''
      });
      loadCampaigns();
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleActivate = async (id: string) => {
    if (!confirm('Activer cette campagne?')) return;
    try {
      await campaignApi.activate(id);
      loadCampaigns();
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  const handleTerminate = async (id: string) => {
    if (!confirm('Terminer cette campagne?')) return;
    try {
      await campaignApi.terminate(id);
      loadCampaigns();
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  if (loading && campaigns.length === 0) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Campagnes d'évaluation</h2>
        <button className="btn btn--primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Annuler' : '+ Nouvelle campagne'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          <h3>Créer une campagne</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-grid">
              <div className="form-group">
                <label>Nom de la campagne</label>
                <input
                  type="text"
                  value={formData.nom}
                  onChange={e => setFormData({ ...formData, nom: e.target.value })}
                  required
                  placeholder="Évaluation Annuelle 2026"
                />
              </div>

              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={formData.description}
                  onChange={e => setFormData({ ...formData, description: e.target.value })}
                  rows={3}
                />
              </div>

              <div className="form-group">
                <label>Type</label>
                <select
                  value={formData.type}
                  onChange={e => setFormData({ ...formData, type: e.target.value })}
                >
                  <option value="ANNUELLE">Annuelle</option>
                  <option value="SEMESTRIELLE">Semestrielle</option>
                </select>
              </div>

              <div className="form-group">
                <label>Année</label>
                <input
                  type="number"
                  value={formData.annee}
                  onChange={e => setFormData({ ...formData, annee: parseInt(e.target.value) })}
                  required
                />
              </div>

              <div className="form-group">
                <label>Mois de début</label>
                <select
                  value={formData.moisDebut}
                  onChange={e => setFormData({ ...formData, moisDebut: parseInt(e.target.value) })}
                  required
                >
                  <option value={6}>Juin</option>
                  <option value={12}>Décembre</option>
                </select>
              </div>

              <div className="form-group">
                <label>Mois de fin</label>
                <select
                  value={formData.moisFin}
                  onChange={e => setFormData({ ...formData, moisFin: parseInt(e.target.value) })}
                  required
                >
                  <option value={6}>Juin</option>
                  <option value={12}>Décembre</option>
                </select>
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn--primary" disabled={loading}>
                {loading ? 'Création...' : 'Créer la campagne'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="table-container">
        <table className="table">
          <thead>
            <tr>
              <th>Nom</th>
              <th>Type</th>
              <th>Période</th>
              <th>Statut</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {campaigns.map(campaign => (
              <tr key={campaign.identifiant}>
                <td>
                  <strong>{campaign.nom}</strong>
                  {campaign.description && (
                    <div className="text-sm text-muted">{campaign.description}</div>
                  )}
                </td>
                <td>
                  <span className="badge badge--info">
                    {campaign.type === 'ANNUELLE' ? 'Annuelle' : 'Semestrielle'}
                  </span>
                </td>
                <td>
                  <div>{getMonthName(campaign.moisDebut)} {campaign.annee}</div>
                  <div className="text-sm text-muted">
                    à {getMonthName(campaign.moisFin)} {campaign.annee}
                  </div>
                </td>
                <td>
                  <StatusBadge status={campaign.statut} />
                </td>
                <td>
                  <div className="actions">
                    {campaign.statut === 'PLANIFIEE' && (
                      <button
                        className="btn btn--success btn--sm"
                        onClick={() => handleActivate(campaign.identifiant)}
                      >
                        Activer
                      </button>
                    )}
                    {campaign.statut === 'ACTIVE' && (
                      <button
                        className="btn btn--warning btn--sm"
                        onClick={() => handleTerminate(campaign.identifiant)}
                      >
                        Terminer
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TemplatesTab() {
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [questions, setQuestions] = useState<Record<string, EvaluationQuestion[]>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);
  const [showQuestionForm, setShowQuestionForm] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    nom: '',
    description: ''
  });
  const [questionData, setQuestionData] = useState({
    libelle: '',
    typeQuestion: 'TEXTE_LIBRE' as 'TEXTE_LIBRE' | 'ECHELLE' | 'CHOIX_MULTIPLE',
    ordre: 1,
    obligatoire: false,
    optionsReponses: '',
    valeurMinimale: 1,
    valeurMaximale: 5
  });

  useEffect(() => {
    loadTemplates();
  }, []);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const data = await templateApi.list();
      setTemplates(data);
    } catch (err: any) {
      console.error('Error loading templates:', err);
      alert('Erreur lors du chargement des templates');
    } finally {
      setLoading(false);
    }
  };

  const loadQuestions = async (templateId: string) => {
    try {
      const data = await templateApi.getQuestions(templateId);
      setQuestions(prev => ({ ...prev, [templateId]: data }));
    } catch (err: any) {
      console.error('Error loading questions:', err);
      alert('Erreur lors du chargement des questions');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await templateApi.create({
        nom: formData.nom,
        description: formData.description,
        creePar: '0a4f7069-0737-4207-97dd-7a46a45f5429'
      });
      setShowForm(false);
      setFormData({ nom: '', description: '' });
      loadTemplates();
      alert('Template créé avec succès!');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Supprimer ce template? Cette action est irréversible.')) return;
    try {
      await templateApi.delete(id);
      loadTemplates();
      alert('Template supprimé avec succès');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  const handleAddQuestion = async (templateId: string, e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await templateApi.addQuestion(templateId, {
        libelle: questionData.libelle,
        typeQuestion: questionData.typeQuestion,
        ordre: questionData.ordre,
        obligatoire: questionData.obligatoire,
        optionsReponses: questionData.typeQuestion === 'CHOIX_MULTIPLE' ? questionData.optionsReponses : undefined,
        valeurMinimale: questionData.typeQuestion === 'ECHELLE' ? questionData.valeurMinimale : undefined,
        valeurMaximale: questionData.typeQuestion === 'ECHELLE' ? questionData.valeurMaximale : undefined,
        creePar: '0a4f7069-0737-4207-97dd-7a46a45f5429'
      });
      setShowQuestionForm(null);
      setQuestionData({
        libelle: '',
        typeQuestion: 'TEXTE_LIBRE',
        ordre: 1,
        obligatoire: false,
        optionsReponses: '',
        valeurMinimale: 1,
        valeurMaximale: 5
      });
      loadQuestions(templateId);
      alert('Question ajoutée avec succès!');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteQuestion = async (templateId: string, questionId: string) => {
    if (!confirm('Supprimer cette question?')) return;
    try {
      await templateApi.deleteQuestion(templateId, questionId);
      loadQuestions(templateId);
      alert('Question supprimée avec succès');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  if (loading && templates.length === 0) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Templates d'évaluation générale</h2>
        <button className="btn btn--primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Annuler' : '+ Nouveau template'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          <h3>Créer un template</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-grid">
              <div className="form-group">
                <label>Nom du template *</label>
                <input
                  type="text"
                  value={formData.nom}
                  onChange={e => setFormData({ ...formData, nom: e.target.value })}
                  required
                  placeholder="Template évaluation 2026"
                />
              </div>

              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={formData.description}
                  onChange={e => setFormData({ ...formData, description: e.target.value })}
                  rows={3}
                  placeholder="Description du template..."
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="button" className="btn btn--ghost" onClick={() => setShowForm(false)}>
                Annuler
              </button>
              <button type="submit" className="btn btn--primary" disabled={loading}>
                {loading ? 'Création...' : 'Créer le template'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid--2">
        {templates.map(template => (
          <div key={template.identifiant} className="card">
            <div className="card__header">
              <h3>{template.nom}</h3>
              <div className="card__actions">
                <button
                  className="btn btn--sm"
                  onClick={() => {
                    if (selectedTemplate === template.identifiant) {
                      setSelectedTemplate(null);
                    } else {
                      setSelectedTemplate(template.identifiant);
                      loadQuestions(template.identifiant);
                    }
                  }}
                >
                  {selectedTemplate === template.identifiant ? 'Masquer' : 'Voir questions'}
                </button>
                <button
                  className="btn btn--danger btn--sm"
                  onClick={() => handleDelete(template.identifiant)}
                >
                  Supprimer
                </button>
              </div>
            </div>
            <p className="text-muted">{template.description || 'Aucune description'}</p>
            <div className="text-sm">
              Créé le {new Date(template.creeLe).toLocaleDateString('fr-FR')}
            </div>

            {selectedTemplate === template.identifiant && (
              <div className="questions-list">
                <div className="questions-header">
                  <h4>Questions ({questions[template.identifiant]?.length || 0})</h4>
                  <button
                    className="btn btn--primary btn--sm"
                    onClick={() => setShowQuestionForm(
                      showQuestionForm === template.identifiant ? null : template.identifiant
                    )}
                  >
                    {showQuestionForm === template.identifiant ? 'Annuler' : '+ Ajouter question'}
                  </button>
                </div>

                {showQuestionForm === template.identifiant && (
                  <div className="question-form">
                    <h5>Nouvelle question</h5>
                    <form onSubmit={(e) => handleAddQuestion(template.identifiant, e)}>
                      <div className="form-grid">
                        <div className="form-group">
                          <label>Libellé *</label>
                          <input
                            type="text"
                            value={questionData.libelle}
                            onChange={e => setQuestionData({ ...questionData, libelle: e.target.value })}
                            required
                            placeholder="Ex: Évaluez votre performance globale"
                          />
                        </div>

                        <div className="form-group">
                          <label>Type de question *</label>
                          <select
                            value={questionData.typeQuestion}
                            onChange={e => setQuestionData({ 
                              ...questionData, 
                              typeQuestion: e.target.value as any,
                              ordre: questions[template.identifiant]?.length + 1 || 1
                            })}
                            required
                          >
                            <option value="TEXTE_LIBRE">Texte libre</option>
                            <option value="ECHELLE">Échelle (1-5)</option>
                            <option value="CHOIX_MULTIPLE">Choix multiple</option>
                          </select>
                        </div>

                        <div className="form-group">
                          <label>Ordre</label>
                          <input
                            type="number"
                            value={questionData.ordre}
                            onChange={e => setQuestionData({ ...questionData, ordre: parseInt(e.target.value) })}
                            min="1"
                            required
                          />
                        </div>

                        <div className="form-group checkbox-group">
                          <label>
                            <input
                              type="checkbox"
                              checked={questionData.obligatoire}
                              onChange={e => setQuestionData({ ...questionData, obligatoire: e.target.checked })}
                            />
                            Question obligatoire
                          </label>
                        </div>

                        {questionData.typeQuestion === 'ECHELLE' && (
                          <>
                            <div className="form-group">
                              <label>Valeur minimale</label>
                              <input
                                type="number"
                                value={questionData.valeurMinimale}
                                onChange={e => setQuestionData({ ...questionData, valeurMinimale: parseInt(e.target.value) })}
                                min="0"
                              />
                            </div>
                            <div className="form-group">
                              <label>Valeur maximale</label>
                              <input
                                type="number"
                                value={questionData.valeurMaximale}
                                onChange={e => setQuestionData({ ...questionData, valeurMaximale: parseInt(e.target.value) })}
                                min="1"
                              />
                            </div>
                          </>
                        )}

                        {questionData.typeQuestion === 'CHOIX_MULTIPLE' && (
                          <div className="form-group full-width">
                            <label>Options (séparées par des virgules)</label>
                            <textarea
                              value={questionData.optionsReponses}
                              onChange={e => setQuestionData({ ...questionData, optionsReponses: e.target.value })}
                              rows={2}
                              placeholder="Excellent, Bon, Moyen, À améliorer"
                            />
                          </div>
                        )}
                      </div>

                      <div className="form-actions">
                        <button type="submit" className="btn btn--primary" disabled={loading}>
                          {loading ? 'Ajout...' : 'Ajouter la question'}
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {questions[template.identifiant]?.map((q, idx) => (
                  <div key={q.identifiant} className="question-item">
                    <div className="question-number">{q.ordre}</div>
                    <div className="question-content">
                      <div className="question-text">{q.libelle}</div>
                      <div className="question-meta">
                        <span className="badge badge--info">{q.typeQuestion}</span>
                        {q.obligatoire && <span className="badge badge--warning">Obligatoire</span>}
                        {q.typeQuestion === 'ECHELLE' && (
                          <span className="badge badge--default">
                            {q.valeurMinimale}-{q.valeurMaximale}
                          </span>
                        )}
                      </div>
                    </div>
                    <button
                      className="btn btn--danger btn--sm"
                      onClick={() => handleDeleteQuestion(template.identifiant, q.identifiant)}
                      title="Supprimer"
                    >
                      ×
                    </button>
                  </div>
                ))}

                {(!questions[template.identifiant] || questions[template.identifiant].length === 0) && (
                  <div className="empty-state">
                    <p>Aucune question dans ce template</p>
                    <button
                      className="btn btn--primary btn--sm"
                      onClick={() => setShowQuestionForm(template.identifiant)}
                    >
                      + Ajouter la première question
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}

        {templates.length === 0 && !loading && (
          <div className="empty-state full-width">
            <h3>Aucun template créé</h3>
            <p>Commencez par créer un template d'évaluation</p>
            <button className="btn btn--primary" onClick={() => setShowForm(true)}>
              + Créer un template
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function TechnicalTemplatesTab() {
  const [templates, setTemplates] = useState<TechnicalTemplate[]>([]);
  const [questions, setQuestions] = useState<Record<string, TechnicalQuestion[]>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);
  const [showQuestionForm, setShowQuestionForm] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    nom: '',
    description: '',
    niveauSeniorite: 'SENIOR',
    role: '',
    domaine: ''
  });
  const [questionData, setQuestionData] = useState({
    competence: '',
    description: '',
    niveauxPermis: '',
    ordre: 1
  });

  useEffect(() => {
    loadTemplates();
  }, []);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const data = await templateApi.listTechnical();
      setTemplates(data);
    } catch (err: any) {
      console.error('Error loading technical templates:', err);
      alert('Erreur lors du chargement des templates');
    } finally {
      setLoading(false);
    }
  };

  const loadQuestions = async (templateId: string) => {
    try {
      const data = await templateApi.getTechnicalQuestions(templateId);
      setQuestions(prev => ({ ...prev, [templateId]: data }));
    } catch (err: any) {
      console.error('Error loading questions:', err);
      alert('Erreur lors du chargement des questions');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await templateApi.createTechnical({
        nom: formData.nom,
        description: formData.description,
        niveauSeniorite: formData.niveauSeniorite,
        role: formData.role,
        domaine: formData.domaine,
        creePar: '0a4f7069-0737-4207-97dd-7a46a45f5429'
      });
      setShowForm(false);
      setFormData({ nom: '', description: '', niveauSeniorite: 'SENIOR', role: '', domaine: '' });
      loadTemplates();
      alert('Template technique créé avec succès!');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAddQuestion = async (templateId: string, e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await templateApi.addTechnicalQuestion(templateId, {
        competence: questionData.competence,
        description: questionData.description,
        niveauxPermis: questionData.niveauxPermis,
        ordre: questionData.ordre
      });
      setShowQuestionForm(null);
      setQuestionData({ competence: '', description: '', niveauxPermis: '', ordre: 1 });
      loadQuestions(templateId);
      alert('Compétence ajoutée avec succès!');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading && templates.length === 0) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Templates de compétences techniques</h2>
        <button className="btn btn--primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Annuler' : '+ Nouveau template technique'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          <h3>Créer un template technique</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-grid">
              <div className="form-group">
                <label>Nom *</label>
                <input
                  type="text"
                  value={formData.nom}
                  onChange={e => setFormData({ ...formData, nom: e.target.value })}
                  required
                  placeholder="Développeur Senior - Backend"
                />
              </div>

              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={formData.description}
                  onChange={e => setFormData({ ...formData, description: e.target.value })}
                  rows={3}
                  placeholder="Description du profil..."
                />
              </div>

              <div className="form-group">
                <label>Niveau de séniorité *</label>
                <select
                  value={formData.niveauSeniorite}
                  onChange={e => setFormData({ ...formData, niveauSeniorite: e.target.value })}
                  required
                >
                  <option value="JUNIOR">Junior (0-2 ans)</option>
                  <option value="MID">Mid-level (2-5 ans)</option>
                  <option value="SENIOR">Senior (5-8 ans)</option>
                  <option value="EXPERT">Expert (8+ ans)</option>
                </select>
              </div>

              <div className="form-group">
                <label>Role *</label>
                <input
                  type="text"
                  value={formData.role}
                  onChange={e => setFormData({ ...formData, role: e.target.value })}
                  required
                  placeholder="DEVELOPPEUR"
                />
              </div>

              <div className="form-group">
                <label>Domaine</label>
                <input
                  type="text"
                  value={formData.domaine}
                  onChange={e => setFormData({ ...formData, domaine: e.target.value })}
                  placeholder="IT, RH, Finance..."
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="button" className="btn btn--ghost" onClick={() => setShowForm(false)}>
                Annuler
              </button>
              <button type="submit" className="btn btn--primary" disabled={loading}>
                {loading ? 'Création...' : 'Créer le template'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid--3">
        {templates.map(template => (
          <div key={template.identifiant} className="card">
            <div className="card__header">
              <h3>{template.nom}</h3>
              <button
                className="btn btn--sm"
                onClick={() => {
                  if (selectedTemplate === template.identifiant) {
                    setSelectedTemplate(null);
                  } else {
                    setSelectedTemplate(template.identifiant);
                    loadQuestions(template.identifiant);
                  }
                }}
              >
                {selectedTemplate === template.identifiant ? 'Masquer' : 'Voir compétences'}
              </button>
            </div>
            <p className="text-muted">{template.description || 'Aucune description'}</p>
            <div className="template-meta">
              <div>
                <strong>Niveau:</strong> {template.niveauSeniorite}
              </div>
              <div>
                <strong>Role:</strong> {template.role}
              </div>
              {template.domaine && (
                <div>
                  <strong>Domaine:</strong> {template.domaine}
                </div>
              )}
            </div>

            {selectedTemplate === template.identifiant && (
              <div className="questions-list">
                <div className="questions-header">
                  <h4>Compétences ({questions[template.identifiant]?.length || 0})</h4>
                  <button
                    className="btn btn--primary btn--sm"
                    onClick={() => setShowQuestionForm(
                      showQuestionForm === template.identifiant ? null : template.identifiant
                    )}
                  >
                    {showQuestionForm === template.identifiant ? 'Annuler' : '+ Ajouter'}
                  </button>
                </div>

                {showQuestionForm === template.identifiant && (
                  <div className="question-form">
                    <h5>Nouvelle compétence</h5>
                    <form onSubmit={(e) => handleAddQuestion(template.identifiant, e)}>
                      <div className="form-grid">
                        <div className="form-group full-width">
                          <label>Compétence *</label>
                          <input
                            type="text"
                            value={questionData.competence}
                            onChange={e => setQuestionData({ ...questionData, competence: e.target.value })}
                            required
                            placeholder="Ex: Java/Spring Boot"
                          />
                        </div>

                        <div className="form-group full-width">
                          <label>Description</label>
                          <textarea
                            value={questionData.description}
                            onChange={e => setQuestionData({ ...questionData, description: e.target.value })}
                            rows={2}
                            placeholder="Description détaillée de la compétence..."
                          />
                        </div>

                        <div className="form-group full-width">
                          <label>Niveaux permis (séparés par virgules)</label>
                          <input
                            type="text"
                            value={questionData.niveauxPermis}
                            onChange={e => setQuestionData({ ...questionData, niveauxPermis: e.target.value })}
                            placeholder="Débutant, Intermédiaire, Avancé, Expert"
                          />
                        </div>

                        <div className="form-group">
                          <label>Ordre</label>
                          <input
                            type="number"
                            value={questionData.ordre}
                            onChange={e => setQuestionData({ ...questionData, ordre: parseInt(e.target.value) })}
                            min="1"
                          />
                        </div>
                      </div>

                      <div className="form-actions">
                        <button type="submit" className="btn btn--primary" disabled={loading}>
                          {loading ? 'Ajout...' : 'Ajouter'}
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {questions[template.identifiant]?.map((q, idx) => (
                  <div key={q.identifiant} className="question-item">
                    <div className="question-number">{q.ordre}</div>
                    <div className="question-content">
                      <div className="question-text">{q.competence}</div>
                      {q.description && (
                        <div className="text-sm text-muted">{q.description}</div>
                      )}
                      {q.niveauxPermis && (
                        <div className="question-meta">
                          <span className="badge badge--info">{q.niveauxPermis}</span>
                        </div>
                      )}
                    </div>
                  </div>
                ))}

                {(!questions[template.identifiant] || questions[template.identifiant].length === 0) && (
                  <div className="empty-state">
                    <p>Aucune compétence définie</p>
                    <button
                      className="btn btn--primary btn--sm"
                      onClick={() => setShowQuestionForm(template.identifiant)}
                    >
                      + Ajouter la première compétence
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}

        {templates.length === 0 && !loading && (
          <div className="empty-state full-width">
            <h3>Aucun template technique</h3>
            <p>Créez des templates pour évaluer les compétences techniques</p>
            <button className="btn btn--primary" onClick={() => setShowForm(true)}>
              + Créer un template
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function EvaluationsTab() {
  const [evaluations, setEvaluations] = useState<EvaluationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    loadEvaluations();
  }, []);

  const loadEvaluations = async () => {
    try {
      setLoading(true);
      const data = await evaluationApi.list();
      setEvaluations(data);
    } catch (err: any) {
      console.error('Error loading evaluations:', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredEvaluations = evaluations.filter(e =>
    e.campaignNom.toLowerCase().includes(filter.toLowerCase())
  );

  if (loading && evaluations.length === 0) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Suivi des évaluations</h2>
        <input
          type="text"
          placeholder="Filtrer par campagne..."
          value={filter}
          onChange={e => setFilter(e.target.value)}
          className="search-input"
        />
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{evaluations.length}</div>
          <div className="stat-label">Total évaluations</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">
            {evaluations.filter(e => e.statut === 'VALIDEE').length}
          </div>
          <div className="stat-label">Validées</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">
            {evaluations.filter(e => e.statut === 'EN_ATTENTE').length}
          </div>
          <div className="stat-label">En attente</div>
        </div>
      </div>

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
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {filteredEvaluations.map(evaluation => (
              <tr key={evaluation.identifiant}>
                <td>
                  <strong>{evaluation.campaignNom}</strong>
                </td>
                <td>{evaluation.collaborateurIdentifiant}</td>
                <td>{evaluation.superieurIdentifiant}</td>
                <td>
                  <span className="badge badge--info">
                    {evaluation.etapeActuelle === 'EVALUATION_GENERALE'
                      ? 'Générale'
                      : 'Technique'}
                  </span>
                </td>
                <td>
                  <StatusBadge status={evaluation.statut} />
                </td>
                <td>
                  {evaluation.scoreSur20 ? `${evaluation.scoreSur20}/20` : '-'}
                </td>
                <td>{new Date(evaluation.creeLe).toLocaleDateString('fr-FR')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const config: Record<string, { label: string; className: string }> = {
    PLANIFIEE: { label: 'Planifiée', className: 'badge--info' },
    ACTIVE: { label: 'Active', className: 'badge--success' },
    TERMINEE: { label: 'Terminée', className: 'badge--default' },
    EN_ATTENTE: { label: 'En attente', className: 'badge--warning' },
    EN_COURS: { label: 'En cours', className: 'badge--info' },
    VALIDEE_COLLABORATEUR: { label: 'Validée (Collab)', className: 'badge--info' },
    VALIDEE_SUPERIEUR: { label: 'Validée (Manager)', className: 'badge--warning' },
    VALIDEE: { label: 'Validée', className: 'badge--success' }
  };

  const { label, className } = config[status] || { label: status, className: 'badge--default' };

  return <span className={`badge ${className}`}>{label}</span>;
}

function getMonthName(month: number): string {
  const months = [
    '', 'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
  ];
  return months[month] || '';
}

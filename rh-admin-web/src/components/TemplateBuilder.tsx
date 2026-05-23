import { useState } from 'react';
import { templateApi, type CreateQuestionRequest } from '../api/evaluationApi';

interface TemplateBuilderProps {
  templateId?: string;
  templateType: 'GENERIC' | 'TECHNICAL';
  onSave: () => void;
  onCancel: () => void;
}

const competencyScale = ['Beginner', 'Supervised', 'Autonomous', 'Advanced', 'Expert'];

export default function TemplateBuilder({ templateType, onSave, onCancel }: TemplateBuilderProps) {
  const [questions, setQuestions] = useState<CreateQuestionRequest[]>([]);
  const [showQuestionForm, setShowQuestionForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [templateInfo, setTemplateInfo] = useState({
    nom: '',
    description: '',
    role: '',
    niveauSeniorite: 'JUNIOR',
    domaine: ''
  });
  const [newQuestion, setNewQuestion] = useState<Partial<CreateQuestionRequest>>({
    typeQuestion: templateType === 'TECHNICAL' ? 'SCALE' : 'PARAGRAPH',
    obligatoire: true,
    valeurMinimale: templateType === 'TECHNICAL' ? 1 : undefined,
    valeurMaximale: templateType === 'TECHNICAL' ? 5 : undefined,
    poids: 1,
    labelsEchelle: templateType === 'TECHNICAL' ? competencyScale : undefined
  });

  const addQuestion = () => {
    if (!newQuestion.libelle?.trim()) {
      alert('Le libelle de la question est requis');
      return;
    }

    const question: CreateQuestionRequest = {
      libelle: newQuestion.libelle,
      description: newQuestion.description,
      typeQuestion: newQuestion.typeQuestion || 'PARAGRAPH',
      ordre: questions.length + 1,
      obligatoire: newQuestion.obligatoire,
      optionsReponses: newQuestion.optionsReponses,
      valeurMinimale: newQuestion.valeurMinimale,
      valeurMaximale: newQuestion.valeurMaximale,
      sectionCode: newQuestion.sectionCode,
      sectionLibelle: newQuestion.sectionLibelle,
      poids: newQuestion.poids || 1,
      labelsEchelle: newQuestion.labelsEchelle,
      placeholder: newQuestion.placeholder
    };

    setQuestions([...questions, question]);
    setShowQuestionForm(false);
    setNewQuestion({
      typeQuestion: templateType === 'TECHNICAL' ? 'SCALE' : 'PARAGRAPH',
      obligatoire: true,
      valeurMinimale: templateType === 'TECHNICAL' ? 1 : undefined,
      valeurMaximale: templateType === 'TECHNICAL' ? 5 : undefined,
      poids: 1,
      labelsEchelle: templateType === 'TECHNICAL' ? competencyScale : undefined
    });
  };

  const saveTemplate = async () => {
    if (!templateInfo.nom.trim()) {
      alert('Le nom du template est requis');
      return;
    }
    if (questions.length === 0) {
      alert('Ajoutez au moins une question');
      return;
    }

    try {
      setLoading(true);
      await templateApi.createV2({
        nom: templateInfo.nom,
        description: templateInfo.description,
        type: templateType,
        role: templateType === 'TECHNICAL' ? templateInfo.role : undefined,
        niveauSeniorite: templateType === 'TECHNICAL' ? templateInfo.niveauSeniorite : undefined,
        domaine: templateType === 'TECHNICAL' ? templateInfo.domaine : undefined,
        questions
      }, '0a4f7069-0737-4207-97dd-7a46a45f5429');
      onSave();
    } catch (error) {
      console.error('Error saving template:', error);
      alert('Echec de la sauvegarde du template');
    } finally {
      setLoading(false);
    }
  };

  const removeQuestion = (index: number) => {
    setQuestions(questions
      .filter((_, currentIndex) => currentIndex !== index)
      .map((question, currentIndex) => ({ ...question, ordre: currentIndex + 1 })));
  };

  const updateSection = (sectionCode: string) => {
    const labels: Record<string, string> = {
      SAVOIR: 'Savoir',
      SAVOIR_FAIRE: 'Savoir-faire',
      SAVOIR_ETRE: 'Savoir-etre',
      OBJECTIVES: 'Objectives',
      TRAINING: 'Training needs',
      FEEDBACK: 'Feedback'
    };
    setNewQuestion({
      ...newQuestion,
      sectionCode,
      sectionLibelle: labels[sectionCode]
    });
  };

  return (
    <div className="template-builder">
      <div className="builder-header">
        <h3>{templateType === 'TECHNICAL' ? 'Template de competences' : 'Template general'}</h3>
        <button className="btn btn--primary" onClick={() => setShowQuestionForm(true)}>
          + Ajouter une question
        </button>
      </div>

      <div className="form-grid">
        <div className="form-group">
          <label>Nom du template *</label>
          <input
            type="text"
            value={templateInfo.nom}
            onChange={e => setTemplateInfo({ ...templateInfo, nom: e.target.value })}
            placeholder={templateType === 'TECHNICAL' ? 'Senior Developer competencies' : 'General evaluation 2026'}
          />
        </div>

        <div className="form-group">
          <label>Description</label>
          <input
            type="text"
            value={templateInfo.description}
            onChange={e => setTemplateInfo({ ...templateInfo, description: e.target.value })}
            placeholder="Usage du template"
          />
        </div>

        {templateType === 'TECHNICAL' && (
          <>
            <div className="form-group">
              <label>Role / famille metier</label>
              <input
                type="text"
                value={templateInfo.role}
                onChange={e => setTemplateInfo({ ...templateInfo, role: e.target.value })}
                placeholder="Developer, Architect, HR, Finance"
              />
            </div>
            <div className="form-group">
              <label>Niveau</label>
              <select
                value={templateInfo.niveauSeniorite}
                onChange={e => setTemplateInfo({ ...templateInfo, niveauSeniorite: e.target.value })}
              >
                <option value="JUNIOR">Junior</option>
                <option value="CONFIRMED">Confirmed</option>
                <option value="SENIOR">Senior</option>
                <option value="ARCHITECT">Architect</option>
                <option value="TEAM_LEAD">Team Lead</option>
                <option value="DIRECTOR">Director</option>
              </select>
            </div>
            <div className="form-group">
              <label>Domaine</label>
              <input
                type="text"
                value={templateInfo.domaine}
                onChange={e => setTemplateInfo({ ...templateInfo, domaine: e.target.value })}
                placeholder="IT, RH, Finance"
              />
            </div>
          </>
        )}
      </div>

      {showQuestionForm && (
        <div className="question-form-modal card">
          <h4>Nouvelle question</h4>
          <div className="form-grid">
            <div className="form-group full-width">
              <label>Libelle *</label>
              <input
                type="text"
                value={newQuestion.libelle || ''}
                onChange={e => setNewQuestion({ ...newQuestion, libelle: e.target.value })}
                placeholder={templateType === 'TECHNICAL'
                  ? 'Technical leadership'
                  : 'What are your objectives for next year?'}
              />
            </div>

            <div className="form-group full-width">
              <label>Description</label>
              <textarea
                value={newQuestion.description || ''}
                onChange={e => setNewQuestion({ ...newQuestion, description: e.target.value })}
                rows={2}
                placeholder="Aide ou contexte pour repondre"
              />
            </div>

            <div className="form-group">
              <label>Section</label>
              <select value={newQuestion.sectionCode || ''} onChange={e => updateSection(e.target.value)}>
                <option value="">Sans section</option>
                <option value="SAVOIR">Savoir</option>
                <option value="SAVOIR_FAIRE">Savoir-faire</option>
                <option value="SAVOIR_ETRE">Savoir-etre</option>
                <option value="OBJECTIVES">Objectives</option>
                <option value="TRAINING">Training needs</option>
                <option value="FEEDBACK">Feedback</option>
              </select>
            </div>

            <div className="form-group">
              <label>Type de question *</label>
              <select
                value={newQuestion.typeQuestion}
                onChange={e => setNewQuestion({ ...newQuestion, typeQuestion: e.target.value as any })}
              >
                <option value="TEXT">Texte court</option>
                <option value="PARAGRAPH">Paragraphe</option>
                <option value="MULTIPLE_CHOICE">Radio</option>
                <option value="CHECKBOX">Cases a cocher</option>
                <option value="RATING">Rating</option>
                <option value="SCALE">Echelle</option>
                <option value="DATE">Date</option>
                <option value="NUMBER">Nombre</option>
              </select>
            </div>

            <div className="form-group">
              <label>Poids</label>
              <input
                type="number"
                min="0.1"
                step="0.1"
                value={newQuestion.poids || 1}
                onChange={e => setNewQuestion({ ...newQuestion, poids: Number(e.target.value) })}
              />
            </div>

            {(newQuestion.typeQuestion === 'MULTIPLE_CHOICE' || newQuestion.typeQuestion === 'CHECKBOX') && (
              <div className="form-group full-width">
                <label>Options, une par ligne</label>
                <textarea
                  value={(newQuestion.optionsReponses || []).join('\n')}
                  onChange={e => setNewQuestion({
                    ...newQuestion,
                    optionsReponses: e.target.value.split('\n').map(option => option.trim()).filter(Boolean)
                  })}
                  rows={4}
                />
              </div>
            )}

            {(newQuestion.typeQuestion === 'RATING' || newQuestion.typeQuestion === 'SCALE' || newQuestion.typeQuestion === 'NUMBER') && (
              <>
                <div className="form-group">
                  <label>Valeur minimale</label>
                  <input
                    type="number"
                    value={newQuestion.valeurMinimale || 1}
                    onChange={e => setNewQuestion({ ...newQuestion, valeurMinimale: Number(e.target.value) })}
                  />
                </div>
                <div className="form-group">
                  <label>Valeur maximale</label>
                  <input
                    type="number"
                    value={newQuestion.valeurMaximale || 5}
                    onChange={e => setNewQuestion({ ...newQuestion, valeurMaximale: Number(e.target.value) })}
                  />
                </div>
              </>
            )}

            <div className="form-group checkbox-group">
              <label>
                <input
                  type="checkbox"
                  checked={Boolean(newQuestion.obligatoire)}
                  onChange={e => setNewQuestion({ ...newQuestion, obligatoire: e.target.checked })}
                />
                Question obligatoire
              </label>
            </div>
          </div>

          <div className="form-actions">
            <button className="btn btn--ghost" onClick={() => setShowQuestionForm(false)}>
              Annuler
            </button>
            <button className="btn btn--primary" onClick={addQuestion}>
              Ajouter
            </button>
          </div>
        </div>
      )}

      <div className="questions-list">
        {questions.map((question, index) => (
          <div key={`${question.ordre}-${question.libelle}`} className="question-item">
            <div className="question-number">{index + 1}</div>
            <div className="question-content">
              <div className="question-label">{question.libelle}</div>
              <div className="question-meta">
                <span className="badge badge--info">{question.typeQuestion}</span>
                {question.sectionLibelle && <span className="badge badge--default">{question.sectionLibelle}</span>}
                <span className="badge badge--default">Poids {question.poids || 1}</span>
                {question.obligatoire && <span className="badge badge--warning">Obligatoire</span>}
              </div>
            </div>
            <button className="btn btn--danger btn--sm" onClick={() => removeQuestion(index)}>
              Supprimer
            </button>
          </div>
        ))}
      </div>

      {questions.length === 0 && !showQuestionForm && (
        <div className="empty-state">
          <p>Aucune question ajoutee</p>
          <button className="btn btn--primary" onClick={() => setShowQuestionForm(true)}>
            + Ajouter la premiere question
          </button>
        </div>
      )}

      <div className="builder-footer form-actions">
        <button className="btn btn--ghost" onClick={onCancel}>
          Annuler
        </button>
        <button className="btn btn--primary btn--lg" onClick={saveTemplate} disabled={loading}>
          {loading ? 'Sauvegarde...' : 'Sauvegarder le template'}
        </button>
      </div>
    </div>
  );
}

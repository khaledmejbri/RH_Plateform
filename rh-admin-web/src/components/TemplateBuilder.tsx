import { useEffect, useState } from 'react';
import { templateApi, type CreateQuestionRequest } from '../api/evaluationApi';
import {
  FAMILLES_METIER_SEED,
  NIVEAUX_SENIORITE,
  type FamilleMetier,
} from '../api/evaluationCatalog';
import { getFamillesMetier } from '../api/rhClient';
import { currentUserId } from '../auth/jwtRoles';

interface TemplateBuilderProps {
  templateId?: string;
  templateType: 'GENERIC' | 'TECHNICAL';
  onSave: () => void;
  onCancel: () => void;
}

const COMPETENCY_SCALE_FR = ['Débutant', 'Supervisé', 'Autonome', 'Avancé', 'Expert'];

const QUESTION_TYPES: Array<{ value: CreateQuestionRequest['typeQuestion']; label: string; hint: string }> = [
  { value: 'TEXT', label: 'Texte court', hint: 'Une ligne' },
  { value: 'PARAGRAPH', label: 'Paragraphe', hint: 'Réponse libre' },
  { value: 'MULTIPLE_CHOICE', label: 'Choix unique', hint: 'Radio — une seule option' },
  { value: 'CHECKBOX', label: 'Cases à cocher', hint: 'Plusieurs options' },
  { value: 'RATING', label: 'Note', hint: 'Score numérique' },
  { value: 'SCALE', label: 'Échelle', hint: 'Ex. 1 à 5 + libellés' },
  { value: 'NUMBER', label: 'Nombre', hint: 'Valeur' },
  { value: 'DATE', label: 'Date', hint: 'Sélecteur' },
];

const GENERAL_SECTIONS = [
  { code: 'OBJECTIFS_N_1', libelle: 'Objectifs année précédente' },
  { code: 'OBJECTIFS_N', libelle: 'Objectifs année en cours' },
  { code: 'SAVOIR', libelle: 'Savoir' },
  { code: 'SAVOIR_FAIRE', libelle: 'Savoir-faire' },
  { code: 'SAVOIR_ETRE', libelle: 'Savoir-être' },
  { code: 'FORMATIONS', libelle: 'Besoins en formation' },
  { code: 'FEEDBACK', libelle: 'Feedback & commentaires' },
];

export default function TemplateBuilder({ templateType, onSave, onCancel }: TemplateBuilderProps) {
  const [questions, setQuestions] = useState<CreateQuestionRequest[]>([]);
  const [showQuestionForm, setShowQuestionForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [familles, setFamilles] = useState<FamilleMetier[]>(FAMILLES_METIER_SEED);
  const [catalogueStub, setCatalogueStub] = useState(false);
  const [templateInfo, setTemplateInfo] = useState({
    nom: '',
    description: '',
    familleMetierCode: '',
    niveauSeniorite: 'JUNIOR',
    domaine: '',
  });
  const [newQuestion, setNewQuestion] = useState<Partial<CreateQuestionRequest>>({
    typeQuestion: templateType === 'TECHNICAL' ? 'SCALE' : 'PARAGRAPH',
    obligatoire: true,
    valeurMinimale: templateType === 'TECHNICAL' ? 1 : undefined,
    valeurMaximale: templateType === 'TECHNICAL' ? 5 : undefined,
    poids: 1,
    labelsEchelle: templateType === 'TECHNICAL' ? COMPETENCY_SCALE_FR : undefined,
    sectionCode: templateType === 'GENERIC' ? 'OBJECTIFS_N_1' : undefined,
    sectionLibelle: templateType === 'GENERIC' ? 'Objectifs année précédente' : undefined,
  });

  useEffect(() => {
    let cancelled = false;
    void getFamillesMetier(true)
      .then((rows) => {
        if (cancelled || !Array.isArray(rows) || rows.length === 0) return;
        setFamilles(rows.filter(f => f.actif !== false).map(f => ({
          code: f.code,
          libelle: f.libelle,
          actif: f.actif !== false,
          systeme: f.systeme,
        })));
        setCatalogueStub(false);
      })
      .catch(() => {
        if (!cancelled) {
          setFamilles(FAMILLES_METIER_SEED);
          setCatalogueStub(true);
        }
      });
    return () => { cancelled = true; };
  }, []);

  const resetQuestionForm = () => {
    setNewQuestion({
      typeQuestion: templateType === 'TECHNICAL' ? 'SCALE' : 'PARAGRAPH',
      obligatoire: true,
      valeurMinimale: templateType === 'TECHNICAL' ? 1 : undefined,
      valeurMaximale: templateType === 'TECHNICAL' ? 5 : undefined,
      poids: 1,
      labelsEchelle: templateType === 'TECHNICAL' ? COMPETENCY_SCALE_FR : undefined,
      sectionCode: templateType === 'GENERIC' ? 'SAVOIR_FAIRE' : undefined,
      sectionLibelle: templateType === 'GENERIC' ? 'Savoir-faire' : undefined,
    });
  };

  const moveQuestion = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= questions.length) return;
    const next = [...questions];
    const [item] = next.splice(index, 1);
    next.splice(target, 0, item);
    setQuestions(next.map((q, i) => ({ ...q, ordre: i + 1 })));
  };

  const addQuestion = () => {
    if (!newQuestion.libelle?.trim()) {
      setError('Le libellé de la question est obligatoire.');
      return;
    }
    if (
      (newQuestion.typeQuestion === 'MULTIPLE_CHOICE' || newQuestion.typeQuestion === 'CHECKBOX')
      && (!newQuestion.optionsReponses || newQuestion.optionsReponses.length < 2)
    ) {
      setError('Ajoutez au moins deux options de réponse.');
      return;
    }

    const question: CreateQuestionRequest = {
      libelle: newQuestion.libelle.trim(),
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
      placeholder: newQuestion.placeholder,
    };

    setQuestions([...questions, question]);
    setShowQuestionForm(false);
    setError(null);
    resetQuestionForm();
  };

  const saveTemplate = async () => {
    if (!templateInfo.nom.trim()) {
      setError('Le nom du template est obligatoire.');
      return;
    }
    if (templateType === 'TECHNICAL' && !templateInfo.familleMetierCode) {
      setError('Sélectionnez la famille métier (obligatoire pour un template technique).');
      return;
    }
    if (templateType === 'TECHNICAL' && !templateInfo.niveauSeniorite) {
      setError('Sélectionnez le niveau de séniorité.');
      return;
    }
    if (questions.length === 0) {
      setError('Ajoutez au moins une question.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await templateApi.createV2({
        nom: templateInfo.nom.trim(),
        description: templateInfo.description.trim(),
        type: templateType,
        famille_metier_code: templateType === 'TECHNICAL' ? templateInfo.familleMetierCode : undefined,
        niveau_seniorite: templateType === 'TECHNICAL' ? templateInfo.niveauSeniorite : undefined,
        domaine: templateType === 'TECHNICAL' ? templateInfo.domaine || undefined : undefined,
        questions,
      }, currentUserId());
      onSave();
    } catch (err: unknown) {
      const message = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined;
      setError(message || (err instanceof Error ? err.message : 'Échec de la sauvegarde du template'));
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
    const found = GENERAL_SECTIONS.find(s => s.code === sectionCode);
    setNewQuestion({
      ...newQuestion,
      sectionCode: sectionCode || undefined,
      sectionLibelle: found?.libelle,
    });
  };

  return (
    <div className="template-builder">
      <div className="builder-header">
        <div>
          <h3>
            {templateType === 'TECHNICAL'
              ? 'Nouveau template technique'
              : 'Nouveau template d’évaluation générale'}
          </h3>
          <p className="text-muted text-sm" style={{ margin: '0.35rem 0 0' }}>
            {templateType === 'TECHNICAL'
              ? 'Lié au profil métier (famille × niveau). Utilisé au matching à l’activation de campagne.'
              : 'Questionnaire commun : sections et types de réponse (texte, choix, échelle…).'}
          </p>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => { setError(null); setShowQuestionForm(true); }}
        >
          + Ajouter une question
        </button>
      </div>

      {error && (
        <div className="alert alert--error" role="alert" style={{ marginBottom: '1rem' }}>
          {error}
        </div>
      )}

      {catalogueStub && templateType === 'TECHNICAL' && (
        <div className="alert alert--info" style={{ marginBottom: '1rem' }}>
          Catalogue familles métier temporairement indisponible — liste seed affichée.
          Vérifiez que le référentiel RH est démarré.
        </div>
      )}

      <div className="form-grid">
        <div className="form-group">
          <label htmlFor="tpl-nom">Nom du template *</label>
          <input
            id="tpl-nom"
            type="text"
            value={templateInfo.nom}
            onChange={e => setTemplateInfo({ ...templateInfo, nom: e.target.value })}
            placeholder={templateType === 'TECHNICAL'
              ? 'Compétences — Exploitation Senior'
              : 'Évaluation annuelle 2026 — Générale'}
          />
        </div>

        <div className="form-group">
          <label htmlFor="tpl-desc">Description</label>
          <input
            id="tpl-desc"
            type="text"
            value={templateInfo.description}
            onChange={e => setTemplateInfo({ ...templateInfo, description: e.target.value })}
            placeholder="Contexte d’usage du template"
          />
        </div>

        {templateType === 'TECHNICAL' && (
          <>
            <div className="form-group">
              <label htmlFor="tpl-famille">Famille métier *</label>
              <select
                id="tpl-famille"
                value={templateInfo.familleMetierCode}
                onChange={e => setTemplateInfo({ ...templateInfo, familleMetierCode: e.target.value })}
                required
              >
                <option value="">— Sélectionner —</option>
                {familles.map(f => (
                  <option key={f.code} value={f.code}>{f.libelle}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="tpl-niveau">Niveau de séniorité *</label>
              <select
                id="tpl-niveau"
                value={templateInfo.niveauSeniorite}
                onChange={e => setTemplateInfo({ ...templateInfo, niveauSeniorite: e.target.value })}
              >
                {NIVEAUX_SENIORITE.map(n => (
                  <option key={n.code} value={n.code}>{n.libelle}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="tpl-domaine">Domaine (affichage)</label>
              <input
                id="tpl-domaine"
                type="text"
                value={templateInfo.domaine}
                onChange={e => setTemplateInfo({ ...templateInfo, domaine: e.target.value })}
                placeholder="Optionnel — pas une clé de matching"
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
              <label htmlFor="q-libelle">Libellé *</label>
              <input
                id="q-libelle"
                type="text"
                value={newQuestion.libelle || ''}
                onChange={e => setNewQuestion({ ...newQuestion, libelle: e.target.value })}
                placeholder={templateType === 'TECHNICAL'
                  ? 'Ex. Sécurité chantier'
                  : 'Ex. Bilan des objectifs de l’année précédente'}
              />
            </div>

            <div className="form-group full-width">
              <label htmlFor="q-desc">Description / aide</label>
              <textarea
                id="q-desc"
                value={newQuestion.description || ''}
                onChange={e => setNewQuestion({ ...newQuestion, description: e.target.value })}
                rows={2}
                placeholder="Contexte affiché au collaborateur"
              />
            </div>

            {templateType === 'GENERIC' && (
              <div className="form-group">
                <label htmlFor="q-section">Section</label>
                <select
                  id="q-section"
                  value={newQuestion.sectionCode || ''}
                  onChange={e => updateSection(e.target.value)}
                >
                  <option value="">Sans section</option>
                  {GENERAL_SECTIONS.map(s => (
                    <option key={s.code} value={s.code}>{s.libelle}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-group">
              <label htmlFor="q-type">Type de réponse *</label>
              <select
                id="q-type"
                value={newQuestion.typeQuestion}
                onChange={e => setNewQuestion({
                  ...newQuestion,
                  typeQuestion: e.target.value as CreateQuestionRequest['typeQuestion'],
                  labelsEchelle: e.target.value === 'SCALE' && templateType === 'TECHNICAL'
                    ? COMPETENCY_SCALE_FR
                    : newQuestion.labelsEchelle,
                })}
                aria-describedby="q-type-hint"
              >
                {QUESTION_TYPES.map(t => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
              <span id="q-type-hint" className="text-muted text-sm">
                {QUESTION_TYPES.find(t => t.value === newQuestion.typeQuestion)?.hint}
              </span>
            </div>

            <div className="form-group">
              <label htmlFor="q-poids">Poids (scoring)</label>
              <input
                id="q-poids"
                type="number"
                min="0.1"
                step="0.1"
                value={newQuestion.poids || 1}
                onChange={e => setNewQuestion({ ...newQuestion, poids: Number(e.target.value) })}
              />
            </div>

            {(newQuestion.typeQuestion === 'MULTIPLE_CHOICE' || newQuestion.typeQuestion === 'CHECKBOX') && (
              <div className="form-group full-width">
                <label htmlFor="q-options">Options (une par ligne) *</label>
                <textarea
                  id="q-options"
                  value={(newQuestion.optionsReponses || []).join('\n')}
                  onChange={e => setNewQuestion({
                    ...newQuestion,
                    optionsReponses: e.target.value.split('\n').map(option => option.trim()).filter(Boolean),
                  })}
                  rows={4}
                  placeholder={'Atteint\nPartiellement atteint\nNon atteint'}
                />
              </div>
            )}

            {(newQuestion.typeQuestion === 'RATING' || newQuestion.typeQuestion === 'SCALE' || newQuestion.typeQuestion === 'NUMBER') && (
              <>
                <div className="form-group">
                  <label htmlFor="q-min">Valeur minimale</label>
                  <input
                    id="q-min"
                    type="number"
                    value={newQuestion.valeurMinimale ?? 1}
                    onChange={e => setNewQuestion({ ...newQuestion, valeurMinimale: Number(e.target.value) })}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="q-max">Valeur maximale</label>
                  <input
                    id="q-max"
                    type="number"
                    value={newQuestion.valeurMaximale ?? 5}
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
            <button type="button" className="btn btn--ghost" onClick={() => setShowQuestionForm(false)}>
              Annuler
            </button>
            <button type="button" className="btn btn--primary" onClick={addQuestion}>
              Ajouter
            </button>
          </div>
        </div>
      )}

      <div className="questions-list">
        {questions.length > 0 && (
          <p className="text-muted text-sm" style={{ marginBottom: '0.5rem' }}>
            Questions ({questions.length})
          </p>
        )}
        {questions.map((question, index) => (
          <div key={`${question.ordre}-${question.libelle}`} className="question-item">
            <div className="question-number">{index + 1}</div>
            <div className="question-content">
              <div className="question-label">{question.libelle}</div>
              <div className="question-meta">
                <span className="badge badge--info">
                  {QUESTION_TYPES.find(t => t.value === question.typeQuestion)?.label || question.typeQuestion}
                </span>
                {question.sectionLibelle && <span className="badge badge--default">{question.sectionLibelle}</span>}
                <span className="badge badge--default">Poids {question.poids || 1}</span>
                {question.obligatoire && <span className="badge badge--warning">Obligatoire</span>}
              </div>
            </div>
            <div className="question-item__actions">
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                aria-label="Monter"
                disabled={index === 0}
                onClick={() => moveQuestion(index, -1)}
              >
                ↑
              </button>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                aria-label="Descendre"
                disabled={index === questions.length - 1}
                onClick={() => moveQuestion(index, 1)}
              >
                ↓
              </button>
              <button type="button" className="btn btn--danger btn--sm" onClick={() => removeQuestion(index)}>
                Supprimer
              </button>
            </div>
          </div>
        ))}
      </div>

      {questions.length === 0 && !showQuestionForm && (
        <div className="empty-state">
          <p>Aucune question pour l’instant</p>
          <button type="button" className="btn btn--primary" onClick={() => setShowQuestionForm(true)}>
            + Première question
          </button>
        </div>
      )}

      <div className="builder-footer form-actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          Annuler
        </button>
        <button type="button" className="btn btn--primary btn--lg" onClick={() => void saveTemplate()} disabled={loading}>
          {loading ? 'Enregistrement…' : 'Enregistrer le brouillon'}
        </button>
      </div>
    </div>
  );
}

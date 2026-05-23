import { useState } from 'react';
import { EvaluationQuestion } from '../api/evaluationApi';

interface EvaluationFormRendererProps {
  questions: EvaluationQuestion[];
  onSubmit: (responses: any) => void;
  readOnly?: boolean;
}

export default function EvaluationFormRenderer({ questions, onSubmit, readOnly = false }: EvaluationFormRendererProps) {
  const [responses, setResponses] = useState<Record<string, any>>({});

  const renderQuestionInput = (question: EvaluationQuestion) => {
    const value = responses[question.identifiant];

    switch (question.typeQuestion) {
      case 'TEXT':
        return (
          <input
            type="text"
            value={value || ''}
            onChange={e => setResponses({...responses, [question.identifiant]: e.target.value})}
            placeholder={question.placeholder || 'Votre réponse...'}
            required={question.obligatoire}
            disabled={readOnly}
            className="field-input"
          />
        );

      case 'PARAGRAPH':
        return (
          <textarea
            value={value || ''}
            onChange={e => setResponses({...responses, [question.identifiant]: e.target.value})}
            placeholder={question.placeholder || 'Votre réponse détaillée...'}
            required={question.obligatoire}
            disabled={readOnly}
            rows={4}
            className="field-input field-input--area"
          />
        );

      case 'MULTIPLE_CHOICE':
        return (
          <div className="radio-group">
            {question.optionsReponses?.map((option, idx) => (
              <label key={idx} className="radio-label">
                <input
                  type="radio"
                  name={`q-${question.identifiant}`}
                  value={option}
                  checked={value === option}
                  onChange={e => setResponses({...responses, [question.identifiant]: e.target.value})}
                  required={question.obligatoire}
                  disabled={readOnly}
                />
                <span>{option}</span>
              </label>
            ))}
          </div>
        );

      case 'CHECKBOX':
        return (
          <div className="checkbox-group-vertical">
            {question.optionsReponses?.map((option, idx) => (
              <label key={idx} className="checkbox-label">
                <input
                  type="checkbox"
                  value={option}
                  checked={(value || []).includes(option)}
                  onChange={e => {
                    const current = value || [];
                    const updated = e.target.checked
                      ? [...current, option]
                      : current.filter((o: string) => o !== option);
                    setResponses({...responses, [question.identifiant]: updated});
                  }}
                  disabled={readOnly}
                />
                <span>{option}</span>
              </label>
            ))}
          </div>
        );

      case 'RATING':
        return (
          <div className="rating-stars">
            {[1, 2, 3, 4, 5].map(star => (
              <button
                key={star}
                type="button"
                className={`star ${value >= star ? 'active' : ''}`}
                onClick={() => !readOnly && setResponses({...responses, [question.identifiant]: star})}
                disabled={readOnly}
              >
                ★
              </button>
            ))}
            <div className="rating-labels">
              <span>Faible</span>
              <span>Excellent</span>
            </div>
          </div>
        );

      case 'SCALE':
        return (
          <div className="scale-input">
            <input
              type="range"
              min={question.valeurMinimale || 1}
              max={question.valeurMaximale || 10}
              value={value || question.valeurMinimale || 1}
              onChange={e => setResponses({...responses, [question.identifiant]: parseInt(e.target.value)})}
              className="range-slider"
              disabled={readOnly}
            />
            <div className="scale-labels">
              <span>{question.valeurMinimale || 1}</span>
              <span>{value || question.valeurMinimale || 1}</span>
              <span>{question.valeurMaximale || 10}</span>
            </div>
          </div>
        );

      case 'DATE':
        return (
          <input
            type="date"
            value={value || ''}
            onChange={e => setResponses({...responses, [question.identifiant]: e.target.value})}
            required={question.obligatoire}
            disabled={readOnly}
            className="field-input"
          />
        );

      case 'NUMBER':
        return (
          <input
            type="number"
            value={value || ''}
            onChange={e => setResponses({...responses, [question.identifiant]: parseFloat(e.target.value)})}
            min={question.valeurMinimale}
            max={question.valeurMaximale}
            placeholder={question.placeholder}
            required={question.obligatoire}
            disabled={readOnly}
            className="field-input"
          />
        );

      default:
        return null;
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    // Validate required questions
    for (const question of questions) {
      if (question.obligatoire && !responses[question.identifiant]) {
        alert(`Veuillez répondre à: ${question.libelle}`);
        return;
      }
    }

    onSubmit(responses);
  };

  if (questions.length === 0) {
    return (
      <div className="empty-state">
        <p>Aucune question dans ce template</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="evaluation-form">
      {questions.map((question, index) => (
        <div key={question.identifiant} className="form-question card">
          <label className="question-label">
            <span className="question-number">{index + 1}.</span>
            {question.libelle}
            {question.obligatoire && <span className="required">*</span>}
          </label>
          
          {question.description && (
            <p className="question-description text-muted">{question.description}</p>
          )}

          {renderQuestionInput(question)}
        </div>
      ))}

      {!readOnly && (
        <div className="form-actions">
          <button type="submit" className="btn btn--primary btn--lg">
            ✓ Soumettre l'évaluation
          </button>
        </div>
      )}
    </form>
  );
}

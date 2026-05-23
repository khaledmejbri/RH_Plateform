import { useState, useEffect } from 'react';
import { templateApi, type EvaluationTemplate } from '../api/evaluationApi';
import TemplateBuilder from '../components/TemplateBuilder';

interface EnhancedTemplatesTabProps {
  templateType: 'GENERIC' | 'TECHNICAL';
}

export default function EnhancedTemplatesTab({ templateType }: EnhancedTemplatesTabProps) {
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);
  const [filterStatut, setFilterStatut] = useState<string>('');

  useEffect(() => {
    loadTemplates();
  }, [templateType, filterStatut]);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const data = await templateApi.listV2(templateType, filterStatut || undefined);
      setTemplates(data);
    } catch (err: any) {
      console.error('Error loading templates:', err);
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async (templateId: string) => {
    if (!confirm('Publier ce template? Il sera disponible pour les campagnes.')) return;
    try {
      await templateApi.publish(templateId, '0a4f7069-0737-4207-97dd-7a46a45f5429'); // TODO: Get from auth
      loadTemplates();
      alert('Template publié avec succès!');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  const handleArchive = async (templateId: string) => {
    if (!confirm('Archiver ce template?')) return;
    try {
      await templateApi.archive(templateId);
      loadTemplates();
      alert('Template archivé avec succès');
    } catch (err: any) {
      alert('Erreur: ' + err.message);
    }
  };

  const getStatusBadge = (statut: string) => {
    switch (statut) {
      case 'DRAFT':
        return <span className="badge badge--warning">Brouillon</span>;
      case 'PUBLISHED':
        return <span className="badge badge--success">Publié</span>;
      case 'ARCHIVED':
        return <span className="badge badge--info">Archivé</span>;
      default:
        return null;
    }
  };

  if (loading && templates.length === 0) {
    return <div className="loading">Chargement...</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <h2>
          {templateType === 'GENERIC' ? '📝 Templates Généraux' : '🔧 Templates Techniques'}
        </h2>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <select
            value={filterStatut}
            onChange={e => setFilterStatut(e.target.value)}
            className="field-input"
            style={{ width: 'auto' }}
          >
            <option value="">Tous les statuts</option>
            <option value="DRAFT">Brouillon</option>
            <option value="PUBLISHED">Publié</option>
            <option value="ARCHIVED">Archivé</option>
          </select>
          <button 
            className="btn btn--primary" 
            onClick={() => setShowCreateForm(!showCreateForm)}
          >
            {showCreateForm ? 'Annuler' : '+ Nouveau template'}
          </button>
        </div>
      </div>

      {showCreateForm && (
        <div className="card">
          <TemplateBuilder
            templateType={templateType}
            onSave={() => {
              setShowCreateForm(false);
              loadTemplates();
              alert('Template créé avec succès!');
            }}
            onCancel={() => setShowCreateForm(false)}
          />
        </div>
      )}

      <div className="grid grid--2">
        {templates.map(template => (
          <div key={template.identifiant} className="card">
            <div className="card__header">
              <h3>{template.nom}</h3>
              <div className="card__actions">
                {getStatusBadge(template.statut)}
              </div>
            </div>
            
            <p className="text-muted">{template.description || 'Aucune description'}</p>
            
            {template.type === 'TECHNICAL' && (
              <div className="template-meta">
                {template.role && <div><strong>Rôle:</strong> {template.role}</div>}
                {template.niveauSeniorite && <div><strong>Niveau:</strong> {template.niveauSeniorite}</div>}
                {template.domaine && <div><strong>Domaine:</strong> {template.domaine}</div>}
              </div>
            )}

            <div className="template-info">
              <div className="text-sm">
                Version {template.version} • {template.questions?.length || 0} questions
              </div>
              <div className="text-sm text-muted">
                Créé le {new Date(template.creeLe).toLocaleDateString('fr-FR')}
              </div>
            </div>

            <div className="form-actions" style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--border)' }}>
              {template.statut === 'DRAFT' && (
                <>
                  <button
                    className="btn btn--sm btn--success"
                    onClick={() => handlePublish(template.identifiant)}
                  >
                    ✓ Publier
                  </button>
                  <button
                    className="btn btn--sm"
                    onClick={() => setSelectedTemplate(
                      selectedTemplate === template.identifiant ? null : template.identifiant
                    )}
                  >
                    ✏️ Modifier
                  </button>
                </>
              )}
              
              {template.statut === 'PUBLISHED' && (
                <button
                  className="btn btn--sm btn--warning"
                  onClick={() => handleArchive(template.identifiant)}
                >
                  📦 Archiver
                </button>
              )}
              
              <button
                className="btn btn--sm btn--ghost"
                onClick={() => setSelectedTemplate(
                  selectedTemplate === template.identifiant ? null : template.identifiant
                )}
              >
                👁 Voir détails
              </button>
            </div>

            {selectedTemplate === template.identifiant && template.questions && (
              <div className="questions-preview" style={{ marginTop: '1rem' }}>
                <h4>Questions ({template.questions.length})</h4>
                <div className="questions-list">
                  {template.questions.map((question, idx) => (
                    <div key={question.identifiant} className="question-item">
                      <div className="question-number">{idx + 1}</div>
                      <div className="question-content">
                        <div className="question-label">{question.libelle}</div>
                        <div className="question-meta">
                          <span className="badge badge--info">{question.typeQuestion}</span>
                          {question.obligatoire && <span className="badge badge--danger">Obligatoire</span>}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}

        {templates.length === 0 && !showCreateForm && (
          <div className="empty-state full-width">
            <h3>Aucun template</h3>
            <p>Créez votre premier template pour commencer</p>
            <button className="btn btn--primary" onClick={() => setShowCreateForm(true)}>
              + Créer un template
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

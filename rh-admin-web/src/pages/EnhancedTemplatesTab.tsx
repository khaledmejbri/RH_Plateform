import { useState, useEffect } from 'react';
import { templateApi, type EvaluationTemplate } from '../api/evaluationApi';
import {
  FAMILLES_METIER_SEED,
  NIVEAUX_SENIORITE,
  libelleNiveauSeniorite,
  profilMetierLabel,
  type FamilleMetier,
} from '../api/evaluationCatalog';
import { getFamillesMetier } from '../api/rhClient';
import TemplateBuilder from '../components/TemplateBuilder';
import { useConfirm } from '../components/ConfirmDialog';
import { currentUserId } from '../auth/jwtRoles';

interface EnhancedTemplatesTabProps {
  templateType: 'GENERIC' | 'TECHNICAL';
  lectureSeule?: boolean;
}

const QUESTION_TYPE_LABELS: Record<string, string> = {
  TEXT: 'Texte court',
  PARAGRAPH: 'Paragraphe',
  MULTIPLE_CHOICE: 'Choix unique',
  CHECKBOX: 'Cases à cocher',
  RATING: 'Note',
  SCALE: 'Échelle',
  NUMBER: 'Nombre',
  DATE: 'Date',
};

export default function EnhancedTemplatesTab({
  templateType,
  lectureSeule = false,
}: EnhancedTemplatesTabProps) {
  const confirm = useConfirm();
  const [templates, setTemplates] = useState<EvaluationTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);
  const [filterStatut, setFilterStatut] = useState<string>('');
  const [filterFamille, setFilterFamille] = useState<string>('');
  const [filterNiveau, setFilterNiveau] = useState<string>('');
  const [familles, setFamilles] = useState<FamilleMetier[]>(FAMILLES_METIER_SEED);
  const [actionError, setActionError] = useState<string | null>(null);

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
      })
      .catch(() => {
        if (!cancelled) setFamilles(FAMILLES_METIER_SEED);
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    void loadTemplates();
  }, [templateType, filterStatut]);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      setLoadError(null);
      const data = await templateApi.listV2({
        type: templateType,
        statut: filterStatut || undefined,
      });
      setTemplates(data);
    } catch (err: unknown) {
      setLoadError(err instanceof Error ? err.message : 'Impossible de charger les templates');
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async (templateId: string) => {
    const ok = await confirm({
      title: 'Publier le template',
      message:
        templateType === 'TECHNICAL'
          ? 'Il sera proposé aux collaborateurs de la famille et du niveau choisis.'
          : 'Il sera utilisable dans les campagnes.',
      confirmLabel: 'Publier',
    });
    if (!ok) return;
    setActionError(null);
    try {
      await templateApi.publish(templateId, currentUserId());
      await loadTemplates();
    } catch (err: unknown) {
      const message = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined;
      setActionError(message || (err instanceof Error ? err.message : 'Erreur de publication'));
    }
  };

  const handleArchive = async (templateId: string) => {
    const ok = await confirm({
      title: 'Archiver le template',
      message: 'Il ne pourra plus être utilisé dans de nouvelles campagnes.',
      confirmLabel: 'Archiver',
      danger: true,
    });
    if (!ok) return;
    setActionError(null);
    try {
      await templateApi.archive(templateId);
      await loadTemplates();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Erreur d’archivage');
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

  const visibleTemplates = templates.filter(t => {
    if (filterFamille && (t.familleMetierCode || '').toUpperCase() !== filterFamille.toUpperCase()) {
      return false;
    }
    if (filterNiveau && (t.niveauSeniorite || '').toUpperCase() !== filterNiveau.toUpperCase()) {
      return false;
    }
    return true;
  });

  if (loading && templates.length === 0) {
    return <div className="loading">Chargement…</div>;
  }

  return (
    <div>
      <div className="toolbar">
        <div>
          <h2>
            {templateType === 'GENERIC'
              ? 'Templates généraux'
              : 'Templates techniques (par profil)'}
          </h2>
          <p className="text-muted text-sm" style={{ margin: 0 }}>
            {templateType === 'GENERIC'
              ? 'Questionnaire libre : objectifs, savoir, savoir-faire… Types de réponse au choix.'
              : 'Matching à l’activation : famille métier × niveau de séniorité.'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <select
            value={filterStatut}
            onChange={e => setFilterStatut(e.target.value)}
            className="field-input"
            style={{ width: 'auto' }}
            aria-label="Filtrer par statut"
          >
            <option value="">Tous les statuts</option>
            <option value="DRAFT">Brouillon</option>
            <option value="PUBLISHED">Publié</option>
            <option value="ARCHIVED">Archivé</option>
          </select>
          {templateType === 'TECHNICAL' && (
            <>
              <select
                value={filterFamille}
                onChange={e => setFilterFamille(e.target.value)}
                className="field-input"
                style={{ width: 'auto' }}
                aria-label="Filtrer par famille métier"
              >
                <option value="">Toutes les familles</option>
                {familles.map(f => (
                  <option key={f.code} value={f.code}>{f.libelle}</option>
                ))}
              </select>
              <select
                value={filterNiveau}
                onChange={e => setFilterNiveau(e.target.value)}
                className="field-input"
                style={{ width: 'auto' }}
                aria-label="Filtrer par niveau"
              >
                <option value="">Tous les niveaux</option>
                {NIVEAUX_SENIORITE.map(n => (
                  <option key={n.code} value={n.code}>{n.libelle}</option>
                ))}
              </select>
            </>
          )}
          {!lectureSeule && (
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => setShowCreateForm(!showCreateForm)}
            >
              {showCreateForm ? 'Annuler' : '+ Nouveau template'}
            </button>
          )}
        </div>
      </div>

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
          {' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadTemplates()}>
            Réessayer
          </button>
        </div>
      )}

      {actionError && (
        <div className="alert alert--error" role="alert">{actionError}</div>
      )}

      {!lectureSeule && showCreateForm && (
        <div className="card">
          <TemplateBuilder
            templateType={templateType}
            onSave={() => {
              setShowCreateForm(false);
              void loadTemplates();
            }}
            onCancel={() => setShowCreateForm(false)}
          />
        </div>
      )}

      <div className="grid grid--2">
        {visibleTemplates.map(template => {
          const profil = profilMetierLabel(template.familleMetierCode, template.niveauSeniorite, familles);
          return (
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
                  {profil ? (
                    <div>
                      <strong>Profil :</strong>{' '}
                      <span className="badge badge--info">{profil}</span>
                    </div>
                  ) : (
                    <>
                      {template.familleMetierCode && (
                        <div><strong>Famille :</strong> {template.familleMetierCode}</div>
                      )}
                      {template.niveauSeniorite && (
                        <div>
                          <strong>Niveau :</strong> {libelleNiveauSeniorite(template.niveauSeniorite)}
                        </div>
                      )}
                    </>
                  )}
                  {template.domaine && <div><strong>Domaine :</strong> {template.domaine}</div>}
                </div>
              )}

              <div className="template-info">
                <div className="text-sm">
                  Version {template.version} · {template.questions?.length || 0} questions
                </div>
                <div className="text-sm text-muted">
                  Créé le {new Date(template.creeLe).toLocaleDateString('fr-FR')}
                </div>
              </div>

              <div className="form-actions" style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--border)' }}>
                {!lectureSeule && template.statut === 'DRAFT' && (
                  <button
                    type="button"
                    className="btn btn--sm btn--success"
                    onClick={() => void handlePublish(template.identifiant)}
                  >
                    Publier
                  </button>
                )}

                {!lectureSeule && template.statut === 'PUBLISHED' && (
                  <button
                    type="button"
                    className="btn btn--sm btn--warning"
                    onClick={() => void handleArchive(template.identifiant)}
                  >
                    Archiver
                  </button>
                )}

                <button
                  type="button"
                  className="btn btn--sm btn--ghost"
                  onClick={() => setSelectedTemplate(
                    selectedTemplate === template.identifiant ? null : template.identifiant
                  )}
                >
                  {selectedTemplate === template.identifiant ? 'Masquer' : 'Voir les questions'}
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
                            <span className="badge badge--info">
                              {QUESTION_TYPE_LABELS[question.typeQuestion] ?? question.typeQuestion}
                            </span>
                            {question.sectionLibelle && (
                              <span className="badge badge--default">{question.sectionLibelle}</span>
                            )}
                            {question.obligatoire && <span className="badge badge--warning">Obligatoire</span>}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          );
        })}

        {visibleTemplates.length === 0 && !showCreateForm && (
          <div className="empty-state full-width">
            <h3>Aucun template</h3>
            <p>
              {templateType === 'TECHNICAL'
                ? 'Créez un questionnaire technique lié à une famille métier et un niveau, puis publiez-le.'
                : 'Créez le questionnaire général avec les sections et types de réponses souhaités.'}
            </p>
            {!lectureSeule && (
              <button type="button" className="btn btn--primary" onClick={() => setShowCreateForm(true)}>
                + Créer un template
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

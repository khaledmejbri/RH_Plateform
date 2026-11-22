import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import {
  activerModeleWorkflow,
  creerModeleWorkflow,
  desactiverModeleWorkflow,
  estConflitWorkflow,
  executerPourConfirmation,
  getActivationsWorkflow,
  getCatalogueWorkflow,
  getModelesWorkflow,
  messageErreurWorkflow,
  putActivationWorkflow,
  supprimerModeleWorkflow,
  type ActivationWorkflow,
  type CatalogueWorkflow,
  type ModeActivation,
  type ModeleResume,
} from '../../api/workflowApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import { libelleSousTypeModele } from '../../workflow/valeursListes';

type ContexteRoute = { lectureSeule?: boolean };

export default function WorkflowsListePage() {
  const { lectureSeule } = useOutletContext<ContexteRoute>() ?? {};
  const confirm = useConfirm();
  const navigate = useNavigate();

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(null);
  const [activations, setActivations] = useState<ActivationWorkflow[]>([]);
  const [modeles, setModeles] = useState<ModeleResume[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [filtreType, setFiltreType] = useState('');
  const [formOuvert, setFormOuvert] = useState(false);
  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');
  const [description, setDescription] = useState('');
  const [objetType, setObjetType] = useState('CONGE');
  const [sousType, setSousType] = useState('');
  const [envoiCreation, setEnvoiCreation] = useState(false);
  const [erreurCreation, setErreurCreation] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setChargement(true);
    setErreur(null);
    try {
      const [cat, acts, page] = await Promise.all([
        getCatalogueWorkflow(),
        getActivationsWorkflow(),
        getModelesWorkflow({ page: 0, taille: 200 }),
      ]);
      setCatalogue(cat);
      setActivations(acts);
      setModeles(page.elements ?? []);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  const modelesParType = useMemo(() => {
    const m = new Map<string, ModeleResume[]>();
    for (const mod of modeles) {
      const arr = m.get(mod.objet_type) ?? [];
      arr.push(mod);
      m.set(mod.objet_type, arr);
    }
    return m;
  }, [modeles]);

  const typesAffiches = useMemo(() => {
    if (!filtreType) return activations;
    return activations.filter((a) => a.objet_type === filtreType);
  }, [activations, filtreType]);

  async function basculerMode(a: ActivationWorkflow) {
    if (lectureSeule) return;
    const cible: ModeActivation = a.mode === 'MOTEUR' ? 'HERITE' : 'MOTEUR';
    const libCible = cible === 'MOTEUR' ? 'Circuit paramétré' : 'Circuit standard';
    let conflit = false;
    const ok = await confirm({
      title: `Basculer « ${a.libelle} » vers ${libCible} ?`,
      message:
        cible === 'MOTEUR'
          ? 'Les nouvelles demandes de ce type suivront le modèle publié actif. Les demandes déjà en cours gardent leur circuit.'
          : 'Les nouvelles demandes de ce type reviendront au circuit standard. Les instances déjà démarrées dans le moteur continuent jusqu’à leur terme.',
      confirmLabel: `Passer en ${libCible}`,
      envoyer: () => executerPourConfirmation(() => putActivationWorkflow(a.objet_type, cible, a.version)),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage(`« ${a.libelle} » basculé en ${libCible}.`);
      await charger();
    } else if (conflit) {
      await charger();
      setErreur('Ce réglage a été modifié entre-temps par un autre utilisateur : la liste a été rechargée.');
    }
  }

  async function soumettreCreation(e: React.FormEvent) {
    e.preventDefault();
    if (lectureSeule) return;
    const c = code.trim().toUpperCase();
    const l = libelle.trim();
    if (!/^[A-Z0-9_]{2,40}$/.test(c)) {
      setErreurCreation('Le code doit contenir 2 à 40 caractères (majuscules, chiffres ou _).');
      return;
    }
    if (l.length < 2 || l.length > 80) {
      setErreurCreation('Le libellé doit contenir entre 2 et 80 caractères.');
      return;
    }
    setEnvoiCreation(true);
    setErreurCreation(null);
    try {
      const cree = await creerModeleWorkflow({
        code: c,
        libelle: l,
        description: description.trim() || null,
        objet_type: objetType,
        sous_type: sousType.trim() ? sousType.trim().toUpperCase() : null,
      });
      setFormOuvert(false);
      setCode('');
      setLibelle('');
      setDescription('');
      setSousType('');
      navigate(`/app/parametres/workflows/${cree.id}`);
    } catch (err) {
      setErreurCreation(messageErreurWorkflow(err));
    } finally {
      setEnvoiCreation(false);
    }
  }

  async function basculerActif(m: ModeleResume) {
    if (lectureSeule) return;
    let conflit = false;
    const ok = await confirm({
      title: m.actif ? `Désactiver « ${m.libelle} » ?` : `Activer « ${m.libelle} » ?`,
      message: m.actif
        ? 'Ce modèle ne sera plus sélectionné pour les nouvelles demandes.'
        : 'Ce modèle pourra être utilisé pour les nouvelles demandes de son type.',
      confirmLabel: m.actif ? 'Désactiver' : 'Activer',
      danger: m.actif,
      envoyer: () =>
        executerPourConfirmation(() =>
          m.actif ? desactiverModeleWorkflow(m.id, m.version) : activerModeleWorkflow(m.id, m.version),
        ),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage(m.actif ? 'Modèle désactivé.' : 'Modèle activé.');
      await charger();
    } else if (conflit) {
      await charger();
      setErreur('Ce modèle a été modifié entre-temps par un autre utilisateur : la liste a été rechargée.');
    }
  }

  async function supprimer(m: ModeleResume) {
    if (lectureSeule) return;
    let conflit = false;
    const ok = await confirm({
      title: `Supprimer « ${m.libelle} » ?`,
      message: 'Ce modèle jamais publié et ses brouillons seront supprimés définitivement.',
      confirmLabel: 'Supprimer',
      danger: true,
      envoyer: () => executerPourConfirmation(() => supprimerModeleWorkflow(m.id)),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage('Modèle supprimé.');
      await charger();
    } else if (conflit) {
      await charger();
      setErreur('Ce modèle a changé entre-temps : la liste a été rechargée.');
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="Workflows de validation"
        lead="Configurez les procédures de validation par type de demande et basculez entre Circuit standard et Circuit paramétré."
        actions={
          !lectureSeule ? (
            <button type="button" className="btn btn--primary" onClick={() => setFormOuvert((v) => !v)}>
              {formOuvert ? 'Fermer' : 'Nouveau modèle'}
            </button>
          ) : null
        }
      />

      {message ? <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>{message}</div> : null}
      {erreur ? (
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {formOuvert && !lectureSeule ? (
        <form className="card" style={{ marginBottom: 16 }} onSubmit={(e) => void soumettreCreation(e)}>
          <h3 style={{ marginBottom: 12 }}>Créer un modèle de workflow</h3>
          {erreurCreation ? <ErrorAlert>{erreurCreation}</ErrorAlert> : null}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 12, marginTop: 8 }}>
            <div>
              <label className="field-label" htmlFor="wf-new-code">Code</label>
              <input
                id="wf-new-code"
                className="field-input"
                placeholder="EX_CONGE_LONG"
                maxLength={40}
                required
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="wf-new-lib">Libellé</label>
              <input
                id="wf-new-lib"
                className="field-input"
                placeholder="Congé long"
                maxLength={80}
                required
                value={libelle}
                onChange={(e) => setLibelle(e.target.value)}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="wf-new-type">Type d’objet</label>
              <select
                id="wf-new-type"
                className="field-input"
                value={objetType}
                onChange={(e) => setObjetType(e.target.value)}
              >
                {(catalogue?.types ?? []).map((t) => (
                  <option key={t.objet_type} value={t.objet_type}>{t.libelle}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="field-label" htmlFor="wf-new-sous">Sous-type (facultatif)</label>
              <input
                id="wf-new-sous"
                className="field-input"
                placeholder="ex. ANNUEL (vide = tous)"
                maxLength={40}
                value={sousType}
                onChange={(e) => setSousType(e.target.value.toUpperCase())}
              />
            </div>
          </div>
          <div style={{ marginTop: 10 }}>
            <label className="field-label" htmlFor="wf-new-desc">Description (facultatif)</label>
            <input
              id="wf-new-desc"
              className="field-input"
              maxLength={300}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button type="submit" className="btn btn--primary" disabled={envoiCreation}>
              {envoiCreation ? 'Création…' : 'Créer et ouvrir l’éditeur'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => setFormOuvert(false)}>
              Annuler
            </button>
          </div>
        </form>
      ) : null}

      <div style={{ marginBottom: 12, display: 'flex', gap: 12, alignItems: 'center' }}>
        <label htmlFor="wf-filtre-type" className="field-label" style={{ margin: 0 }}>Filtrer par type</label>
        <select
          id="wf-filtre-type"
          className="field-input"
          style={{ maxWidth: 260 }}
          value={filtreType}
          onChange={(e) => setFiltreType(e.target.value)}
        >
          <option value="">Tous les types d’objet</option>
          {activations.map((a) => (
            <option key={a.objet_type} value={a.objet_type}>{a.libelle}</option>
          ))}
        </select>
      </div>

      {chargement ? <LoadingState /> : null}
      {!chargement && typesAffiches.length === 0 ? (
        <EmptyState title="Aucun type de workflow disponible">
          Vérifiez que le service de workflow est démarré.
        </EmptyState>
      ) : null}

      {!chargement && typesAffiches.map((a) => {
        const liste = modelesParType.get(a.objet_type) ?? [];
        const estMoteur = a.mode === 'MOTEUR';
        return (
          <section key={a.objet_type} className="card" style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 12 }}>
              <div>
                <h3 style={{ margin: 0 }}>{a.libelle}</h3>
                <span className="small muted">
                  Mode actif :{' '}
                  <strong className={estMoteur ? 'status status--success' : 'status status--neutral'}>
                    {estMoteur ? 'Circuit paramétré' : 'Circuit standard'}
                  </strong>
                  {a.modifie_le ? ` · modifié le ${formatDateHeure(a.modifie_le)}` : ''}
                </span>
              </div>
              {!lectureSeule ? (
                <button
                  type="button"
                  className={estMoteur ? 'btn btn--secondary btn--sm' : 'btn btn--primary btn--sm'}
                  onClick={() => void basculerMode(a)}
                >
                  {estMoteur ? 'Revenir au Circuit standard' : 'Passer en Circuit paramétré'}
                </button>
              ) : null}
            </div>

            {liste.length === 0 ? (
              <p className="muted small">Aucun modèle défini pour ce type d’objet.</p>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Modèle</th>
                      <th>Sous-type</th>
                      <th>Statut</th>
                      <th>Version publiée</th>
                      <th>Brouillon</th>
                      <th>Modifié le</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {liste.map((m) => (
                      <tr key={m.id}>
                        <td>
                          <Link to={`/app/parametres/workflows/${m.id}`}>
                            <strong>{m.libelle}</strong>
                          </Link>
                          <div className="small muted">{m.code}</div>
                        </td>
                        <td>{m.sous_type ? libelleSousTypeModele(m.sous_type) : 'Tous'}</td>
                        <td>
                          <span className={`status status--${m.actif ? 'success' : 'neutral'}`}>
                            {m.actif ? 'Actif' : 'Inactif'}
                          </span>
                        </td>
                        <td>{m.version_publiee_numero != null ? `v${m.version_publiee_numero}` : 'Aucune'}</td>
                        <td>{m.brouillon_numero != null ? `v${m.brouillon_numero}` : '—'}</td>
                        <td>{formatDateHeure(m.modifie_le)}</td>
                        <td>
                          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                            <Link to={`/app/parametres/workflows/${m.id}`} className="btn btn--secondary btn--sm">
                              {lectureSeule ? 'Consulter' : 'Ouvrir'}
                            </Link>
                            {!lectureSeule ? (
                              <>
                                <button
                                  type="button"
                                  className="btn btn--ghost btn--sm"
                                  onClick={() => void basculerActif(m)}
                                >
                                  {m.actif ? 'Désactiver' : 'Activer'}
                                </button>
                                {m.version_publiee_numero == null ? (
                                  <button
                                    type="button"
                                    className="btn btn--ghost btn--sm"
                                    onClick={() => void supprimer(m)}
                                  >
                                    Supprimer
                                  </button>
                                ) : null}
                              </>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

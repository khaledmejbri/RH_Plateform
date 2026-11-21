import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { getFonctions, type FonctionRh } from '../../api/societeApi';
import {
  creerBrouillonModele,
  getCatalogueWorkflow,
  getModeleWorkflow,
  getVersionsModele,
  getVersionWorkflow,
  messageErreurWorkflow,
  publierVersionWorkflow,
  putVersionWorkflow,
  supprimerBrouillonWorkflow,
  validerVersionWorkflow,
  WorkflowApiError,
  type CatalogueWorkflow,
  type DefinitionWorkflow,
  type ErreurDefinition,
  type EtapeDefinition,
  type ModeleDetail,
  type VersionDetail,
  type VersionResume,
} from '../../api/workflowApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { grouperErreursParEtape } from '../../workflow/erreurs';
import PanneauEtape from './PanneauEtape';
import PanneauSimulation from './PanneauSimulation';
import { PanneauJournalModele, PanneauVersions } from './PanneauVersionsJournal';

type ContexteRoute = { lectureSeule?: boolean };
type OngletPrincipal = 'editeur' | 'simulation' | 'versions' | 'journal';

function nouvelleEtapeParDefaut(index: number): EtapeDefinition {
  return {
    code: `ETAPE_${index + 1}`,
    libelle: `Nouvelle étape ${index + 1}`,
    type: 'VALIDATION',
    intervenants: { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'UN_SEUL' },
    repli_si_vide: { action: 'BLOQUER' },
    actions: ['APPROUVER', 'REFUSER'],
    delai: { duree: 24, unite: 'HEURES_OUVREES' },
  };
}

export default function WorkflowEditeurPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const { lectureSeule } = useOutletContext<ContexteRoute>() ?? {};

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(null);
  const [fonctionsActives, setFonctionsActives] = useState<FonctionRh[]>([]);
  const [modele, setModele] = useState<ModeleDetail | null>(null);
  const [versions, setVersions] = useState<VersionResume[]>([]);
  const [versionActive, setVersionActive] = useState<VersionDetail | null>(null);
  const [definition, setDefinition] = useState<DefinitionWorkflow | null>(null);
  const [erreursDef, setErreursDef] = useState<ErreurDefinition[]>([]);
  const [modifie, setModifie] = useState(false);
  const [defConflit, setDefConflit] = useState<DefinitionWorkflow | null>(null);

  const [etapeIndex, setEtapeIndex] = useState(0);
  const [onglet, setOnglet] = useState<OngletPrincipal>('editeur');
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const chargerVersion = useCallback(async (vid: string) => {
    const det = await getVersionWorkflow(vid);
    setVersionActive(det);
    setDefinition(det.definition);
    setErreursDef(det.erreurs ?? []);
    setModifie(false);
    setDefConflit(null);
    setEtapeIndex(0);
  }, []);

  const chargerTout = useCallback(async () => {
    if (!id) return;
    setChargement(true);
    setErreur(null);
    try {
      const [cat, fns, mod, vers] = await Promise.all([
        getCatalogueWorkflow(),
        getFonctions(true).catch(() => []),
        getModeleWorkflow(id),
        getVersionsModele(id),
      ]);
      setCatalogue(cat);
      setFonctionsActives(fns);
      setModele(mod);
      setVersions(vers);

      const cible =
        (mod.brouillon_id ? vers.find((v) => v.id === mod.brouillon_id) : null) ??
        vers.find((v) => v.statut === 'PUBLIEE') ??
        vers[0];
      if (cible) {
        await chargerVersion(cible.id);
      } else {
        setVersionActive(null);
        setDefinition(null);
      }
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, [id, chargerVersion]);

  useEffect(() => {
    void chargerTout();
  }, [chargerTout]);

  // Alerte navigateur si modifications non enregistrées (D-W7-05)
  useEffect(() => {
    if (!modifie) return undefined;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [modifie]);

  const estEditable = !lectureSeule && versionActive?.statut === 'BROUILLON';
  const erreursParEtape = useMemo(() => grouperErreursParEtape(erreursDef), [erreursDef]);

  function majDefinition(nouvelle: DefinitionWorkflow) {
    setDefinition(nouvelle);
    setModifie(true);
    setMessage(null);
  }

  function insererEtape(aIndex: number) {
    if (!definition || !estEditable) return;
    const liste = [...definition.etapes];
    liste.splice(aIndex, 0, nouvelleEtapeParDefaut(liste.length));
    majDefinition({ ...definition, etapes: liste });
    setEtapeIndex(aIndex);
  }

  function deplacerEtape(idx: number, delta: -1 | 1) {
    if (!definition || !estEditable) return;
    const cible = idx + delta;
    if (cible < 0 || cible >= definition.etapes.length) return;
    const liste = [...definition.etapes];
    const [elem] = liste.splice(idx, 1);
    liste.splice(cible, 0, elem);
    majDefinition({ ...definition, etapes: liste });
    setEtapeIndex(cible);
  }

  async function supprimerEtape(idx: number) {
    if (!definition || !estEditable) return;
    const et = definition.etapes[idx];
    const ok = await confirm({
      title: `Supprimer l’étape « ${et.libelle} » ?`,
      message: 'Cette étape sera retirée du brouillon.',
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    const liste = definition.etapes.filter((_, i) => i !== idx);
    majDefinition({ ...definition, etapes: liste });
    setEtapeIndex(Math.max(0, Math.min(idx, liste.length - 1)));
  }

  async function enregistrer() {
    if (!versionActive || !definition || !estEditable) return;
    setBusy(true);
    setErreur(null);
    setMessage(null);
    try {
      const maj = await putVersionWorkflow(versionActive.id, definition, versionActive.version);
      setVersionActive(maj);
      setDefinition(maj.definition);
      setErreursDef(maj.erreurs ?? []);
      setModifie(false);
      setDefConflit(null);
      setMessage(
        (maj.erreurs ?? []).some((e) => e.niveau === 'ERREUR')
          ? 'Brouillon enregistré avec des erreurs à corriger avant publication.'
          : 'Brouillon enregistré.',
      );
    } catch (e) {
      if (e instanceof WorkflowApiError && e.status === 409 && e.code === 'MODIFIE_ENTRE_TEMPS') {
        setDefConflit(definition);
        setErreur(
          'Ce brouillon a été modifié par un autre utilisateur. La version serveur a été rechargée ; vous pouvez réappliquer votre saisie.',
        );
        try {
          await chargerVersion(versionActive.id);
        } catch {
          // garde l'erreur
        }
      } else {
        setErreur(messageErreurWorkflow(e));
      }
    } finally {
      setBusy(false);
    }
  }

  async function validerSansEnregistrer() {
    if (!versionActive) return;
    setBusy(true);
    setErreur(null);
    setMessage(null);
    try {
      const res = await validerVersionWorkflow(versionActive.id);
      setErreursDef(res.erreurs ?? []);
      setMessage(res.valide ? 'Définition valide : prête pour publication.' : 'Des erreurs ont été détectées.');
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setBusy(false);
    }
  }

  async function publier() {
    if (!versionActive || !estEditable) return;
    if (modifie) {
      setErreur('Enregistrez d’abord vos modifications avant de publier.');
      return;
    }
    const ok = await confirm({
      title: `Publier la version v${versionActive.numero} ?`,
      message: 'Cette version deviendra la version active pour les nouvelles demandes de ce modèle.',
      confirmLabel: 'Publier',
      envoyer: async () => {
        await publierVersionWorkflow(versionActive.id, versionActive.version);
      },
    });
    if (ok) {
      setMessage(`Version v${versionActive.numero} publiée.`);
      await chargerTout();
    }
  }

  async function creerNouvelleVersion() {
    if (!modele || lectureSeule) return;
    const ok = await confirm({
      title: 'Créer un nouveau brouillon ?',
      message: 'Un brouillon sera créé à partir de la dernière version de ce modèle.',
      confirmLabel: 'Créer le brouillon',
      envoyer: async () => {
        await creerBrouillonModele(modele.id);
      },
    });
    if (ok) {
      setMessage('Nouveau brouillon créé.');
      await chargerTout();
    }
  }

  async function supprimerBrouillon() {
    if (!versionActive || !estEditable) return;
    const ok = await confirm({
      title: `Supprimer le brouillon v${versionActive.numero} ?`,
      message: 'Toutes les modifications de ce brouillon seront perdues.',
      confirmLabel: 'Supprimer',
      danger: true,
      envoyer: async () => {
        await supprimerBrouillonWorkflow(versionActive.id);
      },
    });
    if (ok) {
      setMessage('Brouillon supprimé.');
      await chargerTout();
    }
  }

  async function quitterVersListe() {
    if (modifie) {
      const ok = await confirm({
        title: 'Quitter sans enregistrer ?',
        message: 'Vous avez des modifications non enregistrées sur ce brouillon.',
        confirmLabel: 'Quitter sans enregistrer',
        danger: true,
      });
      if (!ok) return;
    }
    navigate('/app/parametres/workflows');
  }

  if (chargement) return <div className="page"><LoadingState /></div>;
  if (!modele || !catalogue) {
    return (
      <div className="page">
        <ErrorAlert>
          {erreur ?? 'Modèle introuvable.'}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerTout()}>
            Réessayer
          </button>
        </ErrorAlert>
      </div>
    );
  }

  const etapes = definition?.etapes ?? [];
  const etapeCourante = etapes[etapeIndex] ?? null;

  return (
    <div className="page">
      <PageHeader
        title={`${modele.libelle} (${modele.code})`}
        lead={`Type : ${catalogue.types.find((t) => t.objet_type === modele.objet_type)?.libelle ?? modele.objet_type}${modele.sous_type ? ` · Sous-type : ${modele.sous_type}` : ''}${versionActive ? ` · Version v${versionActive.numero} (${versionActive.statut})` : ''}`}
        actions={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" className="btn btn--ghost" onClick={() => void quitterVersListe()}>
              ← Retour à la liste
            </button>
            {estEditable ? (
              <>
                <button type="button" className="btn btn--secondary" disabled={busy} onClick={() => void validerSansEnregistrer()}>
                  Valider
                </button>
                <button type="button" className="btn btn--primary" disabled={busy} onClick={() => void enregistrer()}>
                  {busy ? 'Enregistrement…' : 'Enregistrer'}
                </button>
                <button type="button" className="btn btn--primary" disabled={busy || modifie} onClick={() => void publier()}>
                  Publier
                </button>
                <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => void supprimerBrouillon()}>
                  Supprimer brouillon
                </button>
              </>
            ) : !lectureSeule && !modele.brouillon_id ? (
              <button type="button" className="btn btn--primary" disabled={busy} onClick={() => void creerNouvelleVersion()}>
                Nouvelle version (brouillon)
              </button>
            ) : null}
          </div>
        }
      />

      {message ? <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>{message}</div> : null}
      {erreur ? (
        <div className="alert alert--error" role="alert" style={{ marginBottom: 12 }}>
          {erreur}
          {defConflit ? (
            <div style={{ marginTop: 8 }}>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  setDefinition(defConflit);
                  setDefConflit(null);
                  setModifie(true);
                  setErreur(null);
                }}
              >
                Réappliquer ma saisie
              </button>
            </div>
          ) : null}
        </div>
      ) : null}

      {erreursDef.length > 0 ? (
        <div className="alert alert--warning" role="alert" style={{ marginBottom: 12 }}>
          <strong>Validation ({erreursDef.length} point{erreursDef.length > 1 ? 's' : ''}) :</strong>
          <ul style={{ margin: '6px 0 0 18px' }}>
            {erreursDef.map((e, i) => (
              <li key={i} className="small">
                <strong>{e.niveau}</strong> {e.chemin ? `(${e.chemin}) ` : ''}— {e.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="tabs" role="tablist" aria-label="Vues du modèle" style={{ marginBottom: 16 }}>
        {(
          [
            ['editeur', 'Éditeur'],
            ['simulation', 'Simulation'],
            ['versions', `Versions (${versions.length})`],
            ['journal', 'Journal'],
          ] as Array<[OngletPrincipal, string]>
        ).map(([k, l]) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={onglet === k}
            className={`tab${onglet === k ? ' tab--active' : ''}`}
            onClick={() => setOnglet(k)}
          >
            {l}
          </button>
        ))}
      </div>

      {onglet === 'editeur' && definition ? (
        <div>
          <div className="card" style={{ marginBottom: 14 }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                checked={Boolean(definition.options?.separation_des_taches)}
                disabled={!estEditable}
                onChange={(e) =>
                  majDefinition({
                    ...definition,
                    options: { ...(definition.options ?? {}), separation_des_taches: e.target.checked },
                  })
                }
              />
              <span>Séparation des tâches (le demandeur ne peut jamais valider sa propre demande)</span>
            </label>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(260px, 340px) 1fr', gap: 16, alignItems: 'start' }}>
            <section className="card" aria-label="Liste des étapes">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <h3 style={{ margin: 0 }}>Étapes ({etapes.length})</h3>
                {estEditable ? (
                  <button type="button" className="btn btn--secondary btn--sm" onClick={() => insererEtape(etapes.length)}>
                    + Étape
                  </button>
                ) : null}
              </div>

              {etapes.length === 0 ? (
                <p className="muted small">Aucune étape. Ajoutez la première étape du circuit.</p>
              ) : (
                <ol style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {etapes.map((et, idx) => {
                    const nbErr = (erreursParEtape.get(idx) ?? []).length;
                    const active = idx === etapeIndex;
                    return (
                      <li key={`${et.code}-${idx}`}>
                        {estEditable ? (
                          <div style={{ textAlign: 'center', marginBottom: 4 }}>
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                              onClick={() => insererEtape(idx)}
                            >
                              + Insérer ici
                            </button>
                          </div>
                        ) : null}
                        <div
                          tabIndex={0}
                          role="button"
                          aria-current={active ? 'step' : undefined}
                          onClick={() => setEtapeIndex(idx)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault();
                              setEtapeIndex(idx);
                            } else if (e.altKey && e.key === 'ArrowUp') {
                              e.preventDefault();
                              deplacerEtape(idx, -1);
                            } else if (e.altKey && e.key === 'ArrowDown') {
                              e.preventDefault();
                              deplacerEtape(idx, 1);
                            }
                          }}
                          style={{
                            border: active ? '2px solid var(--primary, #2563eb)' : '1px solid var(--border, #d8dee9)',
                            borderRadius: 8,
                            padding: 10,
                            cursor: 'pointer',
                            background: active ? 'var(--bg-subtle, #eff6ff)' : '#fff',
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                            <strong>{idx + 1}. {et.libelle || et.code}</strong>
                            {nbErr > 0 ? <span className="status status--danger">{nbErr}</span> : null}
                          </div>
                          <div className="small muted">{et.type} · {et.code}</div>
                          {estEditable ? (
                            <div style={{ display: 'flex', gap: 4, marginTop: 6 }} onClick={(e) => e.stopPropagation()}>
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                aria-label="Monter l'étape"
                                disabled={idx === 0}
                                onClick={() => deplacerEtape(idx, -1)}
                              >
                                ↑
                              </button>
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                aria-label="Descendre l'étape"
                                disabled={idx === etapes.length - 1}
                                onClick={() => deplacerEtape(idx, 1)}
                              >
                                ↓
                              </button>
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                onClick={() => void supprimerEtape(idx)}
                              >
                                Supprimer
                              </button>
                            </div>
                          ) : null}
                        </div>
                      </li>
                    );
                  })}
                </ol>
              )}
            </section>

            <div>
              {etapeCourante ? (
                <PanneauEtape
                  etape={etapeCourante}
                  catalogue={catalogue}
                  objetType={modele.objet_type}
                  fonctionsActives={fonctionsActives}
                  erreursEtape={erreursParEtape.get(etapeIndex) ?? []}
                  disabled={!estEditable}
                  onChange={(maj) => {
                    const next = etapes.map((x, i) => (i === etapeIndex ? maj : x));
                    majDefinition({ ...definition, etapes: next });
                  }}
                />
              ) : (
                <div className="card"><p className="muted">Sélectionnez ou ajoutez une étape pour modifier ses propriétés.</p></div>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {onglet === 'simulation' && versionActive ? (
        <PanneauSimulation
          versionId={versionActive.id}
          objetType={modele.objet_type}
          catalogue={catalogue}
        />
      ) : null}

      {onglet === 'versions' ? (
        <PanneauVersions
          versions={versions}
          versionActiveId={versionActive?.id ?? null}
          onChoisirVersion={(vid) => {
            void chargerVersion(vid).then(() => setOnglet('editeur'));
          }}
        />
      ) : null}

      {onglet === 'journal' ? <PanneauJournalModele modeleId={modele.id} /> : null}
    </div>
  );
}

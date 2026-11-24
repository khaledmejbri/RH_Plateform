import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getPresenceSites, type PresenceSite } from '../../api/presenceApi';
import { getFonctions, type FonctionRh } from '../../api/societeApi';
import {
  creerBrouillonModele,
  estConflitWorkflow,
  executerPourConfirmation,
  getActivationsWorkflow,
  getCatalogueWorkflow,
  getModeleWorkflow,
  getVersionsModele,
  getVersionWorkflow,
  messageErreurWorkflow,
  publierVersionWorkflow,
  putActivationWorkflow,
  putVersionWorkflow,
  supprimerBrouillonWorkflow,
  validerVersionWorkflow,
  WorkflowApiError,
  type ActivationWorkflow,
  type CatalogueWorkflow,
  type DefinitionWorkflow,
  type ErreurDefinition,
  type ModeleDetail,
  type VersionDetail,
  type VersionResume,
} from '../../api/workflowApi';
import { useLectureSeule } from '../../auth/useLectureSeule';
import { useConfirm } from '../../components/ConfirmDialog';
import { ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { grouperErreursParEtape, rattacherErreurs } from '../../workflow/erreurs';
import { nouvelleEtapeParDefaut, NB_ETAPES_MAX } from '../../workflow/etapes';
import { useActiverGarde } from '../../workflow/gardeNavigation';
import { libelleNiveauErreur, libelleStatutVersion, libelleTypeEtape } from '../../workflow/libelles';
import { libelleSousTypeModele } from '../../workflow/valeursListes';
import PanneauEtape from './PanneauEtape';
import PanneauNotificationsPointage from './PanneauNotificationsPointage';
import PanneauSimulation from './PanneauSimulation';
import { PanneauJournalModele, PanneauVersions } from './PanneauVersionsJournal';

type OngletPrincipal = 'editeur' | 'simulation' | 'versions' | 'journal';

type Props = {
  initialCatalogue?: CatalogueWorkflow | null;
  initialModele?: ModeleDetail | null;
  initialVersions?: VersionResume[];
  initialVersionActive?: VersionDetail | null;
  initialActivationPointage?: ActivationWorkflow | null;
  initialSitesPointage?: PresenceSite[];
};

export default function WorkflowEditeurPage({
  initialCatalogue = null,
  initialModele = null,
  initialVersions = [],
  initialVersionActive = null,
  initialActivationPointage = null,
  initialSitesPointage = [],
}: Props = {}) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const lectureSeule = useLectureSeule();

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(initialCatalogue);
  const [fonctionsActives, setFonctionsActives] = useState<FonctionRh[]>([]);
  const [modele, setModele] = useState<ModeleDetail | null>(initialModele);
  const [versions, setVersions] = useState<VersionResume[]>(initialVersions);
  const [versionActive, setVersionActive] = useState<VersionDetail | null>(initialVersionActive);
  const [definition, setDefinition] = useState<DefinitionWorkflow | null>(initialVersionActive?.definition ?? null);
  const [erreursDef, setErreursDef] = useState<ErreurDefinition[]>(initialVersionActive?.erreurs ?? []);
  const [activationPointage, setActivationPointage] = useState<ActivationWorkflow | null>(initialActivationPointage);
  const [sitesPointage, setSitesPointage] = useState<PresenceSite[]>(initialSitesPointage);
  const [sitesChargement, setSitesChargement] = useState(false);
  const [sitesErreur, setSitesErreur] = useState<string | null>(null);
  const [modifie, setModifie] = useState(false);
  const [defConflit, setDefConflit] = useState<DefinitionWorkflow | null>(null);

  const [etapeIndex, setEtapeIndex] = useState(0);
  const [onglet, setOnglet] = useState<OngletPrincipal>('editeur');
  const [chargement, setChargement] = useState(!(initialModele && initialCatalogue));
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Protège les chargements contre un changement rapide d'identifiant de modèle ou de version.
  const sequence = useRef(0);

  useActiverGarde(modifie || defConflit !== null);

  /** Charge une version ; `garderEtape` conserve l'étape sélectionnée (rechargement après un conflit). */
  const chargerVersion = useCallback(async (vid: string, garderEtape = false) => {
    const n = sequence.current;
    const det = await getVersionWorkflow(vid);
    if (n !== sequence.current) return false;
    setVersionActive(det);
    setDefinition(det.definition);
    setErreursDef(det.erreurs ?? []);
    setModifie(false);
    setDefConflit(null);
    if (!garderEtape) setEtapeIndex(0);
    return true;
  }, []);

  const chargerTout = useCallback(async () => {
    if (!id) return;
    const n = ++sequence.current;
    setChargement(true);
    setErreur(null);
    try {
      const [cat, fns, mod, vers] = await Promise.all([
        getCatalogueWorkflow(),
        getFonctions(true).catch(() => []),
        getModeleWorkflow(id),
        getVersionsModele(id),
      ]);
      if (n !== sequence.current) return;
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

      if (mod.objet_type === 'POINTAGE') {
        setSitesChargement(true);
        setSitesErreur(null);
        setOnglet((o) => (o === 'simulation' ? 'editeur' : o));
        const [acts, pageSites] = await Promise.all([
          getActivationsWorkflow().catch(() => []),
          getPresenceSites({ page: 0, size: 50 }).catch(() => {
            if (n === sequence.current) {
              setSitesErreur('Impossible de charger la liste des sites de pointage.');
            }
            return { contenu: [] as PresenceSite[] };
          }),
        ]);
        if (n !== sequence.current) return;
        setActivationPointage(acts.find((a) => a.objet_type === 'POINTAGE') ?? null);
        setSitesPointage(pageSites.contenu ?? []);
        setSitesChargement(false);
      } else {
        setActivationPointage(null);
        setSitesPointage([]);
        setSitesErreur(null);
        setSitesChargement(false);
      }
    } catch (e) {
      if (n === sequence.current) setErreur(messageErreurWorkflow(e));
    } finally {
      if (n === sequence.current) setChargement(false);
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

  /** Les erreurs du serveur sont indexées par position d'étape : une modification de structure les périme. */
  function majStructure(nouvelle: DefinitionWorkflow) {
    majDefinition(nouvelle);
    setErreursDef([]);
  }

  function insererEtape(aIndex: number) {
    if (!definition || !estEditable) return;
    if (definition.etapes.length >= NB_ETAPES_MAX) return;
    const liste = [...definition.etapes];
    liste.splice(aIndex, 0, nouvelleEtapeParDefaut(liste.map((x) => x.code)));
    majStructure({ ...definition, etapes: liste });
    setEtapeIndex(aIndex);
  }

  function deplacerEtape(idx: number, delta: -1 | 1) {
    if (!definition || !estEditable) return;
    const cible = idx + delta;
    if (cible < 0 || cible >= definition.etapes.length) return;
    const liste = [...definition.etapes];
    const [elem] = liste.splice(idx, 1);
    liste.splice(cible, 0, elem);
    majStructure({ ...definition, etapes: liste });
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
    majStructure({ ...definition, etapes: liste });
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
        // Comme SocieteParametresPage : recharger la version serveur, puis seulement poser la saisie en conflit.
        const saisie = definition;
        setErreur(
          'Ce brouillon a été modifié par un autre utilisateur. La version serveur a été rechargée ; vous pouvez réappliquer votre saisie.',
        );
        try {
          const recharge = await chargerVersion(versionActive.id, true);
          if (recharge) setDefConflit(saisie);
        } catch {
          // garde l'erreur : la version serveur n'a pas pu être rechargée
        }
      } else {
        setErreur(messageErreurWorkflow(e));
      }
    } finally {
      setBusy(false);
    }
  }

  async function validerSansEnregistrer() {
    if (!versionActive || modifie) return;
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
    let conflit = false;
    const ok = await confirm({
      title: `Publier la version v${versionActive.numero} ?`,
      message:
        modele?.objet_type === 'POINTAGE'
          ? 'Cette version deviendra la version active pour les règles de notification de pointage.'
          : 'Cette version deviendra la version active pour les nouvelles demandes de ce modèle.',
      confirmLabel: 'Publier',
      envoyer: () =>
        executerPourConfirmation(() => publierVersionWorkflow(versionActive.id, versionActive.version)),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage(`Version v${versionActive.numero} publiée.`);
      await chargerTout();
    } else if (conflit) {
      await chargerTout();
      setErreur('Ce modèle a été modifié entre-temps par un autre utilisateur : la page a été rechargée.');
    }
  }

  async function activerCircuitPointage() {
    if (!activationPointage || lectureSeule || activationPointage.mode === 'MOTEUR') return;
    let conflit = false;
    const ok = await confirm({
      title: 'Basculer « Pointage » vers Circuit paramétré ?',
      message: 'Les notifications dynamiques de pointage suivront la version publiée de ce modèle.',
      confirmLabel: 'Passer en Circuit paramétré',
      envoyer: () =>
        executerPourConfirmation(() =>
          putActivationWorkflow('POINTAGE', 'MOTEUR', activationPointage.version),
        ),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage('Circuit Pointage passé en Circuit paramétré.');
      await chargerTout();
    } else if (conflit) {
      await chargerTout();
      setErreur('L’activation Pointage a été modifiée entre-temps : la page a été rechargée.');
    }
  }

  async function creerNouvelleVersion() {
    if (!modele || lectureSeule) return;
    if ((modifie || defConflit !== null) && !(await confirmerAbandon())) return;
    let conflit = false;
    const ok = await confirm({
      title: 'Créer un nouveau brouillon ?',
      message: 'Un brouillon sera créé à partir de la dernière version de ce modèle.',
      confirmLabel: 'Créer le brouillon',
      envoyer: () => executerPourConfirmation(() => creerBrouillonModele(modele.id)),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage('Nouveau brouillon créé.');
      await chargerTout();
    } else if (conflit) {
      await chargerTout();
      setErreur('Un brouillon existe déjà ou le modèle a changé : la page a été rechargée.');
    }
  }

  async function supprimerBrouillon() {
    if (!versionActive || !estEditable) return;
    let conflit = false;
    const ok = await confirm({
      title: `Supprimer le brouillon v${versionActive.numero} ?`,
      message: 'Toutes les modifications de ce brouillon seront perdues.',
      confirmLabel: 'Supprimer',
      danger: true,
      envoyer: () => executerPourConfirmation(() => supprimerBrouillonWorkflow(versionActive.id)),
      echecDefinitif: (e) => {
        conflit = estConflitWorkflow(e);
        return conflit;
      },
    });
    if (ok) {
      setMessage('Brouillon supprimé.');
      await chargerTout();
    } else if (conflit) {
      await chargerTout();
      setErreur('Ce brouillon a changé entre-temps : la page a été rechargée.');
    }
  }

  /** Confirmation avant d'abandonner la saisie en cours (ouverture d'une autre version, nouvelle version). */
  function confirmerAbandon(): Promise<boolean> {
    return confirm({
      title: 'Abandonner les modifications ?',
      message: 'Vous avez des modifications non enregistrées sur ce brouillon. Elles seront perdues.',
      confirmLabel: 'Abandonner les modifications',
      danger: true,
    });
  }

  async function choisirVersion(vid: string) {
    if ((modifie || defConflit !== null) && !(await confirmerAbandon())) return;
    setErreur(null);
    try {
      if (await chargerVersion(vid)) setOnglet('editeur');
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
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

  const estPointage = modele.objet_type === 'POINTAGE';
  const etapes = definition?.etapes ?? [];
  const etapeCourante = etapes[etapeIndex] ?? null;
  const ongletsDisponibles: Array<[OngletPrincipal, string]> = estPointage
    ? [
        ['editeur', 'Éditeur'],
        ['versions', `Versions (${versions.length})`],
        ['journal', 'Journal'],
      ]
    : [
        ['editeur', 'Éditeur'],
        ['simulation', 'Simulation'],
        ['versions', `Versions (${versions.length})`],
        ['journal', 'Journal'],
      ];

  return (
    <div className="page">
      <PageHeader
        title={`${modele.libelle} (${modele.code})`}
        lead={`Type : ${catalogue.types.find((t) => t.objet_type === modele.objet_type)?.libelle ?? 'Type inconnu'}${modele.sous_type ? ` · Sous-type : ${libelleSousTypeModele(modele.sous_type)}` : ''}${versionActive ? ` · Version v${versionActive.numero} (${libelleStatutVersion(versionActive.statut)})` : ''}`}
        actions={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" className="btn btn--ghost" onClick={() => void quitterVersListe()}>
              ← Retour à la liste
            </button>
            {estEditable ? (
              <>
                <button
                  type="button"
                  className="btn btn--secondary"
                  disabled={busy || modifie}
                  title={modifie ? 'La validation porte sur la dernière version enregistrée : enregistrez d’abord.' : undefined}
                  onClick={() => void validerSansEnregistrer()}
                >
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
      {modifie && estEditable ? (
        <div className="alert alert--info" role="status" style={{ marginBottom: 12 }}>
          {estPointage
            ? 'Modifications non enregistrées. Enregistrez d’abord vos modifications avant de valider ou publier.'
            : 'Modifications non enregistrées. « Valider » et la simulation portent sur la dernière version enregistrée : enregistrez d’abord.'}
        </div>
      ) : null}
      {erreur ? (
        <div className="alert alert--error" role="alert" style={{ marginBottom: 12 }}>
          {erreur}
        </div>
      ) : null}
      {defConflit !== null ? (
        <div className="alert alert--warning" role="alert" style={{ marginBottom: 12 }}>
          Votre saisie n’a pas été enregistrée : le brouillon a été modifié entre-temps.
          {estEditable ? (
            <div style={{ marginTop: 8 }}>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  setDefinition(defConflit);
                  setDefConflit(null);
                  setModifie(true);
                  setErreur(null);
                  setErreursDef([]);
                  setEtapeIndex((i) => Math.max(0, Math.min(i, (defConflit.etapes ?? []).length - 1)));
                }}
              >
                Réappliquer ma saisie
              </button>{' '}
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDefConflit(null)}>
                Abandonner ma saisie
              </button>
            </div>
          ) : (
            <div style={{ marginTop: 8 }}>
              Cette version n’est plus modifiable : votre saisie ne peut pas être réappliquée.{' '}
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDefConflit(null)}>
                Fermer
              </button>
            </div>
          )}
        </div>
      ) : null}

      {erreursDef.length > 0 ? (
        <div className="alert alert--warning" role="alert" style={{ marginBottom: 12 }}>
          <strong>Validation ({erreursDef.length} point{erreursDef.length > 1 ? 's' : ''}) :</strong>
          <ul style={{ margin: '6px 0 0 18px' }}>
            {erreursDef.map((e, i) => {
              const { indexEtape } = rattacherErreurs(e.chemin);
              const etape = indexEtape !== null ? etapes[indexEtape] : undefined;
              return (
                <li key={i} className="small">
                  <strong>{libelleNiveauErreur(e.niveau)}</strong>
                  {etape ? (
                    <>
                      {' '}— étape{' '}
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        style={{ padding: '0 4px' }}
                        onClick={() => {
                          setOnglet('editeur');
                          setEtapeIndex(indexEtape as number);
                        }}
                      >
                        {(indexEtape as number) + 1}. {etape.libelle || etape.code}
                      </button>
                    </>
                  ) : null}{' '}
                  : {e.message}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}

      {estPointage && activationPointage ? (
        <div
          className="card"
          role="region"
          aria-label="État du circuit Pointage"
          style={{
            marginBottom: 14,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <strong>État du circuit Pointage :</strong>
              <span
                className={
                  activationPointage.mode === 'MOTEUR' ? 'status status--success' : 'status status--neutral'
                }
              >
                {activationPointage.mode === 'MOTEUR' ? 'Circuit paramétré' : 'Circuit standard'}
              </span>
              {!modele.actif ? <span className="status status--warning">Modèle inactif</span> : null}
            </div>
            <p className="small muted" style={{ margin: '4px 0 0 0' }}>
              {activationPointage.mode === 'MOTEUR'
                ? 'Les notifications dynamiques suivent la version publiée active de ce modèle.'
                : modele.version_publiee_numero != null
                  ? 'Le circuit Pointage est en mode standard : aucune notification dynamique n’est envoyée tant qu’il n’est pas activé.'
                  : 'Le circuit Pointage est en mode standard. Publiez d’abord une version avant de pouvoir passer en Circuit paramétré.'}
            </p>
          </div>
          {!lectureSeule && activationPointage.mode === 'HERITE' && modele.version_publiee_numero != null ? (
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={busy}
              onClick={() => void activerCircuitPointage()}
            >
              Passer en Circuit paramétré
            </button>
          ) : null}
        </div>
      ) : null}

      <div className="tabs" role="tablist" aria-label="Vues du modèle" style={{ marginBottom: 16 }}>
        {ongletsDisponibles.map(([k, l]) => (
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
        estPointage ? (
          <PanneauNotificationsPointage
            valeur={definition.notifications_dynamiques}
            sites={sitesPointage}
            sitesChargement={sitesChargement}
            sitesErreur={sitesErreur}
            erreurs={erreursDef}
            disabled={!estEditable}
            onChange={(nd) => majDefinition({ ...definition, notifications_dynamiques: nd })}
          />
        ) : (
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
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={etapes.length >= NB_ETAPES_MAX}
                    title={etapes.length >= NB_ETAPES_MAX ? `Maximum ${NB_ETAPES_MAX} étapes par modèle.` : undefined}
                    onClick={() => insererEtape(etapes.length)}
                  >
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
                          <div className="small muted">{libelleTypeEtape(et.type)}</div>
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
        )
      ) : null}

      {!estPointage && onglet === 'simulation' && versionActive ? (
        <PanneauSimulation
          key={versionActive.id}
          modifie={modifie}
          versionId={versionActive.id}
          objetType={modele.objet_type}
          catalogue={catalogue}
        />
      ) : null}

      {onglet === 'versions' ? (
        <PanneauVersions
          versions={versions}
          versionActiveId={versionActive?.id ?? null}
          onChoisirVersion={(vid) => void choisirVersion(vid)}
        />
      ) : null}

      {onglet === 'journal' ? <PanneauJournalModele modeleId={modele.id} /> : null}
    </div>
  );
}


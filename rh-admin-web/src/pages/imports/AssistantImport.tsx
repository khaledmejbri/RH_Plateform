import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react';
import {
  analyserCollaborateurs,
  analyserSoldes,
  confirmerImport,
  controlerFichier,
  corrigerCorrespondance,
  getLignes,
  messageImport,
  telechargerLignesEnErreur,
  telechargerModele,
  type Analyse,
  type CompteRendu,
  type LigneAnalyse,
  type TypeImport,
} from '../../api/importsApi';
import { ApiMetierError, type PageContrat } from '../../api/metierApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { Chip, Icon, LoadingState, type Tone } from '../../components/ui';
import { formatDateHeure, formatJoursNombre, formatJoursSigne, TIRET } from '../../utils/format';
import { pluriel } from '../../utils/filtres';
import './imports.css';

type Etape = 'depot' | 'correspondance' | 'apercu' | 'compte-rendu';

const TITRES: Record<Etape, string> = {
  depot: '1. Déposer le fichier',
  correspondance: '2. Associer les colonnes',
  apercu: '3. Vérifier l’aperçu',
  'compte-rendu': '4. Compte rendu',
};

const MOTIF_MIN = 5;
const MOTIF_MAX = 500;

function toneAction(action: string): Tone {
  if (action === 'ERREUR' || action === 'REJETEE') return 'danger';
  if (action === 'CREATION') return 'success';
  if (action === 'MISE_A_JOUR' || action === 'AJUSTEMENT') return 'info';
  return 'neutral';
}

type Props = {
  type: TypeImport;
  /** Libellé du bouton de sortie du compte rendu et destination (la liste d'origine est rechargée à l'arrivée). */
  libelleSortie: string;
  onTermine: () => void;
};

/** Assistant d'import générique : dépôt, correspondance des colonnes, aperçu ligne par ligne, confirmation, compte rendu. */
export default function AssistantImport({ type, libelleSortie, onTermine }: Props) {
  const confirm = useConfirm();
  const soldes = type === 'SOLDES';
  const [etape, setEtape] = useState<Etape>('depot');
  const [fichier, setFichier] = useState<File | null>(null);
  const [erreurFichier, setErreurFichier] = useState<string | null>(null);
  const [creerComptes, setCreerComptes] = useState(false);
  const [motif, setMotif] = useState('');
  const [analyse, setAnalyse] = useState<Analyse | null>(null);
  const [revision, setRevision] = useState(0);
  const [occupe, setOccupe] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [compteRendu, setCompteRendu] = useState<CompteRendu | null>(null);
  const [telechargement, setTelechargement] = useState(false);
  const titreRef = useRef<HTMLHeadingElement>(null);

  // Focus sur le titre de chaque étape (clavier et lecteurs d'écran).
  useEffect(() => { titreRef.current?.focus(); }, [etape]);

  function retourAuDepot(message: string | null) {
    setAnalyse(null);
    setCompteRendu(null);
    setEtape('depot');
    setErreur(message);
  }

  /** Une analyse expirée ou confirmée ramène au dépôt ; les autres erreurs restent affichées sur l'étape. */
  function traiterErreur(e: unknown) {
    if (e instanceof ApiMetierError && (e.code === 'IMPORT_EXPIRE' || e.code === 'ANALYSE_INTROUVABLE')) {
      retourAuDepot(messageImport(e));
      return;
    }
    setErreur(messageImport(e));
  }

  function choisir(f: File | null) {
    setErreur(null);
    if (!f) return;
    const probleme = controlerFichier(f);
    setErreurFichier(probleme);
    setFichier(probleme ? null : f);
  }

  async function analyser() {
    if (!fichier || occupe) return;
    setOccupe(true);
    setErreur(null);
    try {
      const a = soldes ? await analyserSoldes(fichier, motif) : await analyserCollaborateurs(fichier, creerComptes);
      setAnalyse(a);
      setRevision((r) => r + 1);
      setEtape('correspondance');
    } catch (e) {
      setErreur(messageImport(e));
    } finally {
      setOccupe(false);
    }
  }

  async function recalculer(colonnes: Array<{ index: number; champ: string | null }>, comptes: boolean) {
    if (!analyse || occupe) return;
    setOccupe(true);
    setErreur(null);
    try {
      const a = await corrigerCorrespondance(analyse.analyse_id, {
        ligne_en_tete: analyse.ligne_en_tete,
        colonnes,
        ...(soldes ? {} : { creer_comptes: comptes }),
      });
      setAnalyse(a);
      setCreerComptes(a.creer_comptes);
      setRevision((r) => r + 1);
    } catch (e) {
      traiterErreur(e);
    } finally {
      setOccupe(false);
    }
  }

  function changerColonne(index: number, champ: string) {
    if (!analyse) return;
    const colonnes = analyse.colonnes.map((c) => ({ index: c.index, champ: c.index === index ? (champ || null) : c.champ }));
    void recalculer(colonnes, creerComptes);
  }

  function changerComptes(valeur: boolean) {
    setCreerComptes(valeur);
    if (analyse) void recalculer(analyse.colonnes.map((c) => ({ index: c.index, champ: c.champ })), valeur);
  }

  const importables = analyse
    ? soldes ? (analyse.totaux.ajustements ?? 0) : (analyse.totaux.creations ?? 0) + (analyse.totaux.mises_a_jour ?? 0)
    : 0;
  const motifOk = !soldes || (motif.trim().length >= MOTIF_MIN && motif.trim().length <= MOTIF_MAX);
  const peutImporter = !!analyse && analyse.confirmable && importables > 0 && motifOk && !occupe;

  async function demanderConfirmation() {
    if (!analyse || !peutImporter) return;
    const a = analyse;
    const nbErreurs = a.totaux.erreurs;
    const objet = soldes ? pluriel(importables, 'ajustement') : pluriel(importables, 'fiche');
    const reste = nbErreurs > 0
      ? ` ${nbErreurs > 1 ? `${nbErreurs} lignes en erreur ne seront pas importées` : '1 ligne en erreur ne sera pas importée'}.`
      : '';
    let echec: unknown = null;
    let rendu: CompteRendu | null = null;
    const ok = await confirm({
      title: soldes ? 'Appliquer ces ajustements ?' : 'Importer ces fiches ?',
      message: `${soldes ? 'Appliquer' : 'Importer'} ${objet} ?${reste}`,
      confirmLabel: soldes ? 'Appliquer' : 'Importer',
      envoyer: async () => {
        try {
          rendu = await confirmerImport(a.analyse_id, soldes ? motif.trim() : undefined);
        } catch (e) {
          echec = e;
          throw new Error(messageImport(e));
        }
      },
      echecDefinitif: () => echec instanceof ApiMetierError
        && ['IMPORT_EXPIRE', 'IMPORT_DEJA_CONFIRME', 'ANALYSE_INTROUVABLE'].includes(echec.code ?? ''),
    });
    if (echec && !ok) {
      traiterErreur(echec);
      return;
    }
    if (ok && rendu) {
      setCompteRendu(rendu);
      setEtape('compte-rendu');
      setErreur(null);
    }
  }

  async function telechargerErreurs() {
    if (!analyse || telechargement) return;
    setTelechargement(true);
    setErreur(null);
    try {
      await telechargerLignesEnErreur(analyse.analyse_id);
    } catch (e) {
      traiterErreur(e);
    } finally {
      setTelechargement(false);
    }
  }

  function recommencer() {
    setFichier(null);
    setErreurFichier(null);
    setMotif('');
    setCreerComptes(false);
    retourAuDepot(null);
  }

  return (
    <div className="import">
      <ol className="import__etapes" aria-label="Étapes de l’import">
        {(Object.keys(TITRES) as Etape[]).map((e) => (
          <li key={e} aria-current={e === etape ? 'step' : undefined} className={e === etape ? 'import__etape import__etape--on' : 'import__etape'}>
            {TITRES[e]}
          </li>
        ))}
      </ol>

      <h2 ref={titreRef} tabIndex={-1} className="import__titre">{TITRES[etape]}</h2>

      {erreur ? <div className="alert alert--error" role="alert">{erreur}</div> : null}

      {etape === 'depot' ? (
        <Depot
          type={type}
          fichier={fichier}
          erreurFichier={erreurFichier}
          creerComptes={creerComptes}
          onComptes={setCreerComptes}
          occupe={occupe}
          onChoisir={choisir}
          onAnalyser={() => void analyser()}
        />
      ) : null}

      {etape === 'correspondance' && analyse ? (
        <div>
          <BandeauxFichier analyse={analyse} />
          <p className="muted">
            Fichier « {analyse.nom_fichier} ». Vérifiez à quel champ correspond chaque colonne ; choisissez « Ignorer » pour une colonne inutile.
          </p>
          {!soldes ? (
            <label className="import__case">
              <input type="checkbox" checked={creerComptes} disabled={occupe} onChange={(e) => changerComptes(e.target.checked)} />{' '}
              Créer les comptes d’accès
              <span className="muted small"> — Un courriel de bienvenue est envoyé à chaque nouvelle fiche qui a un courriel.</span>
            </label>
          ) : null}
          <div className="table-wrap" aria-busy={occupe}>
            <table className="data-table">
              <caption className="sr-only">Correspondance entre les colonnes du fichier et les champs</caption>
              <thead>
                <tr><th scope="col">Colonne du fichier</th><th scope="col">Champ</th><th scope="col">État</th></tr>
              </thead>
              <tbody>
                {analyse.colonnes.map((c) => (
                  <tr key={c.index}>
                    <td>{c.en_tete || `Colonne ${c.index + 1}`}</td>
                    <td>
                      <label className="sr-only" htmlFor={`col-${c.index}`}>Champ pour la colonne {c.en_tete || c.index + 1}</label>
                      <select id={`col-${c.index}`} className="field-input" value={c.champ ?? ''} disabled={occupe}
                        onChange={(e) => changerColonne(c.index, e.target.value)}>
                        <option value="">Ignorer</option>
                        {analyse.champs_disponibles.map((d) => (
                          <option key={d.champ} value={d.champ}>{d.libelle}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      {c.etat === 'RECONNUE' ? <Chip tone="success" icon="check">Reconnue</Chip>
                        : c.etat === 'EN_DOUBLE' ? <Chip tone="danger" icon="alert">En double</Chip>
                          : <Chip>Ignorée</Chip>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {occupe ? <LoadingState label="Recalcul de l’aperçu…" /> : null}
          <div className="import__actions">
            <button type="button" className="btn btn--ghost" disabled={occupe} onClick={recommencer}>Changer de fichier</button>
            <button type="button" className="btn btn--primary" disabled={occupe} onClick={() => { setErreur(null); setEtape('apercu'); }}>
              Voir l’aperçu
            </button>
          </div>
        </div>
      ) : null}

      {etape === 'apercu' && analyse ? (
        <div>
          <BandeauxFichier analyse={analyse} />
          <Totaux analyse={analyse} soldes={soldes} />
          {soldes ? (
            <div className="import__motif">
              <label className="field-label" htmlFor="import-motif">Motif global (obligatoire)</label>
              <input id="import-motif" className="field-input" value={motif} maxLength={MOTIF_MAX} autoComplete="off"
                placeholder="Ex. Reprise des soldes au 30/11/2026" onChange={(e) => setMotif(e.target.value)} />
              <p className={`muted small${motif.trim().length < MOTIF_MIN ? ' field-error' : ''}`} aria-live="polite">
                {motif.trim().length} / {MOTIF_MAX} caractères (au moins {MOTIF_MIN}). Appliqué aux lignes sans motif propre.
              </p>
            </div>
          ) : null}
          <ApercuLignes key={revision} analyseId={analyse.analyse_id} soldes={soldes} onErreur={traiterErreur} />
          <div className="import__actions">
            <button type="button" className="btn btn--ghost" onClick={() => setEtape('correspondance')}>Retour aux colonnes</button>
            {analyse.totaux.erreurs > 0 ? (
              <button type="button" className="btn btn--secondary" disabled={telechargement} onClick={() => void telechargerErreurs()}>
                <Icon name="download" />
                {telechargement ? 'Téléchargement…' : 'Fichier des erreurs'}
              </button>
            ) : null}
            <button type="button" className="btn btn--primary" disabled={!peutImporter} aria-busy={occupe}
              onClick={() => void demanderConfirmation()}>
              {soldes ? `Appliquer ${pluriel(importables, 'ajustement')}` : `Importer ${pluriel(importables, 'ligne')}`}
            </button>
          </div>
          {!analyse.confirmable ? <p className="muted small">L’import est impossible tant que le fichier contient une erreur de structure.</p> : null}
          {analyse.confirmable && importables === 0 ? <p className="muted small">Aucune ligne importable dans ce fichier.</p> : null}
        </div>
      ) : null}

      {etape === 'compte-rendu' && compteRendu ? (
        <div>
          <div className="alert alert--success" role="status">
            <Icon name="check" />
            {soldes ? 'Import des soldes terminé.' : 'Import terminé.'}
          </div>
          <dl className="detail-grid">
            {compteRendu.creees !== null ? <div><dt>Fiches créées</dt><dd>{compteRendu.creees}</dd></div> : null}
            {compteRendu.mises_a_jour !== null ? <div><dt>Fiches mises à jour</dt><dd>{compteRendu.mises_a_jour}</dd></div> : null}
            {compteRendu.ajustements !== null ? <div><dt>Ajustements appliqués</dt><dd>{compteRendu.ajustements}</dd></div> : null}
            {compteRendu.inchangees !== null ? <div><dt>Inchangées</dt><dd>{compteRendu.inchangees}</dd></div> : null}
            <div><dt>Lignes rejetées</dt><dd>{compteRendu.rejetees}</dd></div>
            {compteRendu.comptes_demandes !== null ? <div><dt>Comptes d’accès demandés</dt><dd>{compteRendu.comptes_demandes}</dd></div> : null}
          </dl>
          {compteRendu.avertissements.length > 0 ? (
            <div className="alert alert--warning" role="status">
              <ul className="import__liste">{compteRendu.avertissements.map((m, i) => <li key={i}>{m}</li>)}</ul>
            </div>
          ) : null}
          <div className="import__actions">
            {compteRendu.rejetees > 0 ? (
              <button type="button" className="btn btn--secondary" disabled={telechargement} onClick={() => void telechargerErreurs()}>
                <Icon name="download" />
                {telechargement ? 'Téléchargement…' : 'Fichier des erreurs'}
              </button>
            ) : null}
            <button type="button" className="btn btn--ghost" onClick={recommencer}>Nouvel import</button>
            <button type="button" className="btn btn--primary" onClick={onTermine}>{libelleSortie}</button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function Depot({
  type, fichier, erreurFichier, creerComptes, onComptes, occupe, onChoisir, onAnalyser,
}: {
  type: TypeImport;
  fichier: File | null;
  erreurFichier: string | null;
  creerComptes: boolean;
  onComptes: (v: boolean) => void;
  occupe: boolean;
  onChoisir: (f: File | null) => void;
  onAnalyser: () => void;
}) {
  const entree = useRef<HTMLInputElement>(null);
  const [survol, setSurvol] = useState(false);
  const [erreurModele, setErreurModele] = useState<string | null>(null);

  function deposer(e: DragEvent) {
    e.preventDefault();
    setSurvol(false);
    onChoisir(e.dataTransfer.files?.[0] ?? null);
  }

  async function modele() {
    setErreurModele(null);
    try {
      await telechargerModele(type);
    } catch (e) {
      setErreurModele(messageImport(e));
    }
  }

  return (
    <div>
      <div
        className={`import__depot${survol ? ' import__depot--survol' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setSurvol(true); }}
        onDragLeave={() => setSurvol(false)}
        onDrop={deposer}
      >
        <Icon name="file" size={28} />
        <p>Glissez votre fichier ici ou</p>
        <button type="button" className="btn btn--secondary" onClick={() => entree.current?.click()}>Choisir un fichier</button>
        <input ref={entree} type="file" className="sr-only" tabIndex={-1} aria-label="Fichier à importer"
          accept=".xlsx,.csv" onChange={(e) => { onChoisir(e.target.files?.[0] ?? null); e.target.value = ''; }} />
        <p className="muted small">Format .xlsx ou .csv, 10 Mo au maximum.</p>
        {fichier ? <p role="status"><strong>{fichier.name}</strong> prêt à être analysé.</p> : null}
      </div>
      {erreurFichier ? <p className="field-error" role="alert">{erreurFichier}</p> : null}
      <p>
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => void modele()}>
          <Icon name="download" size={14} />
          Télécharger le modèle
        </button>
      </p>
      {erreurModele ? <p className="field-error" role="alert">{erreurModele}</p> : null}
      {type === 'COLLABORATEURS' ? (
        <label className="import__case">
          <input type="checkbox" checked={creerComptes} onChange={(e) => onComptes(e.target.checked)} />{' '}
          Créer les comptes d’accès
          <span className="muted small"> — Un courriel de bienvenue est envoyé à chaque nouvelle fiche qui a un courriel.</span>
        </label>
      ) : null}
      <div className="import__actions">
        <button type="button" className="btn btn--primary" disabled={!fichier || occupe} aria-busy={occupe} onClick={onAnalyser}>
          {occupe ? 'Analyse en cours…' : 'Analyser le fichier'}
        </button>
      </div>
    </div>
  );
}

function BandeauxFichier({ analyse }: { analyse: Analyse }) {
  return (
    <>
      {analyse.erreurs_fichier.length > 0 ? (
        <div className="alert alert--error" role="alert">
          <ul className="import__liste">{analyse.erreurs_fichier.map((m, i) => <li key={i}>{m.message}</li>)}</ul>
        </div>
      ) : null}
      {analyse.avertissements_fichier.length > 0 ? (
        <div className="alert alert--warning" role="status">
          <ul className="import__liste">{analyse.avertissements_fichier.map((m, i) => <li key={i}>{m.message}</li>)}</ul>
        </div>
      ) : null}
      {analyse.colonnes_ignorees.length > 0 ? (
        <p className="muted small">Colonnes ignorées : {analyse.colonnes_ignorees.join(', ')}.</p>
      ) : null}
      <p className="muted small">Cette analyse expire le {formatDateHeure(analyse.expire_le)} si vous ne faites rien.</p>
    </>
  );
}

function Totaux({ analyse, soldes }: { analyse: Analyse; soldes: boolean }) {
  const t = analyse.totaux;
  const items: Array<[string, number | null, Tone]> = soldes
    ? [['Lignes', t.lignes, 'neutral'], ['Ajustements', t.ajustements, 'info'], ['Inchangés', t.inchangees, 'neutral'], ['Erreurs', t.erreurs, 'danger'], ['Avertissements', t.avertissements, 'warning']]
    : [['Lignes', t.lignes, 'neutral'], ['Créations', t.creations, 'success'], ['Mises à jour', t.mises_a_jour, 'info'], ['Inchangées', t.inchangees, 'neutral'], ['Erreurs', t.erreurs, 'danger'], ['Avertissements', t.avertissements, 'warning']];
  return (
    <ul className="import__totaux" aria-label="Totaux de l’analyse">
      {items.map(([label, n, tone]) => (
        <li key={label}><Chip tone={tone}>{label} : <strong>{n ?? 0}</strong></Chip></li>
      ))}
    </ul>
  );
}

function ApercuLignes({ analyseId, soldes, onErreur }: { analyseId: string; soldes: boolean; onErreur: (e: unknown) => void }) {
  const [page, setPage] = useState(0);
  const [erreursSeulement, setErreursSeulement] = useState(false);
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; data?: PageContrat<LigneAnalyse>; message?: string }>({ statut: 'chargement' });
  const surErreur = useRef(onErreur);
  surErreur.current = onErreur;

  const charger = useCallback(async () => {
    setEtat((e) => ({ ...e, statut: 'chargement' }));
    try {
      setEtat({ statut: 'ok', data: await getLignes(analyseId, page, erreursSeulement) });
    } catch (e) {
      if (e instanceof ApiMetierError && (e.code === 'IMPORT_EXPIRE' || e.code === 'ANALYSE_INTROUVABLE')) {
        surErreur.current(e);
        return;
      }
      setEtat({ statut: 'erreur', message: messageImport(e) });
    }
  }, [analyseId, page, erreursSeulement]);

  useEffect(() => { void charger(); }, [charger]);

  const data = etat.data;
  const pages = data ? Math.max(1, data.total_pages) : 1;

  return (
    <section aria-label="Aperçu ligne par ligne">
      <label className="import__case">
        <input type="checkbox" checked={erreursSeulement} onChange={(e) => { setErreursSeulement(e.target.checked); setPage(0); }} /> Erreurs seulement
      </label>
      {etat.statut === 'chargement' && !data ? <LoadingState label="Chargement de l’aperçu…" /> : null}
      {etat.statut === 'erreur' ? (
        <div className="alert alert--error" role="alert">
          {etat.message}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
        </div>
      ) : null}
      {data && data.contenu.length === 0 ? (
        <p className="detail-vide">{erreursSeulement ? 'Aucune ligne en erreur.' : 'Aucune ligne à afficher.'}</p>
      ) : null}
      {data && data.contenu.length > 0 ? (
        <div className="table-wrap" aria-busy={etat.statut === 'chargement'}>
          <table className="data-table">
            <caption className="sr-only">Lignes du fichier analysé</caption>
            <thead>
              <tr>
                <th scope="col">Ligne</th>
                <th scope="col">Matricule</th>
                <th scope="col">Nom</th>
                {soldes ? (
                  <>
                    <th scope="col">Solde actuel</th>
                    <th scope="col">Solde cible</th>
                    <th scope="col">Écart</th>
                  </>
                ) : null}
                <th scope="col">Action</th>
                <th scope="col">Détail</th>
              </tr>
            </thead>
            <tbody>
              {data.contenu.map((l) => (
                <tr key={l.numero}>
                  <td>{l.numero}</td>
                  <td>{l.matricule ?? TIRET}</td>
                  <td>{l.nom_complet ?? TIRET}</td>
                  {soldes ? (
                    <>
                      <td>{formatJoursNombre(l.solde_actuel)}</td>
                      <td>{formatJoursNombre(l.solde_cible)}</td>
                      <td>{l.ecart === null || l.ecart === undefined ? TIRET : formatJoursSigne(l.ecart)}</td>
                    </>
                  ) : null}
                  <td><Chip tone={toneAction(l.action)}>{l.action_libelle}</Chip></td>
                  <td><DetailLigne ligne={l} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {data && pages > 1 ? (
        <div className="pager">
          <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
          <span>Page {page + 1} sur {pages}</span>
          <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
        </div>
      ) : null}
    </section>
  );
}

/** Erreurs, avertissements et changements d'une ligne ; tout est affiché comme du texte. */
function DetailLigne({ ligne }: { ligne: LigneAnalyse }) {
  const rien = ligne.erreurs.length === 0 && ligne.avertissements.length === 0
    && (ligne.action === 'INCHANGEE' || ligne.action === 'INCHANGE' || ligne.modifications.length === 0) && !ligne.motif;
  if (rien) return <>{TIRET}</>;
  return (
    <ul className="import__liste">
      {ligne.erreurs.map((e, i) => <li key={`e${i}`} className="field-error">{e.message}</li>)}
      {ligne.avertissements.map((a, i) => <li key={`a${i}`} className="muted">{a.message}</li>)}
      {ligne.action !== 'INCHANGEE' && ligne.action !== 'INCHANGE'
        ? ligne.modifications.map((m, i) => (
          <li key={`m${i}`}>{m.champ_libelle} : {m.avant ? m.avant : TIRET} → {m.apres ? m.apres : TIRET}</li>
        ))
        : null}
      {ligne.motif ? <li className="muted">Motif : {ligne.motif}</li> : null}
    </ul>
  );
}

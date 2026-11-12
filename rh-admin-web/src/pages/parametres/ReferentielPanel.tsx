import { useCallback, useEffect, useMemo, useState, type DragEvent } from 'react';
import {
  desactiverValeur,
  getValeurs,
  messageParametre,
  ordonnerValeurs,
  ParametreApiError,
  reactiverValeur,
  supprimerValeur,
  type DescripteurReferentiel,
  type ServiceParametre,
  type Valeur,
} from '../../api/parametresApi';
import { useConfirm } from '../../components/ConfirmDialog';
import PastilleCouleur from '../../components/PastilleCouleur';
import { EmptyState, Icon, LoadingState } from '../../components/ui';
import Onglets from '../../detail/Onglets';
import { nomCouleur } from '../../utils/palette';
import { rafraichirParametres } from '../../utils/parametresStore';
import JournalPanel from './JournalPanel';
import ValeurFormulaire, { ATTRIBUTS, libelleAttribut } from './ValeurFormulaire';

type Bandeau =
  | { genre: 'utilise'; message: string; utilisations: number | null; valeur: Valeur }
  | { genre: 'modifie'; message: string }
  | { genre: 'erreur'; message: string }
  | { genre: 'succes'; message: string };

type Action = 'desactiver' | 'reactiver' | 'supprimer';

/** Les erreurs métier (4xx) ferment la fenêtre de confirmation et s'affichent dans le bandeau. */
function echecMetier(e: unknown): boolean {
  return e instanceof ParametreApiError && e.status >= 400 && e.status < 500 && e.status !== 408;
}

function valeurAttribut(v: Valeur, nom: string): unknown {
  return (v as unknown as Record<string, unknown>)[nom];
}

function cellAttribut(v: Valeur, nom: string, type: string) {
  const brut = valeurAttribut(v, nom);
  if (type === 'BOOLEEN') {
    const verrou = nom === 'piece_jointe_obligatoire' && v.piece_jointe_verrouillee;
    return (
      <>
        {brut ? 'Oui' : 'Non'}
        {verrou ? <Icon name="lock" size={12} label="Toujours obligatoire" style={{ marginLeft: 4 }} /> : null}
      </>
    );
  }
  if (brut === null || brut === undefined) return '—';
  return `${String(brut)}${ATTRIBUTS[nom]?.unite ? ` ${ATTRIBUTS[nom].unite}` : ''}`;
}

type Props = { service: ServiceParametre; descripteur: DescripteurReferentiel; lectureSeule: boolean };

/** Un référentiel : tableau des valeurs, ajout / modification, ordre, historique (D-R10). */
export default function ReferentielPanel({ service, descripteur, lectureSeule }: Props) {
  const confirm = useConfirm();
  const ref = descripteur.referentiel;
  const [onglet, setOnglet] = useState('valeurs');
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; valeurs: Valeur[]; message?: string }>({
    statut: 'chargement',
    valeurs: [],
  });
  const [bandeau, setBandeau] = useState<Bandeau | null>(null);
  const [formulaire, setFormulaire] = useState<{ valeur?: Valeur } | null>(null);
  const [brouillon, setBrouillon] = useState<string[] | null>(null);
  const [survol, setSurvol] = useState<string | null>(null);
  const [glisse, setGlisse] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setEtat((e) => ({ ...e, statut: 'chargement' }));
    try {
      const valeurs = await getValeurs(service, ref);
      setEtat({ statut: 'ok', valeurs });
    } catch (e) {
      setEtat({ statut: 'erreur', valeurs: [], message: messageParametre(e) });
    }
  }, [service, ref]);

  // Changement de référentiel : état propre.
  useEffect(() => {
    setOnglet('valeurs');
    setBandeau(null);
    setFormulaire(null);
    setBrouillon(null);
    void charger();
  }, [charger]);

  const triees = useMemo(
    () => [...etat.valeurs].sort((a, b) => a.ordre - b.ordre || a.libelle.localeCompare(b.libelle, 'fr')),
    [etat.valeurs],
  );
  const lignes = useMemo(() => {
    if (!brouillon) return triees;
    const parCode = new Map(triees.map((v) => [v.code, v]));
    return brouillon.map((c) => parCode.get(c)).filter((v): v is Valeur => !!v);
  }, [triees, brouillon]);

  const peutEcrire = !lectureSeule;

  async function apresEcriture(message?: string) {
    await Promise.all([charger(), rafraichirParametres()]);
    setBandeau(message ? { genre: 'succes', message } : null);
  }

  function traiterEchec(e: unknown, valeur?: Valeur) {
    if (e instanceof ParametreApiError && e.code === 'PARAMETRE_UTILISE' && valeur) {
      setBandeau({ genre: 'utilise', message: e.message, utilisations: e.utilisations, valeur });
    } else if (e instanceof ParametreApiError && e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') {
      setBandeau({ genre: 'modifie', message: e.message });
    } else {
      setBandeau({ genre: 'erreur', message: messageParametre(e) });
    }
  }

  async function agir(action: Action, v: Valeur) {
    setBandeau(null);
    const cible = `la valeur « ${v.libelle} » du référentiel « ${descripteur.libelle} »`;
    const textes: Record<Action, { verbe: string; detail: string; danger: boolean }> = {
      desactiver: {
        verbe: 'Désactiver',
        detail: 'Elle ne sera plus proposée à la saisie ; les éléments existants la conservent et l’affichent normalement.',
        danger: false,
      },
      reactiver: { verbe: 'Réactiver', detail: 'Elle sera de nouveau proposée à la saisie.', danger: false },
      supprimer: {
        verbe: 'Supprimer',
        detail: 'Suppression définitive. Elle est refusée si la valeur a déjà été utilisée : il faut alors la désactiver.',
        danger: true,
      },
    };
    const t = textes[action];
    const etat: { echec?: { e: unknown } } = {};
    const ok = await confirm({
      title: 'Êtes-vous sûr ?',
      message: (
        <>
          <p>{t.verbe} {cible} ?</p>
          <p>{t.detail}</p>
        </>
      ),
      confirmLabel: t.verbe,
      danger: t.danger,
      envoyer: async () => {
        try {
          if (action === 'desactiver') await desactiverValeur(service, ref, v.code, v.version);
          else if (action === 'reactiver') await reactiverValeur(service, ref, v.code, v.version);
          else await supprimerValeur(service, ref, v.code, v.version);
        } catch (e) {
          etat.echec = { e };
          throw e;
        }
      },
      echecDefinitif: echecMetier,
    });
    if (ok) {
      const passe = { desactiver: 'désactivée', reactiver: 'réactivée', supprimer: 'supprimée' }[action];
      await apresEcriture(`Valeur « ${v.libelle} » ${passe}.`);
    } else if (etat.echec) {
      traiterEchec(etat.echec.e, v);
    }
  }

  /* ─── Ordre (glisser-déposer + boutons clavier) ───────────────────────── */

  function deplacer(code: string, vers: number) {
    const base = brouillon ?? triees.map((v) => v.code);
    const depart = base.indexOf(code);
    if (depart < 0 || vers < 0 || vers >= base.length || vers === depart) return;
    const suite = [...base];
    suite.splice(depart, 1);
    suite.splice(vers, 0, code);
    setBrouillon(suite);
  }

  function surDepot(e: DragEvent, cible: string) {
    e.preventDefault();
    const code = glisse ?? e.dataTransfer.getData('text/plain');
    setSurvol(null);
    setGlisse(null);
    if (!code || code === cible) return;
    const base = brouillon ?? triees.map((v) => v.code);
    deplacer(code, base.indexOf(cible));
  }

  async function enregistrerOrdre() {
    if (!brouillon) return;
    setBandeau(null);
    const etat: { echec?: { e: unknown } } = {};
    const ok = await confirm({
      title: 'Êtes-vous sûr ?',
      message: <p>Enregistrer le nouvel ordre des {brouillon.length} valeurs du référentiel « {descripteur.libelle} » ? Il s’applique à tous les écrans et formulaires.</p>,
      confirmLabel: 'Enregistrer l’ordre',
      envoyer: async () => {
        try {
          await ordonnerValeurs(service, ref, brouillon);
        } catch (e) {
          etat.echec = { e };
          throw e;
        }
      },
      echecDefinitif: echecMetier,
    });
    if (ok) {
      setBrouillon(null);
      await apresEcriture('Nouvel ordre enregistré.');
    } else if (etat.echec) {
      traiterEchec(etat.echec.e);
    }
  }

  const libelleDe = useCallback((code: string) => etat.valeurs.find((v) => v.code === code)?.libelle, [etat.valeurs]);

  const peutCreer = peutEcrire && descripteur.operations.creer;
  const comportement = descripteur.nature === 'COMPORTEMENT';
  const listeFixe = comportement || (!descripteur.operations.creer && !descripteur.operations.supprimer);

  const contenuValeurs = (() => {
    if (etat.statut === 'chargement' && etat.valeurs.length === 0) return <LoadingState label="Chargement des valeurs…" />;
    if (etat.statut === 'erreur') {
      return (
        <div className="alert alert--error" role="alert">
          {etat.message ?? 'Impossible de charger les valeurs.'}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
        </div>
      );
    }
    if (lignes.length === 0) {
      return (
        <EmptyState icon="inbox" title="Aucune valeur" action={peutCreer ? (
          <button type="button" className="btn btn--primary" onClick={() => setFormulaire({})}>Ajouter une valeur</button>
        ) : undefined}>
          Ce référentiel ne contient encore aucune valeur.
        </EmptyState>
      );
    }
    return (
      <div className="table-wrap">
        <table className="data-table param-table">
          <caption className="sr-only">Valeurs du référentiel {descripteur.libelle}</caption>
          <thead>
            <tr>
              {peutEcrire ? <th scope="col" className="param-poignee"><span className="sr-only">Ordre</span></th> : null}
              <th scope="col">Libellé</th>
              <th scope="col">Description</th>
              <th scope="col">Couleur</th>
              <th scope="col">Ordre</th>
              <th scope="col">Actif</th>
              <th scope="col">Utilisations</th>
              {descripteur.attributs.map((a) => <th key={a.nom} scope="col">{libelleAttribut(a.nom)}</th>)}
              {peutEcrire ? <th scope="col"><span className="sr-only">Actions</span></th> : null}
            </tr>
          </thead>
          <tbody>
            {lignes.map((v, i) => (
              <tr
                key={v.code}
                draggable={peutEcrire}
                className={`${v.actif ? '' : 'param-ligne--inactive'}${survol === v.code ? ' param-ligne--depose' : ''}`}
                onDragStart={(e) => {
                  setGlisse(v.code);
                  e.dataTransfer.setData('text/plain', v.code);
                  e.dataTransfer.effectAllowed = 'move';
                }}
                onDragOver={(e) => { if (peutEcrire) { e.preventDefault(); setSurvol(v.code); } }}
                onDragLeave={() => setSurvol((s) => (s === v.code ? null : s))}
                onDrop={(e) => surDepot(e, v.code)}
                onDragEnd={() => { setGlisse(null); setSurvol(null); }}
              >
                {peutEcrire ? (
                  <td className="param-poignee">
                    <span className="param-deplacer">
                      <button type="button" className="btn btn--ghost btn--sm" aria-label={`Monter « ${v.libelle} »`} disabled={i === 0}
                        onClick={() => deplacer(v.code, i - 1)}>▲</button>
                      <button type="button" className="btn btn--ghost btn--sm" aria-label={`Descendre « ${v.libelle} »`} disabled={i === lignes.length - 1}
                        onClick={() => deplacer(v.code, i + 1)}>▼</button>
                    </span>
                  </td>
                ) : null}
                <td>
                  <strong>{v.libelle}</strong>
                  {v.systeme ? <Icon name="lock" size={13} label="Valeur système : ni désactivable ni supprimable" style={{ marginLeft: 6 }} /> : null}
                  <span className="param-ref">Référence technique : {v.code}</span>
                </td>
                <td>{v.description || '—'}</td>
                <td><PastilleCouleur couleur={v.couleur}>{nomCouleur(v.couleur)}</PastilleCouleur></td>
                <td>{brouillon ? i + 1 : v.ordre}</td>
                <td><span className={`status status--${v.actif ? 'success' : 'neutral'}`}>{v.actif ? 'Actif' : 'Inactif'}</span></td>
                <td>
                  {v.utilisations}
                  {v.deja_utilisee ? <span className="param-ref">Déjà utilisée</span> : null}
                </td>
                {descripteur.attributs.map((a) => <td key={a.nom}>{cellAttribut(v, a.nom, a.type)}</td>)}
                {peutEcrire ? (
                  <td className="param-actions">
                    <button type="button" className="btn btn--ghost btn--sm" aria-label={`Modifier « ${v.libelle} »`}
                      onClick={() => { void rafraichirParametres(); setFormulaire({ valeur: v }); }}>Modifier</button>
                    {descripteur.operations.desactiver && !v.systeme ? (
                      v.actif ? (
                        <button type="button" className="btn btn--ghost btn--sm" aria-label={`Désactiver « ${v.libelle} »`}
                          onClick={() => void agir('desactiver', v)}>Désactiver</button>
                      ) : (
                        <button type="button" className="btn btn--ghost btn--sm" aria-label={`Réactiver « ${v.libelle} »`}
                          onClick={() => void agir('reactiver', v)}>Réactiver</button>
                      )
                    ) : null}
                    {descripteur.operations.supprimer && !v.systeme ? (
                      <button type="button" className="btn btn--ghost btn--sm" aria-label={`Supprimer « ${v.libelle} »`}
                        onClick={() => void agir('supprimer', v)}>Supprimer</button>
                    ) : null}
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  })();

  return (
    <section aria-labelledby="param-titre-ref" className="panel">
      <div className="param-panel__tete">
        <div>
          <h3 id="param-titre-ref">{descripteur.libelle}</h3>
          <p className="muted small" style={{ margin: '4px 0 0' }}>
            {comportement
              ? 'Codes pilotés par une règle du serveur : seuls le libellé, la description, la couleur et l’ordre se règlent.'
              : 'Valeurs métier : elles se créent, se modifient et se désactivent librement.'}
          </p>
        </div>
        {peutCreer ? (
          <button type="button" className="btn btn--primary" onClick={() => { void rafraichirParametres(); setFormulaire({}); }}>
            Ajouter une valeur
          </button>
        ) : null}
      </div>

      {listeFixe ? (
        <div className="callout callout--info" role="note">
          <strong>Liste fixe</strong> : ces valeurs pilotent le fonctionnement de l’application (droits, circuits de validation).
          Vous pouvez modifier leur libellé, leur aide, leur couleur et leur ordre, mais pas en ajouter ni en supprimer.
        </div>
      ) : null}

      {bandeau ? (
        <div className={`alert alert--${bandeau.genre === 'succes' ? 'success' : bandeau.genre === 'erreur' ? 'error' : 'warning'}`}
          role={bandeau.genre === 'succes' ? 'status' : 'alert'}>
          {bandeau.genre === 'utilise' ? (
            <>
              {bandeau.message}
              {bandeau.utilisations !== null ? ` (${bandeau.utilisations} utilisation${bandeau.utilisations > 1 ? 's' : ''})` : ''}{' '}
              {peutEcrire && bandeau.valeur.actif && !bandeau.valeur.systeme ? (
                <button type="button" className="btn btn--secondary btn--sm" onClick={() => void agir('desactiver', bandeau.valeur)}>Désactiver</button>
              ) : null}
            </>
          ) : bandeau.genre === 'modifie' ? (
            <>
              {bandeau.message}{' '}
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void apresEcriture()}>Recharger</button>
            </>
          ) : bandeau.message}
        </div>
      ) : null}

      <Onglets
        onglets={[{ id: 'valeurs', label: 'Valeurs' }, { id: 'historique', label: 'Historique des modifications' }]}
        actif={onglet}
        onChange={setOnglet}
      >
        {onglet === 'historique' ? (
          <JournalPanel service={service} referentiel={ref} libelleDe={libelleDe} />
        ) : (
          <>
            {brouillon ? (
              <div className="alert alert--info param-bandeau-ordre" role="status">
                <span>Nouvel ordre non enregistré.</span>
                <button type="button" className="btn btn--primary btn--sm" onClick={() => void enregistrerOrdre()}>Enregistrer l’ordre</button>
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setBrouillon(null)}>Annuler</button>
              </div>
            ) : null}
            {contenuValeurs}
          </>
        )}
      </Onglets>

      {formulaire ? (
        <ValeurFormulaire
          service={service}
          descripteur={descripteur}
          valeur={formulaire.valeur}
          onClose={() => setFormulaire(null)}
          onSaved={(v) => {
            setFormulaire(null);
            void apresEcriture(`Valeur « ${v.libelle} » enregistrée.`);
          }}
          onConflit={() => { setFormulaire(null); void apresEcriture(); }}
        />
      ) : null}
    </section>
  );
}

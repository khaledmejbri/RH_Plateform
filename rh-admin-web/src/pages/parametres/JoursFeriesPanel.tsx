import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  creerJourFerie,
  desactiverJourFerie,
  getJournalJoursFeries,
  getJoursFeries,
  getParametresConges,
  messageParametre,
  modifierJourFerie,
  ParametreApiError,
  reactiverJourFerie,
  supprimerJourFerie,
  type JourFerie,
  type ParametresConges,
} from '../../api/parametresApi';
import { SaisieDate } from '../../components/ChampsFr';
import { useConfirm } from '../../components/ConfirmDialog';
import { EmptyState, LoadingState } from '../../components/ui';
import Onglets from '../../detail/Onglets';
import { formatDate } from '../../utils/format';
import JournalPanel from './JournalPanel';

type Props = { lectureSeule: boolean };

type Bandeau = { genre: 'succes' | 'erreur' | 'modifie'; message: string };

/** Message d'une erreur de jour férié : le doublon a un texte fixe, le reste vient du serveur. */
function messageJourFerie(e: unknown): string {
  if (e instanceof ParametreApiError && e.code === 'JOUR_FERIE_EN_DOUBLE') return 'Ce jour férié existe déjà';
  if (e instanceof ParametreApiError && e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') {
    return 'Ce paramètre a été modifié entre-temps. La liste a été rechargée, vérifiez puis recommencez.';
  }
  if (e instanceof ParametreApiError && e.code === 'DATE_INVALIDE') {
    return e.message || 'Date hors de la plage autorisée (de l’année précédente à dans cinq ans).';
  }
  return messageParametre(e);
}

/** Jours fériés (D-S02) : tableau par année, ajout, modification, désactivation, suppression, historique. */
export default function JoursFeriesPanel({ lectureSeule }: Props) {
  const confirm = useConfirm();
  const anneeCourante = new Date().getFullYear();
  const [annee, setAnnee] = useState(anneeCourante);
  const [onglet, setOnglet] = useState('liste');
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; liste: JourFerie[]; message?: string }>({
    statut: 'chargement',
    liste: [],
  });
  const [bandeau, setBandeau] = useState<Bandeau | null>(null);
  const [formulaire, setFormulaire] = useState<{ jour?: JourFerie } | null>(null);
  const [conges, setConges] = useState<ParametresConges | null>(null);

  const charger = useCallback(async () => {
    setEtat((e) => ({ ...e, statut: 'chargement' }));
    try {
      setEtat({ statut: 'ok', liste: await getJoursFeries(annee) });
    } catch (e) {
      setEtat({ statut: 'erreur', liste: [], message: messageParametre(e) });
    }
  }, [annee]);

  useEffect(() => {
    void charger();
  }, [charger]);

  useEffect(() => {
    void getParametresConges().then(setConges).catch(() => setConges(null));
  }, []);

  async function agir(action: 'desactiver' | 'reactiver' | 'supprimer', j: JourFerie) {
    setBandeau(null);
    const cible = `le jour férié « ${j.libelle} » du ${formatDate(j.date)}`;
    let echec: unknown = null;
    const ok = await confirm({
      title: action === 'supprimer' ? 'Supprimer ce jour férié ?' : action === 'desactiver' ? 'Désactiver ce jour férié ?' : 'Réactiver ce jour férié ?',
      message: action === 'supprimer'
        ? `Supprimer ${cible} ? Les demandes déjà approuvées ne changent pas.`
        : action === 'desactiver'
          ? `Désactiver ${cible} ? Il sera compté comme un jour ouvré dans les nouvelles simulations.`
          : `Réactiver ${cible} ?`,
      confirmLabel: action === 'supprimer' ? 'Supprimer' : action === 'desactiver' ? 'Désactiver' : 'Réactiver',
      danger: action === 'supprimer',
      envoyer: async () => {
        try {
          if (action === 'supprimer') await supprimerJourFerie(j.id, j.version);
          else if (action === 'desactiver') await desactiverJourFerie(j.id, j.version);
          else await reactiverJourFerie(j.id, j.version);
        } catch (e) {
          echec = e;
          throw e;
        }
      },
      echecDefinitif: () => echec instanceof ParametreApiError && echec.status >= 400 && echec.status < 500,
    });
    if (echec) {
      setBandeau({
        genre: echec instanceof ParametreApiError && echec.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS' ? 'modifie' : 'erreur',
        message: messageJourFerie(echec),
      });
      await charger();
      return;
    }
    if (ok) {
      setBandeau({ genre: 'succes', message: action === 'supprimer' ? 'Jour férié supprimé.' : action === 'desactiver' ? 'Jour férié désactivé.' : 'Jour férié réactivé.' });
      await charger();
    }
  }

  const annees = Array.from({ length: 7 }, (_, i) => anneeCourante - 1 + i);

  return (
    <section aria-labelledby="param-titre-feries" className="panel">
      <div className="param-panel__tete">
        <h3 id="param-titre-feries">Jours fériés</h3>
        {!lectureSeule ? (
          <button type="button" className="btn btn--primary btn--sm" onClick={() => { setBandeau(null); setFormulaire({}); }}>
            Ajouter un jour férié
          </button>
        ) : null}
      </div>
      <p className="field-help">
        Les jours fériés actifs ne sont pas décomptés d’un congé. Une modification n’a pas d’effet sur les demandes déjà approuvées.
        {conges?.debut_acquisitions_le ? ` Les acquisitions mensuelles ont commencé le ${formatDate(conges.debut_acquisitions_le)}.` : ''}
      </p>
      <Onglets
        onglets={[{ id: 'liste', label: 'Liste' }, { id: 'historique', label: 'Historique des modifications' }]}
        actif={onglet}
        onChange={setOnglet}
      >
        {onglet === 'historique' ? (
          <JournalPanel service="referentiel" source={getJournalJoursFeries} />
        ) : (
          <>
            <div style={{ maxWidth: 220, marginBottom: 12 }}>
              <label className="field-label" htmlFor="feries-annee">Année</label>
              <select id="feries-annee" className="field-input" value={annee} onChange={(e) => setAnnee(Number(e.target.value))}>
                {annees.map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>

            {bandeau ? (
              <div className={`alert ${bandeau.genre === 'succes' ? 'alert--success' : bandeau.genre === 'modifie' ? 'alert--warning' : 'alert--error'}`} role={bandeau.genre === 'succes' ? 'status' : 'alert'}>
                {bandeau.message}
              </div>
            ) : null}

            {formulaire ? (
              <FormulaireJourFerie
                jour={formulaire.jour}
                onAnnule={() => setFormulaire(null)}
                onEnregistre={async (message) => {
                  setFormulaire(null);
                  setBandeau({ genre: 'succes', message });
                  await charger();
                }}
                onConflit={async (message) => {
                  setFormulaire(null);
                  setBandeau({ genre: 'modifie', message });
                  await charger();
                }}
              />
            ) : null}

            {etat.statut === 'chargement' ? <LoadingState label="Chargement des jours fériés…" /> : null}
            {etat.statut === 'erreur' ? (
              <div className="alert alert--error" role="alert">
                {etat.message}{' '}
                <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
              </div>
            ) : null}
            {etat.statut === 'ok' && etat.liste.length === 0 ? (
              <EmptyState icon="calendar" title={`Aucun jour férié en ${annee}`}>
                {lectureSeule ? 'Aucun jour férié n’est enregistré pour cette année.' : 'Ajoutez les jours fériés de l’année pour qu’ils ne soient pas décomptés des congés.'}
              </EmptyState>
            ) : null}
            {etat.statut === 'ok' && etat.liste.length > 0 ? (
              <div className="table-wrap">
                <table className="data-table">
                  <caption className="sr-only">Jours fériés de {annee}</caption>
                  <thead>
                    <tr>
                      <th scope="col">Date</th>
                      <th scope="col">Libellé</th>
                      <th scope="col">Actif</th>
                      {!lectureSeule ? <th scope="col"><span className="sr-only">Actions</span></th> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {etat.liste.map((j) => (
                      <tr key={j.id}>
                        <td>{formatDate(j.date)}</td>
                        <td>{j.libelle}</td>
                        <td>{j.actif ? 'Oui' : 'Non'}</td>
                        {!lectureSeule ? (
                          <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                            <button type="button" className="btn btn--ghost btn--sm" aria-label={`Modifier ${j.libelle}`} onClick={() => { setBandeau(null); setFormulaire({ jour: j }); }}>Modifier</button>
                            {j.actif ? (
                              <button type="button" className="btn btn--ghost btn--sm" aria-label={`Désactiver ${j.libelle}`} onClick={() => void agir('desactiver', j)}>Désactiver</button>
                            ) : (
                              <button type="button" className="btn btn--ghost btn--sm" aria-label={`Réactiver ${j.libelle}`} onClick={() => void agir('reactiver', j)}>Réactiver</button>
                            )}
                            <button type="button" className="btn btn--ghost btn--sm" aria-label={`Supprimer ${j.libelle}`} onClick={() => void agir('supprimer', j)}>Supprimer</button>
                          </td>
                        ) : null}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </>
        )}
      </Onglets>
    </section>
  );
}

function FormulaireJourFerie({
  jour, onAnnule, onEnregistre, onConflit,
}: {
  jour?: JourFerie;
  onAnnule: () => void;
  onEnregistre: (message: string) => Promise<void>;
  onConflit: (message: string) => Promise<void>;
}) {
  const [date, setDate] = useState(jour?.date ?? '');
  const [libelle, setLibelle] = useState(jour?.libelle ?? '');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const longueur = libelle.trim().length;
  const valide = date !== '' && longueur >= 2 && longueur <= 80;

  async function soumettre(ev: FormEvent) {
    ev.preventDefault();
    if (envoi || !valide) return;
    setEnvoi(true);
    setErreur(null);
    try {
      if (jour) await modifierJourFerie(jour.id, { date, libelle: libelle.trim(), version: jour.version });
      else await creerJourFerie(date, libelle.trim());
      await onEnregistre(jour ? 'Jour férié modifié.' : 'Jour férié ajouté.');
    } catch (e) {
      if (e instanceof ParametreApiError && e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') {
        await onConflit(messageJourFerie(e));
        return;
      }
      setErreur(messageJourFerie(e));
      setEnvoi(false);
    }
  }

  return (
    <form className="panel panel--soft" onSubmit={(e) => void soumettre(e)} aria-label={jour ? 'Modifier un jour férié' : 'Ajouter un jour férié'}>
      <h4 style={{ marginTop: 0 }}>{jour ? 'Modifier le jour férié' : 'Ajouter un jour férié'}</h4>
      <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
        <div>
          <label className="field-label" htmlFor="ferie-date">Date</label>
          <SaisieDate id="ferie-date" valeur={date} onChange={setDate} required />
        </div>
        <div>
          <label className="field-label" htmlFor="ferie-libelle">Libellé</label>
          <input id="ferie-libelle" className="field-input" value={libelle} maxLength={80} autoComplete="off"
            onChange={(e) => setLibelle(e.target.value)} aria-describedby="ferie-libelle-aide" />
          <p id="ferie-libelle-aide" className="muted small">{longueur} / 80 caractères (2 au minimum)</p>
        </div>
      </div>
      {erreur ? <p className="field-error" role="alert">{erreur}</p> : null}
      <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
        <button type="submit" className="btn btn--primary btn--sm" disabled={!valide || envoi}>{envoi ? 'Envoi…' : 'Enregistrer'}</button>
        <button type="button" className="btn btn--ghost btn--sm" disabled={envoi} onClick={onAnnule}>Annuler</button>
      </div>
    </form>
  );
}

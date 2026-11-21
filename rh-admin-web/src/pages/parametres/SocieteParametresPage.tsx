import { useCallback, useEffect, useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  getSocieteJournal,
  getSocieteParametres,
  messageErreurSociete,
  putSocieteParametres,
  SocieteApiError,
  type BaseDecompteConge,
  type PageSpring,
  type SocieteJournalItem,
  type SocieteParametres,
} from '../../api/societeApi';
import { SaisieHeure } from '../../components/ChampsFr';
import { ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import {
  controlerRepartition,
  heuresVersMinutes,
  JOURS_SEMAINE,
  minutesVersHeuresTexte,
} from '../../workflow/societeUtils';

type ContexteRoute = { lectureSeule?: boolean };

const BASES_DECOMPTE: ReadonlyArray<{ code: BaseDecompteConge; libelle: string }> = [
  { code: 'JOURS_TRAVAILLES', libelle: 'Jours travaillés de la société' },
  { code: 'JOURS_OUVRES_LUN_VEN', libelle: 'Jours ouvrés (lundi au vendredi)' },
  { code: 'JOURS_OUVRABLES_LUN_SAM', libelle: 'Jours ouvrables (lundi au samedi)' },
];

type SaisieSociete = {
  jours_travailles: string[];
  duree_hebdo_texte: string;
  duree_jour_textes: Record<string, string>;
  heure_debut_journee: string;
  heure_fin_journee: string;
  base_decompte_conge: BaseDecompteConge;
  jours_acquis_texte: string;
  duree_max_sortie_minutes: string;
  delegation_auto_conges: boolean;
  publication_quatre_yeux: boolean;
};

function depuisServeur(p: SocieteParametres): SaisieSociete {
  const duree_jour_textes: Record<string, string> = {};
  for (const j of JOURS_SEMAINE) {
    duree_jour_textes[j.code] = minutesVersHeuresTexte(p.duree_jour_minutes?.[j.code] ?? 0);
  }
  return {
    jours_travailles: [...(p.jours_travailles ?? [])],
    duree_hebdo_texte: minutesVersHeuresTexte(p.duree_hebdo_minutes),
    duree_jour_textes,
    heure_debut_journee: (p.heure_debut_journee ?? '08:30').slice(0, 5),
    heure_fin_journee: (p.heure_fin_journee ?? '17:00').slice(0, 5),
    base_decompte_conge: p.base_decompte_conge ?? 'JOURS_TRAVAILLES',
    jours_acquis_texte: String(p.jours_acquis_par_mois ?? 1.5).replace('.', ','),
    duree_max_sortie_minutes: String(p.duree_max_sortie_minutes ?? 120),
    delegation_auto_conges: Boolean(p.delegation_auto_conges),
    publication_quatre_yeux: Boolean(p.publication_quatre_yeux),
  };
}

export default function SocieteParametresPage() {
  const { lectureSeule } = useOutletContext<ContexteRoute>() ?? {};
  const [serveur, setServeur] = useState<SocieteParametres | null>(null);
  const [saisie, setSaisie] = useState<SaisieSociete | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreurChargement, setErreurChargement] = useState<string | null>(null);
  const [enregistrement, setEnregistrement] = useState(false);
  const [messageSucces, setMessageSucces] = useState<string | null>(null);
  const [erreurForm, setErreurForm] = useState<string | null>(null);
  const [saisieConflit, setSaisieConflit] = useState<SaisieSociete | null>(null);

  const [onglet, setOnglet] = useState<'parametres' | 'journal'>('parametres');
  const [journal, setJournal] = useState<PageSpring<SocieteJournalItem> | null>(null);
  const [journalPage, setJournalPage] = useState(0);
  const [journalChargement, setJournalChargement] = useState(false);

  const charger = useCallback(async () => {
    setChargement(true);
    setErreurChargement(null);
    try {
      const data = await getSocieteParametres();
      setServeur(data);
      setSaisie(depuisServeur(data));
      setSaisieConflit(null);
    } catch (e) {
      setErreurChargement(messageErreurSociete(e));
    } finally {
      setChargement(false);
    }
  }, []);

  const chargerJournal = useCallback(async (page: number) => {
    setJournalChargement(true);
    try {
      const data = await getSocieteJournal(page, 20);
      setJournal(data);
      setJournalPage(page);
    } catch {
      setJournal(null);
    } finally {
      setJournalChargement(false);
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  useEffect(() => {
    if (onglet === 'journal') void chargerJournal(0);
  }, [onglet, chargerJournal]);

  const repartition = useMemo(() => {
    if (!saisie) return null;
    const hebdo = heuresVersMinutes(saisie.duree_hebdo_texte);
    if (hebdo === null || hebdo <= 0) {
      return { valide: false, sommeMinutes: 0, ecartMinutes: 0, message: 'Durée hebdomadaire invalide.' };
    }
    const parJour: Record<string, number> = {};
    for (const j of saisie.jours_travailles) {
      parJour[j] = heuresVersMinutes(saisie.duree_jour_textes[j] ?? '') ?? 0;
    }
    return controlerRepartition(saisie.jours_travailles, hebdo, parJour);
  }, [saisie]);

  function basculerJour(code: string) {
    if (!saisie || lectureSeule) return;
    const present = saisie.jours_travailles.includes(code);
    const suivants = present
      ? saisie.jours_travailles.filter((j) => j !== code)
      : JOURS_SEMAINE.map((j) => j.code).filter((c) => c === code || saisie.jours_travailles.includes(c));
    const textes = { ...saisie.duree_jour_textes };
    if (!present && (!textes[code] || heuresVersMinutes(textes[code]) === 0)) {
      textes[code] = '8';
    }
    setSaisie({ ...saisie, jours_travailles: suivants, duree_jour_textes: textes });
  }

  async function enregistrer(e: React.FormEvent) {
    e.preventDefault();
    if (!saisie || !serveur || lectureSeule) return;
    setErreurForm(null);
    setMessageSucces(null);

    const hebdo = heuresVersMinutes(saisie.duree_hebdo_texte);
    if (hebdo === null || hebdo < 60 || hebdo > 4320) {
      setErreurForm('La durée hebdomadaire doit être comprise entre 1 h et 72 h.');
      return;
    }
    const dureeJourMinutes: Record<string, number> = {};
    for (const j of saisie.jours_travailles) {
      const m = heuresVersMinutes(saisie.duree_jour_textes[j] ?? '');
      if (m === null || m <= 0) {
        setErreurForm('Renseignez une durée valide pour chaque jour travaillé.');
        return;
      }
      dureeJourMinutes[j] = m;
    }
    const ctrl = controlerRepartition(saisie.jours_travailles, hebdo, dureeJourMinutes);
    if (!ctrl.valide) {
      setErreurForm(ctrl.message);
      return;
    }
    const acquis = Number(saisie.jours_acquis_texte.trim().replace(',', '.'));
    if (!Number.isFinite(acquis) || acquis < 0 || acquis > 5) {
      setErreurForm('Les jours acquis par mois doivent être compris entre 0 et 5.');
      return;
    }
    const sortieMin = Number(saisie.duree_max_sortie_minutes);
    if (!Number.isInteger(sortieMin) || sortieMin < 15 || sortieMin > 480) {
      setErreurForm('La durée maximale d’une autorisation de sortie doit être comprise entre 15 et 480 minutes.');
      return;
    }
    if (!saisie.heure_debut_journee || !saisie.heure_fin_journee || saisie.heure_fin_journee <= saisie.heure_debut_journee) {
      setErreurForm('L’heure de fin de journée doit être postérieure à l’heure de début.');
      return;
    }

    setEnregistrement(true);
    try {
      const maj = await putSocieteParametres({
        jours_travailles: saisie.jours_travailles,
        duree_hebdo_minutes: hebdo,
        duree_jour_minutes: dureeJourMinutes,
        heure_debut_journee: saisie.heure_debut_journee,
        heure_fin_journee: saisie.heure_fin_journee,
        base_decompte_conge: saisie.base_decompte_conge,
        jours_acquis_par_mois: acquis,
        duree_max_sortie_minutes: sortieMin,
        delegation_auto_conges: saisie.delegation_auto_conges,
        publication_quatre_yeux: saisie.publication_quatre_yeux,
        version: serveur.version,
      });
      setServeur(maj);
      setSaisie(depuisServeur(maj));
      setSaisieConflit(null);
      setMessageSucces('Paramètres de la société enregistrés.');
    } catch (err) {
      if (err instanceof SocieteApiError && err.status === 409 && err.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') {
        const sauvegarde = { ...saisie, duree_jour_textes: { ...saisie.duree_jour_textes } };
        setSaisieConflit(sauvegarde);
        setErreurForm(
          'Ces paramètres ont été modifiés par un autre utilisateur. La version serveur a été rechargée ; vous pouvez réappliquer votre saisie.',
        );
        try {
          const frais = await getSocieteParametres();
          setServeur(frais);
          setSaisie(depuisServeur(frais));
        } catch {
          // garde l'erreur principale
        }
      } else {
        setErreurForm(messageErreurSociete(err));
      }
    } finally {
      setEnregistrement(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="Paramètres de la société"
        lead="Jours travaillés, horaires, règles de congé et gouvernance des workflows."
      />

      <div className="tabs" role="tablist" aria-label="Sections société" style={{ marginBottom: 16 }}>
        <button
          type="button"
          role="tab"
          aria-selected={onglet === 'parametres'}
          className={`tab${onglet === 'parametres' ? ' tab--active' : ''}`}
          onClick={() => setOnglet('parametres')}
        >
          Paramètres généraux
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={onglet === 'journal'}
          className={`tab${onglet === 'journal' ? ' tab--active' : ''}`}
          onClick={() => setOnglet('journal')}
        >
          Journal des modifications
        </button>
      </div>

      {chargement ? <LoadingState /> : null}
      {erreurChargement ? (
        <ErrorAlert>
          {erreurChargement}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {!chargement && !erreurChargement && saisie && serveur && onglet === 'parametres' ? (
        <form className="card" onSubmit={(e) => void enregistrer(e)} noValidate>
          {messageSucces ? (
            <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>
              {messageSucces}
            </div>
          ) : null}
          {erreurForm ? (
            <div className="alert alert--error" role="alert" style={{ marginBottom: 12 }}>
              {erreurForm}
              {saisieConflit ? (
                <div style={{ marginTop: 8 }}>
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    onClick={() => {
                      setSaisie(saisieConflit);
                      setSaisieConflit(null);
                      setErreurForm(null);
                    }}
                  >
                    Réappliquer ma saisie
                  </button>
                </div>
              ) : null}
            </div>
          ) : null}

          <section style={{ marginBottom: 20 }}>
            <h3 style={{ marginBottom: 8 }}>Jours travaillés et répartition hebdomadaire</h3>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginBottom: 12 }}>
              {JOURS_SEMAINE.map((j) => {
                const coche = saisie.jours_travailles.includes(j.code);
                return (
                  <label key={j.code} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <input
                      type="checkbox"
                      checked={coche}
                      disabled={lectureSeule}
                      onChange={() => basculerJour(j.code)}
                    />
                    <span>{j.libelle}</span>
                  </label>
                );
              })}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12, marginBottom: 12 }}>
              <div>
                <label className="field-label" htmlFor="soc-hebdo">Durée hebdomadaire (heures)</label>
                <input
                  id="soc-hebdo"
                  className="field-input"
                  value={saisie.duree_hebdo_texte}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, duree_hebdo_texte: e.target.value })}
                />
              </div>
              <div>
                <label className="field-label" htmlFor="soc-debut">Début de journée</label>
                <SaisieHeure
                  id="soc-debut"
                  valeur={saisie.heure_debut_journee}
                  disabled={lectureSeule}
                  onChange={(v) => setSaisie({ ...saisie, heure_debut_journee: v })}
                />
              </div>
              <div>
                <label className="field-label" htmlFor="soc-fin">Fin de journée</label>
                <SaisieHeure
                  id="soc-fin"
                  valeur={saisie.heure_fin_journee}
                  disabled={lectureSeule}
                  onChange={(v) => setSaisie({ ...saisie, heure_fin_journee: v })}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 8 }}>
              {saisie.jours_travailles.map((code) => {
                const lib = JOURS_SEMAINE.find((j) => j.code === code)?.libelle ?? code;
                return (
                  <div key={code}>
                    <label className="field-label" htmlFor={`soc-jour-${code}`}>{lib} (h)</label>
                    <input
                      id={`soc-jour-${code}`}
                      className="field-input"
                      value={saisie.duree_jour_textes[code] ?? ''}
                      disabled={lectureSeule}
                      onChange={(e) =>
                        setSaisie({
                          ...saisie,
                          duree_jour_textes: { ...saisie.duree_jour_textes, [code]: e.target.value },
                        })
                      }
                    />
                  </div>
                );
              })}
            </div>
            {repartition ? (
              <p className={`small ${repartition.valide ? 'muted' : 'field-error'}`} style={{ marginTop: 8 }}>
                {repartition.valide
                  ? `Répartition équilibrée : ${minutesVersHeuresTexte(repartition.sommeMinutes)} h / semaine.`
                  : repartition.message}
              </p>
            ) : null}
          </section>

          <section style={{ marginBottom: 20 }}>
            <h3 style={{ marginBottom: 8 }}>Congés et autorisations de sortie</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <div>
                <label className="field-label" htmlFor="soc-base-conge">Base de décompte des congés</label>
                <select
                  id="soc-base-conge"
                  className="field-input"
                  value={saisie.base_decompte_conge}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, base_decompte_conge: e.target.value as BaseDecompteConge })}
                >
                  {BASES_DECOMPTE.map((b) => (
                    <option key={b.code} value={b.code}>{b.libelle}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="field-label" htmlFor="soc-acquis">Jours acquis par mois</label>
                <input
                  id="soc-acquis"
                  className="field-input"
                  value={saisie.jours_acquis_texte}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, jours_acquis_texte: e.target.value })}
                />
              </div>
              <div>
                <label className="field-label" htmlFor="soc-sortie">Durée maximale d’une sortie (minutes)</label>
                <input
                  id="soc-sortie"
                  type="number"
                  min={15}
                  max={480}
                  className="field-input"
                  value={saisie.duree_max_sortie_minutes}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, duree_max_sortie_minutes: e.target.value })}
                />
              </div>
            </div>
          </section>

          <section style={{ marginBottom: 20 }}>
            <h3 style={{ marginBottom: 8 }}>Gouvernance du moteur de workflow</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  checked={saisie.publication_quatre_yeux}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, publication_quatre_yeux: e.target.checked })}
                />
                <span>Exiger la règle des quatre yeux pour publier une version de workflow</span>
              </label>
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  checked={saisie.delegation_auto_conges}
                  disabled={lectureSeule}
                  onChange={(e) => setSaisie({ ...saisie, delegation_auto_conges: e.target.checked })}
                />
                <span>Délégation automatique des validations pendant les congés approuvés</span>
              </label>
            </div>
          </section>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <span className="small muted">
              {serveur.modifie_le
                ? `Dernière modification le ${formatDateHeure(serveur.modifie_le)}${serveur.modifie_par_nom ? ` par ${serveur.modifie_par_nom}` : ''}`
                : 'Paramètres initiaux'}
            </span>
            {!lectureSeule ? (
              <button
                type="submit"
                className="btn btn--primary"
                disabled={enregistrement || (repartition != null && !repartition.valide)}
                aria-busy={enregistrement}
              >
                {enregistrement ? 'Enregistrement…' : 'Enregistrer'}
              </button>
            ) : null}
          </div>
        </form>
      ) : null}

      {onglet === 'journal' ? (
        <div className="card">
          {journalChargement ? <LoadingState /> : null}
          {!journalChargement && (!journal || journal.content.length === 0) ? (
            <p className="muted">Aucune modification enregistrée dans le journal.</p>
          ) : null}
          {!journalChargement && journal && journal.content.length > 0 ? (
            <>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Auteur</th>
                      <th>Avant</th>
                      <th>Après</th>
                    </tr>
                  </thead>
                  <tbody>
                    {journal.content.map((item) => (
                      <tr key={item.id}>
                        <td>{formatDateHeure(item.horodatage)}</td>
                        <td>{item.auteur_nom ?? 'Système'}</td>
                        <td><code className="small">{item.avant_json ?? '—'}</code></td>
                        <td><code className="small">{item.apres_json ?? '—'}</code></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {journal.totalPages > 1 ? (
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}>
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={journalPage === 0}
                    onClick={() => void chargerJournal(journalPage - 1)}
                  >
                    Précédent
                  </button>
                  <span className="small muted">Page {journalPage + 1} / {journal.totalPages}</span>
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={journalPage + 1 >= journal.totalPages}
                    onClick={() => void chargerJournal(journalPage + 1)}
                  >
                    Suivant
                  </button>
                </div>
              ) : null}
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

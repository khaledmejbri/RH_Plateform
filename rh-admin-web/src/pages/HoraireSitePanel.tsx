import { useCallback, useEffect, useState } from 'react';
import {
  codeErreur,
  deleteHoraireSite,
  formatDateTunis,
  getHoraireHistorique,
  getHoraireSite,
  JOURS_SEMAINE,
  messageErreurCode,
  putHoraireSite,
  TOLERANCES_MINUTES,
  type HoraireHistoriqueEntree,
  type HoraireInstantane,
  type HoraireSite,
  type JourSemaine,
  type PagePresence,
} from '../api/presenceApi';
import { useConfirm } from '../components/ConfirmDialog';
import { SaisieHeure } from '../components/ChampsFr';

type LigneJour = { ferme: boolean; debut: string; fin: string };
type Brouillon = Record<JourSemaine, LigneJour>;

const DEBUT_DEFAUT = '08:00';
const FIN_DEFAUT = '17:00';
const TOLERANCE_DEFAUT = 60;
const HISTO_TAILLE = 5;

/** Fin avant le début (HH:mm, comparaison de texte) : l'horaire se termine le lendemain. */
function finLendemain(debut: string, fin: string): boolean {
  return Boolean(debut) && Boolean(fin) && fin < debut;
}

function brouillonDepuis(h: HoraireSite | null): Brouillon {
  const b = {} as Brouillon;
  for (const { value } of JOURS_SEMAINE) {
    const j = h?.jours.find((x) => x.jour === value);
    b[value] = j
      ? { ferme: false, debut: j.debut.slice(0, 5), fin: j.fin.slice(0, 5) }
      : { ferme: true, debut: DEBUT_DEFAUT, fin: FIN_DEFAUT };
  }
  return b;
}

function resume(i: HoraireInstantane | null): string {
  if (!i || i.jours.length === 0) return 'Aucun horaire';
  const jours = i.jours
    .map((j) => `${JOURS_SEMAINE.find((x) => x.value === j.jour)?.label.slice(0, 3) ?? j.jour} ${j.debut}–${j.fin}`)
    .join(', ');
  return `${jours} · tolérances ${i.tolerance_avant_minutes}/${i.tolerance_apres_minutes} min`;
}

type Erreurs = { tolerance?: string; grille?: string; jours: Partial<Record<JourSemaine, string>> };
const SANS_ERREUR: Erreurs = { jours: {} };

/**
 * Horaire hebdomadaire d'un site et tolérances (D-P11 — SCRUM-48). Lecture seule pour DIRECTION.
 * Le serveur reste juge (chevauchements, tolérances) ; seul « début = fin » est signalé tout de suite.
 */
export default function HoraireSitePanel({ siteId, lectureSeule }: { siteId: string; lectureSeule: boolean }) {
  const confirm = useConfirm();
  const [horaire, setHoraire] = useState<HoraireSite | null>(null);
  const [brouillon, setBrouillon] = useState<Brouillon>(() => brouillonDepuis(null));
  const [tolAvant, setTolAvant] = useState(TOLERANCE_DEFAUT);
  const [tolApres, setTolApres] = useState(TOLERANCE_DEFAUT);
  const [loading, setLoading] = useState(true);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [erreurs, setErreurs] = useState<Erreurs>(SANS_ERREUR);
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  const [histoOuvert, setHistoOuvert] = useState(false);
  const [histo, setHisto] = useState<PagePresence<HoraireHistoriqueEntree> | null>(null);
  const [histoPage, setHistoPage] = useState(0);
  const [histoLoading, setHistoLoading] = useState(false);
  const [histoErr, setHistoErr] = useState<string | null>(null);

  const appliquer = useCallback((h: HoraireSite) => {
    setHoraire(h);
    setBrouillon(brouillonDepuis(h));
    setTolAvant(h.avec_horaire ? h.tolerance_avant_minutes : TOLERANCE_DEFAUT);
    setTolApres(h.avec_horaire ? h.tolerance_apres_minutes : TOLERANCE_DEFAUT);
  }, []);

  const charger = useCallback(async () => {
    setLoading(true);
    setLoadErr(null);
    try {
      appliquer(await getHoraireSite(siteId));
    } catch (e) {
      setLoadErr(messageErreurCode(e, 'Impossible de charger l’horaire du site'));
    } finally {
      setLoading(false);
    }
  }, [siteId, appliquer]);

  useEffect(() => {
    setErreurs(SANS_ERREUR);
    setActionErr(null);
    setOk(null);
    setHistoOuvert(false);
    setHisto(null);
    setHistoPage(0);
    void charger();
  }, [charger]);

  const chargerHisto = useCallback(
    async (page: number) => {
      setHistoLoading(true);
      setHistoErr(null);
      try {
        setHisto(await getHoraireHistorique(siteId, page, HISTO_TAILLE));
        setHistoPage(page);
      } catch (e) {
        setHistoErr(messageErreurCode(e, 'Impossible de charger l’historique'));
      } finally {
        setHistoLoading(false);
      }
    },
    [siteId],
  );

  function majJour(jour: JourSemaine, patch: Partial<LigneJour>) {
    setBrouillon((b) => ({ ...b, [jour]: { ...b[jour], ...patch } }));
    setErreurs((e) => ({ ...e, jours: { ...e.jours, [jour]: undefined }, grille: undefined }));
    setOk(null);
  }

  async function enregistrer() {
    setActionErr(null);
    setOk(null);
    const jours = JOURS_SEMAINE.filter(({ value }) => !brouillon[value].ferme).map(({ value }) => ({
      jour: value,
      debut: brouillon[value].debut,
      fin: brouillon[value].fin,
    }));
    const err: Erreurs = { jours: {} };
    for (const j of jours) {
      if (!j.debut || !j.fin) err.jours[j.jour] = 'Renseignez le début et la fin.';
      else if (j.debut === j.fin) err.jours[j.jour] = 'Le début et la fin ne peuvent pas être identiques.';
    }
    if (jours.length === 0) {
      err.grille = 'Ouvrez au moins un jour, ou utilisez « Retirer l’horaire » pour supprimer l’horaire du site.';
    }
    if (err.grille || Object.keys(err.jours).length > 0) {
      setErreurs(err);
      return;
    }
    setErreurs(SANS_ERREUR);
    setBusy(true);
    try {
      appliquer(
        await putHoraireSite(siteId, {
          tolerance_avant_minutes: tolAvant,
          tolerance_apres_minutes: tolApres,
          jours,
        }),
      );
      setOk('Horaire enregistré. Il s’applique aux pointages suivants.');
      if (histoOuvert) void chargerHisto(0);
    } catch (e) {
      const code = codeErreur(e);
      const message = messageErreurCode(e);
      if (code === 'TOLERANCE_INVALIDE') setErreurs({ jours: {}, tolerance: message });
      else if (code === 'HORAIRE_INVALIDE' || code === 'HORAIRE_FORMAT_INVALIDE' || code === 'HORAIRES_CHEVAUCHANTS') {
        setErreurs({ jours: {}, grille: message });
      } else setActionErr(message);
    } finally {
      setBusy(false);
    }
  }

  async function retirer() {
    const confirme = await confirm({
      title: 'Retirer l’horaire du site',
      message: (
        <>
          <p>
            Le site reviendra sans horaire : le pointage y sera de nouveau possible à toute heure.
          </p>
          <p>
            Les demandes d’heures supplémentaires en attente ou validées sur les jours concernés dont la fenêtre n’a pas
            encore ouvert seront <strong>annulées automatiquement</strong>, et les demandeurs et bénéficiaires seront
            notifiés. Les heures déjà comptées sont conservées.
          </p>
        </>
      ),
      confirmLabel: 'Retirer l’horaire',
      danger: true,
    });
    if (!confirme) return;
    setBusy(true);
    setActionErr(null);
    setOk(null);
    try {
      await deleteHoraireSite(siteId);
      setErreurs(SANS_ERREUR);
      await charger();
      setOk('Horaire retiré : le site n’a plus de contrôle horaire.');
      if (histoOuvert) void chargerHisto(0);
    } catch (e) {
      setActionErr(messageErreurCode(e));
    } finally {
      setBusy(false);
    }
  }

  function basculerHisto() {
    const ouvrir = !histoOuvert;
    setHistoOuvert(ouvrir);
    if (ouvrir && !histo) void chargerHisto(0);
  }

  const desactive = lectureSeule || busy;

  return (
    <section aria-labelledby="horaire-titre">
      <h3 className="section-title" id="horaire-titre">
        Horaire et tolérances
      </h3>

      {loading ? <p className="muted">Chargement de l’horaire…</p> : null}
      {loadErr ? (
        <div className="alert alert--error" role="alert">
          {loadErr}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {!loading && !loadErr && horaire ? (
        <>
          {!horaire.avec_horaire ? (
            <p className="muted small">
              Aucun horaire : le pointage est possible à toute heure.
              {lectureSeule ? '' : ' Renseignez la grille puis enregistrez pour activer le contrôle horaire.'}
            </p>
          ) : (
            <p className="small">
              Dernière modification : {horaire.modifie_par ?? '—'} · {formatDateTunis(horaire.modifie_le)}
            </p>
          )}

          {ok ? (
            <div className="alert alert--success" role="status">
              {ok}
            </div>
          ) : null}
          {actionErr ? (
            <div className="alert alert--error" role="alert">
              {actionErr}
            </div>
          ) : null}

          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Jour</th>
                  <th>Fermé</th>
                  <th>Début</th>
                  <th>Fin</th>
                  <th>Repère</th>
                </tr>
              </thead>
              <tbody>
                {JOURS_SEMAINE.map(({ value, label }) => {
                  const l = brouillon[value];
                  // D-P18.a : « Site fermé » seulement si au moins un jour est paramétré ; sinon « Non défini ».
                  const horaireNonDefini = JOURS_SEMAINE.every(({ value: j }) => brouillon[j].ferme);
                  const nuit = !l.ferme && finLendemain(l.debut, l.fin);
                  const erreurJour = erreurs.jours[value];
                  return (
                    <tr key={value}>
                      <td>{label}</td>
                      <td>
                        <input
                          type="checkbox"
                          checked={l.ferme}
                          disabled={desactive}
                          aria-label={`${label} fermé`}
                          onChange={(e) => majJour(value, { ferme: e.target.checked })}
                        />
                      </td>
                      <td>
                        <SaisieHeure
                          valeur={l.debut}
                          disabled={desactive || l.ferme}
                          ariaLabel={`${label} début`}
                          onChange={(v) => majJour(value, { debut: v })}
                        />
                      </td>
                      <td>
                        <SaisieHeure
                          valeur={l.fin}
                          disabled={desactive || l.ferme}
                          ariaLabel={`${label} fin`}
                          onChange={(v) => majJour(value, { fin: v })}
                        />
                      </td>
                      <td>
                        {l.ferme ? (
                          <span className="muted small">{horaireNonDefini ? 'Non défini' : 'Site fermé'}</span>
                        ) : nuit ? (
                          <span className="badge badge--info" title="La fin tombe le lendemain">
                            Nuit · fin le lendemain
                          </span>
                        ) : null}
                        {erreurJour ? <div className="text-danger small">{erreurJour}</div> : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {erreurs.grille ? (
            <p className="text-danger small" role="alert">
              {erreurs.grille}
            </p>
          ) : null}

          <div className="form-grid mt-md">
            <label>
              Tolérance avant (min)
              <select
                value={tolAvant}
                disabled={desactive}
                aria-invalid={erreurs.tolerance ? true : undefined}
                onChange={(e) => {
                  setTolAvant(Number(e.target.value));
                  setErreurs((x) => ({ ...x, tolerance: undefined }));
                }}
              >
                {TOLERANCES_MINUTES.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Tolérance après (min)
              <select
                value={tolApres}
                disabled={desactive}
                aria-invalid={erreurs.tolerance ? true : undefined}
                onChange={(e) => {
                  setTolApres(Number(e.target.value));
                  setErreurs((x) => ({ ...x, tolerance: undefined }));
                }}
              >
                {TOLERANCES_MINUTES.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {erreurs.tolerance ? (
            <p className="text-danger small" role="alert">
              {erreurs.tolerance}
            </p>
          ) : null}

          {!lectureSeule ? (
            <div className="page__head-actions mt-md">
              <button type="button" className="btn btn--primary" disabled={busy} onClick={() => void enregistrer()}>
                {busy ? 'Enregistrement…' : 'Enregistrer l’horaire'}
              </button>
              {horaire.avec_horaire ? (
                <button type="button" className="btn btn--danger" disabled={busy} onClick={() => void retirer()}>
                  Retirer l’horaire
                </button>
              ) : null}
            </div>
          ) : null}

          <div className="mt-md">
            <button type="button" className="btn btn--ghost btn--sm" aria-expanded={histoOuvert} onClick={basculerHisto}>
              {histoOuvert ? 'Masquer l’historique' : 'Afficher l’historique des modifications'}
            </button>
            {histoOuvert ? (
              <div className="mt-sm">
                {histoLoading ? <p className="muted">Chargement de l’historique…</p> : null}
                {histoErr ? (
                  <div className="alert alert--error" role="alert">
                    {histoErr}
                  </div>
                ) : null}
                {!histoLoading && !histoErr && histo && histo.contenu.length === 0 ? (
                  <p className="muted small">Aucune modification enregistrée pour ce site.</p>
                ) : null}
                {!histoLoading && histo && histo.contenu.length > 0 ? (
                  <>
                    <div className="table-wrap">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Date</th>
                            <th>Auteur</th>
                            <th>Avant</th>
                            <th>Après</th>
                          </tr>
                        </thead>
                        <tbody>
                          {histo.contenu.map((h, i) => (
                            <tr key={`${h.modifie_le}-${i}`}>
                              <td className="small">{formatDateTunis(h.modifie_le)}</td>
                              <td className="small">{h.modifie_par ?? '—'}</td>
                              <td className="small">{resume(h.avant)}</td>
                              <td className="small">{h.apres ? resume(h.apres) : 'Horaire retiré'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <div className="pager">
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        disabled={histoPage <= 0}
                        onClick={() => void chargerHisto(histoPage - 1)}
                      >
                        Précédent
                      </button>
                      <span className="small">
                        Page {histoPage + 1} / {Math.max(1, histo.totalPages)}
                      </span>
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        disabled={histoPage + 1 >= histo.totalPages}
                        onClick={() => void chargerHisto(histoPage + 1)}
                      >
                        Suivant
                      </button>
                    </div>
                  </>
                ) : null}
              </div>
            ) : null}
          </div>
        </>
      ) : null}
    </section>
  );
}

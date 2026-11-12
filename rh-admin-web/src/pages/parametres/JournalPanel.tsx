import { useCallback, useEffect, useState } from 'react';
import {
  getJournalReferentiel,
  getJournalReglages,
  messageParametre,
  type EntreeJournal,
  type PageJournal,
  type ServiceParametre,
} from '../../api/parametresApi';
import { formatDateHeure } from '../../utils/format';
import { nomCouleur } from '../../utils/palette';
import { LoadingState } from '../../components/ui';
import { libelleAttribut } from './ValeurFormulaire';

const TAILLE = 20;

const ACTIONS: Record<string, string> = {
  CREATION: 'Création',
  MODIFICATION: 'Modification',
  DESACTIVATION: 'Désactivation',
  REACTIVATION: 'Réactivation',
  SUPPRESSION: 'Suppression',
  ORDRE: 'Changement d’ordre',
  REGLAGE: 'Réglage',
};

const CHAMPS: Record<string, string> = {
  libelle: 'Libellé',
  description: 'Description',
  couleur: 'Couleur',
  ordre: 'Ordre',
  actif: 'Actif',
  valeur: 'Valeur',
  codes: 'Ordre des valeurs',
};

/** Champs techniques sans intérêt pour la RH. */
const IGNORES = new Set(['version', 'id', 'cree_le', 'modifie_le', 'modifie_par_compte_id', 'code', 'nature', 'systeme', 'referentiel']);

function nomChamp(cle: string): string {
  return CHAMPS[cle] ?? libelleAttribut(cle);
}

function formatValeur(cle: string, v: unknown): string {
  if (v === null || v === undefined || v === '') return '—';
  if (typeof v === 'boolean') return v ? 'Oui' : 'Non';
  if (cle === 'couleur') return nomCouleur(String(v));
  if (Array.isArray(v)) return `${v.length} valeurs`;
  return String(v);
}

/** Changements lisibles d'une entrée : « Libellé : A → B ». */
function changements(e: EntreeJournal): string[] {
  const avant = e.avant ?? {};
  const apres = e.apres ?? {};
  const cles = Array.from(new Set([...Object.keys(avant), ...Object.keys(apres)])).filter((k) => !IGNORES.has(k));
  const lignes: string[] = [];
  for (const k of cles) {
    const a = avant[k];
    const b = apres[k];
    if (JSON.stringify(a) === JSON.stringify(b)) continue;
    if (!e.avant) lignes.push(`${nomChamp(k)} : ${formatValeur(k, b)}`);
    else lignes.push(`${nomChamp(k)} : ${formatValeur(k, a)} → ${formatValeur(k, b)}`);
  }
  return lignes;
}

type Props = {
  service: ServiceParametre;
  /** Absent : historique des réglages du service. */
  referentiel?: string;
  /** Libellé courant d'un code, si la valeur existe encore. */
  libelleDe?: (code: string) => string | undefined;
};

/** Historique paginé des modifications (D-R02.g) : lecture seule, jamais modifiable. */
export default function JournalPanel({ service, referentiel, libelleDe }: Props) {
  const [page, setPage] = useState(0);
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; data?: PageJournal; message?: string }>({ statut: 'chargement' });

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      const data = referentiel
        ? await getJournalReferentiel(service, referentiel, page, TAILLE)
        : await getJournalReglages(service, page, TAILLE);
      setEtat({ statut: 'ok', data });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageParametre(e) });
    }
  }, [service, referentiel, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

  if (etat.statut === 'chargement') return <LoadingState label="Chargement de l’historique…" />;
  if (etat.statut === 'erreur' || !etat.data) {
    return (
      <div className="alert alert--error" role="alert">
        {etat.message ?? 'Impossible de charger l’historique.'}{' '}
        <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
      </div>
    );
  }
  const { contenu, total } = etat.data;
  if (contenu.length === 0 && page === 0) return <p className="detail-vide">Aucune modification enregistrée.</p>;
  const pages = Math.max(1, Math.ceil(total / TAILLE));

  return (
    <div>
      <div className="table-wrap">
        <table className="data-table">
          <caption className="sr-only">Historique des modifications</caption>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Action</th>
              <th scope="col">Valeur</th>
              <th scope="col">Auteur</th>
              <th scope="col">Détail</th>
            </tr>
          </thead>
          <tbody>
            {contenu.map((e, i) => {
              const lignes = changements(e);
              const nom = e.code ? libelleDe?.(e.code) ?? (typeof (e.apres ?? e.avant)?.libelle === 'string' ? String((e.apres ?? e.avant)?.libelle) : undefined) : undefined;
              return (
                <tr key={`${e.horodatage}-${i}`}>
                  <td>{formatDateHeure(e.horodatage)}</td>
                  <td>{ACTIONS[e.action] ?? 'Valeur non reconnue'}</td>
                  <td>
                    {nom ?? (e.code ? '—' : 'Tout le référentiel')}
                    {e.code ? <span className="param-ref">Référence technique : {e.code}</span> : null}
                  </td>
                  <td>{e.auteur_nom ?? '—'}</td>
                  <td>
                    {lignes.length === 0 ? '—' : (
                      <ul className="param-diff">
                        {lignes.map((l) => <li key={l}>{l}</li>)}
                      </ul>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="pager">
        <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
        <span>Page {page + 1} sur {pages}</span>
        <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import {
  getJournalImport,
  messageParametrageImport,
  type PageJournalImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { LoadingState } from '../../components/ui';
import { formatDateHeure, TIRET } from '../../utils/format';

const TAILLE = 20;

/** Historique paginé des modifications du paramétrage d'un type d'import (P7), en lecture seule. */
export default function ImportJournalPanel({ type }: { type: TypeParametrable }) {
  const [page, setPage] = useState(0);
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; data?: PageJournalImport; message?: string }>({ statut: 'chargement' });

  useEffect(() => { setPage(0); }, [type]);

  const charger = useCallback(async () => {
    setEtat((e) => ({ ...e, statut: 'chargement' }));
    try {
      setEtat({ statut: 'ok', data: await getJournalImport(type, page, TAILLE) });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageParametrageImport(e) });
    }
  }, [type, page]);

  useEffect(() => { void charger(); }, [charger]);

  if (etat.statut === 'chargement' && !etat.data) return <LoadingState label="Chargement de l’historique…" />;
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
    <div aria-busy={etat.statut === 'chargement'}>
      <div className="table-wrap">
        <table className="data-table">
          <caption className="sr-only">Historique des modifications du paramétrage</caption>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Auteur</th>
              <th scope="col">Champ</th>
              <th scope="col">Modification</th>
              <th scope="col">Avant → après</th>
            </tr>
          </thead>
          <tbody>
            {contenu.map((e) => (
              <tr key={e.id}>
                <td>{formatDateHeure(e.horodatage)}</td>
                <td>{e.auteur_nom}</td>
                <td>{e.champ_libelle}</td>
                <td>{e.attribut_libelle}</td>
                <td>{e.avant ?? TIRET} → {e.apres ?? TIRET}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {pages > 1 ? (
        <div className="pager">
          <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
          <span>Page {page + 1} sur {pages}</span>
          <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
        </div>
      ) : null}
    </div>
  );
}

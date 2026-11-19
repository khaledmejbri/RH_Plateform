import { useState } from 'react';
import { getJournalValeurs } from '../../api/valeursPersoApi';
import { Icon } from '../../components/ui';
import { DialogPanel } from '../../components/useDialogA11y';
import { formatDateHeure } from '../../utils/format';
import { Charge } from '../Etats';
import { useChargement } from '../useChargement';
import { Tableau } from './commun';

const VIDE = '(vide)';

/** Historique des valeurs des champs personnalisés d'une fiche (V3), 20 par page, lecture seule. */
export default function ChampsPersoJournalDialog({ id, onFermer }: { id: string; onFermer: () => void }) {
  const [page, setPage] = useState(0);
  const journal = useChargement(() => getJournalValeurs(id, page, 20), [id, page]);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onFermer}>
      <DialogPanel label="Historique des informations complémentaires" className="modal modal--lg" onClose={onFermer}>
        <div className="modal__head">
          <h3 className="modal__title" style={{ margin: 0 }}>Historique des informations complémentaires</h3>
          <button type="button" className="modal__close" aria-label="Fermer" onClick={onFermer}><Icon name="x" size={18} /></button>
        </div>
        <div className="modal__scroll">
          <Charge etat={journal.etat} recharger={journal.recharger} vide={(p) => p.contenu.length === 0} messageVide="Aucune modification enregistrée.">
            {(p) => {
              const pages = Math.max(1, Math.ceil(p.total / p.taille));
              return (
                <>
                  <Tableau colonnes={['Date', 'Auteur', 'Champ', 'Avant', 'Après', 'Origine']}>
                    {p.contenu.map((e) => (
                      <tr key={e.id}>
                        <td>{formatDateHeure(e.horodatage)}</td>
                        <td>{e.auteur_nom}</td>
                        <td>{e.champ_libelle}</td>
                        <td>{e.avant ?? VIDE}</td>
                        <td>{e.apres ?? VIDE}</td>
                        <td>{e.origine_libelle}</td>
                      </tr>
                    ))}
                  </Tableau>
                  {pages > 1 ? (
                    <div className="pager">
                      <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
                      <span>Page {page + 1} sur {pages}</span>
                      <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
                    </div>
                  ) : null}
                </>
              );
            }}
          </Charge>
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onFermer}>Fermer</button>
        </div>
      </DialogPanel>
    </div>
  );
}

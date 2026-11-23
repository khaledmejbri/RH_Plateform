import {
  annulerDelegationAdmin,
  executerPourConfirmation,
  type CatalogueWorkflow,
  type DelegationAdmin,
} from '../../api/workflowApi';
import type { useConfirm } from '../../components/ConfirmDialog';
import DetailLink from '../../detail/DetailLink';
import { formatDateHeure, formatPeriode, TIRET } from '../../utils/format';
import { libelleOrigineDelegation } from '../../workflow/libelles';
import {
  formatObjetTypesDelegation,
  libelleStatutDelegation,
  statutDelegation,
  toneStatutDelegation,
} from '../../workflow/supervision';

export type ModeDelegationsTableau = 'ADMIN' | 'DONNEES' | 'RECUES';

export type DelegationsTableauProps = {
  elements: ReadonlyArray<DelegationAdmin>;
  catalogue: Pick<CatalogueWorkflow, 'types'> | null;
  lectureSeule: boolean;
  mode?: ModeDelegationsTableau;
  aujourdhui?: string;
  onAnnuler?: (d: DelegationAdmin) => void;
};

/** Confirmation unifiée d'annulation d'une délégation (partagée entre la page et la fiche collaborateur). */
export async function confirmerAnnulationDelegation(
  confirm: ReturnType<typeof useConfirm>,
  d: DelegationAdmin,
): Promise<boolean> {
  return confirm({
    title: 'Annuler cette délégation ?',
    message: `La délégation de ${d.delegant_nom} vers ${d.delegue_nom} (${formatPeriode(d.du, d.au)}) sera annulée et retirée des tâches ouvertes concernées.`,
    confirmLabel: 'Annuler la délégation',
    danger: true,
    envoyer: () => executerPourConfirmation(() => annulerDelegationAdmin(d.id)),
  });
}

/** Tableau partagé des délégations ; masque toute colonne/action d'écriture en lecture seule DIRECTION. */
export default function DelegationsTableau({
  elements,
  catalogue,
  lectureSeule,
  mode = 'ADMIN',
  aujourdhui,
  onAnnuler,
}: DelegationsTableauProps) {
  const afficherDelegant = mode === 'ADMIN' || mode === 'RECUES';
  const afficherDelegue = mode === 'ADMIN' || mode === 'DONNEES';

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {afficherDelegant ? <th scope="col">Délégant</th> : null}
            {afficherDelegue ? <th scope="col">Délégué</th> : null}
            <th scope="col">Période</th>
            <th scope="col">Types couverts</th>
            <th scope="col">Origine</th>
            <th scope="col">Statut</th>
            <th scope="col">Motif</th>
            {!lectureSeule ? <th scope="col">Actions</th> : null}
          </tr>
        </thead>
        <tbody>
          {elements.map((d) => {
            const st = statutDelegation(d, aujourdhui);
            const annulable = !lectureSeule && (st === 'ACTIVE' || st === 'PLANIFIEE');
            return (
              <tr key={d.id}>
                {afficherDelegant ? (
                  <td>
                    <DetailLink type="collaborateur" id={d.delegant_id} libelle={d.delegant_nom} />
                  </td>
                ) : null}
                {afficherDelegue ? (
                  <td>
                    <DetailLink type="collaborateur" id={d.delegue_id} libelle={d.delegue_nom} />
                  </td>
                ) : null}
                <td>{formatPeriode(d.du, d.au)}</td>
                <td>{formatObjetTypesDelegation(d.objet_types, catalogue)}</td>
                <td>{libelleOrigineDelegation(d.origine)}</td>
                <td>
                  <span className={`status status--${toneStatutDelegation(st)}`}>
                    {libelleStatutDelegation(st)}
                  </span>
                  {d.annulee_le ? (
                    <div className="small muted">Le {formatDateHeure(d.annulee_le)}</div>
                  ) : null}
                </td>
                <td>{d.motif ?? TIRET}</td>
                {!lectureSeule ? (
                  <td>
                    {annulable && onAnnuler ? (
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => onAnnuler(d)}
                      >
                        Annuler
                      </button>
                    ) : (
                      TIRET
                    )}
                  </td>
                ) : null}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

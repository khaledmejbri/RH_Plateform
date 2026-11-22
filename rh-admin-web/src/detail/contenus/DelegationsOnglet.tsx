import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getToken } from '../../api/auth';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  annulerDelegationAdmin,
  executerPourConfirmation,
  getCatalogueWorkflow,
  getDelegationsAdmin,
  type CatalogueWorkflow,
  type DelegationAdmin,
} from '../../api/workflowApi';
import { canWriteBackoffice } from '../../auth/jwtRoles';
import { useConfirm } from '../../components/ConfirmDialog';
import DelegationFormCard from '../../pages/workflow/DelegationFormCard';
import { formatDateHeure, formatPeriode, TIRET } from '../../utils/format';
import { libelleOrigineDelegation } from '../../workflow/libelles';
import {
  formatObjetTypesDelegation,
  libelleStatutDelegation,
  statutDelegation,
  toneStatutDelegation,
} from '../../workflow/supervision';
import DetailLink from '../DetailLink';
import { Charge } from '../Etats';
import { useChargement } from '../useChargement';
import { Tableau } from './commun';

const TAILLE_ONGLET = 50;

/**
 * Onglet « Délégations » de la fiche collaborateur (D-W7b-05).
 * Rendu aussi dans `DetailProvider` (hors `<Outlet>`) : utilise `canWriteBackoffice(getToken())`, jamais `useOutletContext`.
 */
export default function DelegationsOnglet({ id }: { id: string }) {
  const lectureSeule = !canWriteBackoffice(getToken());
  const confirm = useConfirm();
  const [rev, setRev] = useState(0);
  const [formOuvert, setFormOuvert] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(false);
  const [collabsCharges, setCollabsCharges] = useState(false);

  const donnees = useChargement(async () => {
    const [donneesRes, recuesRes, catRes] = await Promise.all([
      getDelegationsAdmin({ delegant_id: id, taille: TAILLE_ONGLET }),
      getDelegationsAdmin({ delegue_id: id, taille: TAILLE_ONGLET }),
      getCatalogueWorkflow().catch(() => null),
    ]);
    return { donnees: donneesRes, recues: recuesRes, catalogue: catRes as CatalogueWorkflow | null };
  }, [id, rev]);

  useEffect(() => {
    if (!formOuvert || lectureSeule || collabsCharges) return;
    let annule = false;
    setCollabChargement(true);
    void getTousCollaborateurs()
      .then((list) => {
        if (!annule) {
          setCollaborateurs(list);
          setCollabsCharges(true);
        }
      })
      .catch(() => {
        if (!annule) setCollaborateurs([]);
      })
      .finally(() => {
        if (!annule) setCollabChargement(false);
      });
    return () => {
      annule = true;
    };
  }, [formOuvert, lectureSeule, collabsCharges]);

  async function demanderAnnulation(d: DelegationAdmin) {
    if (lectureSeule) return;
    setMessage(null);
    const ok = await confirm({
      title: 'Annuler cette délégation ?',
      message: `La délégation de ${d.delegant_nom} vers ${d.delegue_nom} (${formatPeriode(d.du, d.au)}) sera annulée et retirée des tâches ouvertes concernées.`,
      confirmLabel: 'Annuler la délégation',
      danger: true,
      envoyer: () => executerPourConfirmation(() => annulerDelegationAdmin(d.id)),
    });
    if (ok) {
      setMessage('Délégation annulée.');
      setRev((n) => n + 1);
    }
  }

  const catalogue = donnees.etat.statut === 'ok' ? donnees.etat.data.catalogue : null;

  return (
    <>
      {!lectureSeule ? (
        <div className="import__actions" style={{ justifyContent: 'flex-start', marginBottom: 12 }}>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => {
              setMessage(null);
              setFormOuvert((v) => !v);
            }}
          >
            {formOuvert ? 'Fermer le formulaire' : 'Nouvelle délégation'}
          </button>
        </div>
      ) : null}

      {message ? (
        <div className="alert alert--success" role="status">
          {message}
        </div>
      ) : null}

      {formOuvert && !lectureSeule ? (
        <DelegationFormCard
          idPrefixe="collab-delegation"
          collaborateurs={collaborateurs}
          collabChargement={collabChargement}
          catalogue={catalogue}
          delegantIdInitial={id}
          onCree={() => {
            setFormOuvert(false);
            setMessage('Délégation enregistrée.');
            setRev((n) => n + 1);
          }}
          onAnnulerForm={() => setFormOuvert(false)}
        />
      ) : null}

      <Charge etat={donnees.etat} recharger={donnees.recharger}>
        {({ donnees: delDonnees, recues: delRecues, catalogue: cat }) => {
          const colsDonnees = [
            'Délégué',
            'Période',
            'Types couverts',
            'Origine',
            'Statut',
            'Motif',
            ...(lectureSeule ? [] : ['Actions']),
          ];
          const colsRecues = [
            'Délégant',
            'Période',
            'Types couverts',
            'Origine',
            'Statut',
            'Motif',
            ...(lectureSeule ? [] : ['Actions']),
          ];

          return (
            <>
              <h3 className="detail-h3" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
                Délégations données
              </h3>
              {delDonnees.elements.length === 0 ? (
                <p className="detail-vide">Aucune délégation donnée par ce collaborateur.</p>
              ) : (
                <>
                  <Tableau colonnes={colsDonnees}>
                    {delDonnees.elements.map((d) => {
                      const st = statutDelegation(d);
                      const annulable = !lectureSeule && (st === 'ACTIVE' || st === 'PLANIFIEE');
                      return (
                        <tr key={d.id}>
                          <td>
                            <DetailLink type="collaborateur" id={d.delegue_id} libelle={d.delegue_nom} />
                          </td>
                          <td>{formatPeriode(d.du, d.au)}</td>
                          <td>{formatObjetTypesDelegation(d.objet_types, cat)}</td>
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
                              {annulable ? (
                                <button
                                  type="button"
                                  className="btn btn--ghost btn--sm"
                                  onClick={() => void demanderAnnulation(d)}
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
                  </Tableau>
                  {delDonnees.total > delDonnees.elements.length ? (
                    <p className="cell-meta">
                      {delDonnees.elements.length} affichées sur {delDonnees.total}.{' '}
                      <Link to={`/app/workflows/delegations?delegant_id=${encodeURIComponent(id)}`}>
                        Voir toutes les délégations
                      </Link>
                    </p>
                  ) : null}
                </>
              )}

              <h3 className="detail-h3">Délégations reçues</h3>
              {delRecues.elements.length === 0 ? (
                <p className="detail-vide">Aucune délégation reçue par ce collaborateur.</p>
              ) : (
                <>
                  <Tableau colonnes={colsRecues}>
                    {delRecues.elements.map((d) => {
                      const st = statutDelegation(d);
                      const annulable = !lectureSeule && (st === 'ACTIVE' || st === 'PLANIFIEE');
                      return (
                        <tr key={d.id}>
                          <td>
                            <DetailLink type="collaborateur" id={d.delegant_id} libelle={d.delegant_nom} />
                          </td>
                          <td>{formatPeriode(d.du, d.au)}</td>
                          <td>{formatObjetTypesDelegation(d.objet_types, cat)}</td>
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
                              {annulable ? (
                                <button
                                  type="button"
                                  className="btn btn--ghost btn--sm"
                                  onClick={() => void demanderAnnulation(d)}
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
                  </Tableau>
                  {delRecues.total > delRecues.elements.length ? (
                    <p className="cell-meta">
                      {delRecues.elements.length} affichées sur {delRecues.total}.{' '}
                      <Link to={`/app/workflows/delegations?delegue_id=${encodeURIComponent(id)}`}>
                        Voir toutes les délégations
                      </Link>
                    </p>
                  ) : null}
                </>
              )}
            </>
          );
        }}
      </Charge>
    </>
  );
}

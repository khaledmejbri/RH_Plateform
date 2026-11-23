import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getToken } from '../../api/auth';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  getCatalogueWorkflow,
  getDelegationsAdmin,
  type CatalogueWorkflow,
  type DelegationAdmin,
  type PageWorkflow,
} from '../../api/workflowApi';
import { canWriteBackoffice } from '../../auth/jwtRoles';
import { useConfirm } from '../../components/ConfirmDialog';
import DelegationFormCard from '../../pages/workflow/DelegationFormCard';
import DelegationsTableau, { confirmerAnnulationDelegation } from '../../pages/workflow/DelegationsTableau';
import { Charge } from '../Etats';
import type { EtatChargement } from '../useChargement';

const TAILLE_ONGLET = 50;

export type DonneesDelegationsOnglet = {
  donnees: PageWorkflow<DelegationAdmin>;
  recues: PageWorkflow<DelegationAdmin>;
  catalogue: CatalogueWorkflow | null;
};

/**
 * Onglet « Délégations » de la fiche collaborateur (D-W7b-05).
 * Rendu aussi dans `DetailProvider` (hors `<Outlet>`) : utilise `canWriteBackoffice(getToken())`, jamais `useOutletContext`.
 */
export default function DelegationsOnglet({
  id,
  initialDonnees,
}: {
  id: string;
  initialDonnees?: DonneesDelegationsOnglet;
}) {
  const lectureSeule = !canWriteBackoffice(getToken());
  const confirm = useConfirm();
  const [formOuvert, setFormOuvert] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(false);
  const [collabsCharges, setCollabsCharges] = useState(false);

  const [etat, setEtat] = useState<EtatChargement<DonneesDelegationsOnglet>>(
    initialDonnees ? { statut: 'ok', data: initialDonnees } : { statut: 'chargement' },
  );
  const [catalogueErreur, setCatalogueErreur] = useState<string | null>(null);

  const monteRef = useRef(true);
  const reqIdRef = useRef(0);

  useEffect(() => {
    monteRef.current = true;
    return () => {
      monteRef.current = false;
    };
  }, []);

  const charger = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setEtat({ statut: 'chargement' });
    setCatalogueErreur(null);
    try {
      const [donneesRes, recuesRes, catRes] = await Promise.all([
        getDelegationsAdmin({ delegant_id: id, taille: TAILLE_ONGLET }),
        getDelegationsAdmin({ delegue_id: id, taille: TAILLE_ONGLET }),
        getCatalogueWorkflow().catch(() => {
          if (monteRef.current && reqId === reqIdRef.current) {
            setCatalogueErreur('Le catalogue des types d’objet est momentanément indisponible.');
          }
          return null;
        }),
      ]);
      if (!monteRef.current || reqId !== reqIdRef.current) return;
      setEtat({
        statut: 'ok',
        data: { donnees: donneesRes, recues: recuesRes, catalogue: catRes },
      });
    } catch (erreur) {
      if (!monteRef.current || reqId !== reqIdRef.current) return;
      setEtat({ statut: 'erreur', erreur });
    }
  }, [id]);

  useEffect(() => {
    void charger();
  }, [charger]);

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
    const ok = await confirmerAnnulationDelegation(confirm, d);
    if (!monteRef.current) return;
    if (ok) {
      setMessage('Délégation annulée.');
      await charger();
    }
  }

  const catalogue = etat.statut === 'ok' ? etat.data.catalogue : null;

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

      {catalogueErreur ? (
        <div className="alert alert--warning" role="status" style={{ marginBottom: 12 }}>
          <span>{catalogueErreur}</span>
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
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
            void charger();
          }}
          onAnnulerForm={() => setFormOuvert(false)}
        />
      ) : null}

      <Charge etat={etat} recharger={() => void charger()}>
        {({ donnees: delDonnees, recues: delRecues, catalogue: cat }) => (
          <>
            <h3 className="detail-h3" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
              Délégations données
            </h3>
            {delDonnees.elements.length === 0 ? (
              <p className="detail-vide">Aucune délégation donnée par ce collaborateur.</p>
            ) : (
              <>
                <DelegationsTableau
                  elements={delDonnees.elements}
                  catalogue={cat}
                  lectureSeule={lectureSeule}
                  mode="DONNEES"
                  onAnnuler={(d) => void demanderAnnulation(d)}
                />
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
                <DelegationsTableau
                  elements={delRecues.elements}
                  catalogue={cat}
                  lectureSeule={lectureSeule}
                  mode="RECUES"
                  onAnnuler={(d) => void demanderAnnulation(d)}
                />
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
        )}
      </Charge>
    </>
  );
}

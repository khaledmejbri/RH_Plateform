import { useCallback, useEffect, useRef, useState } from 'react';
import { getToken } from '../../api/auth';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  getCatalogueWorkflow,
  getDelegationsAdmin,
  messageErreurWorkflow,
  type CatalogueWorkflow,
  type DelegationAdmin,
  type PageWorkflow,
} from '../../api/workflowApi';
import { canWriteBackoffice } from '../../auth/jwtRoles';
import BarreFiltres, { AucunResultat, ChampSelect } from '../../components/BarreFiltres';
import { useConfirm } from '../../components/ConfirmDialog';
import FiltreCollaborateur from '../../components/FiltreCollaborateur';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { useFiltresUrl } from '../../utils/filtres';
import DelegationFormCard from './DelegationFormCard';
import DelegationsTableau, { confirmerAnnulationDelegation } from './DelegationsTableau';

const TAILLE_PAGE = 20;

const FILTRES_DEFAUT = {
  actives: '',
  delegant_id: '',
  delegue_id: '',
  page: '0',
};

const OPTIONS_ACTIVES = [
  { valeur: 'true', libelle: 'Actives et planifiées' },
  { valeur: 'false', libelle: 'Expirées et annulées' },
];

type Props = {
  initialDonnees?: PageWorkflow<DelegationAdmin> | null;
  initialCatalogue?: CatalogueWorkflow | null;
};

export default function DelegationsPage({
  initialDonnees = null,
  initialCatalogue = null,
}: Props = {}) {
  const lectureSeule = !canWriteBackoffice(getToken());
  const confirm = useConfirm();
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const page = Math.max(0, Number(f.page) || 0);

  const monteRef = useRef(true);
  const catReqIdRef = useRef(0);
  const reqIdRef = useRef(0);

  useEffect(() => {
    monteRef.current = true;
    return () => {
      monteRef.current = false;
    };
  }, []);

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(initialCatalogue);
  const [catalogueErreur, setCatalogueErreur] = useState<string | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(true);

  const [donnees, setDonnees] = useState<PageWorkflow<DelegationAdmin> | null>(initialDonnees);
  const [chargement, setChargement] = useState(initialDonnees === null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [formOuvert, setFormOuvert] = useState(false);

  const chargerCatalogue = useCallback(async () => {
    const reqId = ++catReqIdRef.current;
    setCatalogueErreur(null);
    try {
      const c = await getCatalogueWorkflow();
      if (!monteRef.current || reqId !== catReqIdRef.current) return;
      setCatalogue(c);
    } catch {
      if (!monteRef.current || reqId !== catReqIdRef.current) return;
      setCatalogueErreur('Le catalogue des types d’objet est momentanément indisponible.');
    }
  }, []);

  useEffect(() => {
    let annule = false;
    void chargerCatalogue();
    setCollabChargement(true);
    void getTousCollaborateurs()
      .then((list) => { if (!annule) setCollaborateurs(list); })
      .catch(() => { if (!annule) setCollaborateurs([]); })
      .finally(() => { if (!annule) setCollabChargement(false); });
    return () => {
      annule = true;
    };
  }, [chargerCatalogue]);

  const charger = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setChargement(true);
    setErreur(null);
    try {
      const activesParam = f.actives === 'true' ? true : f.actives === 'false' ? false : undefined;
      const res = await getDelegationsAdmin({
        actives: activesParam,
        delegant_id: f.delegant_id || undefined,
        delegue_id: f.delegue_id || undefined,
        page,
        taille: TAILLE_PAGE,
      });
      if (!monteRef.current || reqId !== reqIdRef.current) return;
      setDonnees(res);
    } catch (e) {
      if (!monteRef.current || reqId !== reqIdRef.current) return;
      setDonnees(null);
      setErreur(messageErreurWorkflow(e));
    } finally {
      if (monteRef.current && reqId === reqIdRef.current) setChargement(false);
    }
  }, [f.actives, f.delegant_id, f.delegue_id, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

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

  const elements = donnees?.elements ?? [];
  const totalPages = donnees ? Math.max(1, Math.ceil(donnees.total / donnees.taille)) : 1;
  const nbActifs = [f.actives, f.delegant_id, f.delegue_id].filter(Boolean).length;

  return (
    <div className="page">
      <PageHeader
        title="Délégations"
        lead="Consultation et gestion des délégations de validation (manuelles et automatiques sur congé)."
        actions={
          !lectureSeule ? (
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => {
                setMessage(null);
                setFormOuvert((v) => !v);
              }}
            >
              {formOuvert ? 'Fermer le formulaire' : 'Nouvelle délégation'}
            </button>
          ) : null
        }
      />

      {catalogueErreur ? (
        <div className="alert alert--warning" role="status" style={{ marginBottom: 12 }}>
          <span>{catalogueErreur}</span>
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerCatalogue()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {message ? (
        <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>
          {message}
        </div>
      ) : null}

      {formOuvert && !lectureSeule ? (
        <DelegationFormCard
          idPrefixe="delegation-admin"
          collaborateurs={collaborateurs}
          collabChargement={collabChargement}
          catalogue={catalogue}
          onCree={() => {
            setFormOuvert(false);
            setMessage('Délégation enregistrée.');
            if (page !== 0) filtres.definir({ page: '0' });
            else void charger();
          }}
          onAnnulerForm={() => setFormOuvert(false)}
        />
      ) : null}

      <BarreFiltres
        id="wf-delegations"
        nbResultats={elements.length}
        nbTotal={donnees?.total}
        nbActifs={nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="del-actives"
          libelle="État"
          valeur={f.actives}
          toutesLibelle="Toutes"
          options={OPTIONS_ACTIVES}
          onChange={(v) => filtres.definir({ actives: v, page: '0' })}
        />
        <FiltreCollaborateur
          id="del-delegant"
          libelle="Délégant"
          collaborateurs={collaborateurs}
          chargement={collabChargement}
          valeur={f.delegant_id}
          onChange={(v) => filtres.definir({ delegant_id: v, page: '0' })}
        />
        <FiltreCollaborateur
          id="del-delegue"
          libelle="Délégué"
          collaborateurs={collaborateurs}
          chargement={collabChargement}
          valeur={f.delegue_id}
          onChange={(v) => filtres.definir({ delegue_id: v, page: '0' })}
        />
      </BarreFiltres>

      {erreur ? (
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {chargement ? <LoadingState /> : null}

      {!chargement && !erreur && elements.length === 0 ? (
        nbActifs > 0 ? (
          <AucunResultat onReinitialiser={filtres.reinitialiser} />
        ) : (
          <EmptyState title="Aucune délégation enregistrée">
            Les délégations créées manuellement ou automatiquement lors d’un congé approuvé apparaîtront ici.
          </EmptyState>
        )
      ) : null}

      {!chargement && !erreur && elements.length > 0 ? (
        <div className="card">
          <DelegationsTableau
            elements={elements}
            catalogue={catalogue}
            lectureSeule={lectureSeule}
            mode="ADMIN"
            onAnnuler={(d) => void demanderAnnulation(d)}
          />

          {totalPages > 1 ? (
            <div className="pager">
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={page <= 0}
                onClick={() => filtres.definir({ page: String(page - 1) })}
              >
                Précédent
              </button>
              <span className="small muted">Page {page + 1} sur {totalPages}</span>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={page + 1 >= totalPages}
                onClick={() => filtres.definir({ page: String(page + 1) })}
              >
                Suivant
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { getToken } from '../../api/auth';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  annulerDelegationAdmin,
  executerPourConfirmation,
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
import DetailLink from '../../detail/DetailLink';
import { useFiltresUrl } from '../../utils/filtres';
import { formatDateHeure, formatPeriode, TIRET } from '../../utils/format';
import { libelleOrigineDelegation } from '../../workflow/libelles';
import {
  formatObjetTypesDelegation,
  libelleStatutDelegation,
  statutDelegation,
  toneStatutDelegation,
} from '../../workflow/supervision';
import DelegationFormCard from './DelegationFormCard';

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

export default function DelegationsPage() {
  const lectureSeule = !canWriteBackoffice(getToken());
  const confirm = useConfirm();
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const page = Math.max(0, Number(f.page) || 0);

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(true);

  const [donnees, setDonnees] = useState<PageWorkflow<DelegationAdmin> | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [formOuvert, setFormOuvert] = useState(false);

  useEffect(() => {
    let annule = false;
    void getCatalogueWorkflow()
      .then((c) => { if (!annule) setCatalogue(c); })
      .catch(() => undefined);
    setCollabChargement(true);
    void getTousCollaborateurs()
      .then((list) => { if (!annule) setCollaborateurs(list); })
      .catch(() => { if (!annule) setCollaborateurs([]); })
      .finally(() => { if (!annule) setCollabChargement(false); });
    return () => {
      annule = true;
    };
  }, []);

  const charger = useCallback(async () => {
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
      setDonnees(res);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, [f.actives, f.delegant_id, f.delegue_id, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

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

      {!chargement && elements.length > 0 ? (
        <div className="card">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Délégant</th>
                  <th scope="col">Délégué</th>
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
                  const st = statutDelegation(d);
                  const annulable = !lectureSeule && (st === 'ACTIVE' || st === 'PLANIFIEE');
                  return (
                    <tr key={d.id}>
                      <td>
                        <DetailLink type="collaborateur" id={d.delegant_id} libelle={d.delegant_nom} />
                      </td>
                      <td>
                        <DetailLink type="collaborateur" id={d.delegue_id} libelle={d.delegue_nom} />
                      </td>
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
              </tbody>
            </table>
          </div>

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

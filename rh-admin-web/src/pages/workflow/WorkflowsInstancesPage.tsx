import { useCallback, useEffect, useMemo, useState } from 'react';
import { getToken } from '../../api/auth';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  getCatalogueWorkflow,
  getIndicateursWorkflow,
  getInstancesWorkflow,
  messageErreurWorkflow,
  type CatalogueWorkflow,
  type IndicateursWorkflow,
  type InstanceResume,
  type PageWorkflow,
} from '../../api/workflowApi';
import { canWriteBackoffice } from '../../auth/jwtRoles';
import BarreFiltres, { AucunResultat, ChampDate, ChampSelect } from '../../components/BarreFiltres';
import FiltreCollaborateur from '../../components/FiltreCollaborateur';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import DetailLink from '../../detail/DetailLink';
import { useFiltresUrl } from '../../utils/filtres';
import { formatDateHeure, TIRET } from '../../utils/format';
import { libelleStatutInstance, STATUT_INSTANCE, toneStatutInstance } from '../../workflow/libelles';
import { formatDelaiHeures, periodeParDefaut30Jours } from '../../workflow/supervision';
import InstanceDetailModal from './InstanceDetailModal';

const TAILLE_PAGE = 20;

const FILTRES_DEFAUT = {
  objet_type: '',
  statut: '',
  etape: '',
  en_retard: '',
  demandeur_id: '',
  intervenant_id: '',
  du: '',
  au: '',
  page: '0',
};

export default function WorkflowsInstancesPage() {
  const lectureSeule = !canWriteBackoffice(getToken());
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const page = Math.max(0, Number(f.page) || 0);

  const [catalogue, setCatalogue] = useState<CatalogueWorkflow | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(true);

  const [etapeSaisie, setEtapeSaisie] = useState(f.etape);
  useEffect(() => {
    setEtapeSaisie(f.etape);
  }, [f.etape]);
  useEffect(() => {
    if (etapeSaisie === f.etape) return undefined;
    const t = window.setTimeout(() => filtres.definir({ etape: etapeSaisie, page: '0' }), 350);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etapeSaisie]);

  // Période par défaut des indicateurs « Délais moyens par étape » (30 derniers jours)
  const [periodeInd, setPeriodeInd] = useState(() => periodeParDefaut30Jours());
  const [indicateurs, setIndicateurs] = useState<IndicateursWorkflow | null>(null);
  const [indChargement, setIndChargement] = useState(true);
  const [indErreur, setIndErreur] = useState<string | null>(null);

  const [donnees, setDonnees] = useState<PageWorkflow<InstanceResume> | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const [instanceOuverteId, setInstanceOuverteId] = useState<string | null>(null);

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

  const chargerIndicateurs = useCallback(async () => {
    setIndChargement(true);
    setIndErreur(null);
    try {
      const res = await getIndicateursWorkflow(periodeInd.du || undefined, periodeInd.au || undefined);
      setIndicateurs(res);
    } catch (e) {
      setIndErreur(messageErreurWorkflow(e));
    } finally {
      setIndChargement(false);
    }
  }, [periodeInd.du, periodeInd.au]);

  useEffect(() => {
    void chargerIndicateurs();
  }, [chargerIndicateurs]);

  const chargerInstances = useCallback(async () => {
    setChargement(true);
    setErreur(null);
    try {
      const res = await getInstancesWorkflow({
        objet_type: f.objet_type || undefined,
        statut: f.statut || undefined,
        etape: f.etape.trim() || undefined,
        en_retard: f.en_retard === 'true' ? true : undefined,
        demandeur_id: f.demandeur_id || undefined,
        intervenant_id: f.intervenant_id || undefined,
        du: f.du || undefined,
        au: f.au || undefined,
        page,
        taille: TAILLE_PAGE,
      });
      setDonnees(res);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, [f.objet_type, f.statut, f.etape, f.en_retard, f.demandeur_id, f.intervenant_id, f.du, f.au, page]);

  useEffect(() => {
    void chargerInstances();
  }, [chargerInstances]);

  const optionsTypes = useMemo(
    () => (catalogue?.types ?? []).map((t) => ({ valeur: t.objet_type, libelle: t.libelle })),
    [catalogue],
  );

  const optionsStatuts = useMemo(
    () => Object.entries(STATUT_INSTANCE).map(([valeur, libelle]) => ({ valeur, libelle })),
    [],
  );

  const nbActifs = [
    f.objet_type,
    f.statut,
    f.etape.trim(),
    f.en_retard,
    f.demandeur_id,
    f.intervenant_id,
    f.du,
    f.au,
  ].filter(Boolean).length;

  function basculerKpi(cible: 'EN_COURS' | 'EN_RETARD' | 'BLOQUEE') {
    if (cible === 'EN_RETARD') {
      const dejaActif = f.en_retard === 'true' && !f.statut;
      filtres.definir({ en_retard: dejaActif ? '' : 'true', statut: '', page: '0' });
    } else {
      const dejaActif = f.statut === cible && !f.en_retard;
      filtres.definir({ statut: dejaActif ? '' : cible, en_retard: '', page: '0' });
    }
  }

  const elements = donnees?.elements ?? [];
  const totalPages = donnees ? Math.max(1, Math.ceil(donnees.total / donnees.taille)) : 1;

  return (
    <div className="page">
      <PageHeader
        title="Workflows en cours"
        lead="Supervision des circuits de validation, suivi des délais et réaffectation des tâches bloquées."
      />

      {/* Indicateurs temps réel (indépendants de du/au) */}
      <section aria-label="Indicateurs temps réel" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
          <h3 className="section-title" style={{ margin: 0 }}>Situation actuelle</h3>
          <span className="small muted">Compteurs en temps réel (indépendants de la période ci-dessous)</span>
        </div>

        {indErreur ? (
          <ErrorAlert>
            {indErreur}{' '}
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerIndicateurs()}>
              Réessayer
            </button>
          </ErrorAlert>
        ) : null}

        <div className="grid-stats" style={{ marginBottom: 12 }}>
          <button
            type="button"
            className="stat-card anim-lift"
            aria-pressed={f.statut === 'EN_COURS' && !f.en_retard}
            onClick={() => basculerKpi('EN_COURS')}
            style={{
              textAlign: 'left',
              cursor: 'pointer',
              borderColor: f.statut === 'EN_COURS' && !f.en_retard ? 'var(--color-primary)' : undefined,
            }}
          >
            <div className="stat-card__value">{indChargement && !indicateurs ? '…' : indicateurs?.en_cours ?? 0}</div>
            <div className="stat-card__title">En cours</div>
            <div className="stat-card__subtitle">Temps réel · Cliquer pour filtrer</div>
          </button>

          <button
            type="button"
            className="stat-card anim-lift"
            aria-pressed={f.en_retard === 'true' && !f.statut}
            onClick={() => basculerKpi('EN_RETARD')}
            style={{
              textAlign: 'left',
              cursor: 'pointer',
              borderColor: f.en_retard === 'true' && !f.statut ? 'var(--color-warning)' : undefined,
            }}
          >
            <div className="stat-card__value" style={{ color: (indicateurs?.en_retard ?? 0) > 0 ? 'var(--color-warning)' : undefined }}>
              {indChargement && !indicateurs ? '…' : indicateurs?.en_retard ?? 0}
            </div>
            <div className="stat-card__title">En retard</div>
            <div className="stat-card__subtitle">Temps réel · Échéance dépassée</div>
          </button>

          <button
            type="button"
            className="stat-card anim-lift"
            aria-pressed={f.statut === 'BLOQUEE' && !f.en_retard}
            onClick={() => basculerKpi('BLOQUEE')}
            style={{
              textAlign: 'left',
              cursor: 'pointer',
              borderColor: f.statut === 'BLOQUEE' && !f.en_retard ? 'var(--color-destructive)' : undefined,
            }}
          >
            <div className="stat-card__value" style={{ color: (indicateurs?.bloquees ?? 0) > 0 ? 'var(--color-destructive)' : undefined }}>
              {indChargement && !indicateurs ? '…' : indicateurs?.bloquees ?? 0}
            </div>
            <div className="stat-card__title">Bloquées</div>
            <div className="stat-card__subtitle">Temps réel · Intervention RH requise</div>
          </button>
        </div>

        {/* Délais moyens par étape (dépendent de du/au) */}
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 12, marginBottom: 12 }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1rem' }}>Délais moyens par étape</h3>
              <p className="small muted" style={{ margin: '2px 0 0' }}>
                Calculés sur les tâches traitées pendant la période choisie.
              </p>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'flex-end' }}>
              <ChampDate
                id="ind-du"
                libelle="Période du"
                valeur={periodeInd.du}
                max={periodeInd.au || undefined}
                onChange={(v) => setPeriodeInd((p) => ({ ...p, du: v }))}
              />
              <ChampDate
                id="ind-au"
                libelle="Au"
                valeur={periodeInd.au}
                min={periodeInd.du || undefined}
                onChange={(v) => setPeriodeInd((p) => ({ ...p, au: v }))}
              />
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => setPeriodeInd(periodeParDefaut30Jours())}
              >
                30 derniers jours
              </button>
            </div>
          </div>

          {indChargement && !indicateurs ? (
            <LoadingState label="Calcul des délais…" />
          ) : !indicateurs || indicateurs.par_etape.length === 0 ? (
            <p className="detail-vide">Aucune tâche traitée sur la période sélectionnée.</p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Modèle</th>
                    <th scope="col">Étape</th>
                    <th scope="col">Délai moyen</th>
                    <th scope="col">Tâches traitées</th>
                  </tr>
                </thead>
                <tbody>
                  {indicateurs.par_etape.map((et) => (
                    <tr key={`${et.modele_code}-${et.etape_code}`}>
                      <td><span className="cell-code">{et.modele_code}</span></td>
                      <td>
                        <strong>{et.libelle}</strong>
                        {et.libelle !== et.etape_code ? (
                          <div className="small muted">{et.etape_code}</div>
                        ) : null}
                      </td>
                      <td>{formatDelaiHeures(et.delai_moyen_heures)}</td>
                      <td>{et.nb}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      {/* Filtres et tableau paginé des instances */}
      <BarreFiltres
        id="wf-instances"
        nbResultats={elements.length}
        nbTotal={donnees?.total}
        nbActifs={nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="inst-objet-type"
          libelle="Type d’objet"
          valeur={f.objet_type}
          options={optionsTypes}
          onChange={(v) => filtres.definir({ objet_type: v, page: '0' })}
        />
        <ChampSelect
          id="inst-statut"
          libelle="Statut"
          valeur={f.statut}
          options={optionsStatuts}
          onChange={(v) => filtres.definir({ statut: v, page: '0' })}
        />
        <div>
          <label className="field-label" htmlFor="inst-etape">Étape (code)</label>
          <input
            id="inst-etape"
            type="text"
            className="field-input"
            placeholder="ex. VALIDATION_RH"
            value={etapeSaisie}
            onChange={(e) => setEtapeSaisie(e.target.value)}
          />
        </div>
        <FiltreCollaborateur
          id="inst-demandeur"
          libelle="Demandeur"
          collaborateurs={collaborateurs}
          chargement={collabChargement}
          valeur={f.demandeur_id}
          onChange={(v) => filtres.definir({ demandeur_id: v, page: '0' })}
        />
        <FiltreCollaborateur
          id="inst-intervenant"
          libelle="Intervenant"
          collaborateurs={collaborateurs}
          chargement={collabChargement}
          valeur={f.intervenant_id}
          onChange={(v) => filtres.definir({ intervenant_id: v, page: '0' })}
        />
        <ChampDate
          id="inst-du"
          libelle="Démarré du"
          valeur={f.du}
          max={f.au || undefined}
          onChange={(v) => filtres.definir({ du: v, page: '0' })}
        />
        <ChampDate
          id="inst-au"
          libelle="Au"
          valeur={f.au}
          min={f.du || undefined}
          onChange={(v) => filtres.definir({ au: v, page: '0' })}
        />
        <label className="checkbox-row" style={{ paddingBottom: 8 }}>
          <input
            type="checkbox"
            checked={f.en_retard === 'true'}
            onChange={(e) => filtres.definir({ en_retard: e.target.checked ? 'true' : '', page: '0' })}
          />
          <span>En retard uniquement</span>
        </label>
      </BarreFiltres>

      {erreur ? (
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void chargerInstances()}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {chargement ? <LoadingState /> : null}

      {!chargement && !erreur && elements.length === 0 ? (
        nbActifs > 0 ? (
          <AucunResultat onReinitialiser={filtres.reinitialiser} />
        ) : (
          <EmptyState title="Aucune instance de workflow">
            Les demandes lancées sur un circuit paramétré apparaîtront ici.
          </EmptyState>
        )
      ) : null}

      {!chargement && elements.length > 0 ? (
        <div className="card">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Objet</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Étape en cours</th>
                  <th scope="col">Échéance</th>
                  <th scope="col">Démarrée le</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {elements.map((inst) => (
                  <tr key={inst.id}>
                    <td>
                      <strong>{inst.objet_libelle}</strong>
                      {inst.objet_numero ? <div className="small muted">{inst.objet_numero}</div> : null}
                      <div className="small muted">{inst.modele_code}</div>
                    </td>
                    <td>
                      <DetailLink type="collaborateur" id={inst.demandeur_id} libelle={inst.demandeur_nom} />
                    </td>
                    <td>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                        <span className={`status status--${toneStatutInstance(inst.statut)}`}>
                          {libelleStatutInstance(inst.statut)}
                        </span>
                        {inst.en_retard ? <span className="status status--danger">En retard</span> : null}
                      </div>
                    </td>
                    <td>{inst.etape_courante_libelle ?? TIRET}</td>
                    <td>
                      {inst.echeance_le ? (
                        <span className={inst.en_retard ? 'field-error' : undefined} style={{ margin: 0 }}>
                          {formatDateHeure(inst.echeance_le)}
                        </span>
                      ) : (
                        TIRET
                      )}
                    </td>
                    <td>
                      {formatDateHeure(inst.demarre_le)}
                      {inst.termine_le ? (
                        <div className="small muted">Terminée : {formatDateHeure(inst.termine_le)}</div>
                      ) : null}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        onClick={() => setInstanceOuverteId(inst.id)}
                      >
                        Détail
                      </button>
                    </td>
                  </tr>
                ))}
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

      {instanceOuverteId ? (
        <InstanceDetailModal
          instanceId={instanceOuverteId}
          lectureSeule={lectureSeule}
          catalogue={catalogue}
          collaborateurs={collaborateurs}
          collabChargement={collabChargement}
          onClose={() => setInstanceOuverteId(null)}
          onModifie={() => {
            void chargerInstances();
            void chargerIndicateurs();
          }}
        />
      ) : null}
    </div>
  );
}

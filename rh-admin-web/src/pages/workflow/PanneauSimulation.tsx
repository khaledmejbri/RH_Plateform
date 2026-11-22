import { useEffect, useMemo, useRef, useState } from 'react';
import { getTousCollaborateurs, type CollaborateurRow } from '../../api/rhClient';
import {
  messageErreurWorkflow,
  simulerVersionWorkflow,
  type CatalogueWorkflow,
  type SimulationResponse,
} from '../../api/workflowApi';
import { SaisieDate } from '../../components/ChampsFr';
import FiltreCollaborateur from '../../components/FiltreCollaborateur';
import { ErrorAlert, LoadingState } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import { libelleStatutEtapeSimulee } from '../../workflow/libelles';
import { optionsVariableListe, referentielPourVariable } from '../../workflow/valeursListes';

type Props = {
  /** Vrai si l'éditeur contient des modifications non enregistrées : la simulation porte sur la dernière version enregistrée. */
  modifie?: boolean;
  versionId: string;
  objetType: string;
  catalogue: CatalogueWorkflow;
};

export default function PanneauSimulation({ modifie, versionId, objetType, catalogue }: Props) {
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [collabChargement, setCollabChargement] = useState(true);
  const [demandeurId, setDemandeurId] = useState('');
  const [valeursVars, setValeursVars] = useState<Record<string, unknown>>({});
  const [simulation, setSimulation] = useState<SimulationResponse | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const variablesType = useMemo(() => {
    const t = catalogue.types.find((x) => x.objet_type === objetType);
    return t?.variables ?? [];
  }, [catalogue, objetType]);

  // Le panneau est recréé à chaque changement de version (clé) : un résultat tardif d'une autre version est ignoré.
  const actif = useRef(true);
  useEffect(() => {
    actif.current = true;
    return () => {
      actif.current = false;
    };
  }, []);

  useEffect(() => {
    let annule = false;
    setCollabChargement(true);
    getTousCollaborateurs()
      .then((list) => {
        if (!annule) setCollaborateurs(list.filter((c) => c.statut === 'ACTIF'));
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
  }, []);

  async function lancerSimulation(e: React.FormEvent) {
    e.preventDefault();
    if (!demandeurId) {
      setErreur('Sélectionnez un demandeur pour simuler le circuit.');
      return;
    }
    setEnCours(true);
    setErreur(null);
    try {
      const varsPropres: Record<string, unknown> = {};
      for (const v of variablesType) {
        const val = valeursVars[v.nom];
        if (val === undefined || val === '') continue;
        if (v.type === 'ENTIER' || v.type === 'DECIMAL') {
          const n = Number(String(val).replace(',', '.'));
          if (Number.isFinite(n)) varsPropres[v.nom] = n;
        } else if (v.type === 'BOOLEEN') {
          varsPropres[v.nom] = val === true || val === 'true';
        } else {
          varsPropres[v.nom] = val;
        }
      }
      const res = await simulerVersionWorkflow(versionId, {
        demandeur_id: demandeurId,
        beneficiaires: [],
        variables: varsPropres,
      });
      if (actif.current) setSimulation(res);
    } catch (err) {
      if (actif.current) {
        setErreur(messageErreurWorkflow(err));
        setSimulation(null);
      }
    } finally {
      if (actif.current) setEnCours(false);
    }
  }

  return (
    <div className="card">
      <h3 style={{ marginBottom: 8 }}>Simuler cette version</h3>
      <p className="small muted" style={{ marginBottom: 12 }}>
        Testez le déroulement des étapes, les intervenants résolus et les échéances sans créer de vraie demande.
      </p>

      {modifie ? (
        <div className="alert alert--warning" role="status" style={{ marginBottom: 12 }}>
          La simulation porte sur la dernière version enregistrée : enregistrez d’abord vos modifications.
        </div>
      ) : null}

      <form onSubmit={(e) => void lancerSimulation(e)}>
        {erreur ? <ErrorAlert>{erreur}</ErrorAlert> : null}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginTop: 8 }}>
          <FiltreCollaborateur
            id="sim-demandeur"
            libelle="Demandeur simulé"
            collaborateurs={collaborateurs}
            chargement={collabChargement}
            valeur={demandeurId}
            onChange={setDemandeurId}
          />
          {variablesType.map((v) => {
            const val = valeursVars[v.nom];
            const ref = v.type === 'LISTE' ? referentielPourVariable(v.nom) : null;
            const opts = ref ? optionsVariableListe(v.nom) : [];
            return (
              <div key={v.nom}>
                <label className="field-label" htmlFor={`sim-var-${v.nom}`}>{v.libelle}</label>
                {v.type === 'BOOLEEN' ? (
                  <select
                    id={`sim-var-${v.nom}`}
                    className="field-input"
                    value={val === undefined ? '' : String(val)}
                    onChange={(e) =>
                      setValeursVars((prev) => ({
                        ...prev,
                        [v.nom]: e.target.value === '' ? undefined : e.target.value === 'true',
                      }))
                    }
                  >
                    <option value="">Non renseigné</option>
                    <option value="true">Oui</option>
                    <option value="false">Non</option>
                  </select>
                ) : v.type === 'DATE' ? (
                  <SaisieDate
                    id={`sim-var-${v.nom}`}
                    valeur={typeof val === 'string' ? val : ''}
                    onChange={(iso) => setValeursVars((prev) => ({ ...prev, [v.nom]: iso }))}
                  />
                ) : opts.length > 0 ? (
                  <select
                    id={`sim-var-${v.nom}`}
                    className="field-input"
                    value={String(val ?? '')}
                    onChange={(e) => setValeursVars((prev) => ({ ...prev, [v.nom]: e.target.value }))}
                  >
                    <option value="">Non renseigné</option>
                    {opts.map((o) => (
                      <option key={o.code} value={o.code}>{o.libelle}</option>
                    ))}
                  </select>
                ) : (
                  <input
                    id={`sim-var-${v.nom}`}
                    className="field-input"
                    placeholder={v.type === 'DECIMAL' ? 'ex. 12,5' : v.type === 'ENTIER' ? 'ex. 5' : ''}
                    value={String(val ?? '')}
                    onChange={(e) => setValeursVars((prev) => ({ ...prev, [v.nom]: e.target.value }))}
                  />
                )}
              </div>
            );
          })}
        </div>

        <div style={{ marginTop: 14 }}>
          <button type="submit" className="btn btn--primary" disabled={enCours || Boolean(modifie)}>
            {enCours ? 'Simulation…' : 'Lancer la simulation'}
          </button>
        </div>
      </form>

      {enCours ? <LoadingState label="Calcul de la simulation…" /> : null}

      {simulation && !enCours ? (
        <div style={{ marginTop: 18 }}>
          <h4 style={{ marginBottom: 8 }}>Résultat étape par étape</h4>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Étape</th>
                  <th>Statut simulé</th>
                  <th>Raison</th>
                  <th>Candidats résolus</th>
                  <th>Échéance simulée</th>
                </tr>
              </thead>
              <tbody>
                {simulation.etapes.map((et) => (
                  <tr key={et.code}>
                    <td>
                      <strong>{et.libelle}</strong>
                    </td>
                    <td>
                      <span
                        className={`status status--${
                          et.statut === 'ACTIVEE'
                            ? 'success'
                            : et.statut === 'BLOQUEE'
                              ? 'danger'
                              : 'neutral'
                        }`}
                      >
                        {libelleStatutEtapeSimulee(et.statut)}
                      </span>
                    </td>
                    <td>{et.raison ?? '—'}</td>
                    <td>
                      {et.candidats && et.candidats.length > 0
                        ? et.candidats.map((c, idx) => (
                            <div key={idx} className="small">
                              {c.nom}
                              {c.intitule ? ` (${c.intitule})` : ''}
                            </div>
                          ))
                        : '—'}
                    </td>
                    <td>{et.echeance_simulee ? formatDateHeure(et.echeance_simulee) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </div>
  );
}

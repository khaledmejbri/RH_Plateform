import { useMemo, useState } from 'react';
import type { CollaborateurRow } from '../../api/rhClient';
import {
  creerDelegationAdmin,
  messageErreurWorkflow,
  type CatalogueWorkflow,
  type DelegationAdmin,
} from '../../api/workflowApi';
import { SaisieDate } from '../../components/ChampsFr';
import FiltreCollaborateur from '../../components/FiltreCollaborateur';
import { ErrorAlert } from '../../components/ui';
import { jourIso } from '../../utils/format';
import {
  DELEGATION_DUREE_MAX_JOURS,
  DELEGATION_MOTIF_MAX,
  diffJours,
  validerFormulaireDelegation,
  type ValidationDelegation,
} from '../../workflow/supervision';

type Props = {
  idPrefixe: string;
  collaborateurs: ReadonlyArray<CollaborateurRow>;
  collabChargement: boolean;
  catalogue: CatalogueWorkflow | null;
  delegantIdInitial?: string;
  onCree: (delegation: DelegationAdmin) => void;
  onAnnulerForm: () => void;
};

export default function DelegationFormCard({
  idPrefixe,
  collaborateurs,
  collabChargement,
  catalogue,
  delegantIdInitial = '',
  onCree,
  onAnnulerForm,
}: Props) {
  const aujourdhui = jourIso();
  const [delegantId, setDelegantId] = useState(delegantIdInitial);
  const [delegueId, setDelegueId] = useState('');
  const [du, setDu] = useState(aujourdhui);
  const [au, setAu] = useState('');
  const [modeTypes, setModeTypes] = useState<'TOUS' | 'LISTE'>('TOUS');
  const [typesChoisis, setTypesChoisis] = useState<string[]>([]);
  const [motif, setMotif] = useState('');

  const [envoi, setEnvoi] = useState(false);
  const [erreurServeur, setErreurServeur] = useState<string | null>(null);
  const [erreursChamps, setErreursChamps] = useState<ValidationDelegation['erreurs']>({});

  const delegantsEligibles = useMemo(
    () =>
      collaborateurs.filter(
        (c) => c.statut?.toUpperCase() === 'ACTIF' || (delegantIdInitial && c.identifiant === delegantIdInitial),
      ),
    [collaborateurs, delegantIdInitial],
  );

  const deleguesEligibles = useMemo(
    () =>
      collaborateurs.filter(
        (c) => c.statut?.toUpperCase() === 'ACTIF' && c.identifiant !== delegantId,
      ),
    [collaborateurs, delegantId],
  );

  const dureeJours = du && au ? diffJours(du, au) : null;

  function basculerTypeObjet(code: string) {
    setTypesChoisis((prev) => (prev.includes(code) ? prev.filter((x) => x !== code) : [...prev, code]));
    setErreursChamps((prev) => ({ ...prev, objet_types: undefined }));
  }

  async function soumettre(e: React.FormEvent) {
    e.preventDefault();
    setErreurServeur(null);
    const validation = validerFormulaireDelegation(
      {
        delegant_id: delegantId,
        delegue_id: delegueId,
        du,
        au,
        motif,
        objet_types_selection_mode: modeTypes,
        objet_types: typesChoisis,
      },
      jourIso(),
    );
    setErreursChamps(validation.erreurs);
    if (!validation.valide) return;

    setEnvoi(true);
    try {
      const cree = await creerDelegationAdmin({
        delegant_id: delegantId,
        delegue_id: delegueId,
        du,
        au,
        objet_types: modeTypes === 'TOUS' ? null : typesChoisis,
        motif: motif.trim() || null,
      });
      onCree(cree);
    } catch (err) {
      // Conserve l'ensemble de la saisie (409 DELEGATION_CHEVAUCHEMENT / 422 DELEGATION_INVALIDE).
      setErreurServeur(messageErreurWorkflow(err));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: 16 }} onSubmit={(e) => void soumettre(e)} noValidate>
      <h3 style={{ marginTop: 0, marginBottom: 12 }}>Créer une délégation</h3>
      {erreurServeur ? <ErrorAlert>{erreurServeur}</ErrorAlert> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginTop: 8 }}>
        <div>
          <FiltreCollaborateur
            id={`${idPrefixe}-delegant`}
            libelle="Délégant (titulaire)"
            collaborateurs={delegantsEligibles}
            chargement={collabChargement}
            valeur={delegantId}
            onChange={(v) => {
              setDelegantId(v);
              setErreursChamps((prev) => ({ ...prev, delegant_id: undefined }));
            }}
          />
          {erreursChamps.delegant_id ? <p className="field-error">{erreursChamps.delegant_id}</p> : null}
        </div>

        <div>
          <FiltreCollaborateur
            id={`${idPrefixe}-delegue`}
            libelle="Délégué (remplaçant)"
            collaborateurs={deleguesEligibles}
            chargement={collabChargement}
            valeur={delegueId}
            onChange={(v) => {
              setDelegueId(v);
              setErreursChamps((prev) => ({ ...prev, delegue_id: undefined }));
            }}
          />
          {erreursChamps.delegue_id ? <p className="field-error">{erreursChamps.delegue_id}</p> : null}
        </div>

        <div>
          <label className="field-label" htmlFor={`${idPrefixe}-du`}>Du</label>
          <SaisieDate
            id={`${idPrefixe}-du`}
            valeur={du}
            min={aujourdhui}
            disabled={envoi}
            onChange={(v) => {
              setDu(v);
              setErreursChamps((prev) => ({ ...prev, du: undefined, au: undefined }));
            }}
          />
          {erreursChamps.du ? <p className="field-error">{erreursChamps.du}</p> : null}
        </div>

        <div>
          <label className="field-label" htmlFor={`${idPrefixe}-au`}>Au (max {DELEGATION_DUREE_MAX_JOURS} jours)</label>
          <SaisieDate
            id={`${idPrefixe}-au`}
            valeur={au}
            min={du || aujourdhui}
            disabled={envoi}
            onChange={(v) => {
              setAu(v);
              setErreursChamps((prev) => ({ ...prev, au: undefined }));
            }}
          />
          {erreursChamps.au ? (
            <p className="field-error">{erreursChamps.au}</p>
          ) : dureeJours !== null && dureeJours >= 0 ? (
            <p className="small muted" style={{ margin: '4px 0 0' }}>
              Écart : {dureeJours} jour{dureeJours > 1 ? 's' : ''} (maximum {DELEGATION_DUREE_MAX_JOURS} jours)
            </p>
          ) : null}
        </div>
      </div>

      <fieldset style={{ border: '1px solid var(--border)', borderRadius: 8, padding: '10px 12px', marginTop: 12 }}>
        <legend className="field-label" style={{ marginBottom: 4, padding: '0 6px' }}>Types de demande couverts</legend>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, marginBottom: modeTypes === 'LISTE' ? 10 : 0 }}>
          <label className="checkbox-row">
            <input
              type="radio"
              name={`${idPrefixe}-mode-types`}
              checked={modeTypes === 'TOUS'}
              disabled={envoi}
              onChange={() => {
                setModeTypes('TOUS');
                setErreursChamps((prev) => ({ ...prev, objet_types: undefined }));
              }}
            />
            <span>Tous les types de demande</span>
          </label>
          <label className="checkbox-row">
            <input
              type="radio"
              name={`${idPrefixe}-mode-types`}
              checked={modeTypes === 'LISTE'}
              disabled={envoi}
              onChange={() => setModeTypes('LISTE')}
            />
            <span>Types sélectionnés uniquement</span>
          </label>
        </div>

        {modeTypes === 'LISTE' ? (
          catalogue && catalogue.types.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 16px' }}>
              {catalogue.types.map((t) => (
                <label key={t.objet_type} className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={typesChoisis.includes(t.objet_type)}
                    disabled={envoi}
                    onChange={() => basculerTypeObjet(t.objet_type)}
                  />
                  <span>{t.libelle}</span>
                </label>
              ))}
            </div>
          ) : (
            <p className="small muted" style={{ margin: 0 }}>Catalogue des types indisponible.</p>
          )
        ) : null}
        {erreursChamps.objet_types ? <p className="field-error">{erreursChamps.objet_types}</p> : null}
      </fieldset>

      <div style={{ marginTop: 12 }}>
        <label className="field-label" htmlFor={`${idPrefixe}-motif`}>
          Motif (facultatif, {DELEGATION_MOTIF_MAX} caractères max)
        </label>
        <textarea
          id={`${idPrefixe}-motif`}
          className="field-input field-input--area"
          rows={2}
          maxLength={DELEGATION_MOTIF_MAX}
          disabled={envoi}
          placeholder="Ex. Absence planifiée, congé, formation…"
          value={motif}
          onChange={(e) => {
            setMotif(e.target.value);
            setErreursChamps((prev) => ({ ...prev, motif: undefined }));
          }}
        />
        <p className={`small ${erreursChamps.motif ? 'field-error' : 'muted'}`} style={{ margin: '4px 0 0' }}>
          {motif.trim().length} / {DELEGATION_MOTIF_MAX} caractères
          {erreursChamps.motif ? ` — ${erreursChamps.motif}` : ''}
        </p>
      </div>

      <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
        <button type="submit" className="btn btn--primary" disabled={envoi} aria-busy={envoi}>
          {envoi ? 'Enregistrement…' : 'Enregistrer la délégation'}
        </button>
        <button type="button" className="btn btn--ghost" disabled={envoi} onClick={onAnnulerForm}>
          Annuler
        </button>
      </div>
    </form>
  );
}

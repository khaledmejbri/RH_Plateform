import { useMemo, useState } from 'react';
import type {
  CatalogueWorkflow,
  ConditionFeuille,
  ConditionWorkflow,
  VariableCatalogue,
} from '../../api/workflowApi';
import { SaisieDate } from '../../components/ChampsFr';
import {
  compterFeuillesCondition,
  estEt,
  estNon,
  estOu,
  libelleOperateur,
  LIMITE_FEUILLES_CONDITION,
  LIMITE_PROFONDEUR_CONDITION,
  operateurSansValeur,
  profondeurCondition,
  resumeCondition,
} from '../../workflow/conditions';
import { estCodeListeValide, optionsVariableListe, referentielPourVariable } from '../../workflow/valeursListes';

type Props = {
  valeur: ConditionWorkflow | null | undefined;
  catalogue: CatalogueWorkflow;
  objetType: string;
  disabled?: boolean;
  onChange: (nouvelle: ConditionWorkflow | null) => void;
};

function toutesVariables(catalogue: CatalogueWorkflow, objetType: string): VariableCatalogue[] {
  const type = catalogue.types.find((t) => t.objet_type === objetType);
  return [...(type?.variables ?? []), ...(catalogue.variables_communes ?? [])];
}

function feuilleParDefaut(variables: VariableCatalogue[], catalogue: CatalogueWorkflow): ConditionFeuille {
  const v = variables[0];
  const ops = v ? catalogue.operateurs[v.type] ?? ['egal'] : ['egal'];
  return { champ: v?.nom ?? '', op: ops[0] ?? 'egal', valeur: v?.type === 'BOOLEEN' ? true : '' };
}

export default function ConstructeurCondition({ valeur, catalogue, objetType, disabled, onChange }: Props) {
  const variables = useMemo(() => toutesVariables(catalogue, objetType), [catalogue, objetType]);
  const libelles = useMemo(() => {
    const map: Record<string, string> = {};
    for (const v of variables) {
      if (v.type === 'LISTE') {
        for (const opt of optionsVariableListe(v.nom)) {
          map[opt.code] = opt.libelle;
        }
      }
    }
    return map;
  }, [variables]);

  const nbFeuilles = compterFeuillesCondition(valeur);
  const profondeur = profondeurCondition(valeur);

  return (
    <div>
      <div
        className="alert alert--info"
        role="status"
        style={{ marginBottom: 12, fontSize: '0.9rem' }}
      >
        <strong>Aperçu :</strong> {resumeCondition(valeur, catalogue, libelles, objetType)}
      </div>

      {!valeur ? (
        !disabled ? (
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            onClick={() => onChange(feuilleParDefaut(variables, catalogue))}
          >
            Ajouter une condition
          </button>
        ) : null
      ) : (
        <div>
          <NoeudCondition
            noeud={valeur}
            niveau={1}
            nbFeuillesTotal={nbFeuilles}
            variables={variables}
            catalogue={catalogue}
            disabled={disabled}
            onChange={(n) => onChange(n)}
            onSupprimer={() => onChange(null)}
          />
          <p className="small muted" style={{ marginTop: 8 }}>
            Feuilles : {nbFeuilles} / {LIMITE_FEUILLES_CONDITION} · Profondeur : {profondeur} / {LIMITE_PROFONDEUR_CONDITION}
          </p>
        </div>
      )}
    </div>
  );
}

function NoeudCondition({
  noeud,
  niveau,
  nbFeuillesTotal,
  variables,
  catalogue,
  disabled,
  onChange,
  onSupprimer,
}: {
  noeud: ConditionWorkflow;
  niveau: number;
  nbFeuillesTotal: number;
  variables: VariableCatalogue[];
  catalogue: CatalogueWorkflow;
  disabled?: boolean;
  onChange: (n: ConditionWorkflow) => void;
  onSupprimer: () => void;
}) {
  const peutAjouterFeuille = nbFeuillesTotal < LIMITE_FEUILLES_CONDITION;
  const peutImbriquer = niveau + 1 < LIMITE_PROFONDEUR_CONDITION && peutAjouterFeuille;
  const raisonFeuille = !peutAjouterFeuille
    ? `Limite de ${LIMITE_FEUILLES_CONDITION} conditions atteinte.`
    : undefined;
  const raisonGroupe = !peutImbriquer
    ? niveau + 1 >= LIMITE_PROFONDEUR_CONDITION
      ? `Profondeur maximale (${LIMITE_PROFONDEUR_CONDITION}) atteinte.`
      : raisonFeuille
    : undefined;

  if (estEt(noeud) || estOu(noeud)) {
    const estGroupeEt = estEt(noeud);
    const enfants = estGroupeEt ? noeud.et : noeud.ou;
    const majEnfants = (liste: ConditionWorkflow[]) =>
      onChange(estGroupeEt ? { et: liste } : { ou: liste });

    return (
      <div
        style={{
          border: '1px solid var(--border, #d8dee9)',
          borderRadius: 8,
          padding: 10,
          marginBottom: 8,
          background: 'var(--bg-subtle, #f8fafc)',
        }}
      >
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8, flexWrap: 'wrap' }}>
          <select
            aria-label="Opérateur logique du groupe"
            className="field-input"
            style={{ width: 'auto' }}
            disabled={disabled}
            value={estGroupeEt ? 'et' : 'ou'}
            onChange={(e) => onChange(e.target.value === 'et' ? { et: enfants } : { ou: enfants })}
          >
            <option value="et">Toutes les conditions (ET)</option>
            <option value="ou">Au moins une condition (OU)</option>
          </select>
          {!disabled ? (
            <>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={!peutAjouterFeuille}
                title={raisonFeuille}
                onClick={() => majEnfants([...enfants, feuilleParDefaut(variables, catalogue)])}
              >
                + Condition
              </button>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={!peutImbriquer}
                title={raisonGroupe}
                onClick={() => majEnfants([...enfants, { et: [feuilleParDefaut(variables, catalogue)] }])}
              >
                + Sous-groupe
              </button>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={niveau >= LIMITE_PROFONDEUR_CONDITION}
                onClick={() => onChange({ non: noeud })}
              >
                Nier (NON)
              </button>
              <button type="button" className="btn btn--ghost btn--sm" onClick={onSupprimer}>
                Retirer
              </button>
            </>
          ) : null}
        </div>
        {enfants.map((enf, idx) => (
          <NoeudCondition
            key={idx}
            noeud={enf}
            niveau={niveau + 1}
            nbFeuillesTotal={nbFeuillesTotal}
            variables={variables}
            catalogue={catalogue}
            disabled={disabled}
            onChange={(maj) => majEnfants(enfants.map((x, i) => (i === idx ? maj : x)))}
            onSupprimer={() => {
              const reste = enfants.filter((_, i) => i !== idx);
              if (reste.length === 0) onSupprimer();
              else majEnfants(reste);
            }}
          />
        ))}
      </div>
    );
  }

  if (estNon(noeud)) {
    return (
      <div
        style={{
          border: '1px dashed var(--border, #d8dee9)',
          borderRadius: 8,
          padding: 10,
          marginBottom: 8,
        }}
      >
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
          <strong>NON (négation)</strong>
          {!disabled ? (
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => onChange(noeud.non)}>
              Retirer la négation
            </button>
          ) : null}
        </div>
        <NoeudCondition
          noeud={noeud.non}
          niveau={niveau + 1}
          nbFeuillesTotal={nbFeuillesTotal}
          variables={variables}
          catalogue={catalogue}
          disabled={disabled}
          onChange={(maj) => onChange({ non: maj })}
          onSupprimer={onSupprimer}
        />
      </div>
    );
  }

  // Feuille
  const variable = variables.find((v) => v.nom === noeud.champ);
  const ops = variable ? catalogue.operateurs[variable.type] ?? ['egal'] : ['egal'];

  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
      <select
        aria-label="Variable"
        className="field-input"
        style={{ width: 'auto', minWidth: 180 }}
        disabled={disabled}
        value={noeud.champ}
        onChange={(e) => {
          const nv = variables.find((v) => v.nom === e.target.value);
          const nOps = nv ? catalogue.operateurs[nv.type] ?? ['egal'] : ['egal'];
          const defVal = nv?.type === 'BOOLEEN' ? true : nOps[0] === 'dans' || nOps[0] === 'hors' ? [] : '';
          onChange({ champ: e.target.value, op: nOps[0] ?? 'egal', valeur: defVal });
        }}
      >
        {variables.map((v) => (
          <option key={v.nom} value={v.nom}>{v.libelle}</option>
        ))}
      </select>

      <select
        aria-label="Opérateur"
        className="field-input"
        style={{ width: 'auto' }}
        disabled={disabled}
        value={noeud.op}
        onChange={(e) => {
          const op = e.target.value;
          if (operateurSansValeur(op)) {
            onChange({ champ: noeud.champ, op });
          } else if (op === 'dans' || op === 'hors') {
            const cur = Array.isArray(noeud.valeur)
              ? noeud.valeur
              : noeud.valeur
                ? [noeud.valeur]
                : [];
            onChange({ champ: noeud.champ, op, valeur: cur });
          } else {
            onChange({ champ: noeud.champ, op, valeur: Array.isArray(noeud.valeur) ? noeud.valeur[0] ?? '' : noeud.valeur ?? '' });
          }
        }}
      >
        {ops.map((op) => (
          <option key={op} value={op}>{libelleOperateur(op)}</option>
        ))}
      </select>

      {!operateurSansValeur(noeud.op) ? (
        <ChampValeurCondition
          variable={variable}
          op={noeud.op}
          valeur={noeud.valeur}
          disabled={disabled}
          onChange={(val) => onChange({ ...noeud, valeur: val })}
        />
      ) : null}

      {!disabled ? (
        <>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={!peutImbriquer}
            title={raisonGroupe}
            onClick={() => onChange({ et: [noeud, feuilleParDefaut(variables, catalogue)] })}
          >
            + ET
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={niveau >= LIMITE_PROFONDEUR_CONDITION}
            onClick={() => onChange({ non: noeud })}
          >
            NON
          </button>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onSupprimer}>
            Supprimer
          </button>
        </>
      ) : null}
    </div>
  );
}

function ChampValeurCondition({
  variable,
  op,
  valeur,
  disabled,
  onChange,
}: {
  variable: VariableCatalogue | undefined;
  op: string;
  valeur: unknown;
  disabled?: boolean;
  onChange: (val: unknown) => void;
}) {
  const [saisieCode, setSaisieCode] = useState('');
  const [erreurCode, setErreurCode] = useState<string | null>(null);
  const estMulti = op === 'dans' || op === 'hors';

  if (!variable) {
    return (
      <input
        className="field-input"
        style={{ width: 160 }}
        disabled={disabled}
        value={String(valeur ?? '')}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }

  if (variable.type === 'BOOLEEN') {
    return (
      <select
        aria-label="Valeur booléenne"
        className="field-input"
        style={{ width: 'auto' }}
        disabled={disabled}
        value={valeur === true || valeur === 'true' ? 'true' : 'false'}
        onChange={(e) => onChange(e.target.value === 'true')}
      >
        <option value="true">Oui</option>
        <option value="false">Non</option>
      </select>
    );
  }

  if (variable.type === 'DATE') {
    return (
      <SaisieDate
        valeur={typeof valeur === 'string' ? valeur : ''}
        disabled={disabled}
        onChange={(iso) => onChange(iso)}
        ariaLabel="Date de condition"
      />
    );
  }

  if (variable.type === 'ENTIER' || variable.type === 'DECIMAL') {
    const texte = typeof valeur === 'number' ? String(valeur).replace('.', ',') : String(valeur ?? '');
    return (
      <input
        aria-label="Valeur numérique"
        className="field-input"
        style={{ width: 120 }}
        disabled={disabled}
        value={texte}
        placeholder={variable.type === 'DECIMAL' ? 'ex. 10,5' : 'ex. 10'}
        onChange={(e) => {
          const brut = e.target.value.replace(',', '.');
          const n = Number(brut);
          onChange(brut.trim() !== '' && Number.isFinite(n) ? n : e.target.value);
        }}
      />
    );
  }

  if (variable.type === 'LISTE') {
    const ref = referentielPourVariable(variable.nom);
    const options = ref ? optionsVariableListe(variable.nom) : [];
    const listeCourante = Array.isArray(valeur)
      ? valeur.map(String)
      : valeur
        ? [String(valeur)]
        : [];

    if (options.length > 0) {
      if (!estMulti) {
        return (
          <select
            aria-label="Valeur de liste"
            className="field-input"
            style={{ width: 'auto' }}
            disabled={disabled}
            value={listeCourante[0] ?? ''}
            onChange={(e) => onChange(e.target.value)}
          >
            <option value="">Choisir…</option>
            {options.map((o) => (
              <option key={o.code} value={o.code}>{o.libelle}</option>
            ))}
          </select>
        );
      }
      return (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {options.map((o) => {
            const coche = listeCourante.includes(o.code);
            return (
              <label key={o.code} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                <input
                  type="checkbox"
                  checked={coche}
                  disabled={disabled}
                  onChange={() => {
                    const suivant = coche
                      ? listeCourante.filter((x) => x !== o.code)
                      : [...listeCourante, o.code];
                    onChange(suivant);
                  }}
                />
                <span className="small">{o.libelle}</span>
              </label>
            );
          })}
        </div>
      );
    }

    // Variable LISTE sans référentiel connu : saisie libre contrôlée par ^[A-Z0-9_]{1,40}$ (D-W7-07)
    if (!estMulti) {
      const val = listeCourante[0] ?? '';
      return (
        <div>
          <input
            aria-label="Code technique en majuscules"
            className="field-input"
            style={{ width: 180 }}
            disabled={disabled}
            placeholder="CODE_MAJUSCULES"
            value={val}
            onChange={(e) => onChange(e.target.value.toUpperCase())}
          />
          <span className="small muted" style={{ display: 'block' }}>Code technique en majuscules</span>
          {val && !estCodeListeValide(val) ? (
            <span className="small field-error">Format attendu : majuscules, chiffres ou _ (1 à 40 car.).</span>
          ) : null}
        </div>
      );
    }

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          {listeCourante.map((c) => (
            <span key={c} className="chip chip--neutral">
              {c}
              {!disabled ? (
                <button
                  type="button"
                  aria-label={`Retirer ${c}`}
                  style={{ border: 0, background: 'none', cursor: 'pointer', marginLeft: 4 }}
                  onClick={() => onChange(listeCourante.filter((x) => x !== c))}
                >
                  ×
                </button>
              ) : null}
            </span>
          ))}
          {!disabled ? (
            <>
              <input
                aria-label="Ajouter un code"
                className="field-input"
                style={{ width: 150 }}
                placeholder="CODE_MAJUSCULES"
                value={saisieCode}
                onChange={(e) => {
                  setSaisieCode(e.target.value.toUpperCase());
                  setErreurCode(null);
                }}
              />
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={() => {
                  const code = saisieCode.trim().toUpperCase();
                  if (!estCodeListeValide(code)) {
                    setErreurCode('Code technique en majuscules (A-Z, 0-9, _, 1 à 40 car.).');
                    return;
                  }
                  if (!listeCourante.includes(code)) {
                    onChange([...listeCourante, code]);
                  }
                  setSaisieCode('');
                }}
              >
                Ajouter
              </button>
            </>
          ) : null}
        </div>
        <span className="small muted">Code technique en majuscules</span>
        {erreurCode ? <span className="small field-error">{erreurCode}</span> : null}
      </div>
    );
  }

  return (
    <input
      aria-label="Valeur"
      className="field-input"
      style={{ width: 180 }}
      disabled={disabled}
      value={String(valeur ?? '')}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

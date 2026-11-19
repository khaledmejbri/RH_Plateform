import { useEffect, useState } from 'react';
import { getParametrageImport, type ChampParametre } from '../api/importParametrageApi';
import { nbCriteres, type CriterePerso, type CriteresPerso } from '../api/valeursPersoApi';
import { ChampDate, ChampSelect } from './BarreFiltres';

type Props = {
  idPrefixe: string;
  criteres: CriteresPerso;
  onChange: (criteres: CriteresPerso) => void;
};

const ETATS = [
  { valeur: 'RENSEIGNE', libelle: 'Renseigné' },
  { valeur: 'VIDE', libelle: 'Vide' },
];

/** Filtres sur les champs personnalisés actifs des collaborateurs (V5) : un critère par champ, selon son type. */
export default function FiltresChampsPerso({ idPrefixe, criteres, onChange }: Props) {
  const [champs, setChamps] = useState<ChampParametre[] | null>(null);
  const [ouvert, setOuvert] = useState(false);

  useEffect(() => {
    let annule = false;
    getParametrageImport('collaborateurs').then(
      (p) => { if (!annule) setChamps(p.champs.filter((c) => c.personnalise && c.actif !== false).sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0))); },
      () => { if (!annule) setChamps([]); },
    );
    return () => { annule = true; };
  }, []);

  // Rien à filtrer (ou chargement raté) : le bloc n'apparaît pas.
  if (!champs || champs.length === 0) return null;
  const n = nbCriteres(criteres);

  function maj(code: string, patch: CriterePerso) {
    const suivant: CriterePerso = { ...(criteres[code] ?? {}) };
    for (const [k, v] of Object.entries(patch) as Array<[keyof CriterePerso, string]>) {
      if (v) suivant[k] = v; else delete suivant[k];
    }
    // « Renseigné / Vide » est exclusif des autres critères du champ.
    if (patch.etat) { for (const k of Object.keys(suivant) as Array<keyof CriterePerso>) if (k !== 'etat') delete suivant[k]; }
    else if (Object.keys(patch).some((k) => k !== 'etat' && patch[k as keyof CriterePerso])) delete suivant.etat;
    onChange({ ...criteres, [code]: suivant });
  }

  return (
    <div style={{ flexBasis: '100%' }}>
      <button type="button" className="btn btn--ghost btn--sm" aria-expanded={ouvert} aria-controls={`${idPrefixe}-cp`} onClick={() => setOuvert((o) => !o)}>
        Champs personnalisés{n > 0 ? ` (${n})` : ''} {ouvert ? '▲' : '▼'}
      </button>
      {ouvert ? (
        <div id={`${idPrefixe}-cp`} className="toolbar-filters" style={{ marginTop: 8 }}>
          {champs.map((c) => {
            const v = criteres[c.code] ?? {};
            const id = `${idPrefixe}-cp-${c.code}`;
            const type = c.type_valeur ?? 'TEXTE';
            const exclu = v.etat ? true : false;
            return (
              <fieldset key={c.code} className="filtre-perso">
                <legend className="field-label">{c.libelle}</legend>
                {type === 'TEXTE' ? (
                  <div>
                    <label className="sr-only" htmlFor={id}>{c.libelle} contient</label>
                    <input id={id} className="field-input" placeholder="Contient…" maxLength={100} value={v.contient ?? ''} disabled={exclu}
                      onChange={(e) => maj(c.code, { contient: e.target.value })} />
                  </div>
                ) : null}
                {type === 'NOMBRE' ? (
                  <div className="filtre-perso__paire">
                    <div>
                      <label className="sr-only" htmlFor={`${id}-min`}>{c.libelle} minimum</label>
                      <input id={`${id}-min`} className="field-input" inputMode="decimal" placeholder="Min" value={v.min ?? ''} disabled={exclu}
                        onChange={(e) => maj(c.code, { min: e.target.value })} />
                    </div>
                    <div>
                      <label className="sr-only" htmlFor={`${id}-max`}>{c.libelle} maximum</label>
                      <input id={`${id}-max`} className="field-input" inputMode="decimal" placeholder="Max" value={v.max ?? ''} disabled={exclu}
                        onChange={(e) => maj(c.code, { max: e.target.value })} />
                    </div>
                  </div>
                ) : null}
                {type === 'DATE' ? (
                  <div className="filtre-perso__paire">
                    <ChampDate id={`${id}-du`} libelle="Du" valeur={exclu ? '' : (v.du ?? '')} onChange={(x) => maj(c.code, { du: x })} />
                    <ChampDate id={`${id}-au`} libelle="Au" valeur={exclu ? '' : (v.au ?? '')} onChange={(x) => maj(c.code, { au: x })} />
                  </div>
                ) : null}
                {type === 'LISTE' ? (
                  <div role="group" aria-label={`Valeurs de ${c.libelle}`}>
                    {[...(c.valeurs_liste ?? [])].sort((a, b) => a.ordre - b.ordre).map((o) => {
                      const choisies = (v.valeurs ?? '').split(',').filter(Boolean);
                      return (
                        <label key={o.code} className="import__case" style={{ display: 'block' }}>
                          <input type="checkbox" checked={choisies.includes(o.code)} disabled={exclu}
                            onChange={(e) => maj(c.code, { valeurs: (e.target.checked ? [...choisies, o.code] : choisies.filter((x) => x !== o.code)).join(',') })} />{' '}
                          {o.libelle}{o.actif ? '' : ' (inactive)'}
                        </label>
                      );
                    })}
                  </div>
                ) : null}
                <ChampSelect id={`${id}-etat`} libelle="État" valeur={v.etat ?? ''} options={ETATS} toutesLibelle="Indifférent"
                  onChange={(x) => maj(c.code, { etat: x })} />
              </fieldset>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

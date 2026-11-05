import type { ReactNode } from 'react';
import type { Unite } from '../api/rhClient';
import {
  chaineUnite,
  indexerUnites,
  niveauUnite,
  pluriel,
  type NiveauUnite,
  type SelectionUnite,
} from '../utils/filtres';
import { EmptyState, Icon } from './ui';

export type OptionFiltre = { valeur: string; libelle: string };

/** Sélecteur de filtre avec option « Tous » (valeur vide). */
export function ChampSelect({
  id,
  libelle,
  valeur,
  options,
  onChange,
  toutesLibelle = 'Tous',
  sansTous = false,
}: {
  id: string;
  libelle: string;
  valeur: string;
  options: ReadonlyArray<OptionFiltre>;
  onChange: (v: string) => void;
  toutesLibelle?: string;
  sansTous?: boolean;
}) {
  return (
    <div>
      <label className="field-label" htmlFor={id}>{libelle}</label>
      <select id={id} className="field-input" value={valeur} onChange={(e) => onChange(e.target.value)}>
        {sansTous ? null : <option value="">{toutesLibelle}</option>}
        {options.map((o) => (
          <option key={o.valeur} value={o.valeur}>{o.libelle}</option>
        ))}
      </select>
    </div>
  );
}

/** Champ date de filtre. */
export function ChampDate({
  id,
  libelle,
  valeur,
  onChange,
  min,
  max,
}: {
  id: string;
  libelle: string;
  valeur: string;
  onChange: (v: string) => void;
  min?: string;
  max?: string;
}) {
  return (
    <div>
      <label className="field-label" htmlFor={id}>{libelle}</label>
      <input
        id={id}
        type="date"
        className="field-input"
        value={valeur}
        min={min || undefined}
        max={max || undefined}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

/** Direction / Département / Service en cascade, alimentés par la liste des unités. */
export function FiltresUnite({
  idPrefixe,
  unites,
  selection,
  onChange,
}: {
  idPrefixe: string;
  unites: ReadonlyArray<Unite>;
  selection: SelectionUnite;
  onChange: (s: SelectionUnite) => void;
}) {
  const index = indexerUnites(unites);
  const actives = unites.filter((u) => u.actif !== false);
  const parNiveau = (n: NiveauUnite) => actives.filter((u) => niveauUnite(u) === n);
  const dansChaine = (u: Unite, parentId: string) => !parentId || chaineUnite(index, u.identifiant).has(parentId);
  const opts = (liste: Unite[]): OptionFiltre[] =>
    liste
      .map((u) => ({ valeur: u.identifiant, libelle: u.libelle }))
      .sort((a, b) => a.libelle.localeCompare(b.libelle, 'fr'));

  const directions = opts(parNiveau('direction'));
  const departements = opts(parNiveau('departement').filter((u) => dansChaine(u, selection.direction)));
  const services = opts(
    parNiveau('service').filter(
      (u) => dansChaine(u, selection.direction) && dansChaine(u, selection.departement),
    ),
  );
  if (directions.length + departements.length + services.length === 0) return null;

  return (
    <>
      <ChampSelect
        id={`${idPrefixe}-direction`}
        libelle="Direction"
        valeur={selection.direction}
        options={directions}
        onChange={(v) => onChange({ direction: v, departement: '', service: '' })}
      />
      <ChampSelect
        id={`${idPrefixe}-departement`}
        libelle="Département"
        valeur={selection.departement}
        options={departements}
        onChange={(v) => onChange({ ...selection, departement: v, service: '' })}
      />
      <ChampSelect
        id={`${idPrefixe}-service`}
        libelle="Service"
        valeur={selection.service}
        options={services}
        onChange={(v) => onChange({ ...selection, service: v })}
      />
    </>
  );
}

/**
 * Barre de filtres commune : recherche texte, filtres spécifiques (enfants), compteur,
 * bouton « Réinitialiser ». Les valeurs vivent dans l'URL via `useFiltresUrl`.
 */
export default function BarreFiltres({
  id,
  recherche,
  onRecherche,
  placeholder = 'Rechercher…',
  libelleRecherche = 'Recherche',
  nbResultats,
  nbTotal,
  nbActifs,
  onReinitialiser,
  note,
  children,
}: {
  id: string;
  /** Sans `onRecherche`, le champ de recherche texte n'est pas affiché (filtres serveur seuls). */
  recherche?: string;
  onRecherche?: (v: string) => void;
  placeholder?: string;
  libelleRecherche?: string;
  nbResultats: number;
  nbTotal?: number;
  nbActifs: number;
  onReinitialiser: () => void;
  /** Précision affichée sous le compteur (ex. filtrage limité à la page). */
  note?: string;
  children?: ReactNode;
}) {
  return (
    <div className="barre-filtres" role="search" aria-label="Filtres de la liste">
      <div className="toolbar-filters">
        {onRecherche ? (
          <div className="barre-filtres__recherche">
            <label className="field-label" htmlFor={`${id}-q`}>{libelleRecherche}</label>
            <input
              id={`${id}-q`}
              type="search"
              className="field-input"
              value={recherche ?? ''}
              placeholder={placeholder}
              autoComplete="off"
              onChange={(e) => onRecherche(e.target.value)}
            />
          </div>
        ) : null}
        {children}
        {nbActifs > 0 ? (
          <button type="button" className="btn btn--ghost btn--sm" onClick={onReinitialiser}>
            <Icon name="refresh" size={14} />
            Réinitialiser
          </button>
        ) : null}
      </div>
      <p className="barre-filtres__compteur muted small" aria-live="polite" role="status">
        {pluriel(nbResultats, 'résultat')}
        {nbTotal != null && nbTotal !== nbResultats ? ` sur ${nbTotal}` : ''}
        {note ? ` · ${note}` : ''}
      </p>
    </div>
  );
}

/** État vide quand les filtres ne retournent rien (la liste de départ n'est pas vide). */
export function AucunResultat({ onReinitialiser }: { onReinitialiser?: () => void }) {
  return (
    <EmptyState
      icon="search"
      title="Aucun résultat pour ces filtres"
      action={
        onReinitialiser ? (
          <button type="button" className="btn btn--secondary btn--sm" onClick={onReinitialiser}>
            Réinitialiser les filtres
          </button>
        ) : undefined
      }
    >
      Modifiez la recherche ou retirez un filtre.
    </EmptyState>
  );
}

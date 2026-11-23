import { useId, useMemo, useState } from 'react';
import { nomCollaborateur, type CollaborateurRow } from '../api/rhClient';
import { correspond } from '../utils/filtres';
import { libelleCollaborateur } from './FiltreCollaborateur';

const MAX_RESULTATS_AFFICHES = 50;

/**
 * Sélecteur multi-collaborateurs (recherche par nom, prénom ou matricule + cases à cocher + pastilles).
 * Permet de choisir 1 ou plusieurs collaborateurs pour l'export Excel mensuel de pointage.
 */
export default function SelecteurCollaborateursMulti({
  id,
  collaborateurs,
  valeurs,
  onChange,
  chargement = false,
  disabled = false,
  invalide = false,
  libelle = 'Collaborateurs à exporter (min. 1) *',
  maxSelection = 200,
}: {
  id: string;
  collaborateurs: ReadonlyArray<CollaborateurRow>;
  valeurs: ReadonlyArray<string>;
  onChange: (identifiants: string[]) => void;
  chargement?: boolean;
  disabled?: boolean;
  invalide?: boolean;
  libelle?: string;
  maxSelection?: number;
}) {
  const listeId = useId();
  const [recherche, setRecherche] = useState('');

  const ensembleSelection = useMemo(() => new Set(valeurs), [valeurs]);

  const indexParId = useMemo(
    () => new Map(collaborateurs.map((c) => [c.identifiant, c])),
    [collaborateurs],
  );

  const filtres = useMemo(() => {
    const q = recherche.trim();
    return collaborateurs.filter((c) =>
      correspond([c.prenom, nomCollaborateur(c), c.matricule], q),
    );
  }, [collaborateurs, recherche]);

  const visibles = useMemo(
    () => filtres.slice(0, MAX_RESULTATS_AFFICHES),
    [filtres],
  );

  function basculer(identifiant: string) {
    if (disabled) return;
    if (ensembleSelection.has(identifiant)) {
      onChange(valeurs.filter((x) => x !== identifiant));
    } else if (valeurs.length < maxSelection) {
      onChange([...valeurs, identifiant]);
    }
  }

  function retirer(identifiant: string) {
    if (disabled) return;
    onChange(valeurs.filter((x) => x !== identifiant));
  }

  function toutSelectionnerFiltres() {
    if (disabled) return;
    const prochain = [...valeurs];
    const deja = new Set(prochain);
    for (const c of filtres) {
      if (prochain.length >= maxSelection) break;
      if (!deja.has(c.identifiant)) {
        deja.add(c.identifiant);
        prochain.push(c.identifiant);
      }
    }
    onChange(prochain);
  }

  function toutEffacer() {
    if (disabled) return;
    onChange([]);
  }

  const nbSelectionnes = valeurs.length;

  return (
    <div className="selecteur-collabs-multi">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
        <label className="field-label" htmlFor={id}>
          {libelle}
        </label>
        <span className="muted small" aria-live="polite">
          {nbSelectionnes} collaborateur{nbSelectionnes > 1 ? 's' : ''} sélectionné{nbSelectionnes > 1 ? 's' : ''}
        </span>
      </div>

      {nbSelectionnes > 0 ? (
        <div
          style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 8 }}
          aria-label="Collaborateurs sélectionnés"
        >
          {valeurs.map((cid) => {
            const col = indexParId.get(cid);
            const nom = col ? libelleCollaborateur(col) : 'Collaborateur sélectionné';
            return (
              <span key={cid} className="chip chip--info">
                <span>{nom}</span>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  style={{ minHeight: 20, padding: '0 4px', border: 'none' }}
                  aria-label={`Retirer ${nom}`}
                  disabled={disabled}
                  onClick={() => retirer(cid)}
                >
                  ×
                </button>
              </span>
            );
          })}
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 6 }}>
        <input
          id={id}
          type="search"
          className="field-input"
          style={{ flex: '1 1 220px' }}
          disabled={disabled}
          aria-invalid={invalide || undefined}
          aria-controls={listeId}
          placeholder={chargement ? 'Chargement…' : 'Rechercher par nom, prénom ou matricule…'}
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
        />
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          disabled={disabled || filtres.length === 0 || nbSelectionnes >= maxSelection}
          onClick={toutSelectionnerFiltres}
        >
          Tout sélectionner
        </button>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          disabled={disabled || nbSelectionnes === 0}
          onClick={toutEffacer}
        >
          Tout effacer
        </button>
      </div>

      <div
        id={listeId}
        role="group"
        aria-label="Liste des collaborateurs"
        style={{
          maxHeight: 160,
          overflowY: 'auto',
          border: '1px solid var(--color-border, #d8dee9)',
          borderRadius: 6,
          padding: '6px 8px',
          background: 'var(--color-surface, #fff)',
        }}
      >
        {chargement ? (
          <p className="muted small" style={{ margin: 0 }}>Chargement des collaborateurs…</p>
        ) : collaborateurs.length === 0 ? (
          <p className="muted small" style={{ margin: 0 }}>Aucun collaborateur disponible.</p>
        ) : visibles.length === 0 ? (
          <p className="muted small" style={{ margin: 0 }}>Aucun collaborateur ne correspond à la recherche.</p>
        ) : (
          visibles.map((c) => {
            const coche = ensembleSelection.has(c.identifiant);
            const lib = libelleCollaborateur(c);
            return (
              <label
                key={c.identifiant}
                style={{
                  display: 'flex',
                  gap: 8,
                  alignItems: 'center',
                  padding: '3px 0',
                  cursor: disabled ? 'not-allowed' : 'pointer',
                }}
              >
                <input
                  type="checkbox"
                  checked={coche}
                  disabled={disabled || (!coche && nbSelectionnes >= maxSelection)}
                  onChange={() => basculer(c.identifiant)}
                />
                <span className="small">{lib}</span>
              </label>
            );
          })
        )}
        {filtres.length > MAX_RESULTATS_AFFICHES ? (
          <p className="muted small" style={{ margin: '4px 0 0' }}>
            Affichage des {MAX_RESULTATS_AFFICHES} premiers résultats sur {filtres.length}. Affinez la recherche pour filtrer davantage.
          </p>
        ) : null}
      </div>
    </div>
  );
}

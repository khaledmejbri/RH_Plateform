import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { nomCollaborateur, type CollaborateurRow } from '../api/rhClient';
import { correspond } from '../utils/filtres';

const MAX_SUGGESTIONS = 8;

export function libelleCollaborateur(c: Pick<CollaborateurRow, 'prenom' | 'matricule' | 'nom' | 'name'>): string {
  return `${c.prenom} ${nomCollaborateur(c)}`.trim() + ` (${c.matricule})`;
}

/**
 * Filtre « Collaborateur » : recherche par nom, prénom ou matricule (sans accents), puis choix dans la liste.
 * `valeur` = identifiant de la fiche collaborateur (vide = tous).
 */
export default function FiltreCollaborateur({
  id,
  collaborateurs,
  valeur,
  onChange,
  chargement = false,
  libelle = 'Collaborateur',
}: {
  id: string;
  collaborateurs: ReadonlyArray<CollaborateurRow>;
  valeur: string;
  onChange: (identifiant: string) => void;
  chargement?: boolean;
  libelle?: string;
}) {
  const listeId = useId();
  const choisi = useMemo(() => collaborateurs.find((c) => c.identifiant === valeur) ?? null, [collaborateurs, valeur]);
  const [saisie, setSaisie] = useState('');
  const [ouvert, setOuvert] = useState(false);
  // Vrai quand c'est la frappe qui vient de désélectionner : on garde alors le texte saisi.
  const deselectionParSaisie = useRef(false);

  useEffect(() => {
    if (!choisi && deselectionParSaisie.current) {
      deselectionParSaisie.current = false;
      return;
    }
    setSaisie(choisi ? libelleCollaborateur(choisi) : '');
  }, [choisi]);

  const suggestions = useMemo(() => {
    if (!ouvert) return [];
    const q = choisi && saisie === libelleCollaborateur(choisi) ? '' : saisie;
    return collaborateurs.filter((c) => correspond([c.prenom, nomCollaborateur(c), c.matricule], q)).slice(0, MAX_SUGGESTIONS);
  }, [ouvert, saisie, collaborateurs, choisi]);

  function choisir(c: CollaborateurRow | null) {
    onChange(c ? c.identifiant : '');
    setOuvert(false);
    setSaisie(c ? libelleCollaborateur(c) : '');
  }

  return (
    <div className="filtre-collab">
      <label className="field-label" htmlFor={id}>{libelle}</label>
      <input
        id={id}
        type="search"
        className="field-input"
        role="combobox"
        aria-expanded={ouvert}
        aria-controls={listeId}
        aria-autocomplete="list"
        autoComplete="off"
        placeholder={chargement ? 'Chargement…' : 'Nom ou matricule'}
        value={saisie}
        onFocus={() => setOuvert(true)}
        onBlur={() => window.setTimeout(() => setOuvert(false), 150)}
        onChange={(e) => {
          setSaisie(e.target.value);
          setOuvert(true);
          if (valeur) {
            if (choisi) deselectionParSaisie.current = true;
            onChange('');
          }
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOuvert(false);
          if (e.key === 'Enter' && suggestions.length === 1) {
            e.preventDefault();
            choisir(suggestions[0]);
          }
        }}
      />
      {ouvert ? (
        <ul id={listeId} className="filtre-collab__liste" role="listbox" aria-label="Collaborateurs correspondants">
          {suggestions.length === 0 ? (
            <li className="filtre-collab__vide muted small">Aucun collaborateur trouvé</li>
          ) : (
            suggestions.map((c) => (
              <li key={c.identifiant} role="option" aria-selected={c.identifiant === valeur}>
                <button type="button" className="filtre-collab__option" onMouseDown={(e) => e.preventDefault()} onClick={() => choisir(c)}>
                  {libelleCollaborateur(c)}
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}

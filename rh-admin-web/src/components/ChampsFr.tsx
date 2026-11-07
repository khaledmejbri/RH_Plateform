import { useEffect, useRef, useState } from 'react';

/**
 * Saisie de date jj/mm/aaaa indépendante de la langue du navigateur (D-L01.c).
 * La valeur échangée reste le jour civil `AAAA-MM-JJ` ; chaîne vide si la saisie est incomplète ou invalide.
 */

function isoVersTexte(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
}

function texteVersIso(texte: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texte);
  if (!m) return null;
  const j = +m[1];
  const mo = +m[2];
  const a = +m[3];
  const d = new Date(Date.UTC(a, mo - 1, j));
  if (d.getUTCFullYear() !== a || d.getUTCMonth() !== mo - 1 || d.getUTCDate() !== j) return null;
  return `${m[3]}-${m[2]}-${m[1]}`;
}

/** Insère les « / » au fil de la frappe. */
function masquerDate(brut: string): string {
  const c = brut.replace(/\D/g, '').slice(0, 8);
  if (c.length <= 2) return c;
  if (c.length <= 4) return `${c.slice(0, 2)}/${c.slice(2)}`;
  return `${c.slice(0, 2)}/${c.slice(2, 4)}/${c.slice(4)}`;
}

export function SaisieDate({
  id,
  valeur,
  onChange,
  min,
  max,
  disabled,
  required,
  className = 'field-input',
  ariaLabel,
}: {
  id?: string;
  valeur: string;
  onChange: (iso: string) => void;
  min?: string;
  max?: string;
  disabled?: boolean;
  required?: boolean;
  className?: string;
  ariaLabel?: string;
}) {
  const [texte, setTexte] = useState(isoVersTexte(valeur));
  // Dernière valeur émise vers le parent : un retour identique ne doit pas écraser la frappe en cours.
  const emise = useRef(valeur);
  useEffect(() => {
    if (valeur === emise.current) return;
    emise.current = valeur;
    setTexte(isoVersTexte(valeur));
  }, [valeur]);

  const iso = texteVersIso(texte);
  const horsBornes = !!iso && ((!!min && iso < min) || (!!max && iso > max));
  const invalide = texte.length > 0 && (iso === null || horsBornes);

  return (
    <input
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      className={className}
      placeholder="jj/mm/aaaa"
      maxLength={10}
      value={texte}
      disabled={disabled}
      required={required}
      aria-label={ariaLabel}
      aria-invalid={invalide || undefined}
      onChange={(e) => {
        const t = masquerDate(e.target.value);
        setTexte(t);
        // Le parent ne reçoit que une date complète et valide, ou vide si tout est effacé.
        if (t === '') {
          emise.current = '';
          onChange('');
          return;
        }
        const v = texteVersIso(t);
        if (v && !(min && v < min) && !(max && v > max)) {
          emise.current = v;
          onChange(v);
        } else if (emise.current !== '') {
          // La date n'est plus valide : le parent est vidé, sans écraser le texte en cours de frappe.
          emise.current = '';
          onChange('');
        }
      }}
    />
  );
}

/** Saisie d'heure hh:mm (24 h) indépendante de la langue du navigateur. Valeur `HH:mm`, vide si invalide. */
export function SaisieHeure({
  id,
  valeur,
  onChange,
  disabled,
  className = 'field-input',
  ariaLabel,
}: {
  id?: string;
  valeur: string;
  onChange: (hhmm: string) => void;
  disabled?: boolean;
  className?: string;
  ariaLabel?: string;
}) {
  const normal = (v: string) => v.slice(0, 5);
  const [texte, setTexte] = useState(normal(valeur));
  // Même mécanisme que SaisieDate : une heure invalide reste affichée (et signalée) au lieu d'être effacée.
  const emise = useRef(normal(valeur));
  useEffect(() => {
    if (normal(valeur) === emise.current) return;
    emise.current = normal(valeur);
    setTexte(normal(valeur));
  }, [valeur]);

  const valide = (t: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(t);

  return (
    <input
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      className={className}
      placeholder="hh:mm"
      maxLength={5}
      value={texte}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-invalid={(texte.length > 0 && !valide(texte)) || undefined}
      onChange={(e) => {
        const chiffres = e.target.value.replace(/\D/g, '').slice(0, 4);
        const t = chiffres.length > 2 ? `${chiffres.slice(0, 2)}:${chiffres.slice(2)}` : chiffres;
        setTexte(t);
        const v = valide(t) ? t : '';
        if (v !== emise.current) {
          emise.current = v;
          onChange(v);
        }
      }}
    />
  );
}

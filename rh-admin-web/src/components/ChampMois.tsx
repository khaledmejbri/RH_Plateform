import { useEffect, useMemo, useRef, useState } from 'react';

export const MOIS_FR: ReadonlyArray<{ valeur: string; libelle: string }> = [
  { valeur: '01', libelle: 'Janvier' },
  { valeur: '02', libelle: 'Février' },
  { valeur: '03', libelle: 'Mars' },
  { valeur: '04', libelle: 'Avril' },
  { valeur: '05', libelle: 'Mai' },
  { valeur: '06', libelle: 'Juin' },
  { valeur: '07', libelle: 'Juillet' },
  { valeur: '08', libelle: 'Août' },
  { valeur: '09', libelle: 'Septembre' },
  { valeur: '10', libelle: 'Octobre' },
  { valeur: '11', libelle: 'Novembre' },
  { valeur: '12', libelle: 'Décembre' },
];

const MOIS_RE = /^(\d{4})-(0[1-9]|1[0-2])$/;

/**
 * Calcule le cycle mensuel de pointage (du 27 du mois précédent au 26 du mois sélectionné)
 * au format ISO `AAAA-MM-JJ`, ou `null` si `mois` n'est pas au format `AAAA-MM` valide.
 */
export function cycleMensuelDepuisMois(mois: string): { du: string; au: string } | null {
  const m = MOIS_RE.exec((mois ?? '').trim());
  if (!m) return null;
  const annee = Number(m[1]);
  const numMois = Number(m[2]);
  const anneePrec = numMois === 1 ? annee - 1 : annee;
  const moisPrec = numMois === 1 ? 12 : numMois - 1;
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    du: `${String(anneePrec).padStart(4, '0')}-${pad(moisPrec)}-27`,
    au: `${m[1]}-${m[2]}-26`,
  };
}

function decomposerMois(valeur: string): { annee: string; mois: string } {
  const m = MOIS_RE.exec((valeur ?? '').trim());
  return m ? { annee: m[1], mois: m[2] } : { annee: '', mois: '' };
}

function anneeCouranteTunis(): number {
  const txt = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Tunis', year: 'numeric' }).format(new Date());
  const n = Number(txt);
  return Number.isFinite(n) ? n : new Date().getFullYear();
}

/**
 * Sélecteur de mois/année (`AAAA-MM`) indépendant de la langue du navigateur (D-L01.c).
 */
export default function ChampMois({
  id,
  libelle = 'Mois *',
  valeur,
  onChange,
  required = false,
  disabled = false,
  invalide = false,
  anneeMin,
  anneeMax,
}: {
  id: string;
  libelle?: string;
  valeur: string;
  onChange: (mois: string) => void;
  required?: boolean;
  disabled?: boolean;
  invalide?: boolean;
  anneeMin?: number;
  anneeMax?: number;
}) {
  const initial = decomposerMois(valeur);
  const [moisSel, setMoisSel] = useState(initial.mois);
  const [anneeSel, setAnneeSel] = useState(initial.annee);
  const emise = useRef(valeur);

  useEffect(() => {
    if (valeur === emise.current) return;
    emise.current = valeur;
    const p = decomposerMois(valeur);
    setMoisSel(p.mois);
    setAnneeSel(p.annee);
  }, [valeur]);

  const annees = useMemo(() => {
    const courante = anneeCouranteTunis();
    const min = anneeMin ?? courante - 4;
    const max = anneeMax ?? courante + 2;
    const liste: string[] = [];
    for (let a = max; a >= min; a -= 1) {
      liste.push(String(a));
    }
    if (anneeSel && !liste.includes(anneeSel)) {
      liste.push(anneeSel);
      liste.sort((a, b) => Number(b) - Number(a));
    }
    return liste;
  }, [anneeMin, anneeMax, anneeSel]);

  function notifier(nouveauMois: string, nouvelleAnnee: string) {
    const prochain = nouveauMois && nouvelleAnnee ? `${nouvelleAnnee}-${nouveauMois}` : '';
    if (prochain !== emise.current) {
      emise.current = prochain;
      onChange(prochain);
    }
  }

  return (
    <div>
      <label className="field-label" htmlFor={`${id}-mois`}>
        {libelle}
      </label>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <select
          id={`${id}-mois`}
          className="field-input"
          value={moisSel}
          disabled={disabled}
          required={required}
          aria-label="Mois"
          aria-invalid={invalide || undefined}
          onChange={(e) => {
            const m = e.target.value;
            setMoisSel(m);
            notifier(m, anneeSel);
          }}
        >
          <option value="">Choisir…</option>
          {MOIS_FR.map((m) => (
            <option key={m.valeur} value={m.valeur}>
              {m.libelle}
            </option>
          ))}
        </select>

        <select
          id={`${id}-annee`}
          className="field-input"
          value={anneeSel}
          disabled={disabled}
          required={required}
          aria-label="Année"
          aria-invalid={invalide || undefined}
          style={{ minWidth: 110 }}
          onChange={(e) => {
            const a = e.target.value;
            setAnneeSel(a);
            notifier(moisSel, a);
          }}
        >
          <option value="">Choisir…</option>
          {annees.map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

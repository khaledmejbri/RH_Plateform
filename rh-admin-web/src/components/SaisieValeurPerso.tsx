import type { ReglesPerso, TypeValeurPerso } from '../api/champsPersoApi';
import type { ValeurListeSaisie } from '../api/valeursPersoApi';
import { SaisieDate } from './ChampsFr';

export type DefinitionSaisie = {
  code: string;
  libelle: string;
  type_valeur: TypeValeurPerso;
  regles: ReglesPerso | null;
  valeurs_liste: ValeurListeSaisie[] | null;
};

/** Valeur canonique du serveur (« 42.5 ») vers la saisie française (« 42,5 »). */
export function versSaisie(type: TypeValeurPerso, valeur: string | null): string {
  if (valeur === null || valeur === undefined) return '';
  return type === 'NOMBRE' ? valeur.replace('.', ',') : valeur;
}

/** Aide courte sous le champ, construite depuis les règles renvoyées par le serveur (jamais recalculées). */
export function aideRegles(type: TypeValeurPerso, r: ReglesPerso | null): string | null {
  if (!r) return null;
  const parts: string[] = [];
  const fr = (n: string) => n.replace('.', ',');
  if (type === 'TEXTE' && r.longueur_max) parts.push(`${r.longueur_max} caractères au plus`);
  if (type === 'NOMBRE') {
    if (r.decimales !== null && r.decimales !== undefined) parts.push(r.decimales === 0 ? 'nombre entier' : `${r.decimales} décimale${r.decimales > 1 ? 's' : ''} au plus`);
    if (r.minimum && r.maximum) parts.push(`entre ${fr(r.minimum)} et ${fr(r.maximum)}`);
    else if (r.minimum) parts.push(`au moins ${fr(r.minimum)}`);
    else if (r.maximum) parts.push(`au plus ${fr(r.maximum)}`);
  }
  return parts.length ? parts.join(', ') : null;
}

type Props = {
  id: string;
  champ: DefinitionSaisie;
  /** Saisie courante : texte, nombre à virgule, date ISO, code de liste. */
  valeur: string;
  onChange: (valeur: string) => void;
  erreur?: string | null;
  disabled?: boolean;
  /** Libellé de la valeur de liste actuelle quand elle est désactivée (affichée « (inactive) »). */
  libelleInactive?: string | null;
};

/** Saisie d'une valeur de champ personnalisé selon son type ; l'erreur du serveur s'affiche sous le champ. */
export default function SaisieValeurPerso({ id, champ, valeur, onChange, erreur, disabled, libelleInactive }: Props) {
  const idErreur = `${id}-erreur`;
  const idAide = `${id}-aide`;
  const aide = aideRegles(champ.type_valeur, champ.regles);
  const decrit = [erreur ? idErreur : null, aide ? idAide : null].filter(Boolean).join(' ') || undefined;
  const effacer = valeur !== '' && !disabled ? (
    <button type="button" className="btn btn--ghost btn--sm" aria-label={`Effacer ${champ.libelle}`} onClick={() => onChange('')}>Effacer</button>
  ) : null;

  let controle;
  if (champ.type_valeur === 'DATE') {
    controle = <SaisieDate id={id} valeur={valeur} onChange={onChange} disabled={disabled}
      min={champ.regles?.date_min ?? undefined} max={champ.regles?.date_max ?? undefined} />;
  } else if (champ.type_valeur === 'LISTE') {
    const options = (champ.valeurs_liste ?? []);
    const presente = options.some((o) => o.code === valeur);
    controle = (
      <select id={id} className="field-input" value={valeur} disabled={disabled} aria-invalid={erreur ? true : undefined}
        aria-describedby={decrit} onChange={(e) => onChange(e.target.value)}>
        <option value="">— Non renseigné —</option>
        {!presente && valeur ? <option value={valeur}>{libelleInactive ?? valeur} (inactive)</option> : null}
        {options.map((o) => (
          <option key={o.code} value={o.code}>{o.libelle}{o.actif === false ? ' (inactive)' : ''}</option>
        ))}
      </select>
    );
  } else if (champ.type_valeur === 'NOMBRE') {
    // Saisie texte : aucune conversion ni arrondi côté navigateur, le serveur contrôle le format.
    controle = <input id={id} className="field-input" inputMode="decimal" autoComplete="off" value={valeur} disabled={disabled}
      aria-invalid={erreur ? true : undefined} aria-describedby={decrit} onChange={(e) => onChange(e.target.value)} />;
  } else {
    controle = <input id={id} className="field-input" autoComplete="off" value={valeur} disabled={disabled}
      maxLength={champ.regles?.longueur_max ?? 500} aria-invalid={erreur ? true : undefined} aria-describedby={decrit}
      onChange={(e) => onChange(e.target.value)} />;
  }

  return (
    <div className="champ-perso">
      <div className="champ-perso__ligne">
        {controle}
        {effacer}
      </div>
      {aide ? <p id={idAide} className="field-help">{aide}</p> : null}
      {erreur ? <p id={idErreur} className="field-error" role="alert">{erreur}</p> : null}
    </div>
  );
}

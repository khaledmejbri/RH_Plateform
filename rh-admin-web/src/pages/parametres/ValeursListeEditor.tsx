import { useState, type FormEvent } from 'react';
import {
  ajouterValeurListe,
  desactiverValeurListe,
  modifierValeurListe,
  reactiverValeurListe,
  supprimerValeurListe,
  type ValeurListe,
} from '../../api/champsPersoApi';
import {
  estConflit,
  MESSAGE_CONFLIT,
  messageParametrageImport,
  type ParametrageImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { Chip } from '../../components/ui';

/* ─── Brouillon (création, ou passage à « Liste de valeurs ») : libellés envoyés avec le champ ─── */

type BrouillonProps = {
  valeurs: string[];
  onChange: (valeurs: string[]) => void;
  erreur?: string | null;
  disabled?: boolean;
};

export function ValeursListeBrouillon({ valeurs, onChange, erreur, disabled }: BrouillonProps) {
  const [texte, setTexte] = useState('');

  function ajouter() {
    const t = texte.trim();
    if (!t) return;
    onChange([...valeurs, t]);
    setTexte('');
  }

  return (
    <div>
      <label className="field-label" htmlFor="valeur-liste-nouvelle">Valeurs de la liste</label>
      <div style={{ display: 'flex', gap: 8 }}>
        <input id="valeur-liste-nouvelle" className="field-input" value={texte} maxLength={80} autoComplete="off" disabled={disabled}
          onChange={(e) => setTexte(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); ajouter(); } }} />
        <button type="button" className="btn btn--secondary btn--sm" disabled={disabled || texte.trim() === ''} onClick={ajouter}>Ajouter</button>
      </div>
      {valeurs.length === 0 ? <p className="field-help">Ajoutez au moins une valeur (100 au plus).</p> : (
        <ol className="valeurs-liste">
          {valeurs.map((v, i) => (
            <li key={`${v}-${i}`}>
              <span>{v}</span>
              <button type="button" className="btn btn--ghost btn--sm" disabled={disabled} aria-label={`Retirer ${v}`}
                onClick={() => onChange(valeurs.filter((_, j) => j !== i))}>Retirer</button>
            </li>
          ))}
        </ol>
      )}
      {erreur ? <p className="field-error" role="alert">{erreur}</p> : null}
    </div>
  );
}

/* ─── Édition en direct d'un champ existant (C5, C6) ─── */

type Props = {
  type: TypeParametrable;
  code: string;
  valeurs: ValeurListe[];
  version: number;
  onMaj: (p: ParametrageImport) => void;
  onRecharger: () => Promise<void>;
};

export default function ValeursListeEditor({ type, code, valeurs, version, onMaj, onRecharger }: Props) {
  const confirm = useConfirm();
  const [texte, setTexte] = useState('');
  const [edition, setEdition] = useState<{ code: string; libelle: string } | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [occupe, setOccupe] = useState(false);

  const ordonnees = [...valeurs].sort((a, b) => a.ordre - b.ordre);

  async function ecrire(action: () => Promise<ParametrageImport | void>): Promise<boolean> {
    setOccupe(true);
    setErreur(null);
    try {
      const r = await action();
      if (r) onMaj(r);
      else await onRecharger();
      return true;
    } catch (e) {
      if (estConflit(e)) await onRecharger();
      setErreur(estConflit(e) ? `${MESSAGE_CONFLIT} La liste a été rechargée, vérifiez puis recommencez.` : messageParametrageImport(e));
      return false;
    } finally {
      setOccupe(false);
    }
  }

  async function ajouter(ev: FormEvent) {
    ev.preventDefault();
    const t = texte.trim();
    if (!t || occupe) return;
    if (await ecrire(() => ajouterValeurListe(type, code, t, version))) setTexte('');
  }

  async function enregistrerLibelle(v: ValeurListe) {
    if (!edition) return;
    const t = edition.libelle.trim();
    if (!t || t === v.libelle) { setEdition(null); return; }
    if (await ecrire(() => modifierValeurListe(type, code, v.code, t, v.ordre, version))) setEdition(null);
  }

  function deplacer(v: ValeurListe, sens: -1 | 1) {
    void ecrire(() => modifierValeurListe(type, code, v.code, v.libelle, v.ordre + sens, version));
  }

  async function supprimer(v: ValeurListe) {
    const ok = await confirm({
      title: 'Supprimer la valeur ?',
      message: `Supprimer la valeur « ${v.libelle} » de la liste ? Cette action est définitive.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (ok) await ecrire(() => supprimerValeurListe(type, code, v.code, version));
  }

  return (
    <div>
      <form onSubmit={(e) => void ajouter(e)} aria-label="Ajouter une valeur">
        <label className="field-label" htmlFor={`valeur-ajout-${code}`}>Nouvelle valeur</label>
        <div style={{ display: 'flex', gap: 8 }}>
          <input id={`valeur-ajout-${code}`} className="field-input" value={texte} maxLength={80} autoComplete="off" disabled={occupe}
            onChange={(e) => { setTexte(e.target.value); setErreur(null); }} />
          <button type="submit" className="btn btn--secondary btn--sm" disabled={occupe || texte.trim() === ''}>Ajouter</button>
        </div>
      </form>
      {erreur ? <p className="field-error" role="alert">{erreur}</p> : null}
      <ul className="valeurs-liste valeurs-liste--actions">
        {ordonnees.map((v, i) => (
          <li key={v.code} className={v.actif ? undefined : 'param-ligne--inactive'}>
            {edition?.code === v.code ? (
              <>
                <label className="sr-only" htmlFor={`valeur-edit-${v.code}`}>Libellé de la valeur</label>
                <input id={`valeur-edit-${v.code}`} className="field-input" value={edition.libelle} maxLength={80} autoComplete="off" disabled={occupe}
                  onChange={(e) => setEdition({ code: v.code, libelle: e.target.value })} />
                <button type="button" className="btn btn--primary btn--sm" disabled={occupe} onClick={() => void enregistrerLibelle(v)}>Enregistrer</button>
                <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} onClick={() => setEdition(null)}>Annuler</button>
              </>
            ) : (
              <>
                <span>
                  {v.libelle} {v.actif ? null : <Chip>Désactivée</Chip>}
                  {v.utilisations > 0 ? <span className="param-ref">{v.utilisations === 1 ? '1 utilisation' : `${v.utilisations} utilisations`}</span> : null}
                </span>
                <span className="param-actions">
                  <button type="button" className="btn btn--ghost btn--sm" disabled={occupe || i === 0} aria-label={`Monter ${v.libelle}`} onClick={() => deplacer(v, -1)}>▲</button>
                  <button type="button" className="btn btn--ghost btn--sm" disabled={occupe || i === ordonnees.length - 1} aria-label={`Descendre ${v.libelle}`} onClick={() => deplacer(v, 1)}>▼</button>
                  <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`Renommer ${v.libelle}`}
                    onClick={() => setEdition({ code: v.code, libelle: v.libelle })}>Renommer</button>
                  <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`${v.actif ? 'Désactiver' : 'Réactiver'} ${v.libelle}`}
                    onClick={() => void ecrire(() => (v.actif ? desactiverValeurListe : reactiverValeurListe)(type, code, v.code, version))}>
                    {v.actif ? 'Désactiver' : 'Réactiver'}
                  </button>
                  {v.supprimable ? (
                    <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`Supprimer ${v.libelle}`} onClick={() => void supprimer(v)}>Supprimer</button>
                  ) : null}
                </span>
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

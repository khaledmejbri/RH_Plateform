import { useState, type FormEvent } from 'react';
import { ajusterSolde, messageConges, type ReponseAjustement, type SoldeLigne } from '../../api/congesApi';
import { ApiMetierError } from '../../api/metierApi';
import { DialogPanel } from '../../components/useDialogA11y';
import { Icon } from '../../components/ui';
import { formatJours, formatNombre } from '../../utils/format';

/** Saisie française (virgule ou point). NaN si ce n'est pas un nombre. */
function lireNombre(texte: string): number {
  const t = texte.trim().replace(',', '.');
  return /^[+-]?\d+(\.\d+)?$/.test(t) ? Number(t) : Number.NaN;
}

/** Les ajustements se font par pas de 0,5 jour : contrôle de confort avant envoi, le serveur reste juge. */
function estUnDemi(n: number): boolean {
  return Math.abs(n * 2 - Math.round(n * 2)) < 1e-9;
}

type Props = {
  solde: SoldeLigne;
  onClose: () => void;
  onAjuste: (r: ReponseAjustement) => void;
  /** 409 : le solde a changé entre-temps ; la page recharge ses données. */
  onConflit: () => void;
};

/** Fenêtre « Ajuster » : écart ou nouveau solde, motif obligatoire dans la même fenêtre (D-S08, D-L03.d). */
export default function AjusterSoldeDialog({ solde, onClose, onAjuste, onConflit }: Props) {
  const [mode, setMode] = useState<'ecart' | 'nouveau_solde'>('ecart');
  const [valeur, setValeur] = useState('');
  const [motif, setMotif] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const nombre = lireNombre(valeur);
  const saisieVide = valeur.trim() === '';
  const formatInvalide = !saisieVide && (Number.isNaN(nombre) || !estUnDemi(nombre));
  const resultat = !saisieVide && !formatInvalide ? (mode === 'ecart' ? solde.acquis + nombre : nombre) : null;
  const negatif = resultat !== null && resultat < 0;
  const sousReserve = resultat !== null && !negatif && resultat < solde.reserve;
  const longueur = motif.trim().length;
  const motifOk = longueur >= 5 && longueur <= 500;
  const peutEnvoyer = !saisieVide && !formatInvalide && !negatif && motifOk && !envoi;

  async function soumettre(ev: FormEvent) {
    ev.preventDefault();
    if (!peutEnvoyer) return;
    setEnvoi(true);
    setErreur(null);
    try {
      const corps = mode === 'ecart'
        ? { ecart: nombre, motif: motif.trim(), version: solde.version }
        : { nouveau_solde: nombre, motif: motif.trim(), version: solde.version };
      onAjuste(await ajusterSolde(solde.collaborateur_id, corps));
    } catch (e) {
      if (e instanceof ApiMetierError && e.code === 'SOLDE_MODIFIE_ENTRE_TEMPS') {
        onConflit();
        return;
      }
      setErreur(messageConges(e));
      setEnvoi(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={() => { if (!envoi) onClose(); }}>
      <DialogPanel label={`Ajuster le solde de ${solde.nom_complet}`} className="modal modal--md" onClose={() => { if (!envoi) onClose(); }}>
        <form onSubmit={(e) => void soumettre(e)}>
          <div className="modal__head">
            <div>
              <h3 className="modal__title" style={{ margin: 0 }}>Ajuster le solde</h3>
              <p className="cell-meta" style={{ margin: 0 }}>{solde.nom_complet} ({solde.matricule})</p>
            </div>
            <button type="button" className="modal__close" aria-label="Fermer" onClick={onClose} disabled={envoi}>
              <Icon name="x" size={18} />
            </button>
          </div>
          <div className="modal__scroll">
            <p>
              Solde acquis : <strong>{formatJours(solde.acquis)}</strong>, dont {formatJours(solde.reserve)} réservés par des demandes en cours.
            </p>
            <fieldset style={{ border: 0, padding: 0, margin: '0 0 12px' }}>
              <legend className="field-label">Type d’ajustement</legend>
              <label style={{ marginRight: 16 }}>
                <input type="radio" name="mode" checked={mode === 'ecart'} onChange={() => setMode('ecart')} /> Écart (ajouter ou retirer)
              </label>
              <label>
                <input type="radio" name="mode" checked={mode === 'nouveau_solde'} onChange={() => setMode('nouveau_solde')} /> Nouveau solde
              </label>
            </fieldset>
            <label className="field-label" htmlFor="ajust-valeur">
              {mode === 'ecart' ? 'Écart en jours (ex. -2 ou 1,5)' : 'Nouveau solde en jours (ex. 7,5)'}
            </label>
            <input id="ajust-valeur" className="field-input" inputMode="decimal" autoComplete="off" value={valeur}
              aria-invalid={formatInvalide || negatif || undefined} aria-describedby="ajust-aide"
              onChange={(e) => setValeur(e.target.value)} />
            <div id="ajust-aide" aria-live="polite">
              {formatInvalide ? <p className="field-error">Saisissez un nombre de jours par pas de 0,5 (virgule ou point).</p> : null}
              {resultat !== null && !negatif ? <p className="muted small">Solde après ajustement : <strong>{formatJours(resultat)}</strong></p> : null}
              {negatif ? <p className="field-error">Le solde ne peut pas devenir négatif ({formatNombre(resultat, 2)} jour).</p> : null}
              {sousReserve ? (
                <p className="alert alert--warning" role="status">
                  Des demandes en cours ({formatJours(solde.reserve)}) dépasseront ce solde : elles pourront être bloquées à l’approbation.
                </p>
              ) : null}
            </div>
            <label className="field-label" htmlFor="ajust-motif" style={{ marginTop: 12 }}>Motif (obligatoire)</label>
            <textarea id="ajust-motif" className="field-input field-input--area" rows={3} maxLength={500} value={motif}
              placeholder="Expliquez la raison de l’ajustement…" disabled={envoi} onChange={(e) => setMotif(e.target.value)} />
            <p className={`muted small${longueur > 0 && longueur < 5 ? ' field-error' : ''}`} aria-live="polite">
              {longueur} / 500 caractères{longueur < 5 ? ' (au moins 5)' : ''}
            </p>
            {erreur ? <p className="alert alert--error" role="alert">{erreur}</p> : null}
          </div>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose} disabled={envoi}>Annuler</button>
            <button type="submit" className="btn btn--primary" disabled={!peutEnvoyer} aria-busy={envoi}>
              {envoi ? 'Envoi…' : 'Ajuster'}
            </button>
          </div>
        </form>
      </DialogPanel>
    </div>
  );
}

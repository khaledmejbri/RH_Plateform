import { useRef, useState } from 'react';
import { AlerteEcartApiError, CONCLUSIONS, messageErreurAction, type ConclusionAlerteEcart } from '../api/alertesEcartApi';
import { DialogPanel } from './useDialogA11y';

const MIN = 10;
const MAX = 1000;

type Props = {
  onClose: () => void;
  /** Confirmation « Êtes-vous sûr ? » demandée avant l'envoi ; false = abandon. */
  confirmer?: () => Promise<boolean>;
  /** Envoie la clôture ; rejette avec AlerteEcartApiError en cas d'échec. */
  onSubmit: (conclusion: ConclusionAlerteEcart, commentaire: string) => Promise<void>;
  /** Erreur qui impose de quitter la fenêtre (403, 404, 409) : le parent affiche le message et recharge. */
  onErreurBloquante: (e: AlerteEcartApiError) => void;
};

export function ClotureAlerteDialog({ onClose, onSubmit, onErreurBloquante, confirmer }: Props) {
  const [conclusion, setConclusion] = useState<ConclusionAlerteEcart | ''>('');
  const [commentaire, setCommentaire] = useState('');
  const [toucheConclusion, setToucheConclusion] = useState(false);
  const [toucheCommentaire, setToucheCommentaire] = useState(false);
  const [busy, setBusy] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const envoiEnCours = useRef(false);

  const longueur = commentaire.trim().length;
  const erreurConclusion = conclusion ? null : 'Conclusion obligatoire';
  const erreurCommentaire =
    longueur < MIN
      ? 'Commentaire obligatoire (10 caractères minimum)'
      : longueur > MAX
        ? 'Commentaire trop long (1000 caractères maximum)'
        : null;
  const valide = !erreurConclusion && !erreurCommentaire;
  const afficheConclusion = toucheConclusion && !!erreurConclusion;
  const afficheCommentaire = toucheCommentaire && !!erreurCommentaire;

  async function envoyer(e: React.FormEvent) {
    e.preventDefault();
    setToucheConclusion(true);
    setToucheCommentaire(true);
    if (!valide || !conclusion || envoiEnCours.current) return;
    envoiEnCours.current = true;
    if (confirmer && !(await confirmer())) {
      envoiEnCours.current = false;
      return;
    }
    setBusy(true);
    setErreur(null);
    try {
      await onSubmit(conclusion, commentaire);
    } catch (err) {
      if (err instanceof AlerteEcartApiError && [403, 404, 409].includes(err.status)) {
        onErreurBloquante(err);
      } else {
        setErreur(messageErreurAction(err));
        envoiEnCours.current = false;
        setBusy(false);
      }
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <DialogPanel onClose={() => !busy && onClose()} label="Clore l’alerte d’écart" className="modal">
        <form onSubmit={(e) => void envoyer(e)} noValidate>
          <h2 className="modal__title">Clore l’alerte</h2>
          <div className="modal__body">
            <div className="form-group">
              <label htmlFor="cloture-conclusion">Conclusion</label>
              <select
                id="cloture-conclusion"
                value={conclusion}
                aria-invalid={afficheConclusion}
                aria-describedby={afficheConclusion ? 'cloture-conclusion-err' : undefined}
                onChange={(e) => {
                  setConclusion(e.target.value as ConclusionAlerteEcart | '');
                  setToucheConclusion(true);
                }}
                onBlur={() => setToucheConclusion(true)}
              >
                <option value="">Choisir une conclusion…</option>
                {CONCLUSIONS.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.libelle}
                  </option>
                ))}
              </select>
              {afficheConclusion ? (
                <p id="cloture-conclusion-err" className="field-error" role="alert">
                  {erreurConclusion}
                </p>
              ) : null}
            </div>
            <div className="form-group">
              <label htmlFor="cloture-commentaire">Commentaire</label>
              <textarea
                id="cloture-commentaire"
                rows={5}
                value={commentaire}
                aria-invalid={afficheCommentaire}
                aria-describedby={
                  afficheCommentaire ? 'cloture-commentaire-aide cloture-commentaire-err' : 'cloture-commentaire-aide'
                }
                onChange={(e) => {
                  setCommentaire(e.target.value);
                  setToucheCommentaire(true);
                }}
                onBlur={() => setToucheCommentaire(true)}
              />
              <p id="cloture-commentaire-aide" className={`small ${longueur > MAX ? 'field-error' : 'muted'}`}>
                {longueur} / {MAX} caractères (10 minimum)
              </p>
              {afficheCommentaire ? (
                <p id="cloture-commentaire-err" className="field-error" role="alert">
                  {erreurCommentaire}
                </p>
              ) : null}
            </div>
            {erreur ? (
              <div className="alert alert--error" role="alert">
                {erreur}
              </div>
            ) : null}
          </div>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" disabled={busy} onClick={onClose}>
              Annuler
            </button>
            <button type="submit" className="btn btn--primary" disabled={!valide || busy}>
              {busy ? 'Clôture en cours…' : 'Clore l’alerte'}
            </button>
          </div>
        </form>
      </DialogPanel>
    </div>
  );
}

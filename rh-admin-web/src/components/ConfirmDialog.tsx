import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

type ConfirmOptions = {
  title?: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Action destructive / irréversible → bouton rouge. */
  danger?: boolean;
  /** Champ de motif porté par la même fenêtre (D-L03). */
  motif?: { label: string; obligatoire?: boolean; placeholder?: string };
  /**
   * Action à exécuter à la confirmation : la fenêtre porte l'état d'envoi et affiche l'erreur ;
   * elle se ferme (résultat `true`) si l'action réussit, reste ouverte si elle échoue.
   */
  envoyer?: (motif: string) => Promise<void>;
  /** Si vrai après un échec de `envoyer` (ex. 409), la fenêtre se ferme au lieu de rester ouverte. */
  echecDefinitif?: (erreur: unknown) => boolean;
};

type Pending = ConfirmOptions & { resolve: (ok: boolean) => void };

const ConfirmContext = createContext<((opts: ConfirmOptions) => Promise<boolean>) | null>(null);

/** Remplace window.confirm : `if (!(await confirm({ message }))) return;` */
export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm doit être utilisé sous <ConfirmProvider>');
  return ctx;
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<Pending | null>(null);

  const confirm = useCallback(
    (opts: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ ...opts, resolve })),
    [],
  );

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending ? (
        <ConfirmModal
          {...pending}
          onClose={(ok) => {
            pending.resolve(ok);
            setPending(null);
          }}
        />
      ) : null}
    </ConfirmContext.Provider>
  );
}

function ConfirmModal({
  title = 'Êtes-vous sûr ?',
  message,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  danger,
  motif: champMotif,
  envoyer,
  echecDefinitif,
  onClose,
}: ConfirmOptions & { onClose: (ok: boolean) => void }) {
  const [texteMotif, setTexteMotif] = useState('');
  const [busy, setBusy] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const motifManquant = !!champMotif?.obligatoire && !texteMotif.trim();

  async function valider() {
    if (busy || motifManquant) return;
    if (!envoyer) {
      onClose(true);
      return;
    }
    setBusy(true);
    setErreur(null);
    try {
      await envoyer(texteMotif.trim());
      onClose(true);
    } catch (e) {
      if (echecDefinitif?.(e)) {
        onClose(false);
        return;
      }
      setErreur(e instanceof Error ? e.message : 'L’action a échoué.');
      setBusy(false);
    }
  }

  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    // Règle UX : le focus est toujours sur « Annuler » (aucune validation involontaire par Entrée).
    cancelRef.current?.focus();
    return () => previous?.focus();
  }, []);

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      e.stopPropagation();
      if (!busy) onClose(false);
    } else if (e.key === 'Tab') {
      // Piège le focus entre les deux boutons
      const first = document.getElementById('confirm-motif') ?? cancelRef.current;
      // Dernier élément focalisable : « Confirmer », ou « Annuler » si Confirmer est désactivé.
      const last = confirmRef.current && !confirmRef.current.disabled ? confirmRef.current : cancelRef.current;
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last?.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first?.focus();
      }
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose(false)}>
      <div
        className="modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        onKeyDown={onKeyDown}
      >
        <h2 id="confirm-title" className="modal__title">
          {title}
        </h2>
        <div id="confirm-message" className="modal__body">
          {message}
        </div>
        {champMotif ? (
          <div className="modal__body">
            <label htmlFor="confirm-motif" className="field-label">
              {champMotif.label}{champMotif.obligatoire ? ' (obligatoire)' : ''}
            </label>
            <textarea
              id="confirm-motif"
              className="field-input field-input--area"
              rows={3}
              maxLength={500}
              value={texteMotif}
              placeholder={champMotif.placeholder}
              disabled={busy}
              onChange={(e) => setTexteMotif(e.target.value)}
            />
          </div>
        ) : null}
        {erreur ? <p className="alert alert--error" role="alert">{erreur}</p> : null}
        <div className="modal__actions">
          <button ref={cancelRef} type="button" className="btn btn--ghost" disabled={busy} onClick={() => onClose(false)}>
            {cancelLabel}
          </button>
          <button
            ref={confirmRef}
            type="button"
            className={`btn ${danger ? 'btn--danger' : 'btn--primary'}`}
            disabled={busy || motifManquant}
            aria-busy={busy}
            onClick={() => void valider()}
          >
            {busy ? 'Envoi…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

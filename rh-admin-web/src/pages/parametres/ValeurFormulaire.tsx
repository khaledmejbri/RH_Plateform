import { useState, type FormEvent } from 'react';
import {
  creerValeur,
  modifierValeur,
  ParametreApiError,
  type CorpsValeur,
  type DescripteurReferentiel,
  type ServiceParametre,
  type Valeur,
} from '../../api/parametresApi';
import { useConfirm } from '../../components/ConfirmDialog';
import PastilleCouleur from '../../components/PastilleCouleur';
import { DialogPanel } from '../../components/useDialogA11y';
import { PALETTE } from '../../utils/palette';
import { rafraichirParametres } from '../../utils/parametresStore';

/** Aide de saisie des attributs spécifiques (le serveur reste le garde des bornes). */
export const ATTRIBUTS: Record<string, { label: string; aide?: string; unite?: string }> = {
  sla_heures: { label: 'Délai de traitement (SLA)', aide: 'En heures. Sans effet sur les demandes déjà déposées.', unite: 'h' },
  piece_jointe_obligatoire: { label: 'Pièce jointe obligatoire' },
  seuil_ecart: { label: 'Seuil d’écart', aide: 'Écart minimal d’étoiles (1 à 4) à partir duquel ce niveau s’applique.' },
};

export function libelleAttribut(nom: string): string {
  return ATTRIBUTS[nom]?.label ?? nom.replaceAll('_', ' ').replace(/^./, (c) => c.toUpperCase());
}

type Props = {
  service: ServiceParametre;
  descripteur: DescripteurReferentiel;
  /** Valeur éditée ; absente = création. */
  valeur?: Valeur;
  onClose: () => void;
  /** Appelée après l'enregistrement ; le formulaire se ferme. */
  onSaved: (v: Valeur) => void;
  /** 409 « modifié entre-temps » : l'appelant recharge la liste. */
  onConflit: () => void;
};

/** Formulaire d'ajout / de modification d'une valeur (sans confirmation, sauf seuil d'alerte). */
export default function ValeurFormulaire({ service, descripteur, valeur, onClose, onSaved, onConflit }: Props) {
  const confirm = useConfirm();
  const edition = !!valeur;
  const [libelle, setLibelle] = useState(valeur?.libelle ?? '');
  const [description, setDescription] = useState(valeur?.description ?? '');
  const [couleur, setCouleur] = useState(valeur?.couleur ?? 'NEUTRE');
  const [entiers, setEntiers] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      descripteur.attributs
        .filter((a) => a.type !== 'BOOLEEN')
        .map((a) => [a.nom, String((valeur as unknown as Record<string, unknown> | undefined)?.[a.nom] ?? '')]),
    ),
  );
  const [booleens, setBooleens] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(
      descripteur.attributs
        .filter((a) => a.type === 'BOOLEEN')
        .map((a) => [a.nom, !!(valeur as unknown as Record<string, unknown> | undefined)?.[a.nom]]),
    ),
  );
  const [erreurs, setErreurs] = useState<Record<string, string>>({});
  const [erreurGlobale, setErreurGlobale] = useState<string | null>(null);
  const [conflit, setConflit] = useState(false);
  const [envoi, setEnvoi] = useState(false);

  const verrouillee = !!valeur?.piece_jointe_verrouillee;

  function corps(): CorpsValeur {
    const c: CorpsValeur = {
      libelle: libelle.trim(),
      // Chaîne vide = effacer ; null = inchangé (contrat PUT).
      description: description.trim(),
      couleur,
    };
    for (const a of descripteur.attributs) {
      if (a.type === 'BOOLEEN') {
        (c as Record<string, unknown>)[a.nom] = booleens[a.nom] ?? false;
      } else {
        const brut = (entiers[a.nom] ?? '').trim();
        (c as Record<string, unknown>)[a.nom] = brut === '' ? null : Number(brut);
      }
    }
    return c;
  }

  function enregistrer(c: CorpsValeur): Promise<Valeur> {
    return valeur
      ? modifierValeur(service, descripteur.referentiel, valeur.code, c, valeur.version)
      : creerValeur(service, descripteur.referentiel, c);
  }

  function afficherEchec(e: unknown) {
    if (e instanceof ParametreApiError) {
      if (e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') {
        setConflit(true);
        setErreurGlobale(e.message);
        return;
      }
      if (e.champ && (e.status === 400 || e.status === 422)) {
        setErreurs({ [e.champ]: e.message });
        return;
      }
      setErreurGlobale(e.message);
      return;
    }
    setErreurGlobale('Une erreur inattendue est survenue. Rien n’a été modifié.');
  }

  async function soumettre(ev: FormEvent) {
    ev.preventDefault();
    if (envoi) return;
    setErreurs({});
    setErreurGlobale(null);
    const c = corps();
    const nouveauSeuil = (c as Record<string, unknown>).seuil_ecart;
    const ancienSeuil = valeur?.seuil_ecart ?? null;
    const seuilModifie = descripteur.attributs.some((a) => a.nom === 'seuil_ecart')
      && edition && nouveauSeuil !== ancienSeuil;
    const seuilUn = nouveauSeuil === 1;
    setEnvoi(true);
    try {
      if (seuilModifie || seuilUn) {
        // Seuil d'alerte : confirmation nommant la valeur et le référentiel (D-L03, D-R04.d).
        const etat: { echec: unknown; resultat: Valeur | null } = { echec: null, resultat: null };
        const ok = await confirm({
          title: 'Êtes-vous sûr ?',
          confirmLabel: 'Enregistrer',
          message: (
            <>
              <p>
                {edition ? 'Modifier le seuil de la valeur' : 'Créer la valeur'} « {c.libelle} » du référentiel
                « {descripteur.libelle} » ({edition ? `seuil ${ancienSeuil ?? '—'} → ${String(nouveauSeuil ?? '—')}` : `seuil ${String(nouveauSeuil)}`}) ?
              </p>
              <p>Le nouveau seuil s’applique aux détections et recalculs postérieurs ; les alertes existantes gardent leur niveau.</p>
              {seuilUn ? (
                <p className="alert alert--warning" role="alert">Ce réglage peut multiplier les alertes : un seuil de 1 signale le moindre écart d’une étoile.</p>
              ) : null}
            </>
          ),
          envoyer: async () => {
            try {
              etat.resultat = await enregistrer(c);
            } catch (e) {
              etat.echec = e;
              throw e;
            }
          },
          echecDefinitif: (e) => e instanceof ParametreApiError && e.status >= 400 && e.status < 500,
        });
        if (ok && etat.resultat) {
          await rafraichirParametres();
          onSaved(etat.resultat);
          return;
        }
        if (etat.echec) afficherEchec(etat.echec);
        return;
      }
      const v = await enregistrer(c);
      await rafraichirParametres();
      onSaved(v);
    } catch (e) {
      afficherEchec(e);
    } finally {
      setEnvoi(false);
    }
  }

  const titre = edition ? `Modifier « ${valeur?.libelle} »` : `Ajouter une valeur — ${descripteur.libelle}`;

  return (
    <div className="modal-backdrop">
      <DialogPanel onClose={() => !envoi && onClose()} label={titre} className="modal modal--md">
        <form onSubmit={(e) => void soumettre(e)} noValidate>
          <div className="modal__head">
            <h2 className="modal__title">{titre}</h2>
            <button type="button" className="modal__close" aria-label="Fermer" onClick={onClose} disabled={envoi}>×</button>
          </div>
          <div className="modal__body" style={{ display: 'grid', gap: '0.9rem' }}>
            {edition ? (
              <p className="muted small" style={{ margin: 0 }}>Référence technique : {valeur?.code}</p>
            ) : null}
            <div>
              <label htmlFor="param-libelle" className="field-label">Libellé (2 à 80 caractères)</label>
              <input
                id="param-libelle"
                className="field-input"
                value={libelle}
                maxLength={80}
                required
                aria-invalid={!!erreurs.libelle}
                aria-describedby={erreurs.libelle ? 'param-libelle-err' : undefined}
                onChange={(e) => setLibelle(e.target.value)}
              />
              {erreurs.libelle ? <p id="param-libelle-err" className="field-error" role="alert">{erreurs.libelle}</p> : null}
            </div>
            <div>
              <label htmlFor="param-description" className="field-label">Description (facultative, 300 caractères au plus)</label>
              <textarea
                id="param-description"
                className="field-input field-input--area"
                rows={3}
                maxLength={300}
                value={description}
                aria-invalid={!!erreurs.description}
                aria-describedby={erreurs.description ? 'param-description-err' : undefined}
                onChange={(e) => setDescription(e.target.value)}
              />
              {erreurs.description ? <p id="param-description-err" className="field-error" role="alert">{erreurs.description}</p> : null}
            </div>
            <fieldset className="param-palette" aria-describedby={erreurs.couleur ? 'param-couleur-err' : undefined}>
              <legend className="field-label">Couleur</legend>
              {PALETTE.map((p) => (
                <label key={p.code}>
                  <input
                    type="radio"
                    name="param-couleur"
                    value={p.code}
                    checked={couleur === p.code}
                    onChange={() => setCouleur(p.code)}
                  />
                  <PastilleCouleur couleur={p.code}>{p.nom}</PastilleCouleur>
                </label>
              ))}
            </fieldset>
            {erreurs.couleur ? <p id="param-couleur-err" className="field-error" role="alert">{erreurs.couleur}</p> : null}

            {descripteur.attributs.map((a) => {
              const meta = ATTRIBUTS[a.nom];
              const id = `param-attr-${a.nom}`;
              if (a.type === 'BOOLEEN') {
                const bloque = a.nom === 'piece_jointe_obligatoire' && verrouillee;
                return (
                  <div key={a.nom}>
                    <label className="inline-row" htmlFor={id}>
                      <input
                        id={id}
                        type="checkbox"
                        checked={bloque ? true : booleens[a.nom] ?? false}
                        disabled={bloque}
                        onChange={(e) => setBooleens({ ...booleens, [a.nom]: e.target.checked })}
                      />{' '}
                      {libelleAttribut(a.nom)}
                    </label>
                    {bloque ? <p className="field-help">Toujours obligatoire pour ce type : non modifiable.</p> : null}
                    {erreurs[a.nom] ? <p className="field-error" role="alert">{erreurs[a.nom]}</p> : null}
                  </div>
                );
              }
              return (
                <div key={a.nom}>
                  <label htmlFor={id} className="field-label">
                    {libelleAttribut(a.nom)}{meta?.unite ? ` (${meta.unite})` : ''}
                  </label>
                  <input
                    id={id}
                    className="field-input"
                    type="number"
                    inputMode="numeric"
                    style={{ maxWidth: '8rem' }}
                    value={entiers[a.nom] ?? ''}
                    aria-invalid={!!erreurs[a.nom]}
                    aria-describedby={erreurs[a.nom] ? `${id}-err` : meta?.aide ? `${id}-aide` : undefined}
                    onChange={(e) => setEntiers({ ...entiers, [a.nom]: e.target.value })}
                  />
                  {meta?.aide ? <p id={`${id}-aide`} className="field-help">{meta.aide}</p> : null}
                  {erreurs[a.nom] ? <p id={`${id}-err`} className="field-error" role="alert">{erreurs[a.nom]}</p> : null}
                </div>
              );
            })}

            {erreurs && Object.keys(erreurs).some((k) => !['libelle', 'description', 'couleur', ...descripteur.attributs.map((a) => a.nom)].includes(k)) ? (
              <p className="field-error" role="alert">{Object.values(erreurs).join(' ')}</p>
            ) : null}
            {erreurGlobale ? (
              <div className="alert alert--error" role="alert">
                {erreurGlobale}{' '}
                {conflit ? (
                  <button type="button" className="btn btn--secondary btn--sm" onClick={onConflit}>Recharger</button>
                ) : null}
              </div>
            ) : null}
          </div>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose} disabled={envoi}>Annuler</button>
            <button type="submit" className="btn btn--primary" disabled={envoi || libelle.trim() === ''} aria-busy={envoi}>
              {envoi ? 'Envoi…' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </DialogPanel>
    </div>
  );
}

import { useState, type FormEvent } from 'react';
import {
  champFormulaireDeLErreur,
  creerChampPerso,
  modifierChampPerso,
  TYPES_VALEUR,
  type ReglesPerso,
  type TypeValeurPerso,
} from '../../api/champsPersoApi';
import {
  estConflit,
  MESSAGE_CONFLIT,
  messageParametrageImport,
  type ChampParametre,
  type ParametrageImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { SaisieDate } from '../../components/ChampsFr';
import { Icon } from '../../components/ui';
import { DialogPanel } from '../../components/useDialogA11y';
import ValeursListeEditor, { ValeursListeBrouillon } from './ValeursListeEditor';

type Props = {
  type: TypeParametrable;
  typeLibelle: string;
  /** Absent = création. */
  champ?: ChampParametre;
  version: number;
  onMaj: (p: ParametrageImport) => void;
  onRecharger: () => Promise<void>;
  onFermer: () => void;
};

type Erreurs = Partial<Record<'libelle' | 'finalite' | 'type_valeur' | 'regles' | 'valeurs_liste' | 'confirmation' | 'general', string>>;

const nombreOuNull = (t: string): number | null => (t.trim() === '' ? null : Number(t.trim()));
const texteOuNull = (t: string): string | null => (t.trim() === '' ? null : t.trim().replace(',', '.'));

/** Création (C1) et modification (C2) d'un champ personnalisé. Le serveur valide : ses messages sont affichés tels quels. */
export default function ChampPersoFormDialog({ type, typeLibelle, champ, version, onMaj, onRecharger, onFermer }: Props) {
  const edition = !!champ;
  const r0 = champ?.regles ?? {};
  const [libelle, setLibelle] = useState(champ?.libelle ?? '');
  const [finalite, setFinalite] = useState(champ?.finalite ?? '');
  const [typeValeur, setTypeValeur] = useState<TypeValeurPerso>(champ?.type_valeur ?? 'TEXTE');
  const [longueurMax, setLongueurMax] = useState(r0.longueur_max != null ? String(r0.longueur_max) : '');
  const [decimales, setDecimales] = useState(r0.decimales != null ? String(r0.decimales) : '');
  const [minimum, setMinimum] = useState((r0.minimum ?? '').replace('.', ','));
  const [maximum, setMaximum] = useState((r0.maximum ?? '').replace('.', ','));
  const [dateMin, setDateMin] = useState(r0.date_min ?? '');
  const [dateMax, setDateMax] = useState(r0.date_max ?? '');
  const [visible, setVisible] = useState(champ?.visible_collaborateur ?? false);
  const [confirmation, setConfirmation] = useState(false);
  const [brouillonListe, setBrouillonListe] = useState<string[]>([]);
  const [erreurs, setErreurs] = useState<Erreurs>({});
  const [envoi, setEnvoi] = useState(false);

  const typeFige = edition && champ.type_modifiable === false;
  const typeInitial = champ?.type_valeur ?? null;
  // Liste existante gérée en direct ; une liste créée par changement de type part d'un brouillon.
  const listeEnDirect = edition && typeInitial === 'LISTE' && typeValeur === 'LISTE';
  const listeBrouillon = typeValeur === 'LISTE' && !listeEnDirect;

  function regles(): ReglesPerso {
    return {
      longueur_max: typeValeur === 'TEXTE' ? nombreOuNull(longueurMax) : null,
      decimales: typeValeur === 'NOMBRE' ? nombreOuNull(decimales) : null,
      minimum: typeValeur === 'NOMBRE' ? texteOuNull(minimum) : null,
      maximum: typeValeur === 'NOMBRE' ? texteOuNull(maximum) : null,
      date_min: typeValeur === 'DATE' ? (dateMin || null) : null,
      date_max: typeValeur === 'DATE' ? (dateMax || null) : null,
    };
  }

  /** Contrôles de confort ; le serveur reste juge. */
  function controleLocal(): Erreurs {
    const e: Erreurs = {};
    if (libelle.trim().length < 2) e.libelle = 'Le libellé est obligatoire (2 caractères au moins).';
    const f = finalite.trim().length;
    if (f < 5 || f > 200) e.finalite = 'Indiquez la finalité en 5 à 200 caractères.';
    if (!edition && !confirmation) e.confirmation = 'Cochez la case pour confirmer que ce champ ne contient aucune donnée sensible.';
    if (listeBrouillon && brouillonListe.length === 0) e.valeurs_liste = 'Ajoutez au moins une valeur à la liste.';
    return e;
  }

  async function soumettre(ev: FormEvent) {
    ev.preventDefault();
    if (envoi) return;
    const locales = controleLocal();
    setErreurs(locales);
    if (Object.keys(locales).length > 0) return;
    setEnvoi(true);
    const base = {
      version,
      libelle: libelle.trim(),
      finalite: finalite.trim(),
      type_valeur: typeValeur,
      regles: regles(),
      visible_collaborateur: visible,
      ...(listeBrouillon ? { valeurs_liste: brouillonListe.map((l) => ({ libelle: l })) } : {}),
    };
    try {
      const p = champ
        ? await modifierChampPerso(type, champ.code, base)
        : await creerChampPerso(type, { ...base, confirmation_donnees_non_sensibles: confirmation });
      onMaj(p);
      onFermer();
    } catch (e) {
      if (estConflit(e)) {
        await onRecharger();
        setErreurs({ general: `${MESSAGE_CONFLIT} Les données ont été rechargées : vérifiez puis enregistrez de nouveau.` });
      } else {
        const cible = champFormulaireDeLErreur(e);
        const message = messageParametrageImport(e);
        setErreurs(cible ? { [cible]: message } : { general: message });
      }
      setEnvoi(false);
    }
  }

  const titre = edition ? 'Modifier le champ personnalisé' : 'Nouveau champ personnalisé';

  return (
    <div className="modal-backdrop" role="presentation" onClick={() => { if (!envoi) onFermer(); }}>
      <DialogPanel label={titre} className="modal modal--lg" onClose={() => { if (!envoi) onFermer(); }}>
        <form onSubmit={(e) => void soumettre(e)} noValidate>
          <div className="modal__head">
            <div>
              <h3 className="modal__title" style={{ margin: 0 }}>{titre}</h3>
              <p className="cell-meta" style={{ margin: 0 }}>Import « {typeLibelle} »</p>
            </div>
            <button type="button" className="modal__close" aria-label="Fermer" onClick={onFermer} disabled={envoi}>
              <Icon name="x" size={18} />
            </button>
          </div>
          <div className="modal__scroll">
            <div className="form-grid">
              <div>
                <label className="field-label" htmlFor="cp-libelle">Libellé</label>
                <input id="cp-libelle" className="field-input" value={libelle} maxLength={80} autoComplete="off" disabled={envoi}
                  aria-invalid={erreurs.libelle ? true : undefined} aria-describedby={erreurs.libelle ? 'cp-libelle-err' : undefined}
                  onChange={(e) => { setLibelle(e.target.value); setErreurs((x) => ({ ...x, libelle: undefined })); }} />
                {erreurs.libelle ? <p id="cp-libelle-err" className="field-error" role="alert">{erreurs.libelle}</p> : null}
              </div>
              <div>
                <label className="field-label" htmlFor="cp-type">Type de valeur</label>
                <select id="cp-type" className="field-input" value={typeValeur} disabled={envoi || typeFige}
                  aria-describedby="cp-type-aide" onChange={(e) => { setTypeValeur(e.target.value as TypeValeurPerso); setErreurs((x) => ({ ...x, type_valeur: undefined, regles: undefined })); }}>
                  {TYPES_VALEUR.map((t) => <option key={t.code} value={t.code}>{t.libelle}</option>)}
                </select>
                <p id="cp-type-aide" className="field-help">
                  {typeFige
                    ? `Le type ne peut plus changer : ${champ?.utilisations_libelle ?? 'des valeurs sont déjà enregistrées'}.`
                    : 'Le type ne pourra plus changer dès qu’une valeur sera enregistrée.'}
                </p>
                {erreurs.type_valeur ? <p className="field-error" role="alert">{erreurs.type_valeur}</p> : null}
              </div>
            </div>

            <label className="field-label" htmlFor="cp-finalite" style={{ marginTop: 12 }}>Finalité (pourquoi cette information est collectée)</label>
            <input id="cp-finalite" className="field-input" value={finalite} maxLength={200} autoComplete="off" disabled={envoi}
              aria-invalid={erreurs.finalite ? true : undefined} aria-describedby={erreurs.finalite ? 'cp-finalite-err' : undefined}
              onChange={(e) => { setFinalite(e.target.value); setErreurs((x) => ({ ...x, finalite: undefined })); }} />
            {erreurs.finalite ? <p id="cp-finalite-err" className="field-error" role="alert">{erreurs.finalite}</p> : null}

            {typeValeur === 'TEXTE' ? (
              <div style={{ marginTop: 12, maxWidth: 260 }}>
                <label className="field-label" htmlFor="cp-longueur">Longueur maximale (1 à 500)</label>
                <input id="cp-longueur" className="field-input" inputMode="numeric" value={longueurMax} disabled={envoi}
                  onChange={(e) => setLongueurMax(e.target.value)} />
              </div>
            ) : null}
            {typeValeur === 'NOMBRE' ? (
              <div className="form-grid" style={{ marginTop: 12 }}>
                <div>
                  <label className="field-label" htmlFor="cp-decimales">Décimales (0 à 4)</label>
                  <input id="cp-decimales" className="field-input" inputMode="numeric" value={decimales} disabled={envoi} onChange={(e) => setDecimales(e.target.value)} />
                </div>
                <div>
                  <label className="field-label" htmlFor="cp-min">Minimum</label>
                  <input id="cp-min" className="field-input" inputMode="decimal" value={minimum} disabled={envoi} onChange={(e) => setMinimum(e.target.value)} />
                </div>
                <div>
                  <label className="field-label" htmlFor="cp-max">Maximum</label>
                  <input id="cp-max" className="field-input" inputMode="decimal" value={maximum} disabled={envoi} onChange={(e) => setMaximum(e.target.value)} />
                </div>
              </div>
            ) : null}
            {typeValeur === 'DATE' ? (
              <div className="form-grid" style={{ marginTop: 12 }}>
                <div>
                  <label className="field-label" htmlFor="cp-date-min">Date minimale</label>
                  <SaisieDate id="cp-date-min" valeur={dateMin} onChange={setDateMin} disabled={envoi} />
                </div>
                <div>
                  <label className="field-label" htmlFor="cp-date-max">Date maximale</label>
                  <SaisieDate id="cp-date-max" valeur={dateMax} onChange={setDateMax} disabled={envoi} />
                </div>
              </div>
            ) : null}
            {erreurs.regles ? <p className="field-error" role="alert">{erreurs.regles}</p> : null}

            {listeBrouillon ? (
              <div style={{ marginTop: 12 }}>
                <ValeursListeBrouillon valeurs={brouillonListe} onChange={(v) => { setBrouillonListe(v); setErreurs((x) => ({ ...x, valeurs_liste: undefined })); }}
                  erreur={erreurs.valeurs_liste} disabled={envoi} />
              </div>
            ) : null}
            {listeEnDirect && champ ? (
              <div style={{ marginTop: 12 }}>
                <h4 className="detail-h3">Valeurs de la liste</h4>
                <p className="field-help">Les changements de valeurs sont enregistrés tout de suite.</p>
                <ValeursListeEditor type={type} code={champ.code} valeurs={champ.valeurs_liste ?? []} version={version}
                  onMaj={onMaj} onRecharger={onRecharger} />
              </div>
            ) : null}

            <label className="import__case" style={{ display: 'block', marginTop: 12 }}>
              <input type="checkbox" checked={visible} disabled={envoi} onChange={(e) => setVisible(e.target.checked)} />{' '}
              Visible par le collaborateur dans « Mon dossier »
            </label>

            {!edition ? (
              <div style={{ marginTop: 12 }}>
                <label className="import__case">
                  <input type="checkbox" checked={confirmation} disabled={envoi}
                    aria-describedby={erreurs.confirmation ? 'cp-confirm-err' : undefined}
                    onChange={(e) => { setConfirmation(e.target.checked); setErreurs((x) => ({ ...x, confirmation: undefined })); }} />{' '}
                  Je confirme que ce champ ne contient aucune donnée sensible (santé, religion, origine, opinions, situation familiale détaillée…).
                </label>
                {erreurs.confirmation ? <p id="cp-confirm-err" className="field-error" role="alert">{erreurs.confirmation}</p> : null}
              </div>
            ) : null}

            {erreurs.general ? <p className="alert alert--error" role="alert">{erreurs.general}</p> : null}
          </div>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onFermer} disabled={envoi}>Annuler</button>
            <button type="submit" className="btn btn--primary" disabled={envoi} aria-busy={envoi}>
              {envoi ? 'Enregistrement…' : edition ? 'Enregistrer' : 'Créer le champ'}
            </button>
          </div>
        </form>
      </DialogPanel>
    </div>
  );
}

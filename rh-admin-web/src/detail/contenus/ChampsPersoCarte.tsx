import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  ecrireValeursFiche,
  erreursParChamp,
  estValeursModifiees,
  getValeursFiche,
  messageValeursPerso,
  type ChampValeur,
  type ValeursFiche,
} from '../../api/valeursPersoApi';
import { ApiMetierError } from '../../api/metierApi';
import { useLectureSeule } from '../../auth/useLectureSeule';
import SaisieValeurPerso, { versSaisie } from '../../components/SaisieValeurPerso';
import { Icon } from '../../components/ui';
import { TIRET } from '../../utils/format';
import { Squelette } from '../Etats';
import ChampsPersoJournalDialog from './ChampsPersoJournalDialog';
import { Champ, Champs, Section } from './commun';

type Brouillon = Record<string, string>;

function brouillonDe(v: ValeursFiche): Brouillon {
  const b: Brouillon = {};
  v.champs.forEach((c) => { b[c.code] = versSaisie(c.type_valeur, c.valeur); });
  return b;
}

function libelleListeInactive(c: ChampValeur): string | null {
  return c.valeur_inactive ? c.valeur_affichee : null;
}

/** Carte « Informations complémentaires » de la fiche : champs personnalisés en lecture, modification selon `modifiable` (V1, V2, V3). */
export default function ChampsPersoCarte({ id }: { id: string }) {
  const lectureSeule = useLectureSeule();
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; erreur?: unknown }>({ statut: 'chargement' });
  const [valeurs, setValeurs] = useState<ValeursFiche | null>(null);
  const [edition, setEdition] = useState(false);
  const [brouillon, setBrouillon] = useState<Brouillon>({});
  const [erreurs, setErreurs] = useState<Record<string, string>>({});
  const [bandeau, setBandeau] = useState<{ genre: 'succes' | 'attention' | 'erreur'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [journal, setJournal] = useState(false);

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      const v = await getValeursFiche(id);
      setValeurs(v);
      setEtat({ statut: 'ok' });
    } catch (erreur) {
      setEtat({ statut: 'erreur', erreur });
    }
  }, [id]);

  useEffect(() => { setEdition(false); setBandeau(null); void charger(); }, [charger]);

  function ouvrirEdition() {
    if (!valeurs) return;
    setBrouillon(brouillonDe(valeurs));
    setErreurs({});
    setBandeau(null);
    setEdition(true);
  }

  async function enregistrer(ev: FormEvent) {
    ev.preventDefault();
    if (!valeurs || envoi) return;
    const initial = brouillonDe(valeurs);
    const changes: Record<string, string | null> = {};
    for (const c of valeurs.champs) {
      const v = (brouillon[c.code] ?? '').trim();
      if (v !== initial[c.code]) changes[c.code] = v === '' ? null : v;
    }
    if (Object.keys(changes).length === 0) { setEdition(false); return; }
    setEnvoi(true);
    setErreurs({});
    setBandeau(null);
    try {
      const v = await ecrireValeursFiche(id, valeurs.version, changes);
      setValeurs(v);
      setEdition(false);
      setBandeau({ genre: 'succes', texte: 'Informations enregistrées.' });
    } catch (e) {
      if (estValeursModifiees(e)) {
        // Conflit : on relit, puis on rejoue par-dessus les seules valeurs que l'utilisateur avait changées.
        try {
          const frais = await getValeursFiche(id);
          const reprise = brouillonDe(frais);
          Object.keys(changes).forEach((code) => { if (code in reprise) reprise[code] = versSaisie(frais.champs.find((c) => c.code === code)?.type_valeur ?? 'TEXTE', changes[code]); });
          setValeurs(frais);
          setBrouillon(reprise);
          setBandeau({ genre: 'attention', texte: `${messageValeursPerso(e)} Vos modifications sont conservées : vérifiez puis enregistrez de nouveau.` });
        } catch {
          setBandeau({ genre: 'erreur', texte: 'Les informations ont changé et n’ont pas pu être rechargées. Réessayez.' });
        }
      } else if (e instanceof ApiMetierError && e.status === 403) {
        setBandeau({ genre: 'erreur', texte: messageValeursPerso(e) });
        void charger();
        setEdition(false);
      } else {
        const parChamp = erreursParChamp(e);
        const connus = Object.keys(parChamp).filter((k) => valeurs.champs.some((c) => c.code === k));
        setErreurs(parChamp);
        if (connus.length === 0 || parChamp['']) setBandeau({ genre: 'erreur', texte: parChamp[''] ?? messageValeursPerso(e) });
        else setBandeau({ genre: 'erreur', texte: 'Les informations n’ont pas été enregistrées : corrigez les champs signalés.' });
      }
    } finally {
      setEnvoi(false);
    }
  }

  if (etat.statut === 'chargement') {
    return <Section titre="Informations complémentaires" icone="file"><Squelette /></Section>;
  }
  if (etat.statut === 'erreur' || !valeurs) {
    const refus = etat.erreur instanceof ApiMetierError && etat.erreur.status === 403;
    return (
      <Section titre="Informations complémentaires" icone="file">
        <div className="alert alert--error" role="alert">
          {refus ? 'Vous n’avez pas accès à ces informations.' : 'Impossible de charger les informations complémentaires.'}{' '}
          {refus ? null : <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>}
        </div>
      </Section>
    );
  }

  const peutModifier = valeurs.modifiable && !lectureSeule;
  const aucun = valeurs.champs.length === 0 && valeurs.champs_desactives.length === 0;

  return (
    <Section titre="Informations complémentaires" icone="file">
      {bandeau ? (
        <div className={`alert ${bandeau.genre === 'succes' ? 'alert--success' : bandeau.genre === 'attention' ? 'alert--warning' : 'alert--error'}`}
          role={bandeau.genre === 'erreur' ? 'alert' : 'status'}>{bandeau.texte}</div>
      ) : null}

      {aucun ? (
        <p className="detail-vide">Aucun champ personnalisé n’est défini. Les RH peuvent en créer dans Paramètres ▸ Import Excel.</p>
      ) : null}

      {valeurs.champs.length > 0 && !edition ? (
        <Champs>
          {valeurs.champs.map((c) => (
            <Champ key={c.code} label={c.libelle}>
              {c.valeur_affichee}
              {c.valeur_inactive ? <span className="param-ref">Valeur désactivée</span> : null}
            </Champ>
          ))}
        </Champs>
      ) : null}

      {edition ? (
        <form onSubmit={(e) => void enregistrer(e)} noValidate aria-label="Modifier les informations complémentaires">
          <div className="form-grid">
            {valeurs.champs.map((c) => {
              const idc = `cp-fiche-${c.code}`;
              return (
                <div key={c.code}>
                  <label className="field-label" htmlFor={idc}>{c.libelle}</label>
                  <SaisieValeurPerso id={idc} champ={c} valeur={brouillon[c.code] ?? ''} disabled={envoi}
                    erreur={erreurs[c.code]} libelleInactive={libelleListeInactive(c)}
                    onChange={(v) => { setBrouillon((b) => ({ ...b, [c.code]: v })); setErreurs((x) => { const { [c.code]: _r, ...reste } = x; return reste; }); }} />
                </div>
              );
            })}
          </div>
          <div className="import__actions">
            <button type="button" className="btn btn--ghost" disabled={envoi} onClick={() => { setEdition(false); setErreurs({}); }}>Annuler</button>
            <button type="submit" className="btn btn--primary" disabled={envoi} aria-busy={envoi}>{envoi ? 'Enregistrement…' : 'Enregistrer'}</button>
          </div>
        </form>
      ) : (
        <div className="import__actions" style={{ justifyContent: 'flex-start' }}>
          {peutModifier && valeurs.champs.length > 0 ? (
            <button type="button" className="btn btn--secondary btn--sm" onClick={ouvrirEdition}>Modifier</button>
          ) : null}
          {!aucun ? (
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setJournal(true)}>
              <Icon name="clock" size={14} /> Historique des valeurs
            </button>
          ) : null}
        </div>
      )}
      {!valeurs.modifiable && !lectureSeule && valeurs.raison_non_modifiable && valeurs.champs.length > 0 ? (
        <p className="muted small" role="status">{valeurs.raison_non_modifiable}</p>
      ) : null}

      {valeurs.champs_desactives.length > 0 ? (
        <div style={{ marginTop: 12 }}>
          <h4 className="detail-h3">Champs désactivés</h4>
          <p className="field-help">Ces champs ne sont plus utilisés ; leurs valeurs restent consultables mais ne sont plus modifiables.</p>
          <Champs>
            {valeurs.champs_desactives.map((c) => <Champ key={c.code} label={c.libelle}>{c.valeur_affichee ?? TIRET}</Champ>)}
          </Champs>
        </div>
      ) : null}

      {journal ? <ChampsPersoJournalDialog id={id} onFermer={() => setJournal(false)} /> : null}
    </Section>
  );
}

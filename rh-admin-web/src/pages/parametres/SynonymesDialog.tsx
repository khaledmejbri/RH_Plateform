import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  ajouterSynonyme,
  desactiverSynonyme,
  estConflit,
  getParametrageImport,
  MESSAGE_CONFLIT,
  messageParametrageImport,
  reactiverSynonyme,
  supprimerSynonyme,
  type ChampParametre,
  type ParametrageImport,
  type SynonymeImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { Chip } from '../../components/ui';

type Props = {
  type: TypeParametrable;
  champ: ChampParametre;
  version: number;
  lectureSeule: boolean;
  onMaj: (p: ParametrageImport) => void;
  onRecharger: () => Promise<void>;
  onFermer: () => void;
};

/** Synonymes reconnus d'un champ : ajout (P3), désactivation / réactivation (P4), suppression des synonymes RH (P5). */
export default function SynonymesDialog({ type, champ, version, lectureSeule, onMaj, onRecharger, onFermer }: Props) {
  const confirm = useConfirm();
  const [texte, setTexte] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [occupe, setOccupe] = useState(false);
  const fermerRef = useRef<HTMLButtonElement>(null);
  const zoneRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const precedent = document.activeElement as HTMLElement | null;
    fermerRef.current?.focus();
    return () => precedent?.focus();
  }, []);

  function touche(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      e.stopPropagation();
      if (!occupe) onFermer();
      return;
    }
    if (e.key !== 'Tab' || !zoneRef.current) return;
    const cibles = Array.from(zoneRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href]'));
    if (cibles.length === 0) return;
    const premier = cibles[0];
    const dernier = cibles[cibles.length - 1];
    if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
    else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
  }

  /** Exécute une écriture ; un conflit de version recharge le paramétrage et le dit. */
  async function ecrire(action: () => Promise<ParametrageImport | void>, succes: string): Promise<boolean> {
    setOccupe(true);
    setErreur(null);
    setInfo(null);
    try {
      const r = await action();
      if (r) onMaj(r);
      else await onRecharger();
      setInfo(succes);
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
    const ok = await ecrire(() => ajouterSynonyme(type, champ.code, t, version), `« ${t} » est maintenant reconnu pour ce champ.`);
    if (ok) setTexte('');
  }

  async function basculer(s: SynonymeImport) {
    if (occupe) return;
    if (s.actif) {
      const ok = await confirm({
        title: 'Désactiver ce synonyme ?',
        message: `Désactiver « ${s.texte} » pour le champ « ${champ.libelle} » ? Une colonne portant ce nom ne sera plus reconnue.`,
        confirmLabel: 'Désactiver',
      });
      if (!ok) return;
      await ecrire(() => desactiverSynonyme(type, s.id, version), `« ${s.texte} » désactivé.`);
    } else {
      await ecrire(() => reactiverSynonyme(type, s.id, version), `« ${s.texte} » réactivé.`);
    }
  }

  async function supprimer(s: SynonymeImport) {
    if (occupe) return;
    const ok = await confirm({
      title: 'Supprimer ce synonyme ?',
      message: `Supprimer « ${s.texte} » du champ « ${champ.libelle} » ? Cette action est définitive.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    // 204 sans corps : on relit ensuite le paramétrage pour obtenir la nouvelle version.
    await ecrire(async () => {
      await supprimerSynonyme(type, s.id, version);
      onMaj(await getParametrageImport(type));
    }, `« ${s.texte} » supprimé.`);
  }

  const titreId = `syn-titre-${champ.code}`;

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !occupe && onFermer()}>
      <div ref={zoneRef} className="modal" role="dialog" aria-modal="true" aria-labelledby={titreId} onKeyDown={touche}
        style={{ maxWidth: 720, width: '100%' }}>
        <h2 id={titreId} className="modal__title">Synonymes du champ « {champ.libelle} »</h2>
        <div className="modal__body">
          <p className="field-help">
            Une colonne du fichier est reconnue si son nom correspond à l’en-tête du modèle ou à l’un des synonymes actifs
            (sans tenir compte des majuscules, des accents ni de la ponctuation).
          </p>

          {!lectureSeule ? (
            <form onSubmit={(e) => void ajouter(e)} aria-label="Ajouter un synonyme">
              <label className="field-label" htmlFor={`syn-texte-${champ.code}`}>Nouveau synonyme</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <input id={`syn-texte-${champ.code}`} className="field-input" value={texte} maxLength={80} autoComplete="off"
                  disabled={occupe} aria-describedby={erreur ? 'syn-erreur' : undefined}
                  onChange={(e) => { setTexte(e.target.value); setErreur(null); }} />
                <button type="submit" className="btn btn--primary btn--sm" disabled={occupe || texte.trim().length === 0}>Ajouter</button>
              </div>
            </form>
          ) : null}
          {erreur ? <p id="syn-erreur" className="field-error" role="alert">{erreur}</p> : null}
          {info && !erreur ? <p className="muted small" role="status">{info}</p> : null}

          {champ.synonymes.length === 0 ? (
            <p className="detail-vide">Aucun synonyme pour ce champ.</p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <caption className="sr-only">Synonymes du champ {champ.libelle}</caption>
                <thead>
                  <tr>
                    <th scope="col">Synonyme</th>
                    <th scope="col">Forme reconnue</th>
                    <th scope="col">Origine</th>
                    <th scope="col">État</th>
                    {!lectureSeule ? <th scope="col"><span className="sr-only">Actions</span></th> : null}
                  </tr>
                </thead>
                <tbody>
                  {champ.synonymes.map((s) => (
                    <tr key={s.id} className={s.actif ? undefined : 'param-ligne--inactive'}>
                      <td>{s.texte}</td>
                      <td>{s.forme_normalisee}</td>
                      <td>{s.origine_libelle}</td>
                      <td>{s.actif ? <Chip tone="success">Actif</Chip> : <Chip>Désactivé</Chip>}</td>
                      {!lectureSeule ? (
                        <td className="param-actions">
                          <button type="button" className="btn btn--ghost btn--sm" disabled={occupe}
                            aria-label={`${s.actif ? 'Désactiver' : 'Réactiver'} ${s.texte}`} onClick={() => void basculer(s)}>
                            {s.actif ? 'Désactiver' : 'Réactiver'}
                          </button>
                          {s.supprimable ? (
                            <button type="button" className="btn btn--ghost btn--sm" disabled={occupe}
                              aria-label={`Supprimer ${s.texte}`} onClick={() => void supprimer(s)}>Supprimer</button>
                          ) : null}
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="modal__actions">
          <button ref={fermerRef} type="button" className="btn btn--ghost" disabled={occupe} onClick={onFermer}>Fermer</button>
        </div>
      </div>
    </div>
  );
}

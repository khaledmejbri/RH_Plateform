import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  enregistrerParametrage,
  estConflit,
  getParametrageImport,
  champDeLErreur,
  messageParametrageImport,
  MESSAGE_CONFLIT,
  reinitialiserParametrage,
  type ChampParametre,
  type Exigence,
  type ParametrageImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { telechargerModele } from '../../api/importsApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { Chip, EmptyState, Icon, LoadingState } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import { pluriel } from '../../utils/filtres';
import SynonymesDialog from './SynonymesDialog';

type Brouillon = { code: string; en_tete: string; dans_modele: boolean; exigence: Exigence };

type Bandeau = { genre: 'succes' | 'erreur' | 'modifie'; message: string; details?: string[] };

type Props = {
  type: TypeParametrable;
  typeLibelle: string;
  typeImport: 'COLLABORATEURS' | 'SOLDES';
  lectureSeule: boolean;
  /** Signale au panneau parent que des modifications ne sont pas enregistrées. */
  onModifie: (modifie: boolean) => void;
};

const RAISON_HORS_MODELE = 'Un champ obligatoire reste toujours dans le modèle.';

function versBrouillon(p: ParametrageImport): Brouillon[] {
  // Les champs personnalisés inactifs sont hors modèle : ils se gèrent dans l'onglet « Champs personnalisés ».
  return p.champs
    .filter((c) => c.actif !== false)
    .sort((a, b) => (a.ordre ?? Number.MAX_SAFE_INTEGER) - (b.ordre ?? Number.MAX_SAFE_INTEGER))
    .map((c) => ({ code: c.code, en_tete: c.en_tete, dans_modele: c.dans_modele, exigence: c.exigence }));
}

/** Tableau des champs d'un type d'import : en-tête, présence dans le modèle, ordre, exigence (P1, P2, P6). */
export default function ImportChampsTable({ type, typeLibelle, typeImport, lectureSeule, onModifie }: Props) {
  const confirm = useConfirm();
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; message?: string }>({ statut: 'chargement' });
  const [param, setParam] = useState<ParametrageImport | null>(null);
  const [brouillon, setBrouillon] = useState<Brouillon[]>([]);
  const [bandeau, setBandeau] = useState<Bandeau | null>(null);
  const [erreursChamps, setErreursChamps] = useState<Record<string, string>>({});
  const [envoi, setEnvoi] = useState(false);
  const [codeSynonymes, setCodeSynonymes] = useState<string | null>(null);
  const [erreurModele, setErreurModele] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      const p = await getParametrageImport(type);
      setParam(p);
      setBrouillon(versBrouillon(p));
      setEtat({ statut: 'ok' });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageParametrageImport(e) });
    }
  }, [type]);

  useEffect(() => { void charger(); }, [charger]);

  const parCode = useMemo(() => {
    const m = new Map<string, ChampParametre>();
    param?.champs.forEach((c) => m.set(c.code, c));
    return m;
  }, [param]);

  const modifie = useMemo(
    () => !!param && JSON.stringify(brouillon) !== JSON.stringify(versBrouillon(param)),
    [param, brouillon],
  );

  useEffect(() => { onModifie(modifie); }, [modifie, onModifie]);
  useEffect(() => () => onModifie(false), [onModifie]);

  useEffect(() => {
    if (!modifie) return undefined;
    const garde = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener('beforeunload', garde);
    return () => window.removeEventListener('beforeunload', garde);
  }, [modifie]);

  /** Contrôle local de D-I17 (champ obligatoire hors modèle) avant envoi ; le serveur reste juge. */
  const erreursLocales = useMemo(() => {
    const r: Record<string, string> = {};
    for (const b of brouillon) {
      if (!b.dans_modele && b.exigence !== 'FACULTATIF') {
        r[b.code] = 'Un champ obligatoire doit figurer dans le modèle : cochez « Dans le modèle » ou choisissez « Facultatif ».';
      }
    }
    return r;
  }, [brouillon]);

  function maj(code: string, patch: Partial<Brouillon>) {
    setBandeau(null);
    setErreursChamps((e) => { const { [code]: _retire, ...reste } = e; return reste; });
    setBrouillon((l) => l.map((b) => (b.code === code ? { ...b, ...patch } : b)));
  }

  function deplacer(index: number, sens: -1 | 1) {
    const cible = index + sens;
    if (cible < 0 || cible >= brouillon.length) return;
    setBandeau(null);
    setBrouillon((l) => {
      const copie = [...l];
      [copie[index], copie[cible]] = [copie[cible], copie[index]];
      return copie;
    });
  }

  function annuler() {
    if (!param) return;
    setBrouillon(versBrouillon(param));
    setErreursChamps({});
    setBandeau(null);
  }

  function libelleChamp(code: string): string {
    return parCode.get(code)?.libelle ?? code;
  }

  /** Résumé en clair de la saisie perdue (rappelée après un conflit de version). */
  function resumeSaisie(): string[] {
    if (!param) return [];
    const initial = new Map(versBrouillon(param).map((b) => [b.code, b]));
    const lignes: string[] = [];
    brouillon.forEach((b, i) => {
      const o = initial.get(b.code);
      if (!o) return;
      if (b.en_tete !== o.en_tete) lignes.push(`${libelleChamp(b.code)} : en-tête « ${b.en_tete} »`);
      if (b.dans_modele !== o.dans_modele) lignes.push(`${libelleChamp(b.code)} : ${b.dans_modele ? 'ajouté au' : 'retiré du'} modèle`);
      if (b.exigence !== o.exigence) {
        lignes.push(`${libelleChamp(b.code)} : ${parCode.get(b.code)?.exigences_possibles.find((x) => x.code === b.exigence)?.libelle ?? 'exigence modifiée'}`);
      }
      if (versBrouillon(param)[i]?.code !== b.code && !lignes.includes('Ordre des champs modifié')) lignes.push('Ordre des champs modifié');
    });
    return lignes;
  }

  async function enregistrer() {
    if (!param || envoi || !modifie || lectureSeule) return;
    if (Object.keys(erreursLocales).length > 0) {
      setErreursChamps(erreursLocales);
      setBandeau({ genre: 'erreur', message: 'Corrigez les lignes signalées avant d’enregistrer.' });
      return;
    }
    const retires = brouillon.filter((b) => !b.dans_modele && parCode.get(b.code)?.dans_modele);
    if (retires.length > 0) {
      const ok = await confirm({
        title: 'Retirer du modèle ?',
        message: (
          <>
            <p>Retirer du modèle : {retires.map((b) => `« ${libelleChamp(b.code)} »`).join(', ')}.</p>
            <p>La colonne restera reconnue si un fichier la contient.</p>
          </>
        ),
        confirmLabel: 'Enregistrer',
      });
      if (!ok) return;
    }
    setEnvoi(true);
    setBandeau(null);
    setErreursChamps({});
    try {
      const p = await enregistrerParametrage(type, param.version, brouillon);
      setParam(p);
      setBrouillon(versBrouillon(p));
      setBandeau({ genre: 'succes', message: 'Paramétrage enregistré.' });
    } catch (e) {
      if (estConflit(e)) {
        const perdu = resumeSaisie();
        await charger();
        setBandeau({ genre: 'modifie', message: MESSAGE_CONFLIT + ' Le tableau a été rechargé.', details: perdu.length > 0 ? perdu : undefined });
      } else {
        const code = champDeLErreur(e);
        if (code) setErreursChamps({ [code]: messageParametrageImport(e) });
        setBandeau({ genre: 'erreur', message: code ? 'Le paramétrage n’a pas été enregistré : voir la ligne signalée.' : messageParametrageImport(e) });
      }
    } finally {
      setEnvoi(false);
    }
  }

  async function reinitialiser() {
    if (!param || envoi || lectureSeule) return;
    const nbRh = param.champs.reduce((n, c) => n + c.synonymes.filter((s) => s.origine === 'RH').length, 0);
    let echec: unknown = null;
    const ok = await confirm({
      title: 'Rétablir les valeurs par défaut ?',
      message: (
        <>
          <p>
            Rétablir les valeurs par défaut de l’import « {typeLibelle} » : en-têtes, colonnes du modèle, ordre,
            exigences et synonymes par défaut.
          </p>
          <p>
            {nbRh === 0
              ? 'Aucun synonyme ajouté par la RH ne sera supprimé.'
              : `${pluriel(nbRh, 'synonyme ajouté par la RH')} ${nbRh > 1 ? 'seront supprimés' : 'sera supprimé'}.`}
            {modifie ? ' Les modifications non enregistrées seront perdues.' : ''}
          </p>
        </>
      ),
      confirmLabel: 'Rétablir',
      danger: true,
      envoyer: async () => {
        try {
          const p = await reinitialiserParametrage(type, param.version);
          setParam(p);
          setBrouillon(versBrouillon(p));
        } catch (e) {
          echec = e;
          throw new Error(messageParametrageImport(e));
        }
      },
      echecDefinitif: () => estConflit(echec),
    });
    if (echec && !ok) {
      if (estConflit(echec)) {
        await charger();
        setBandeau({ genre: 'modifie', message: MESSAGE_CONFLIT + ' Le tableau a été rechargé, rien n’a été rétabli.' });
      }
      return;
    }
    if (ok) {
      setErreursChamps({});
      setBandeau({ genre: 'succes', message: 'Valeurs par défaut rétablies.' });
    }
  }

  async function modele() {
    setErreurModele(null);
    try {
      await telechargerModele(typeImport);
    } catch (e) {
      setErreurModele(messageParametrageImport(e));
    }
  }

  if (etat.statut === 'chargement') return <LoadingState label="Chargement du paramétrage…" />;
  if (etat.statut === 'erreur' || !param) {
    return (
      <div className="alert alert--error" role="alert">
        {etat.message ?? 'Le paramétrage est momentanément indisponible.'} Rien n’a été modifié.{' '}
        <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
      </div>
    );
  }
  if (param.champs.length === 0) {
    return <EmptyState icon="file" title="Aucun champ à paramétrer">Ce type d’import n’a pas de champ réglable.</EmptyState>;
  }

  const champSynonymes = codeSynonymes ? parCode.get(codeSynonymes) ?? null : null;

  return (
    <div>
      <p className="field-help">
        Réglez les colonnes du modèle Excel, leur ordre, les champs exigés et les noms de colonnes reconnus. Les champs fixes ne changent pas ; ajoutez les vôtres dans l’onglet « Champs personnalisés ».
        {param.modifie_le ? ` Dernière modification le ${formatDateHeure(param.modifie_le)}${param.modifie_par_nom ? ` par ${param.modifie_par_nom}` : ''}.` : ''}
      </p>

      {lectureSeule ? <div className="alert alert--info" role="status">Consultation uniquement.</div> : null}
      {bandeau ? (
        <div className={`alert ${bandeau.genre === 'succes' ? 'alert--success' : bandeau.genre === 'modifie' ? 'alert--warning' : 'alert--error'}`}
          role={bandeau.genre === 'succes' ? 'status' : 'alert'}>
          {bandeau.message}
          {bandeau.details ? (
            <>
              <br />Vos modifications non enregistrées étaient :
              <ul className="import__liste">{bandeau.details.map((d, i) => <li key={i}>{d}</li>)}</ul>
            </>
          ) : null}
        </div>
      ) : null}

      <div className="table-wrap">
        <table className="data-table param-table">
          <caption className="sr-only">Champs de l’import {typeLibelle}</caption>
          <thead>
            <tr>
              <th scope="col">Ordre</th>
              <th scope="col">Champ</th>
              <th scope="col">Dans le modèle</th>
              <th scope="col">En-tête du modèle</th>
              <th scope="col">Exigence</th>
              <th scope="col">Synonymes</th>
            </tr>
          </thead>
          <tbody>
            {brouillon.map((b, i) => {
              const c = parCode.get(b.code);
              if (!c) return null;
              const montrerErreur = erreursChamps[b.code] ?? (Object.keys(erreursChamps).length > 0 ? erreursLocales[b.code] : undefined);
              const idEntete = `imp-${type}-${b.code}-entete`;
              const idErreur = `imp-${type}-${b.code}-erreur`;
              const actifs = c.synonymes.filter((s) => s.actif).length;
              const exigenceFigee = c.exigences_possibles.length <= 1;
              return (
                <tr key={b.code} aria-invalid={montrerErreur ? true : undefined}>
                  <td className="param-poignee">
                    <span className="param-deplacer">
                      <button type="button" className="btn btn--ghost btn--sm" disabled={lectureSeule || envoi || i === 0}
                        aria-label={`Monter ${c.libelle}`} onClick={() => deplacer(i, -1)}>▲</button>
                      <button type="button" className="btn btn--ghost btn--sm" disabled={lectureSeule || envoi || i === brouillon.length - 1}
                        aria-label={`Descendre ${c.libelle}`} onClick={() => deplacer(i, 1)}>▼</button>
                    </span>
                  </td>
                  <th scope="row">
                    {c.libelle}
                    {c.personnalise ? <span style={{ marginLeft: 6 }}><Chip tone="info">Personnalisé</Chip></span> : null}
                    {c.verrouille ? (
                      <span title={c.verrou_libelle ?? undefined} style={{ marginLeft: 6 }}>
                        <Icon name="lock" size={14} label={c.verrou_libelle ?? 'Champ verrouillé'} />
                      </span>
                    ) : null}
                    {c.verrouille && c.verrou_libelle ? <span className="param-ref">{c.verrou_libelle}</span> : null}
                  </th>
                  <td>
                    <label>
                      <input type="checkbox" checked={b.dans_modele}
                        disabled={lectureSeule || envoi || !c.retirable_du_modele}
                        aria-label={`Dans le modèle : ${c.libelle}`}
                        onChange={(e) => maj(b.code, { dans_modele: e.target.checked })} />
                    </label>
                    {!c.retirable_du_modele ? <span className="param-ref">{RAISON_HORS_MODELE}</span> : null}
                  </td>
                  <td>
                    <label className="sr-only" htmlFor={idEntete}>En-tête du modèle pour {c.libelle}</label>
                    <input id={idEntete} className="field-input" value={b.en_tete} maxLength={80} autoComplete="off"
                      disabled={lectureSeule || envoi} aria-describedby={montrerErreur ? idErreur : undefined}
                      onChange={(e) => maj(b.code, { en_tete: e.target.value })} />
                    <span className="param-ref">Par défaut : {c.en_tete_defaut}</span>
                    {montrerErreur ? <p id={idErreur} className="field-error" role="alert">{montrerErreur}</p> : null}
                  </td>
                  <td>
                    <label className="sr-only" htmlFor={`imp-${type}-${b.code}-exigence`}>Exigence pour {c.libelle}</label>
                    <select id={`imp-${type}-${b.code}-exigence`} className="field-input" value={b.exigence}
                      disabled={lectureSeule || envoi || exigenceFigee}
                      onChange={(e) => maj(b.code, { exigence: e.target.value as Exigence })}>
                      {c.exigences_possibles.map((x) => <option key={x.code} value={x.code}>{x.libelle}</option>)}
                    </select>
                    {exigenceFigee && c.verrou_libelle ? <span className="param-ref">{c.verrou_libelle}</span> : null}
                  </td>
                  <td>
                    <button type="button" className="btn btn--ghost btn--sm" aria-label={`Synonymes de ${c.libelle}`}
                      onClick={() => setCodeSynonymes(b.code)}>
                      {actifs} / {c.synonymes.length}{lectureSeule ? ' (voir)' : ' (gérer)'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {!lectureSeule ? (
        <div className="import__actions">
          <button type="button" className="btn btn--ghost btn--sm" disabled={envoi} onClick={() => void modele()}>
            <Icon name="download" size={14} /> Télécharger le modèle
          </button>
          <button type="button" className="btn btn--ghost" disabled={envoi} onClick={() => void reinitialiser()}>
            Rétablir les valeurs par défaut
          </button>
          <button type="button" className="btn btn--ghost" disabled={envoi || !modifie} onClick={annuler}>
            Annuler les modifications
          </button>
          <button type="button" className="btn btn--primary" disabled={envoi || !modifie} aria-busy={envoi} onClick={() => void enregistrer()}>
            {envoi ? 'Enregistrement…' : 'Enregistrer'}
          </button>
        </div>
      ) : null}
      {modifie && !lectureSeule ? <p className="muted small" role="status">Modifications non enregistrées.</p> : null}
      {erreurModele ? <p className="field-error" role="alert">{erreurModele}</p> : null}

      {champSynonymes && param ? (
        <SynonymesDialog
          type={type}
          champ={champSynonymes}
          version={param.version}
          lectureSeule={lectureSeule}
          onMaj={(p) => setParam(p)}
          onRecharger={async () => {
            try {
              setParam(await getParametrageImport(type));
            } catch {
              /* la liste affichée reste celle d'avant ; la prochaine écriture signalera un conflit */
            }
          }}
          onFermer={() => setCodeSynonymes(null)}
        />
      ) : null}
    </div>
  );
}

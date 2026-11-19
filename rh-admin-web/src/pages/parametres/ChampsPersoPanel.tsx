import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  desactiverChampPerso,
  reactiverChampPerso,
  supprimerChampPerso,
} from '../../api/champsPersoApi';
import {
  estConflit,
  getParametrageImport,
  MESSAGE_CONFLIT,
  messageParametrageImport,
  TYPES_PARAMETRABLES,
  type ChampParametre,
  type ParametrageImport,
  type TypeParametrable,
} from '../../api/importParametrageApi';
import { useConfirm } from '../../components/ConfirmDialog';
import { Chip, EmptyState, LoadingState } from '../../components/ui';
import ChampPersoFormDialog from './ChampPersoFormDialog';

type Props = { lectureSeule: boolean };

type Bandeau = { genre: 'succes' | 'erreur' | 'attention'; message: string };

/** Champs personnalisés d'un type d'import : création, modification, désactivation, suppression (C1 à C6). */
export default function ChampsPersoPanel({ lectureSeule }: Props) {
  const confirm = useConfirm();
  const [type, setType] = useState<TypeParametrable>('collaborateurs');
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; message?: string }>({ statut: 'chargement' });
  const [param, setParam] = useState<ParametrageImport | null>(null);
  const [bandeau, setBandeau] = useState<Bandeau | null>(null);
  const [formulaire, setFormulaire] = useState<{ code: string | null } | null>(null);
  const [occupe, setOccupe] = useState(false);

  const typeLibelle = TYPES_PARAMETRABLES.find((t) => t.type === type)?.libelle ?? '';

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      setParam(await getParametrageImport(type));
      setEtat({ statut: 'ok' });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageParametrageImport(e) });
    }
  }, [type]);

  useEffect(() => { setBandeau(null); setFormulaire(null); void charger(); }, [charger]);

  /** Relecture silencieuse (la liste reste affichée si elle échoue). */
  const relire = useCallback(async () => {
    try { setParam(await getParametrageImport(type)); } catch { /* la prochaine écriture signalera un conflit */ }
  }, [type]);

  const champs = useMemo(() => (param?.champs ?? []).filter((c) => c.personnalise), [param]);
  const actifs = champs.filter((c) => c.actif !== false);
  const inactifs = champs.filter((c) => c.actif === false);

  async function agir(action: () => Promise<ParametrageImport | void>, succes: string) {
    if (occupe) return;
    setOccupe(true);
    setBandeau(null);
    try {
      const r = await action();
      if (r) setParam(r);
      else await relire();
      setBandeau({ genre: 'succes', message: succes });
    } catch (e) {
      if (estConflit(e)) {
        await relire();
        setBandeau({ genre: 'attention', message: `${MESSAGE_CONFLIT} La liste a été rechargée, vérifiez puis recommencez.` });
      } else {
        setBandeau({ genre: 'erreur', message: messageParametrageImport(e) });
      }
    } finally {
      setOccupe(false);
    }
  }

  async function desactiver(c: ChampParametre) {
    if (!param) return;
    const ok = await confirm({
      title: 'Désactiver le champ ?',
      message: `« ${c.libelle} » ne sera plus reconnu à l’import ni proposé dans le modèle, les filtres ou l’export. Les valeurs déjà enregistrées sont conservées.`,
      confirmLabel: 'Désactiver',
    });
    if (ok) await agir(() => desactiverChampPerso(type, c.code, param.version), `Champ « ${c.libelle} » désactivé.`);
  }

  async function supprimer(c: ChampParametre) {
    if (!param) return;
    const ok = await confirm({
      title: 'Supprimer le champ ?',
      message: `Supprimer définitivement le champ « ${c.libelle} » ? Cette action est irréversible. Elle n’est possible que si aucune valeur n’est enregistrée.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (ok) await agir(() => supprimerChampPerso(type, c.code, param.version), `Champ « ${c.libelle} » supprimé.`);
  }

  function ligne(c: ChampParametre) {
    const actif = c.actif !== false;
    return (
      <tr key={c.code} className={actif ? undefined : 'param-ligne--inactive'}>
        <th scope="row">
          {c.libelle}
          <span className="param-ref">{c.utilisations_libelle ?? 'Aucune valeur enregistrée'}</span>
        </th>
        <td>
          {c.type_valeur_libelle ?? '—'}
          {c.regles_libelle ? <span className="param-ref">{c.regles_libelle}</span> : null}
        </td>
        <td>{c.finalite ?? '—'}</td>
        <td>{c.visible_collaborateur ? 'Oui' : 'Non'}</td>
        <td>{actif ? <Chip tone="success">Actif</Chip> : <Chip>Désactivé</Chip>}</td>
        {!lectureSeule ? (
          <td className="param-actions">
            {actif ? (
              <>
                <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`Modifier ${c.libelle}`}
                  onClick={() => setFormulaire({ code: c.code })}>Modifier</button>
                <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`Désactiver ${c.libelle}`}
                  onClick={() => void desactiver(c)}>Désactiver</button>
              </>
            ) : (
              <button type="button" className="btn btn--ghost btn--sm" disabled={occupe} aria-label={`Réactiver ${c.libelle}`}
                onClick={() => param && void agir(() => reactiverChampPerso(type, c.code, param.version), `Champ « ${c.libelle} » réactivé.`)}>Réactiver</button>
            )}
            <button type="button" className="btn btn--ghost btn--sm"
              disabled={occupe || c.supprimable === false}
              title={c.supprimable === false ? `Suppression impossible : ${c.utilisations_libelle ?? 'des valeurs sont enregistrées'}. Désactivez le champ.` : undefined}
              aria-label={`Supprimer ${c.libelle}`} onClick={() => void supprimer(c)}>Supprimer</button>
          </td>
        ) : null}
      </tr>
    );
  }

  const champEdite = formulaire?.code ? champs.find((c) => c.code === formulaire.code) : undefined;

  return (
    <div>
      <div style={{ maxWidth: 260, marginBottom: 12 }}>
        <label className="field-label" htmlFor="champs-perso-type">Type d’import</label>
        <select id="champs-perso-type" className="field-input" value={type} disabled={occupe}
          onChange={(e) => setType(e.target.value as TypeParametrable)}>
          {TYPES_PARAMETRABLES.map((t) => <option key={t.type} value={t.type}>{t.libelle}</option>)}
        </select>
      </div>
      <p className="field-help">
        Les champs personnalisés ajoutent des colonnes au modèle Excel et des informations à la fiche
        {type === 'soldes' ? ' (valeurs portées par les ajustements de solde)' : ' du collaborateur'}. Ne collectez aucune donnée sensible.
      </p>

      {lectureSeule ? <div className="alert alert--info" role="status">Consultation uniquement.</div> : null}
      {bandeau ? (
        <div className={`alert ${bandeau.genre === 'succes' ? 'alert--success' : bandeau.genre === 'attention' ? 'alert--warning' : 'alert--error'}`}
          role={bandeau.genre === 'succes' ? 'status' : 'alert'}>{bandeau.message}</div>
      ) : null}

      {etat.statut === 'chargement' ? <LoadingState label="Chargement des champs personnalisés…" /> : null}
      {etat.statut === 'erreur' ? (
        <div className="alert alert--error" role="alert">
          {etat.message ?? 'Les champs personnalisés sont momentanément indisponibles.'} Rien n’a été modifié.{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
        </div>
      ) : null}

      {etat.statut === 'ok' && param ? (
        <>
          {!lectureSeule ? (
            <div className="import__actions" style={{ justifyContent: 'flex-start' }}>
              <button type="button" className="btn btn--primary" disabled={occupe} onClick={() => setFormulaire({ code: null })}>
                Nouveau champ personnalisé
              </button>
            </div>
          ) : null}
          {champs.length === 0 ? (
            <EmptyState icon="file" title="Aucun champ personnalisé">
              {lectureSeule ? 'Aucun champ personnalisé n’a été créé pour cet import.' : 'Créez un champ pour ajouter une information propre à votre organisation.'}
            </EmptyState>
          ) : (
            <div className="table-wrap">
              <table className="data-table param-table">
                <caption className="sr-only">Champs personnalisés de l’import {typeLibelle}</caption>
                <thead>
                  <tr>
                    <th scope="col">Champ</th>
                    <th scope="col">Type</th>
                    <th scope="col">Finalité</th>
                    <th scope="col">Visible par le collaborateur</th>
                    <th scope="col">État</th>
                    {!lectureSeule ? <th scope="col"><span className="sr-only">Actions</span></th> : null}
                  </tr>
                </thead>
                <tbody>{[...actifs, ...inactifs].map(ligne)}</tbody>
              </table>
            </div>
          )}
        </>
      ) : null}

      {formulaire && param && !lectureSeule && (formulaire.code === null || champEdite) ? (
        <ChampPersoFormDialog
          key={formulaire.code ?? 'nouveau'}
          type={type}
          typeLibelle={typeLibelle}
          champ={champEdite}
          version={param.version}
          onMaj={(p) => { setParam(p); setBandeau({ genre: 'succes', message: champEdite ? 'Champ enregistré.' : 'Champ créé : il figure maintenant dans le modèle d’import.' }); }}
          onRecharger={relire}
          onFermer={() => setFormulaire(null)}
        />
      ) : null}
    </div>
  );
}

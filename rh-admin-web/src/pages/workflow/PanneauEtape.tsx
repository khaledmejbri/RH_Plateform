import { useState } from 'react';
import type { FonctionRh } from '../../api/societeApi';
import type {
  CatalogueWorkflow,
  EcheanceDefinition,
  ErreurDefinition,
  EtapeDefinition,
  IntervenantsDefinition,
  RelancesDefinition,
} from '../../api/workflowApi';
import { rattacherErreurs } from '../../workflow/erreurs';
import {
  completerEtape,
  intervenantsParDefaut,
  NB_TACHES_MAX,
  NB_TACHES_MIN,
  nouvelleTacheParDefaut,
  normaliserEtapePourType,
  optionsPublics,
} from '../../workflow/etapes';
import { libelleNiveauErreur, libellePublic, libelleRole, TYPES_ETAPE } from '../../workflow/libelles';
import ConstructeurCondition from './ConstructeurCondition';

type OngletEtape = 'general' | 'intervenants' | 'condition' | 'delais' | 'notifications';

const TYPES_NOEUD: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'DEPARTEMENT', libelle: 'Département' },
  { code: 'DIRECTION', libelle: 'Direction' },
  { code: 'DIRECTION_GENERALE', libelle: 'Direction générale' },
];

const ROLES_DISPONIBLES = ['RH', 'ADMIN'] as const;
const ACTIONS_VALIDATION = ['APPROUVER', 'REFUSER', 'RENVOYER'] as const;
const UNITES_DELAI: ReadonlyArray<{ code: 'HEURES' | 'JOURS_OUVRES'; libelle: string }> = [
  { code: 'HEURES', libelle: 'Heures (calendaires)' },
  { code: 'JOURS_OUVRES', libelle: 'Jours ouvrés' },
];
/** Bornes de DefinitionValidateur.validerDelaiEtEcheance. */
const DUREE_MAX: Readonly<Record<string, number>> = { HEURES: 720, JOURS_OUVRES: 30 };
const RELANCES_MAX = 3;
const INTERVALLE_RELANCE_MAX_HEURES = 168;
const AVANT_ECHEANCE_MAX_HEURES = 720;

const ONGLET_DU_CHAMP: Readonly<Record<string, OngletEtape>> = {
  code: 'general',
  libelle: 'general',
  type: 'general',
  actions: 'general',
  controle_avant_approbation: 'general',
  commentaire_obligatoire_si: 'general',
  intervenants: 'intervenants',
  repli_si_vide: 'intervenants',
  taches: 'intervenants',
  public: 'intervenants',
  condition: 'condition',
  delai: 'delais',
  relances: 'delais',
  a_echeance: 'delais',
  notifications: 'notifications',
};

function racineChamp(champ: string | null): string {
  return champ ? champ.split(/[.[]/)[0] : '';
}

type Props = {
  etape: EtapeDefinition;
  catalogue: CatalogueWorkflow;
  objetType: string;
  fonctionsActives: FonctionRh[];
  erreursEtape: ErreurDefinition[];
  disabled?: boolean;
  onChange: (maj: EtapeDefinition) => void;
};

/** Erreurs de validation affichées près du champ concerné. */
function ErreursChamp({ erreurs }: { erreurs: ErreurDefinition[] }) {
  if (erreurs.length === 0) return null;
  return (
    <ul className="small field-error" style={{ margin: '4px 0 0 16px', padding: 0 }}>
      {erreurs.map((e, i) => (
        <li key={i}>
          {e.niveau === 'AVERTISSEMENT' ? `${libelleNiveauErreur(e.niveau)} : ` : ''}
          {e.message}
        </li>
      ))}
    </ul>
  );
}

export default function PanneauEtape({
  etape,
  catalogue,
  objetType,
  fonctionsActives,
  erreursEtape,
  disabled,
  onChange,
}: Props) {
  const [onglet, setOnglet] = useState<OngletEtape>('general');
  const typeObjet = catalogue.types.find((t) => t.objet_type === objetType);
  const varsDate = (typeObjet?.variables ?? []).filter((v) => v.type === 'DATE' || v.type === 'DATE_HEURE');

  /** Toute modification écrit aussi les valeurs par défaut affichées (intervenant, mode, repli). */
  const maj = (partiel: Partial<EtapeDefinition>) => onChange(completerEtape({ ...etape, ...partiel }));

  const intervenants: IntervenantsDefinition = etape.intervenants ?? intervenantsParDefaut(etape.type === 'VALIDATION');
  const repli = etape.repli_si_vide ?? { action: 'BLOQUER' };
  const actionsCourantes = etape.actions ?? ['APPROUVER', 'REFUSER'];
  const notifications = etape.notifications ?? { a_l_activation: [], a_la_fin: [], canal: 'WEBSOCKET' };

  const avecChamp = erreursEtape.map((e) => ({ e, champ: rattacherErreurs(e.chemin).champ }));
  const erreursDe = (...racines: string[]) =>
    avecChamp.filter((x) => racines.includes(racineChamp(x.champ)) || (x.champ !== null && racines.includes(x.champ))).map((x) => x.e);
  const erreursExactes = (champ: string) => avecChamp.filter((x) => x.champ === champ).map((x) => x.e);
  const erreursPrefixe = (prefixe: string) =>
    avecChamp.filter((x) => x.champ !== null && x.champ.startsWith(prefixe)).map((x) => x.e);
  const nbErreursOnglet = (o: OngletEtape) =>
    avecChamp.filter((x) => ONGLET_DU_CHAMP[racineChamp(x.champ)] === o).length;
  const erreursSansChamp = avecChamp.filter((x) => !(racineChamp(x.champ) in ONGLET_DU_CHAMP)).map((x) => x.e);

  const publicsInformation = optionsPublics(catalogue.publics ?? [], fonctionsActives, etape.public ?? []);
  const publicsNotif = optionsPublics(
    catalogue.publics ?? [],
    fonctionsActives,
    [...(notifications.a_l_activation ?? []), ...(notifications.a_la_fin ?? [])],
  );

  const delaiUnite = etape.delai?.unite ?? 'HEURES';
  const dureeMax = DUREE_MAX[delaiUnite] ?? 720;
  const dureeHorsBornes = Boolean(etape.delai?.duree) && (etape.delai?.duree ?? 0) > dureeMax;
  const echeance = etape.a_echeance ?? { action: 'RIEN' };
  const relances: RelancesDefinition = etape.relances ?? {};
  const nbTaches = (etape.taches ?? []).length;
  const avant = relances.avant_echeance_heures;
  const avantHorsBornes = avant !== undefined && (avant < 1 || avant > AVANT_ECHEANCE_MAX_HEURES);

  return (
    <div className="card">
      <div className="tabs" role="tablist" aria-label="Propriétés de l'étape" style={{ marginBottom: 14 }}>
        {(
          [
            ['general', 'Général'],
            ['intervenants', 'Intervenants'],
            ['condition', 'Condition'],
            ['delais', 'Délais et escalade'],
            ['notifications', 'Notifications'],
          ] as Array<[OngletEtape, string]>
        ).map(([id, lab]) => {
          const nb = nbErreursOnglet(id);
          return (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={onglet === id}
              className={`tab${onglet === id ? ' tab--active' : ''}`}
              onClick={() => setOnglet(id)}
            >
              {lab}
              {nb > 0 ? <span className="status status--danger" style={{ marginLeft: 6 }}>{nb}</span> : null}
            </button>
          );
        })}
      </div>

      {erreursSansChamp.length > 0 ? (
        <div className="alert alert--warning" role="alert" style={{ marginBottom: 12 }}>
          <strong>Points à vérifier sur cette étape :</strong>
          <ErreursChamp erreurs={erreursSansChamp} />
        </div>
      ) : null}

      {onglet === 'general' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <div>
              <label className="field-label" htmlFor="etape-code">Code d’étape</label>
              <input
                id="etape-code"
                className="field-input"
                maxLength={40}
                disabled={disabled}
                value={etape.code}
                onChange={(e) => maj({ code: e.target.value.toUpperCase() })}
              />
              <ErreursChamp erreurs={erreursExactes('code')} />
            </div>
            <div>
              <label className="field-label" htmlFor="etape-libelle">Libellé affiché</label>
              <input
                id="etape-libelle"
                className="field-input"
                maxLength={80}
                disabled={disabled}
                value={etape.libelle}
                onChange={(e) => maj({ libelle: e.target.value })}
              />
              <ErreursChamp erreurs={erreursExactes('libelle')} />
            </div>
            <div>
              <label className="field-label" htmlFor="etape-type">Type d’étape</label>
              <select
                id="etape-type"
                className="field-input"
                disabled={disabled}
                value={etape.type}
                onChange={(e) => onChange(normaliserEtapePourType(etape, e.target.value))}
              >
                {TYPES_ETAPE.map((t) => (
                  <option key={t.code} value={t.code}>{t.libelle}</option>
                ))}
              </select>
              <ErreursChamp erreurs={erreursExactes('type')} />
            </div>
          </div>

          {etape.type === 'VALIDATION' ? (
            <>
              <div>
                <span className="field-label">Actions permises</span>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 4 }}>
                  {ACTIONS_VALIDATION.map((act) => {
                    const coche = actionsCourantes.includes(act);
                    const obligatoire = act !== 'RENVOYER';
                    return (
                      <label key={act} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <input
                          type="checkbox"
                          checked={coche}
                          disabled={disabled || obligatoire}
                          onChange={() => {
                            const next = coche
                              ? actionsCourantes.filter((x) => x !== act)
                              : [...actionsCourantes, act];
                            maj({ actions: next });
                          }}
                        />
                        <span>
                          {act === 'APPROUVER'
                            ? 'Approuver (obligatoire)'
                            : act === 'REFUSER'
                              ? 'Refuser (obligatoire)'
                              : 'Demander un complément'}
                        </span>
                      </label>
                    );
                  })}
                </div>
                <ErreursChamp erreurs={erreursDe('actions')} />
              </div>

              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  checked={Boolean(etape.controle_avant_approbation)}
                  disabled={disabled}
                  onChange={(e) => maj({ controle_avant_approbation: e.target.checked })}
                />
                <span>Contrôle métier avant approbation (ex. vérification du solde de congé)</span>
              </label>
              <ErreursChamp erreurs={erreursDe('controle_avant_approbation')} />

              <div>
                <span className="field-label">Commentaire obligatoire si (facultatif)</span>
                <ConstructeurCondition
                  valeur={etape.commentaire_obligatoire_si}
                  catalogue={catalogue}
                  objetType={objetType}
                  disabled={disabled}
                  onChange={(c) => maj({ commentaire_obligatoire_si: c })}
                />
                <ErreursChamp erreurs={erreursDe('commentaire_obligatoire_si')} />
              </div>
            </>
          ) : null}
        </div>
      ) : null}

      {onglet === 'intervenants' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {etape.type === 'INFORMATION' ? (
            <div>
              <span className="field-label">Destinataires de l’information</span>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 6 }}>
                {publicsInformation.map((pub) => {
                  const liste = etape.public ?? [];
                  const coche = liste.includes(pub);
                  return (
                    <label key={pub} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                      <input
                        type="checkbox"
                        checked={coche}
                        disabled={disabled}
                        onChange={() => {
                          const next = coche ? liste.filter((x) => x !== pub) : [...liste, pub];
                          maj({ public: next });
                        }}
                      />
                      <span>{libellePublic(pub, fonctionsActives)}</span>
                    </label>
                  );
                })}
              </div>
              <ErreursChamp erreurs={erreursDe('public')} />
            </div>
          ) : etape.type === 'TACHES_PARALLELES' ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, gap: 8, flexWrap: 'wrap' }}>
                <strong>Tâches parallèles ({nbTaches}, de {NB_TACHES_MIN} à {NB_TACHES_MAX})</strong>
                {!disabled ? (
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={nbTaches >= NB_TACHES_MAX}
                    title={nbTaches >= NB_TACHES_MAX ? `Maximum ${NB_TACHES_MAX} tâches en parallèle.` : undefined}
                    onClick={() => {
                      const courantes = etape.taches ?? [];
                      maj({ taches: [...courantes, nouvelleTacheParDefaut(courantes.map((x) => x.code))] });
                    }}
                  >
                    + Ajouter une tâche
                  </button>
                ) : null}
              </div>
              {nbTaches < NB_TACHES_MIN ? (
                <p className="small field-error">Une étape en parallèle doit contenir au moins {NB_TACHES_MIN} tâches.</p>
              ) : null}
              <ErreursChamp erreurs={erreursExactes('taches')} />
              {(etape.taches ?? []).map((t, idx) => (
                <div key={idx} className="card" style={{ marginBottom: 8, padding: 12 }}>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                    <input
                      aria-label="Code tâche"
                      className="field-input"
                      style={{ width: 150 }}
                      disabled={disabled}
                      value={t.code}
                      onChange={(e) => {
                        const next = (etape.taches ?? []).map((x, i) =>
                          i === idx ? { ...x, code: e.target.value.toUpperCase() } : x,
                        );
                        maj({ taches: next });
                      }}
                    />
                    <input
                      aria-label="Libellé tâche"
                      className="field-input"
                      style={{ flex: 1, minWidth: 180 }}
                      disabled={disabled}
                      value={t.libelle}
                      onChange={(e) => {
                        const next = (etape.taches ?? []).map((x, i) =>
                          i === idx ? { ...x, libelle: e.target.value } : x,
                        );
                        maj({ taches: next });
                      }}
                    />
                    <label style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <input
                        type="checkbox"
                        checked={t.obligatoire !== false}
                        disabled={disabled}
                        onChange={(e) => {
                          const next = (etape.taches ?? []).map((x, i) =>
                            i === idx ? { ...x, obligatoire: e.target.checked } : x,
                          );
                          maj({ taches: next });
                        }}
                      />
                      <span className="small">Obligatoire</span>
                    </label>
                    {!disabled ? (
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        disabled={nbTaches <= NB_TACHES_MIN}
                        title={nbTaches <= NB_TACHES_MIN ? `Minimum ${NB_TACHES_MIN} tâches en parallèle.` : undefined}
                        onClick={() => maj({ taches: (etape.taches ?? []).filter((_, i) => i !== idx) })}
                      >
                        Retirer
                      </button>
                    ) : null}
                  </div>
                  <EditeurIntervenant
                    valeur={t.intervenants ?? { regle: 'ROLE', params: { roles: ['RH'] } }}
                    catalogue={catalogue}
                    fonctionsActives={fonctionsActives}
                    autoriserMode={false}
                    disabled={disabled}
                    onChange={(iv) => {
                      const next = (etape.taches ?? []).map((x, i) => (i === idx ? { ...x, intervenants: iv } : x));
                      maj({ taches: next });
                    }}
                  />
                  <ErreursChamp erreurs={erreursPrefixe(`taches[${idx}]`)} />
                </div>
              ))}
            </div>
          ) : (
            <>
              <EditeurIntervenant
                valeur={intervenants}
                catalogue={catalogue}
                fonctionsActives={fonctionsActives}
                autoriserMode={etape.type === 'VALIDATION'}
                interdireDemandeur={etape.type === 'VALIDATION'}
                disabled={disabled}
                onChange={(iv) => maj({ intervenants: iv })}
              />
              <ErreursChamp erreurs={erreursPrefixe('intervenants')} />

              <div style={{ borderTop: '1px solid var(--border, #e5e7eb)', paddingTop: 12 }}>
                <span className="field-label">Repli si aucun intervenant trouvé</span>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', marginTop: 6 }}>
                  <select
                    aria-label="Action de repli"
                    className="field-input"
                    style={{ width: 'auto' }}
                    disabled={disabled}
                    value={repli.action}
                    onChange={(e) => {
                      const action = e.target.value as 'PASSER' | 'ROLE' | 'BLOQUER';
                      maj({
                        repli_si_vide: {
                          ...repli,
                          action,
                          roles: action === 'ROLE' ? (repli.roles?.length ? repli.roles : ['RH']) : repli.roles,
                        },
                      });
                    }}
                  >
                    <option value="PASSER">Passer l’étape automatiquement</option>
                    <option value="ROLE">Rabattre sur un rôle</option>
                    <option value="BLOQUER">Bloquer et alerter la RH</option>
                  </select>
                  {repli.action === 'ROLE' ? (
                    <div style={{ display: 'flex', gap: 10 }}>
                      {ROLES_DISPONIBLES.map((r) => {
                        const roles = repli.roles ?? [];
                        const coche = roles.includes(r);
                        return (
                          <label key={r} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                            <input
                              type="checkbox"
                              checked={coche}
                              disabled={disabled}
                              onChange={() => {
                                const next = coche ? roles.filter((x) => x !== r) : [...roles, r];
                                maj({ repli_si_vide: { ...repli, roles: next } });
                              }}
                            />
                            <span>{libelleRole(r)}</span>
                          </label>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
                <ErreursChamp erreurs={erreursPrefixe('repli_si_vide')} />
              </div>
            </>
          )}
        </div>
      ) : null}

      {onglet === 'condition' ? (
        <div>
          <ConstructeurCondition
            valeur={etape.condition}
            catalogue={catalogue}
            objetType={objetType}
            disabled={disabled}
            onChange={(c) => maj({ condition: c })}
          />
          <ErreursChamp erreurs={erreursDe('condition')} />
        </div>
      ) : null}

      {onglet === 'delais' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
            <div>
              <label className="field-label" htmlFor="delai-duree">Délai cible (nombre)</label>
              <input
                id="delai-duree"
                type="number"
                min={1}
                max={dureeMax}
                className="field-input"
                disabled={disabled}
                value={etape.delai?.duree ?? ''}
                onChange={(e) => {
                  const val = e.target.value ? Number(e.target.value) : undefined;
                  maj({
                    delai: val
                      ? { ...(etape.delai ?? {}), duree: val, unite: etape.delai?.unite ?? 'HEURES' }
                      : null,
                  });
                }}
              />
              {dureeHorsBornes ? (
                <p className="small field-error">
                  Le délai en {delaiUnite === 'JOURS_OUVRES' ? 'jours ouvrés' : 'heures'} doit être compris entre 1 et {dureeMax}.
                </p>
              ) : null}
            </div>
            <div>
              <label className="field-label" htmlFor="delai-unite">Unité</label>
              <select
                id="delai-unite"
                className="field-input"
                disabled={disabled || !etape.delai?.duree}
                value={delaiUnite}
                onChange={(e) => maj({ delai: { ...(etape.delai ?? {}), unite: e.target.value as 'HEURES' | 'JOURS_OUVRES' } })}
              >
                {UNITES_DELAI.map((u) => (
                  <option key={u.code} value={u.code}>{u.libelle}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="field-label" htmlFor="delai-plafond">Plafond avant variable de date</label>
              <select
                id="delai-plafond"
                className="field-input"
                disabled={disabled || !etape.delai?.duree}
                value={etape.delai?.plafond_avant_variable ?? ''}
                onChange={(e) =>
                  maj({
                    delai: {
                      ...(etape.delai ?? {}),
                      plafond_avant_variable: e.target.value || null,
                    },
                  })
                }
              >
                <option value="">Aucun plafond</option>
                {varsDate.map((v) => (
                  <option key={v.nom} value={v.nom}>{v.libelle}</option>
                ))}
              </select>
            </div>
          </div>
          <ErreursChamp erreurs={erreursPrefixe('delai')} />

          <div className="alert alert--info" role="note">
            Les réglages de relances, d’escalade et d’action à l’échéance ci-dessous s’enregistrent avec le modèle et
            seront appliqués à partir d’une prochaine version du moteur.
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
            <div>
              <label className="field-label" htmlFor="rel-nb">Nombre de relances (0 à {RELANCES_MAX})</label>
              <input
                id="rel-nb"
                type="number"
                min={0}
                max={RELANCES_MAX}
                className="field-input"
                disabled={disabled}
                value={relances.nombre ?? ''}
                onChange={(e) => {
                  const v = e.target.value ? Number(e.target.value) : undefined;
                  maj({ relances: v !== undefined ? { ...relances, nombre: v } : null });
                }}
              />
            </div>
            {(relances.nombre ?? 0) > 0 ? (
              <>
                <div>
                  <label className="field-label" htmlFor="rel-intervalle">Intervalle entre relances (heures)</label>
                  <input
                    id="rel-intervalle"
                    type="number"
                    min={1}
                    max={INTERVALLE_RELANCE_MAX_HEURES}
                    className="field-input"
                    disabled={disabled}
                    value={relances.intervalle_heures ?? ''}
                    onChange={(e) => {
                      const { intervalle_heures: _ancien, ...reste } = relances;
                      maj({ relances: e.target.value ? { ...reste, intervalle_heures: Number(e.target.value) } : reste });
                    }}
                  />
                  <span className="small muted">De 1 à {INTERVALLE_RELANCE_MAX_HEURES} h.</span>
                </div>
                <div>
                  <label className="field-label" htmlFor="rel-avant">Première relance avant l’échéance (heures)</label>
                  <input
                    id="rel-avant"
                    type="number"
                    min={1}
                    max={AVANT_ECHEANCE_MAX_HEURES}
                    className="field-input"
                    disabled={disabled}
                    value={relances.avant_echeance_heures ?? ''}
                    aria-invalid={avantHorsBornes ? true : undefined}
                    onChange={(e) => {
                      const { avant_echeance_heures: _ancien, ...reste } = relances;
                      maj({ relances: e.target.value ? { ...reste, avant_echeance_heures: Number(e.target.value) } : reste });
                    }}
                  />
                  {avantHorsBornes ? (
                    <p className="small field-error">Le délai avant échéance doit être compris entre 1 et {AVANT_ECHEANCE_MAX_HEURES} heures.</p>
                  ) : (
                    <span className="small muted">De 1 à {AVANT_ECHEANCE_MAX_HEURES} h.</span>
                  )}
                </div>
              </>
            ) : null}
            <div>
              <label className="field-label" htmlFor="ech-action">Action à l’échéance</label>
              <select
                id="ech-action"
                className="field-input"
                disabled={disabled}
                value={echeance.action ?? 'RIEN'}
                onChange={(e) => {
                  const action = e.target.value as NonNullable<EcheanceDefinition['action']>;
                  const suite: EcheanceDefinition = { ...echeance, action };
                  if (action === 'ESCALADER') {
                    suite.intervenants = echeance.intervenants ?? { regle: 'ROLE', params: { roles: ['RH'] } };
                  } else {
                    delete suite.intervenants;
                  }
                  maj({ a_echeance: suite });
                }}
              >
                <option value="RIEN">Ne rien faire (marquer en retard)</option>
                <option value="ESCALADER">Escalader</option>
                <option value="EXPIRER" disabled={typeObjet ? !typeObjet.effets.includes('EXPIRER') : false}>
                  Expirer la demande
                </option>
                <option value="REFUSER_AUTO">Refuser automatiquement</option>
                <option value="APPROUVER_AUTO" disabled={Boolean(typeObjet?.validation_humaine_obligatoire)}>
                  Approuver automatiquement
                </option>
              </select>
              {typeObjet?.validation_humaine_obligatoire ? (
                <span className="small muted">L’approbation automatique est interdite pour ce type de demande.</span>
              ) : null}
            </div>
          </div>
          <ErreursChamp erreurs={erreursPrefixe('relances')} />

          {echeance.action === 'ESCALADER' ? (
            <div style={{ borderTop: '1px solid var(--border, #e5e7eb)', paddingTop: 12 }}>
              <span className="field-label">Qui reçoit l’escalade à l’échéance</span>
              <EditeurIntervenant
                valeur={echeance.intervenants ?? { regle: 'ROLE', params: { roles: ['RH'] } }}
                catalogue={catalogue}
                fonctionsActives={fonctionsActives}
                autoriserMode={false}
                interdireDemandeur={etape.type === 'VALIDATION'}
                disabled={disabled}
                onChange={(iv) => maj({ a_echeance: { ...echeance, intervenants: iv } })}
              />
            </div>
          ) : null}
          <ErreursChamp erreurs={erreursPrefixe('a_echeance')} />
        </div>
      ) : null}

      {onglet === 'notifications' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <label className="field-label" htmlFor="notif-canal">Canal de notification</label>
            <select
              id="notif-canal"
              className="field-input"
              style={{ maxWidth: 260 }}
              disabled={disabled}
              value={notifications.canal ?? 'WEBSOCKET'}
              onChange={(e) => maj({ notifications: { ...notifications, canal: e.target.value as 'WEBSOCKET' | 'BOTH' } })}
            >
              <option value="WEBSOCKET">Application uniquement</option>
              <option value="BOTH">Application et e-mail</option>
            </select>
          </div>
          <div>
            <span className="field-label">Notifier à l’activation de l’étape</span>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
              {publicsNotif.map((p) => {
                const liste = notifications.a_l_activation ?? [];
                const coche = liste.includes(p);
                return (
                  <label key={p} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <input
                      type="checkbox"
                      checked={coche}
                      disabled={disabled}
                      onChange={() => {
                        const next = coche ? liste.filter((x) => x !== p) : [...liste, p];
                        maj({ notifications: { ...notifications, a_l_activation: next } });
                      }}
                    />
                    <span className="small">{libellePublic(p, fonctionsActives)}</span>
                  </label>
                );
              })}
            </div>
          </div>
          <div>
            <span className="field-label">Notifier à la fin de l’étape</span>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
              {publicsNotif.map((p) => {
                const liste = notifications.a_la_fin ?? [];
                const coche = liste.includes(p);
                return (
                  <label key={p} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <input
                      type="checkbox"
                      checked={coche}
                      disabled={disabled}
                      onChange={() => {
                        const next = coche ? liste.filter((x) => x !== p) : [...liste, p];
                        maj({ notifications: { ...notifications, a_la_fin: next } });
                      }}
                    />
                    <span className="small">{libellePublic(p, fonctionsActives)}</span>
                  </label>
                );
              })}
            </div>
          </div>
          <ErreursChamp erreurs={erreursPrefixe('notifications')} />
        </div>
      ) : null}
    </div>
  );
}

function EditeurIntervenant({
  valeur,
  catalogue,
  fonctionsActives,
  autoriserMode = true,
  interdireDemandeur = false,
  disabled,
  onChange,
}: {
  valeur: IntervenantsDefinition;
  catalogue: CatalogueWorkflow;
  fonctionsActives: FonctionRh[];
  autoriserMode?: boolean;
  /** Vrai sur une étape VALIDATION : la règle DEMANDEUR est interdite (invariant I1). */
  interdireDemandeur?: boolean;
  disabled?: boolean;
  onChange: (iv: IntervenantsDefinition) => void;
}) {
  const params = valeur.params ?? {};
  const roles = Array.isArray(params.roles) ? (params.roles as string[]) : [];
  // La règle « collaborateur nommé » exige un identifiant : masquée sauf si déjà choisie.
  const reglesProposees = (catalogue.regles ?? []).filter(
    (r) =>
      (r.code !== 'COLLABORATEUR' || valeur.regle === 'COLLABORATEUR') &&
      (!(interdireDemandeur && r.code === 'DEMANDEUR') || valeur.regle === 'DEMANDEUR'),
  );

  /** Hors VALIDATION, `mode` est refusé par le serveur : il n'est jamais écrit. */
  function emettre(iv: IntervenantsDefinition) {
    if (autoriserMode) {
      onChange({ ...iv, mode: iv.mode ?? 'UN_SEUL' });
    } else {
      const { mode: _mode, ...sansMode } = iv;
      onChange(sansMode);
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 10 }}>
        <div>
          <label className="field-label">Règle d’intervenant</label>
          <select
            aria-label="Règle d'intervenant"
            className="field-input"
            disabled={disabled}
            value={valeur.regle}
            onChange={(e) => {
              const regle = e.target.value;
              const defParams: Record<string, unknown> =
                regle === 'MANAGER_NIVEAU'
                  ? { niveau: 2 }
                  : regle === 'RESPONSABLE_TYPE'
                    ? { type_noeud: 'DIRECTION' }
                    : regle === 'ROLE'
                      ? { roles: ['RH'] }
                      : regle === 'FONCTION'
                        ? { fonction_code: fonctionsActives[0]?.code ?? '' }
                        : {};
              emettre({ ...valeur, regle, params: defParams });
            }}
          >
            {reglesProposees.map((r) => (
              <option key={r.code} value={r.code}>{r.libelle}</option>
            ))}
          </select>
        </div>

        {autoriserMode ? (
          <div>
            <label className="field-label">Mode de décision</label>
            <select
              aria-label="Mode de décision"
              className="field-input"
              disabled={disabled}
              value={valeur.mode ?? 'UN_SEUL'}
              onChange={(e) => emettre({ ...valeur, mode: e.target.value as 'UN_SEUL' | 'TOUS' })}
            >
              <option value="UN_SEUL">Un seul intervenant suffit</option>
              <option value="TOUS">Tous les intervenants doivent valider</option>
            </select>
          </div>
        ) : null}
      </div>

      {valeur.regle === 'MANAGER_NIVEAU' ? (
        <div style={{ maxWidth: 180 }}>
          <label className="field-label">Niveau hiérarchique (N+k)</label>
          <input
            type="number"
            min={1}
            max={4}
            className="field-input"
            disabled={disabled}
            value={Number(params.niveau ?? 2)}
            onChange={(e) => emettre({ ...valeur, params: { ...params, niveau: Number(e.target.value) } })}
          />
        </div>
      ) : null}

      {valeur.regle === 'RESPONSABLE_TYPE' ? (
        <div style={{ maxWidth: 260 }}>
          <label className="field-label">Type d’unité</label>
          <select
            className="field-input"
            disabled={disabled}
            value={String(params.type_noeud ?? 'DIRECTION')}
            onChange={(e) => emettre({ ...valeur, params: { ...params, type_noeud: e.target.value } })}
          >
            {TYPES_NOEUD.map((tn) => (
              <option key={tn.code} value={tn.code}>{tn.libelle}</option>
            ))}
          </select>
        </div>
      ) : null}

      {valeur.regle === 'ROLE' ? (
        <div>
          <span className="field-label">Rôles ciblés</span>
          <div style={{ display: 'flex', gap: 12, marginTop: 4 }}>
            {ROLES_DISPONIBLES.map((r) => {
              const coche = roles.includes(r);
              return (
                <label key={r} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="checkbox"
                    checked={coche}
                    disabled={disabled}
                    onChange={() => {
                      const next = coche ? roles.filter((x) => x !== r) : [...roles, r];
                      emettre({ ...valeur, params: { ...params, roles: next } });
                    }}
                  />
                  <span>{libelleRole(r)}</span>
                </label>
              );
            })}
          </div>
        </div>
      ) : null}

      {valeur.regle === 'FONCTION' ? (
        <div style={{ maxWidth: 320 }}>
          <label className="field-label">Fonction active</label>
          <select
            className="field-input"
            disabled={disabled}
            value={String(params.fonction_code ?? '')}
            onChange={(e) => emettre({ ...valeur, params: { ...params, fonction_code: e.target.value } })}
          >
            <option value="">Choisir une fonction…</option>
            {fonctionsActives.map((f) => (
              <option key={f.code} value={f.code}>{f.libelle}</option>
            ))}
          </select>
        </div>
      ) : null}
    </div>
  );
}

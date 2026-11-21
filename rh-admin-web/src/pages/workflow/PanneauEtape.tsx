import { useState } from 'react';
import type { FonctionRh } from '../../api/societeApi';
import type {
  CatalogueWorkflow,
  ErreurDefinition,
  EtapeDefinition,
  IntervenantsDefinition,
  TacheParalleleDefinition,
} from '../../api/workflowApi';
import ConstructeurCondition from './ConstructeurCondition';

type OngletEtape = 'general' | 'intervenants' | 'condition' | 'delais' | 'notifications';

const TYPES_ETAPE: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'VALIDATION', libelle: 'Validation (approbation / refus)' },
  { code: 'TACHE', libelle: 'Tâche simple (à faire)' },
  { code: 'TACHES_PARALLELES', libelle: 'Tâches en parallèle' },
  { code: 'INFORMATION', libelle: 'Information seule' },
];

const TYPES_NOEUD: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'SERVICE', libelle: 'Service' },
  { code: 'DEPARTEMENT', libelle: 'Département' },
  { code: 'DIRECTION', libelle: 'Direction' },
  { code: 'DIRECTION_GENERALE', libelle: 'Direction générale' },
];

const ROLES_DISPONIBLES = ['RH', 'ADMIN', 'DIRECTION'] as const;
const ACTIONS_VALIDATION = ['APPROUVER', 'REFUSER', 'DEMANDER_COMPLEMENT'] as const;
const UNITES_DELAI: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'HEURES_OUVREES', libelle: 'Heures ouvrées' },
  { code: 'JOURS_OUVRES', libelle: 'Jours ouvrés' },
  { code: 'HEURES_CALENDAIRES', libelle: 'Heures calendaires' },
  { code: 'JOURS_CALENDAIRES', libelle: 'Jours calendaires' },
];

type Props = {
  etape: EtapeDefinition;
  catalogue: CatalogueWorkflow;
  objetType: string;
  fonctionsActives: FonctionRh[];
  erreursEtape: ErreurDefinition[];
  disabled?: boolean;
  onChange: (maj: EtapeDefinition) => void;
};

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

  const maj = (partiel: Partial<EtapeDefinition>) => onChange({ ...etape, ...partiel });

  const intervenants: IntervenantsDefinition = etape.intervenants ?? {
    regle: 'MANAGER_NOEUD',
    params: {},
    mode: 'UN_SEUL',
  };

  const repli = etape.repli_si_vide ?? { action: 'PASSER', roles: [] };
  const actionsCourantes = etape.actions ?? ['APPROUVER', 'REFUSER'];
  const notifications = etape.notifications ?? { a_l_activation: [], a_la_fin: [], canal: 'WEBSOCKET' };

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
        ).map(([id, lab]) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={onglet === id}
            className={`tab${onglet === id ? ' tab--active' : ''}`}
            onClick={() => setOnglet(id)}
          >
            {lab}
          </button>
        ))}
      </div>

      {erreursEtape.length > 0 ? (
        <div className="alert alert--warning" role="alert" style={{ marginBottom: 12 }}>
          <strong>Points à vérifier sur cette étape :</strong>
          <ul style={{ margin: '6px 0 0 18px' }}>
            {erreursEtape.map((e, i) => (
              <li key={i} className="small">
                [{e.niveau}] {e.message}
              </li>
            ))}
          </ul>
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
            </div>
            <div>
              <label className="field-label" htmlFor="etape-type">Type d’étape</label>
              <select
                id="etape-type"
                className="field-input"
                disabled={disabled}
                value={etape.type}
                onChange={(e) => maj({ type: e.target.value })}
              >
                {TYPES_ETAPE.map((t) => (
                  <option key={t.code} value={t.code}>{t.libelle}</option>
                ))}
              </select>
            </div>
          </div>

          {etape.type === 'VALIDATION' ? (
            <>
              <div>
                <span className="field-label">Actions permises</span>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 4 }}>
                  {ACTIONS_VALIDATION.map((act) => {
                    const coche = actionsCourantes.includes(act);
                    return (
                      <label key={act} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <input
                          type="checkbox"
                          checked={coche}
                          disabled={disabled}
                          onChange={() => {
                            const next = coche
                              ? actionsCourantes.filter((x) => x !== act)
                              : [...actionsCourantes, act];
                            maj({ actions: next });
                          }}
                        />
                        <span>
                          {act === 'APPROUVER'
                            ? 'Approuver'
                            : act === 'REFUSER'
                              ? 'Refuser'
                              : 'Demander un complément'}
                        </span>
                      </label>
                    );
                  })}
                </div>
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

              <div>
                <span className="field-label">Commentaire obligatoire si (facultatif)</span>
                <ConstructeurCondition
                  valeur={etape.commentaire_obligatoire_si}
                  catalogue={catalogue}
                  objetType={objetType}
                  disabled={disabled}
                  onChange={(c) => maj({ commentaire_obligatoire_si: c })}
                />
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
                {(catalogue.publics ?? []).map((pub) => {
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
                      <span>{pub}</span>
                    </label>
                  );
                })}
              </div>
            </div>
          ) : etape.type === 'TACHES_PARALLELES' ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <strong>Tâches parallèles</strong>
                {!disabled ? (
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    onClick={() => {
                      const courantes = etape.taches ?? [];
                      const nouvelle: TacheParalleleDefinition = {
                        code: `TACHE_${courantes.length + 1}`,
                        libelle: `Tâche ${courantes.length + 1}`,
                        intervenants: { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'UN_SEUL' },
                        repli_si_vide: { action: 'BLOQUER' },
                        obligatoire: true,
                      };
                      maj({ taches: [...courantes, nouvelle] });
                    }}
                  >
                    + Ajouter une tâche
                  </button>
                ) : null}
              </div>
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
                        onClick={() => maj({ taches: (etape.taches ?? []).filter((_, i) => i !== idx) })}
                      >
                        Retirer
                      </button>
                    ) : null}
                  </div>
                  <EditeurIntervenant
                    valeur={t.intervenants ?? { regle: 'ROLE', params: { roles: ['RH'] }, mode: 'UN_SEUL' }}
                    catalogue={catalogue}
                    fonctionsActives={fonctionsActives}
                    disabled={disabled}
                    onChange={(iv) => {
                      const next = (etape.taches ?? []).map((x, i) => (i === idx ? { ...x, intervenants: iv } : x));
                      maj({ taches: next });
                    }}
                  />
                </div>
              ))}
            </div>
          ) : (
            <>
              <EditeurIntervenant
                valeur={intervenants}
                catalogue={catalogue}
                fonctionsActives={fonctionsActives}
                disabled={disabled}
                onChange={(iv) => maj({ intervenants: iv })}
              />

              <div style={{ borderTop: '1px solid var(--border, #e5e7eb)', paddingTop: 12 }}>
                <span className="field-label">Repli si aucun intervenant trouvé</span>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', marginTop: 6 }}>
                  <select
                    aria-label="Action de repli"
                    className="field-input"
                    style={{ width: 'auto' }}
                    disabled={disabled}
                    value={repli.action}
                    onChange={(e) => maj({ repli_si_vide: { ...repli, action: e.target.value } })}
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
                            <span>{r}</span>
                          </label>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
              </div>
            </>
          )}
        </div>
      ) : null}

      {onglet === 'condition' ? (
        <ConstructeurCondition
          valeur={etape.condition}
          catalogue={catalogue}
          objetType={objetType}
          disabled={disabled}
          onChange={(c) => maj({ condition: c })}
        />
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
                className="field-input"
                disabled={disabled}
                value={etape.delai?.duree ?? ''}
                onChange={(e) => {
                  const val = e.target.value ? Number(e.target.value) : undefined;
                  maj({
                    delai: val
                      ? { ...(etape.delai ?? {}), duree: val, unite: etape.delai?.unite ?? 'HEURES_OUVREES' }
                      : null,
                  });
                }}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="delai-unite">Unité</label>
              <select
                id="delai-unite"
                className="field-input"
                disabled={disabled || !etape.delai?.duree}
                value={etape.delai?.unite ?? 'HEURES_OUVREES'}
                onChange={(e) => maj({ delai: { ...(etape.delai ?? {}), unite: e.target.value } })}
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

          <div className="alert alert--info" role="note">
            Les réglages de relances, d’escalade et d’action à l’échéance ci-dessous s’enregistrent avec le modèle et
            seront appliqués à partir d’une prochaine version du moteur.
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
            <div>
              <label className="field-label" htmlFor="rel-nb">Nombre de relances</label>
              <input
                id="rel-nb"
                type="number"
                min={0}
                max={10}
                className="field-input"
                disabled={disabled}
                value={etape.relances?.nombre ?? ''}
                onChange={(e) => {
                  const v = e.target.value ? Number(e.target.value) : undefined;
                  maj({ relances: v !== undefined ? { ...(etape.relances ?? {}), nombre: v } : null });
                }}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="ech-action">Action à l’échéance</label>
              <select
                id="ech-action"
                className="field-input"
                disabled={disabled}
                value={etape.a_echeance?.action ?? ''}
                onChange={(e) =>
                  maj({
                    a_echeance: e.target.value ? { ...(etape.a_echeance ?? {}), action: e.target.value } : null,
                  })
                }
              >
                <option value="">Aucune</option>
                <option value="RIEN">Ne rien faire (marquer en retard)</option>
                <option value="ESCALADER">Escalader</option>
                <option value="PASSER">Passer l’étape</option>
                <option value="EXPIRER">Expirer la demande</option>
              </select>
            </div>
          </div>
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
              onChange={(e) => maj({ notifications: { ...notifications, canal: e.target.value } })}
            >
              <option value="WEBSOCKET">Application uniquement</option>
              <option value="BOTH">Application et e-mail</option>
            </select>
          </div>
          <div>
            <span className="field-label">Notifier à l’activation de l’étape</span>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
              {(catalogue.publics ?? []).map((p) => {
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
                    <span className="small">{p}</span>
                  </label>
                );
              })}
            </div>
          </div>
          <div>
            <span className="field-label">Notifier à la fin de l’étape</span>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
              {(catalogue.publics ?? []).map((p) => {
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
                    <span className="small">{p}</span>
                  </label>
                );
              })}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function EditeurIntervenant({
  valeur,
  catalogue,
  fonctionsActives,
  disabled,
  onChange,
}: {
  valeur: IntervenantsDefinition;
  catalogue: CatalogueWorkflow;
  fonctionsActives: FonctionRh[];
  disabled?: boolean;
  onChange: (iv: IntervenantsDefinition) => void;
}) {
  const params = valeur.params ?? {};
  const roles = Array.isArray(params.roles) ? (params.roles as string[]) : [];

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
              onChange({ ...valeur, regle, params: defParams });
            }}
          >
            {(catalogue.regles ?? []).map((r) => (
              <option key={r.code} value={r.code}>{r.libelle}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="field-label">Mode de décision</label>
          <select
            aria-label="Mode de décision"
            className="field-input"
            disabled={disabled}
            value={valeur.mode ?? 'UN_SEUL'}
            onChange={(e) => onChange({ ...valeur, mode: e.target.value })}
          >
            <option value="UN_SEUL">Un seul intervenant suffit</option>
            <option value="TOUS">Tous les intervenants doivent valider</option>
          </select>
        </div>
      </div>

      {valeur.regle === 'MANAGER_NIVEAU' ? (
        <div style={{ maxWidth: 180 }}>
          <label className="field-label">Niveau hiérarchique (N+k)</label>
          <input
            type="number"
            min={1}
            max={5}
            className="field-input"
            disabled={disabled}
            value={Number(params.niveau ?? 2)}
            onChange={(e) => onChange({ ...valeur, params: { ...params, niveau: Number(e.target.value) } })}
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
            onChange={(e) => onChange({ ...valeur, params: { ...params, type_noeud: e.target.value } })}
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
                      onChange({ ...valeur, params: { ...params, roles: next } });
                    }}
                  />
                  <span>{r}</span>
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
            onChange={(e) => onChange({ ...valeur, params: { ...params, fonction_code: e.target.value } })}
          >
            <option value="">Choisir une fonction…</option>
            {fonctionsActives.map((f) => (
              <option key={f.code} value={f.code}>{f.libelle} ({f.code})</option>
            ))}
          </select>
        </div>
      ) : null}
    </div>
  );
}

import type { PresenceSite } from '../../api/presenceApi';
import type {
  CanalNotificationPointage,
  ErreurDefinition,
  NotificationsDynamiquesDefinition,
  PorteeSitesPointage,
  TypePointageNotif,
} from '../../api/workflowApi';
import { ErrorAlert } from '../../components/ui';
import { libelleNiveauErreur } from '../../workflow/libelles';

export const NOTIFICATIONS_POINTAGE_PAR_DEFAUT: NotificationsDynamiquesDefinition = {
  notifier_ro: false,
  portee_sites: 'TOUTE_LA_SOCIETE',
  site_ids: [],
  types_pointage: ['ENTREE', 'SORTIE'],
  canal: 'WEBSOCKET',
};

const TYPES_POINTAGE_OPTIONS: ReadonlyArray<{ value: TypePointageNotif; label: string; id: string }> = [
  { value: 'ENTREE', label: 'Entrée', id: 'notif-type-entree' },
  { value: 'SORTIE', label: 'Sortie', id: 'notif-type-sortie' },
];

export type PropsPanneauNotificationsPointage = {
  valeur: NotificationsDynamiquesDefinition | null | undefined;
  sites: PresenceSite[];
  sitesChargement?: boolean;
  sitesErreur?: string | null;
  erreurs?: ErreurDefinition[];
  disabled?: boolean;
  onChange: (maj: NotificationsDynamiquesDefinition) => void;
};

/** Normalise une configuration de notifications dynamiques avec les valeurs par défaut. */
export function normaliserNotificationsPointage(
  valeur: NotificationsDynamiquesDefinition | null | undefined,
): NotificationsDynamiquesDefinition {
  return {
    notifier_ro: Boolean(valeur?.notifier_ro ?? NOTIFICATIONS_POINTAGE_PAR_DEFAUT.notifier_ro),
    portee_sites: valeur?.portee_sites === 'SITES_SELECTIONNES' ? 'SITES_SELECTIONNES' : 'TOUTE_LA_SOCIETE',
    site_ids: Array.isArray(valeur?.site_ids) ? valeur.site_ids : [],
    types_pointage: Array.isArray(valeur?.types_pointage)
      ? valeur.types_pointage
      : [...NOTIFICATIONS_POINTAGE_PAR_DEFAUT.types_pointage],
    canal: valeur?.canal === 'BOTH' ? 'BOTH' : 'WEBSOCKET',
  };
}

function ErreursChamp({ erreurs }: { erreurs: ErreurDefinition[] }) {
  if (erreurs.length === 0) return null;
  return (
    <ul className="small field-error" role="alert" style={{ margin: '6px 0 0 18px', padding: 0 }}>
      {erreurs.map((e, i) => (
        <li key={`${e.code}-${i}`}>
          {e.niveau === 'AVERTISSEMENT' ? `${libelleNiveauErreur(e.niveau)} : ` : ''}
          {e.message}
        </li>
      ))}
    </ul>
  );
}

/** Panneau de configuration des notifications dynamiques de pointage au responsable (RO). */
export default function PanneauNotificationsPointage({
  valeur,
  sites,
  sitesChargement = false,
  sitesErreur = null,
  erreurs = [],
  disabled = false,
  onChange,
}: PropsPanneauNotificationsPointage) {
  const nd = normaliserNotificationsPointage(valeur);
  const sitesActifs = sites.filter((s) => s.actif);

  const erreursDe = (cheminExact: string, codes: ReadonlyArray<string>): ErreurDefinition[] =>
    erreurs.filter((e) => e.chemin === cheminExact || codes.includes(e.code));

  const erreursNotifierRo = erreursDe('notifications_dynamiques.notifier_ro', ['PARAMETRE_INVALIDE']);
  const erreursPortee = erreursDe('notifications_dynamiques.portee_sites', ['PORTEE_SITES_INVALIDE']);
  const erreursSites = erreursDe('notifications_dynamiques.site_ids', ['SITES_REQUIS', 'SITE_ID_INVALIDE']);
  const erreursTypes = erreursDe('notifications_dynamiques.types_pointage', ['TYPES_POINTAGE_REQUIS']);
  const erreursCanal = erreursDe('notifications_dynamiques.canal', ['CANAL_INVALIDE']);
  const erreursGenerales = erreurs.filter(
    (e) =>
      e.chemin === 'notifications_dynamiques' ||
      e.code === 'NOTIFICATIONS_DYNAMIQUES_REQUISES' ||
      (e.chemin?.startsWith('notifications_dynamiques.') &&
        ![
          'notifications_dynamiques.notifier_ro',
          'notifications_dynamiques.portee_sites',
          'notifications_dynamiques.site_ids',
          'notifications_dynamiques.types_pointage',
          'notifications_dynamiques.canal',
        ].includes(e.chemin) &&
        !['PARAMETRE_INVALIDE', 'PORTEE_SITES_INVALIDE', 'SITES_REQUIS', 'SITE_ID_INVALIDE', 'TYPES_POINTAGE_REQUIS', 'CANAL_INVALIDE'].includes(
          e.code,
        )),
  );

  function majPortee(portee: PorteeSitesPointage) {
    if (portee === 'TOUTE_LA_SOCIETE') {
      onChange({ ...nd, portee_sites: 'TOUTE_LA_SOCIETE', site_ids: [] });
    } else {
      onChange({ ...nd, portee_sites: 'SITES_SELECTIONNES' });
    }
  }

  function basculerSite(siteId: string) {
    const present = nd.site_ids.includes(siteId);
    const next = present ? nd.site_ids.filter((id) => id !== siteId) : [...nd.site_ids, siteId];
    onChange({ ...nd, site_ids: next });
  }

  function basculerType(type: TypePointageNotif) {
    const present = nd.types_pointage.includes(type);
    const next = present ? nd.types_pointage.filter((t) => t !== type) : [...nd.types_pointage, type];
    onChange({ ...nd, types_pointage: next });
  }

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div>
        <h3 style={{ margin: '0 0 4px 0' }}>Notifications dynamiques de pointage</h3>
        <p className="muted small" style={{ margin: 0 }}>
          Définissez si le responsable hiérarchique direct (RO) est notifié lorsqu’un collaborateur de son équipe
          effectue un pointage valide.
        </p>
      </div>

      {erreursGenerales.length > 0 ? <ErreursChamp erreurs={erreursGenerales} /> : null}

      <div
        style={{
          padding: 12,
          borderRadius: 8,
          border: '1px solid var(--border, #d8dee9)',
          background: 'var(--bg-subtle, #f8fafc)',
        }}
      >
        <label
          htmlFor="notif-pointage-notifier-ro"
          style={{ display: 'inline-flex', alignItems: 'center', gap: 10, fontWeight: 600, cursor: disabled ? 'default' : 'pointer' }}
        >
          <input
            id="notif-pointage-notifier-ro"
            type="checkbox"
            checked={nd.notifier_ro}
            disabled={disabled}
            onChange={(e) => onChange({ ...nd, notifier_ro: e.target.checked })}
          />
          <span>Notifier le responsable (RO) à chaque pointage</span>
        </label>
        <p className="muted small" style={{ margin: '6px 0 0 24px' }}>
          {nd.notifier_ro
            ? 'Activé : chaque pointage valide correspondant aux critères ci-dessous enverra une notification au RO du collaborateur.'
            : 'Désactivé : aucune notification au RO ne sera envoyée tant que cette case reste décochée.'}
        </p>
        <ErreursChamp erreurs={erreursNotifierRo} />
      </div>

      <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
        <legend className="field-label" style={{ marginBottom: 8 }}>
          Portée des sites
        </legend>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <label
            htmlFor="notif-portee-societe"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: disabled ? 'default' : 'pointer' }}
          >
            <input
              id="notif-portee-societe"
              type="radio"
              name="notif-portee-sites"
              value="TOUTE_LA_SOCIETE"
              checked={nd.portee_sites === 'TOUTE_LA_SOCIETE'}
              disabled={disabled}
              onChange={() => majPortee('TOUTE_LA_SOCIETE')}
            />
            <span>Toute la société (tous les sites actifs présents et futurs)</span>
          </label>
          <label
            htmlFor="notif-portee-sites-selectionnes"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: disabled ? 'default' : 'pointer' }}
          >
            <input
              id="notif-portee-sites-selectionnes"
              type="radio"
              name="notif-portee-sites"
              value="SITES_SELECTIONNES"
              checked={nd.portee_sites === 'SITES_SELECTIONNES'}
              disabled={disabled}
              onChange={() => majPortee('SITES_SELECTIONNES')}
            />
            <span>Sites sélectionnés</span>
          </label>
        </div>
        <ErreursChamp erreurs={erreursPortee} />

        {nd.portee_sites === 'SITES_SELECTIONNES' ? (
          <div
            style={{
              marginTop: 10,
              marginLeft: 22,
              padding: 12,
              borderRadius: 8,
              border: '1px solid var(--border, #d8dee9)',
            }}
          >
            <div className="small" style={{ fontWeight: 600, marginBottom: 8 }}>
              Sites de pointage couverts
            </div>
            {sitesChargement ? <p className="muted small">Chargement des sites actifs…</p> : null}
            {sitesErreur ? <ErrorAlert>{sitesErreur}</ErrorAlert> : null}
            {!sitesChargement && sitesActifs.length === 0 ? (
              <p className="muted small" style={{ margin: 0 }}>
                Aucun site de pointage actif disponible.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {sitesActifs.map((site) => {
                  const inputId = `notif-site-${site.id}`;
                  const coche = nd.site_ids.includes(site.id);
                  return (
                    <label
                      key={site.id}
                      htmlFor={inputId}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: disabled ? 'default' : 'pointer' }}
                    >
                      <input
                        id={inputId}
                        type="checkbox"
                        checked={coche}
                        disabled={disabled}
                        onChange={() => basculerSite(site.id)}
                      />
                      <span>
                        {site.libelle} <span className="muted small">({site.code})</span>
                      </span>
                    </label>
                  );
                })}
              </div>
            )}
            <ErreursChamp erreurs={erreursSites} />
          </div>
        ) : (
          <ErreursChamp erreurs={erreursSites} />
        )}
      </fieldset>

      <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
        <legend className="field-label" style={{ marginBottom: 8 }}>
          Types de pointage déclencheurs
        </legend>
        <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
          {TYPES_POINTAGE_OPTIONS.map((opt) => {
            const coche = nd.types_pointage.includes(opt.value);
            return (
              <label
                key={opt.value}
                htmlFor={opt.id}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: disabled ? 'default' : 'pointer' }}
              >
                <input
                  id={opt.id}
                  type="checkbox"
                  checked={coche}
                  disabled={disabled}
                  onChange={() => basculerType(opt.value)}
                />
                <span>{opt.label}</span>
              </label>
            );
          })}
        </div>
        <ErreursChamp erreurs={erreursTypes} />
      </fieldset>

      <div>
        <label className="field-label" htmlFor="notif-pointage-canal">
          Canal de notification
        </label>
        <select
          id="notif-pointage-canal"
          className="field-input"
          style={{ maxWidth: 320 }}
          disabled={disabled}
          value={nd.canal}
          onChange={(e) => onChange({ ...nd, canal: e.target.value as CanalNotificationPointage })}
        >
          <option value="WEBSOCKET">Application uniquement (temps réel)</option>
          <option value="BOTH">Application et courriel</option>
        </select>
        <ErreursChamp erreurs={erreursCanal} />
      </div>
    </div>
  );
}

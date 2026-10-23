import type { ReactNode, SVGProps } from 'react';

/* ─── Icônes (tracé 1.75, style Lucide) ─────────────────────────────────────
   Remplacent les émojis : taille et couleur pilotées par le CSS (currentColor). */

const PATHS = {
  check: 'M20 6L9 17l-5-5',
  x: 'M18 6L6 18M6 6l12 12',
  clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  calendar: 'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 012 2v14a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2z',
  clipboard:
    'M9 4h6M9 4a1 1 0 00-1 1v1h8V5a1 1 0 00-1-1M9 4H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V6a2 2 0 00-2-2h-2M9 12h6M9 16h4',
  file: 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zM14 2v6h6M9 13h6M9 17h6',
  paperclip: 'M21 11l-8.6 8.6a5 5 0 01-7.1-7.1l8.6-8.6a3.3 3.3 0 014.7 4.7l-8.6 8.6a1.7 1.7 0 01-2.4-2.4l7.9-7.9',
  mic: 'M12 2a3 3 0 00-3 3v7a3 3 0 006 0V5a3 3 0 00-3-3zM19 10v2a7 7 0 01-14 0v-2M12 19v3',
  user: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z',
  users: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75',
  building: 'M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v.01M9 13v.01M9 17v.01',
  briefcase: 'M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2zM16 7V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v2',
  target: 'M12 22a10 10 0 100-20 10 10 0 000 20zM12 18a6 6 0 100-12 6 6 0 000 12zM12 14a2 2 0 100-4 2 2 0 000 4z',
  alert: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
  info: 'M12 22a10 10 0 100-20 10 10 0 000 20zM12 16v-4M12 8h.01',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2M5.5 5.1L2 12v6a2 2 0 002 2h16a2 2 0 002-2v-6l-3.5-6.9A2 2 0 0016.7 4H7.3a2 2 0 00-1.8 1.1z',
  search: 'M11 19a8 8 0 100-16 8 8 0 000 16zM21 21l-4.35-4.35',
  plus: 'M12 5v14M5 12h14',
  download: 'M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3',
  eye: 'M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zM12 15a3 3 0 100-6 3 3 0 000 6z',
  edit: 'M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4 12.5-12.5z',
  trash: 'M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6',
  chevronRight: 'M9 18l6-6-6-6',
  mapPin: 'M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0zM12 13a3 3 0 100-6 3 3 0 000 6z',
  qr: 'M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 2h2v2h-2v-2zm4-2h2v6h-6v-2h2v-2h2v-2z',
  graduation: 'M22 10L12 5 2 10l10 5 10-5zM6 12.5V17c0 1.5 2.7 3 6 3s6-1.5 6-3v-4.5',
  send: 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z',
  lock: 'M19 11H5a2 2 0 00-2 2v7a2 2 0 002 2h14a2 2 0 002-2v-7a2 2 0 00-2-2zM7 11V7a5 5 0 0110 0v4',
  refresh: 'M23 4v6h-6M1 20v-6h6M3.5 9a9 9 0 0114.9-3.4L23 10M1 14l4.6 4.4A9 9 0 0020.5 15',
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({
  name,
  size = 16,
  label,
  ...rest
}: { name: IconName; size?: number; label?: string } & Omit<SVGProps<SVGSVGElement>, 'name'>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
      {...rest}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/* ─── Statuts ───────────────────────────────────────────────────────────── */

export type Tone = 'success' | 'danger' | 'warning' | 'info' | 'neutral' | 'progress';

const STATUS: Record<string, { label: string; tone: Tone }> = {
  NOUVEAU: { label: 'Nouveau', tone: 'info' },
  EN_ANALYSE: { label: 'En analyse', tone: 'progress' },
  EN_TRAITEMENT: { label: 'En traitement', tone: 'progress' },
  EN_TRAITEMENT_RH: { label: 'En traitement RH', tone: 'progress' },
  EN_COURS: { label: 'En cours', tone: 'progress' },
  EN_ATTENTE_VALIDATION: { label: 'En attente de validation', tone: 'warning' },
  EN_ATTENTE_FILE: { label: 'En file d’attente', tone: 'warning' },
  EN_VALIDATION_RRH: { label: 'Validation RRH', tone: 'warning' },
  PLANIFIEE: { label: 'Planifiée', tone: 'info' },
  INTEGREE_PLAN: { label: 'Intégrée au plan', tone: 'success' },
  APPROUVEE: { label: 'Approuvée', tone: 'success' },
  VALIDEE: { label: 'Validée', tone: 'success' },
  VALIDE: { label: 'Valide', tone: 'success' },
  CONFIRME: { label: 'Confirmé', tone: 'success' },
  RESOLU: { label: 'Résolu', tone: 'success' },
  TERMINEE: { label: 'Terminée', tone: 'neutral' },
  FERME: { label: 'Fermé', tone: 'neutral' },
  ACTIF: { label: 'Actif', tone: 'success' },
  ACTIVE: { label: 'Active', tone: 'success' },
  PUBLISHED: { label: 'Publié', tone: 'success' },
  DRAFT: { label: 'Brouillon', tone: 'neutral' },
  ARCHIVE: { label: 'Archivé', tone: 'neutral' },
  ARCHIVED: { label: 'Archivé', tone: 'neutral' },
  REJETEE: { label: 'Rejetée', tone: 'danger' },
  REFUSEE: { label: 'Refusée', tone: 'danger' },
  ANNULEE: { label: 'Annulée', tone: 'neutral' },
  EXPIRE: { label: 'Expiré', tone: 'danger' },
  DISPONIBLE: { label: 'Disponible', tone: 'success' },
  REVOQUE: { label: 'Révoqué', tone: 'danger' },
};

const TONE_ICON: Record<Tone, IconName> = {
  success: 'check',
  danger: 'x',
  warning: 'clock',
  info: 'info',
  progress: 'refresh',
  neutral: 'info',
};

export function statusInfo(statut: string | null | undefined): { label: string; tone: Tone } {
  const key = (statut ?? '').toUpperCase();
  if (STATUS[key]) return STATUS[key];
  if (key.startsWith('REJETE')) return { label: humanize(key), tone: 'danger' };
  return { label: humanize(key) || '—', tone: 'neutral' };
}

/** Badge de statut : couleur + icône + texte (jamais la couleur seule). */
export function StatusBadge({ statut, label }: { statut: string | null | undefined; label?: string }) {
  const info = statusInfo(statut);
  return (
    <span className={`status status--${info.tone}`}>
      <Icon name={TONE_ICON[info.tone]} size={12} />
      {label ?? info.label}
    </span>
  );
}

export function Chip({ tone = 'neutral', icon, children }: { tone?: Tone; icon?: IconName; children: ReactNode }) {
  return (
    <span className={`chip chip--${tone}`}>
      {icon ? <Icon name={icon} size={14} /> : null}
      {children}
    </span>
  );
}

export function humanize(code: string): string {
  if (!code) return '';
  const s = code.replaceAll('_', ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/* ─── Mise en page ──────────────────────────────────────────────────────── */

export function PageHeader({ title, lead, actions }: { title: string; lead?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page__head">
      <div>
        <h2 className="page__title">{title}</h2>
        {lead ? <p className="page__lead">{lead}</p> : null}
      </div>
      {actions ? <div className="page__head-actions">{actions}</div> : null}
    </div>
  );
}

export function EmptyState({
  icon = 'inbox',
  title,
  children,
  action,
}: {
  icon?: IconName;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty__icon">
        <Icon name={icon} size={24} />
      </span>
      <p className="empty__title">{title}</p>
      {children ? <p className="empty__text">{children}</p> : null}
      {action ? <div className="empty__action">{action}</div> : null}
    </div>
  );
}

export function LoadingState({ label = 'Chargement…' }: { label?: string }) {
  return (
    <div className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden />
      <span>{label}</span>
    </div>
  );
}

export function ErrorAlert({ children }: { children: ReactNode }) {
  return (
    <div className="alert alert--error" role="alert">
      <Icon name="alert" size={16} />
      <span>{children}</span>
    </div>
  );
}

export function Avatar({ name, size = 32 }: { name: string; size?: number }) {
  const initials =
    name
      .split(/[\s._-]+/)
      .filter(Boolean)
      .map((w) => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase() || '?';
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38 }} aria-hidden>
      {initials}
    </span>
  );
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

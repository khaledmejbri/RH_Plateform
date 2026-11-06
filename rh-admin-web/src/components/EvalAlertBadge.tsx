import {
  ALERTE_META,
  DISCREPANCY_FR,
  type CouleurAlerte,
} from '../api/evaluationCatalog';

interface EvalAlertBadgeProps {
  couleur?: CouleurAlerte | null;
  compact?: boolean;
}

/** Pastille Conforme / Vigilance / Plan d’action requis (codes VERT / ORANGE / ROUGE) § 12.5, D-E12.b — toujours libellé + icône (WCAG). */
export function EvalAlertBadge({ couleur, compact = false }: EvalAlertBadgeProps) {
  if (!couleur) {
    return <span className="text-muted" title="Aucune note du responsable pour le moment (D-E12.b)">Non évaluée</span>;
  }
  const meta = ALERTE_META[couleur];
  return (
    <span
      className={`badge ${meta.className}`}
      title={meta.label}
      aria-label={meta.label}
    >
      <span aria-hidden="true">{meta.icon}</span>
      {' '}
      {compact ? meta.court : meta.label}
    </span>
  );
}

interface DiscrepancyLabelProps {
  severity: string;
}

export function DiscrepancyLabel({ severity }: DiscrepancyLabelProps) {
  const meta = DISCREPANCY_FR[severity] ?? { label: severity, icon: '·' };
  return (
    <span className={meta.badge ? `badge ${meta.badge}` : undefined} title={meta.label}>
      <span aria-hidden="true">{meta.icon}</span> {meta.label}
    </span>
  );
}

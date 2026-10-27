import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  deleteOrganigrammeManager,
  getCollaborateursPage,
  getOrganigramme,
  libelleProfilAcces,
  nomCollaborateur,
  postOrganigrammeManager,
  postOrganigrammeNoeud,
  putOrganigrammeNoeud,
  type CollaborateurRow,
  type OrganigrammeNoeud,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { useDialogA11y } from '../components/useDialogA11y';
import { Icon } from '../components/ui';

type ModalKind = 'creer' | 'editer' | 'manager' | 'rattacher' | 'confirm-retrait' | null;

/** D-H04 : catalogue fermé, du plus haut au plus bas niveau (même ordre que le serveur). */
const TYPES_NOEUD = ['Direction Générale', 'Direction', 'Département', 'Service'] as const;
const NIVEAU_DG = 0;
const NIVEAU_DIRECTION = 1;
const SUCCESS_DISMISS_MS = 4000;
const COLLAPSE_DEPTH_DEFAULT = 3;
const DND_MIME = 'application/x-sirh-noeud';

function normaliserType(t: string): string {
  return t.normalize('NFD').replace(/\p{M}/gu, '').replace(/[\s_-]/g, '').toLowerCase();
}

/** Niveau du type (0 = Direction Générale … 3 = Service), -1 si type non reconnu. */
function niveauType(t: string | null | undefined): number {
  if (!t) return -1;
  const cle = normaliserType(t);
  return TYPES_NOEUD.findIndex((x) => normaliserType(x) === cle);
}

/** Types qu'on peut créer sous `parent` (ou sans nœud sélectionné). La DG seulement si elle n'existe pas encore. */
function typesCreables(parent: OrganigrammeNoeud | null, dgExiste: boolean): string[] {
  if (!parent) {
    // Une direction est rattachée d'office à la DG : il faut donc que la DG existe.
    return TYPES_NOEUD.filter((_, i) => (i === NIVEAU_DG ? !dgExiste : i !== NIVEAU_DIRECTION || dgExiste));
  }
  const niveau = niveauType(parent.type_noeud);
  if (niveau < 0) return [];
  return TYPES_NOEUD.filter((_, i) => i > niveau && (i !== NIVEAU_DIRECTION || niveau === NIVEAU_DG));
}

/** Même règle que le serveur : parent de niveau strictement supérieur ; une direction uniquement sous la DG. */
function peutRattacher(noeud: OrganigrammeNoeud, parent: OrganigrammeNoeud | null): boolean {
  const n = niveauType(noeud.type_noeud);
  if (n < 0) return false;
  if (!parent) return n > NIVEAU_DIRECTION;
  if (n === NIVEAU_DG) return false;
  const p = niveauType(parent.type_noeud);
  if (p < 0 || p >= n) return false;
  return n !== NIVEAU_DIRECTION || p === NIVEAU_DG;
}

function flattenNoeuds(racines: OrganigrammeNoeud[]): OrganigrammeNoeud[] {
  const out: OrganigrammeNoeud[] = [];
  const walk = (n: OrganigrammeNoeud) => {
    out.push(n);
    n.enfants.forEach(walk);
  };
  racines.forEach(walk);
  return out;
}

function collectDescendantIds(noeud: OrganigrammeNoeud): Set<string> {
  const ids = new Set<string>();
  const walk = (n: OrganigrammeNoeud) => {
    ids.add(n.identifiant);
    n.enfants.forEach(walk);
  };
  walk(noeud);
  return ids;
}

function findNoeud(racines: OrganigrammeNoeud[], id: string): OrganigrammeNoeud | null {
  for (const n of flattenNoeuds(racines)) {
    if (n.identifiant === id) return n;
  }
  return null;
}

function findPathIds(racines: OrganigrammeNoeud[], targetId: string): string[] {
  const path: string[] = [];
  const walk = (nodes: OrganigrammeNoeud[], trail: string[]): boolean => {
    for (const n of nodes) {
      const next = [...trail, n.identifiant];
      if (n.identifiant === targetId) {
        path.push(...next);
        return true;
      }
      if (walk(n.enfants, next)) return true;
    }
    return false;
  };
  walk(racines, []);
  return path;
}

function matchesSearch(n: OrganigrammeNoeud, q: string): boolean {
  if (!q) return true;
  const hay = [
    n.libelle,
    n.code,
    n.type_noeud,
    n.titre_poste ?? '',
    n.manager ? `${n.manager.prenom} ${n.manager.nom}` : '',
    n.manager?.matricule ?? '',
  ]
    .join(' ')
    .toLowerCase();
  return hay.includes(q);
}

function collectSearchMatchIds(racines: OrganigrammeNoeud[], q: string): Set<string> {
  const ids = new Set<string>();
  if (!q) return ids;
  for (const n of flattenNoeuds(racines)) {
    if (matchesSearch(n, q)) {
      findPathIds(racines, n.identifiant).forEach((id) => ids.add(id));
      ids.add(n.identifiant);
    }
  }
  return ids;
}

function initials(prenom: string, nom: string): string {
  const a = prenom.trim().charAt(0);
  const b = nom.trim().charAt(0);
  return `${a}${b}`.toUpperCase() || '?';
}

function libelleCollaborateurs(n: number): string {
  return n <= 1 ? `${n} collaborateur` : `${n} collaborateurs`;
}

function sansResponsable(n: OrganigrammeNoeud): boolean {
  return n.actif && !n.manager;
}

function writeForbiddenMessage(e: unknown): string {
  const msg = e instanceof Error ? e.message : '';
  if (/403|interdit|forbidden|droit/i.test(msg)) {
    return 'Vous n’avez pas le droit de modifier l’organigramme.';
  }
  return msg || 'Action impossible';
}

function Modal({
  title,
  onClose,
  children,
  wide,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useDialogA11y(onClose);

  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={ref}
        tabIndex={-1}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="org-modal-title"
        style={{ maxWidth: wide ? 560 : 480 }}
      >
        <div className="org-modal__head">
          <h3 id="org-modal-title" className="modal__title" style={{ margin: 0 }}>
            {title}
          </h3>
          <button type="button" className="modal__close" onClick={onClose} aria-label="Fermer">
            <Icon name="x" size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function OrgSkeleton() {
  return (
    <div className="org-skeleton" aria-hidden>
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="org-skeleton__card" style={{ marginLeft: i * 24 }} />
      ))}
    </div>
  );
}

/** Glisser-déposer des unités « à rattacher » (D-H04) : nœud en cours de glissement + dépôt sur une cible. */
type DragProps = {
  dragged: OrganigrammeNoeud | null;
  onDragStart: (n: OrganigrammeNoeud) => void;
  onDragEnd: () => void;
  onDropOn: (cible: OrganigrammeNoeud) => void;
};

function OrgNodeCard({
  noeud,
  selected,
  highlighted,
  lectureSeule,
  canAddChild,
  draggable,
  drag,
  onSelect,
  onAddChild,
}: {
  noeud: OrganigrammeNoeud;
  selected: boolean;
  highlighted: boolean;
  lectureSeule: boolean;
  canAddChild: boolean;
  draggable?: boolean;
  drag?: DragProps;
  onSelect: () => void;
  onAddChild: () => void;
}) {
  const mgr = noeud.manager;
  const emptyMgr = sansResponsable(noeud);
  const [survol, setSurvol] = useState(false);
  const enGlissement = drag?.dragged ?? null;
  const cibleValide =
    !!enGlissement &&
    enGlissement.identifiant !== noeud.identifiant &&
    !collectDescendantIds(enGlissement).has(noeud.identifiant) &&
    peutRattacher(enGlissement, noeud);

  return (
    <div
      className={[
        'org-node',
        selected ? 'org-node--selected' : '',
        !noeud.actif ? 'org-node--inactive' : '',
        emptyMgr ? 'org-node--no-manager' : '',
        highlighted ? 'org-node--highlight' : '',
        lectureSeule ? 'org-node--readonly' : '',
        draggable ? 'org-node--draggable' : '',
        enGlissement?.identifiant === noeud.identifiant ? 'org-node--dragging' : '',
        enGlissement && enGlissement.identifiant !== noeud.identifiant
          ? cibleValide
            ? survol
              ? 'org-node--drop-over'
              : 'org-node--drop-ok'
            : 'org-node--drop-ko'
          : '',
      ]
        .filter(Boolean)
        .join(' ')}
      draggable={draggable}
      onDragStart={(e) => {
        if (!draggable || !drag) return;
        e.dataTransfer.setData(DND_MIME, noeud.identifiant);
        e.dataTransfer.effectAllowed = 'move';
        drag.onDragStart(noeud);
      }}
      onDragEnd={() => drag?.onDragEnd()}
      onDragOver={(e) => {
        if (!cibleValide) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (!survol) setSurvol(true);
      }}
      onDragLeave={() => setSurvol(false)}
      onDrop={(e) => {
        setSurvol(false);
        if (!cibleValide || !drag) return;
        e.preventDefault();
        drag.onDropOn(noeud);
      }}
    >
      <button type="button" className="org-node__body" onClick={onSelect}>
        <div className="org-node__meta">
          <span className="org-node__type">{noeud.type_noeud || 'Service'}</span>
          {!noeud.actif ? <span className="badge badge--default">Inactif</span> : null}
          {emptyMgr ? <span className="badge badge--warning">Sans responsable</span> : null}
        </div>
        <div className="org-node__title">{noeud.libelle}</div>
        {noeud.titre_poste ? <div className="org-node__poste">{noeud.titre_poste}</div> : null}
        <div className={`org-node__manager-block${emptyMgr ? ' org-node__manager-block--empty' : ''}`}>
          {mgr ? (
            <>
              <span className="org-avatar" aria-hidden>
                {initials(mgr.prenom, mgr.nom)}
              </span>
              <span className="org-node__manager-name">
                {mgr.prenom} {mgr.nom}
              </span>
              {mgr.profil_acces ? (
                <span className="text-muted text-sm">{libelleProfilAcces(mgr.profil_acces)}</span>
              ) : null}
            </>
          ) : (
            <span className="org-node__manager-empty">Sans responsable</span>
          )}
        </div>
        {noeud.membres.length > 0 ? (
          <div className="org-node__count">{libelleCollaborateurs(noeud.membres.length)}</div>
        ) : null}
      </button>
      {!lectureSeule && canAddChild ? (
        <button
          type="button"
          className="org-node__add"
          title="Ajouter un élément enfant"
          aria-label={`Ajouter un élément sous ${noeud.libelle}`}
          onClick={onAddChild}
        >
          +
        </button>
      ) : null}
    </div>
  );
}

function OrgTreeBranch({
  noeuds,
  depth,
  selectedId,
  expandedIds,
  highlightIds,
  searchQuery,
  lectureSeule,
  dgExiste,
  drag,
  onToggle,
  onSelect,
  onAddChild,
}: {
  noeuds: OrganigrammeNoeud[];
  depth: number;
  selectedId: string | null;
  expandedIds: Set<string>;
  highlightIds: Set<string>;
  searchQuery: string;
  lectureSeule: boolean;
  dgExiste: boolean;
  drag: DragProps;
  onToggle: (id: string) => void;
  onSelect: (n: OrganigrammeNoeud) => void;
  onAddChild: (parent: OrganigrammeNoeud | null) => void;
}) {
  const q = searchQuery.trim().toLowerCase();

  return (
    <ul className={`org-tree${depth > 0 ? ' org-tree--branch' : ''}`} role={depth === 0 ? 'tree' : 'group'}>
      {noeuds.map((n) => {
        const hasChildren = n.enfants.length > 0;
        const expanded = expandedIds.has(n.identifiant);
        const selfMatch = q ? matchesSearch(n, q) : false;
        const inPath = q ? highlightIds.has(n.identifiant) : true;
        if (q && !inPath) return null;

        return (
          <li key={n.identifiant} className="org-tree__item" role="treeitem" aria-expanded={hasChildren ? expanded : undefined}>
            <div className="org-tree__row">
              {hasChildren ? (
                <button
                  type="button"
                  className={`org-tree__chevron${expanded ? ' org-tree__chevron--open' : ''}`}
                  aria-label={expanded ? 'Replier' : 'Développer'}
                  aria-expanded={expanded}
                  onClick={() => onToggle(n.identifiant)}
                >
                  <Icon name="chevronRight" size={14} />
                </button>
              ) : (
                <span className="org-tree__chevron-spacer" aria-hidden />
              )}
              <OrgNodeCard
                noeud={n}
                selected={selectedId === n.identifiant}
                highlighted={selfMatch}
                lectureSeule={lectureSeule}
                canAddChild={typesCreables(n, dgExiste).length > 0}
                drag={drag}
                onSelect={() => onSelect(n)}
                onAddChild={() => onAddChild(n)}
              />
            </div>
            {hasChildren && expanded ? (
              <OrgTreeBranch
                noeuds={n.enfants}
                depth={depth + 1}
                selectedId={selectedId}
                expandedIds={expandedIds}
                highlightIds={highlightIds}
                searchQuery={searchQuery}
                lectureSeule={lectureSeule}
                dgExiste={dgExiste}
                drag={drag}
                onToggle={onToggle}
                onSelect={onSelect}
                onAddChild={onAddChild}
              />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

function ParentPickerList({
  noeud,
  racines,
  excludeIds,
  value,
  onChange,
  filter,
}: {
  noeud: OrganigrammeNoeud;
  racines: OrganigrammeNoeud[];
  excludeIds: Set<string>;
  value: string;
  onChange: (id: string) => void;
  filter: string;
}) {
  const racineAutorisee = peutRattacher(noeud, null);
  const q = filter.trim().toLowerCase();
  const matchIds = q ? collectSearchMatchIds(racines, q) : null;
  const rows: { id: string; label: string; depth: number; disabled: boolean }[] = [];

  const walk = (nodes: OrganigrammeNoeud[], depth: number) => {
    for (const n of nodes) {
      if (!matchIds || matchIds.has(n.identifiant)) {
        rows.push({
          id: n.identifiant,
          label: `${n.type_noeud} · ${n.libelle}`,
          depth,
          disabled: excludeIds.has(n.identifiant) || !peutRattacher(noeud, n),
        });
      }
      walk(n.enfants, depth + 1);
    }
  };
  walk(racines, 0);

  return (
    <div className="org-parent-picker" role="listbox" aria-label="Parent">
      {racineAutorisee ? (
        <button
          type="button"
          role="option"
          aria-selected={value === ''}
          className={`org-parent-picker__option${value === '' ? ' org-parent-picker__option--selected' : ''}`}
          onClick={() => onChange('')}
        >
          — À rattacher (aucun parent) —
        </button>
      ) : null}
      {rows.map((r) => (
        <button
          key={r.id}
          type="button"
          role="option"
          aria-selected={value === r.id}
          disabled={r.disabled}
          className={[
            'org-parent-picker__option',
            value === r.id ? 'org-parent-picker__option--selected' : '',
            r.disabled ? 'org-parent-picker__option--disabled' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          style={{ paddingLeft: `${0.75 + r.depth * 0.85}rem` }}
          onClick={() => {
            if (!r.disabled) onChange(r.id);
          }}
        >
          {r.label}
          {r.disabled ? ' (non autorisé)' : ''}
        </button>
      ))}
    </div>
  );
}

export default function OrganigrammePage() {
  const confirm = useConfirm();
  const lectureSeule = useLectureSeule();
  const [racines, setRacines] = useState<OrganigrammeNoeud[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [selected, setSelected] = useState<OrganigrammeNoeud | null>(null);
  const [modal, setModal] = useState<ModalKind>(null);
  const [parentPourCreation, setParentPourCreation] = useState<OrganigrammeNoeud | null>(null);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [saving, setSaving] = useState(false);
  const [moving, setMoving] = useState(false);
  const [search, setSearch] = useState('');
  const [inclureInactifs, setInclureInactifs] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [aideOpen, setAideOpen] = useState(false);
  const [parentPickerId, setParentPickerId] = useState('');
  const [parentSearch, setParentSearch] = useState('');
  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');
  const [typeNoeud, setTypeNoeud] = useState('');
  const [titrePoste, setTitrePoste] = useState('');
  const [actif, setActif] = useState(true);

  const [managerId, setManagerId] = useState('');
  const [managerTitre, setManagerTitre] = useState('');
  const [collabSearch, setCollabSearch] = useState('');

  const [dragged, setDragged] = useState<OrganigrammeNoeud | null>(null);

  const tousNoeuds = useMemo(() => flattenNoeuds(racines), [racines]);
  const dgExiste = useMemo(() => racines.some((n) => niveauType(n.type_noeud) === NIVEAU_DG), [racines]);
  /** D-H04 : à droite l'arbre sous la DG ; à gauche les départements / services sans parent, à glisser. */
  const arbre = useMemo(() => racines.filter((n) => niveauType(n.type_noeud) === NIVEAU_DG), [racines]);
  const aRattacher = useMemo(() => racines.filter((n) => niveauType(n.type_noeud) !== NIVEAU_DG), [racines]);
  const searchQ = search.trim().toLowerCase();
  const highlightIds = useMemo(
    () => collectSearchMatchIds(racines, searchQ),
    [racines, searchQ],
  );

  const showSuccess = useCallback((msg: string) => {
    setSuccess(msg);
    if (successTimer.current) clearTimeout(successTimer.current);
    successTimer.current = setTimeout(() => setSuccess(null), SUCCESS_DISMISS_MS);
  }, []);

  useEffect(() => {
    return () => {
      if (successTimer.current) clearTimeout(successTimer.current);
    };
  }, []);

  const initExpanded = useCallback((tree: OrganigrammeNoeud[], keepId?: string | null) => {
    const next = new Set<string>();
    const walk = (nodes: OrganigrammeNoeud[], depth: number) => {
      for (const n of nodes) {
        if (depth < COLLAPSE_DEPTH_DEFAULT) next.add(n.identifiant);
        walk(n.enfants, depth + 1);
      }
    };
    walk(tree, 0);
    if (keepId) {
      findPathIds(tree, keepId).forEach((id) => next.add(id));
    }
    setExpandedIds(next);
  }, []);

  const reload = useCallback(
    async (keepSelectedId?: string | null, opts?: { silent?: boolean }) => {
      const idToKeep = keepSelectedId !== undefined ? keepSelectedId : selected?.identifiant ?? null;
      setErr(null);
      if (!opts?.silent) setLoading(true);
      try {
        const [org, page] = await Promise.all([
          getOrganigramme(inclureInactifs),
          getCollaborateursPage(0, 200),
        ]);
        setRacines(org.racines);
        setCollaborateurs(page.contenu);
        initExpanded(org.racines, idToKeep);
        if (idToKeep) {
          const flat = flattenNoeuds(org.racines);
          setSelected(flat.find((n) => n.identifiant === idToKeep) ?? null);
        }
      } catch (e) {
        setErr(e instanceof Error ? e.message : 'Impossible de charger l’organigramme.');
      } finally {
        setLoading(false);
      }
    },
    [selected?.identifiant, inclureInactifs, initExpanded],
  );

  useEffect(() => {
    void reload(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial + filter inactifs
  }, [inclureInactifs]);

  useEffect(() => {
    if (!searchQ) return;
    setExpandedIds((prev) => {
      const next = new Set(prev);
      highlightIds.forEach((id) => next.add(id));
      return next;
    });
  }, [searchQ, highlightIds]);

  /**
   * § 5.3 / D-H04 : rattachement par « Rattacher… » ou par glisser-déposer d'une unité « à rattacher »,
   * toujours confirmé avec le récapitulatif ancien → nouveau parent (SCRUM-6). Règles de type vérifiées
   * ici pour l'affichage et par le serveur.
   */
  async function rattacherNoeud(noeudId: string, newParentId: string | null) {
    const noeud = findNoeud(racines, noeudId);
    if (!noeud) return;

    const currentParentId = noeud.parent_identifiant ?? null;
    if (currentParentId === newParentId) return;

    const target = newParentId ? findNoeud(racines, newParentId) : null;
    if (newParentId && !target) return;
    if (newParentId && collectDescendantIds(noeud).has(newParentId)) {
      setErr('Impossible : un élément ne peut pas être placé sous l’un de ses propres sous-éléments.');
      return;
    }
    if (!peutRattacher(noeud, target)) {
      setErr(
        target
          ? `Impossible : un(e) ${noeud.type_noeud} ne peut pas être placé(e) sous ${target.type_noeud} « ${target.libelle} ».`
          : `Impossible : un(e) ${noeud.type_noeud} doit rester rattaché(e).`,
      );
      return;
    }
    const ancien = currentParentId ? findNoeud(racines, currentParentId)?.libelle ?? '—' : 'À rattacher';
    const nouveau = target ? target.libelle : 'À rattacher';
    const ok = await confirm({
      title: `Rattacher « ${noeud.libelle} »`,
      message: `${noeud.type_noeud} « ${noeud.libelle} » : ${ancien} → ${nouveau}. Ses sous-éléments suivent ; ses membres et son responsable ne changent pas. Continuer ?`,
      confirmLabel: 'Rattacher',
    });
    if (!ok) return;

    setMoving(true);
    setErr(null);
    try {
      if (newParentId === null) {
        await putOrganigrammeNoeud(noeudId, { detacher_du_parent: true });
      } else {
        await putOrganigrammeNoeud(noeudId, { parent_identifiant: newParentId });
      }
      showSuccess(`« ${noeud.libelle} » rattaché à ${nouveau}.`);
      await reload(noeudId, { silent: true });
    } catch (e) {
      setErr(writeForbiddenMessage(e));
    } finally {
      setMoving(false);
    }
  }

  function expandAll() {
    setExpandedIds(new Set(tousNoeuds.map((n) => n.identifiant)));
  }

  function collapseAll() {
    const next = new Set<string>();
    racines.forEach((n) => next.add(n.identifiant));
    if (selected) {
      findPathIds(racines, selected.identifiant).forEach((id) => next.add(id));
    }
    setExpandedIds(next);
  }

  function toggleExpand(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function openCreer(parent: OrganigrammeNoeud | null) {
    const types = typesCreables(parent, dgExiste);
    if (types.length === 0) return;
    setParentPourCreation(parent);
    setCode('');
    setLibelle('');
    setTypeNoeud(types[0]);
    setTitrePoste('');
    setActif(true);
    setModal('creer');
  }

  function openAjouter() {
    openCreer(selected);
  }

  /** Clic sur la carte déjà sélectionnée = désélection (création libre, sans parent). */
  function selectionner(n: OrganigrammeNoeud) {
    setSelected((cur) => (cur?.identifiant === n.identifiant ? null : n));
  }

  const drag: DragProps = {
    dragged,
    onDragStart: (n) => setDragged(n),
    onDragEnd: () => setDragged(null),
    onDropOn: (cible) => {
      const n = dragged;
      setDragged(null);
      if (n) void rattacherNoeud(n.identifiant, cible.identifiant);
    },
  };

  function openEditer(n: OrganigrammeNoeud) {
    setSelected(n);
    setLibelle(n.libelle);
    setTypeNoeud(n.type_noeud || '');
    setTitrePoste(n.titre_poste || '');
    setActif(n.actif);
    setModal('editer');
  }

  function openRattacher(n: OrganigrammeNoeud) {
    setSelected(n);
    setParentPickerId(n.parent_identifiant || '');
    setParentSearch('');
    setModal('rattacher');
  }

  function openManager(n: OrganigrammeNoeud) {
    setSelected(n);
    setManagerId(n.manager?.identifiant || '');
    setManagerTitre(n.titre_poste || '');
    setCollabSearch('');
    setModal('manager');
  }

  async function handleCreer() {
    setSaving(true);
    setErr(null);
    try {
      const created = await postOrganigrammeNoeud({
        code: code.trim(),
        libelle: libelle.trim(),
        type_noeud: typeNoeud.trim(),
        titre_poste: titrePoste.trim() || undefined,
        parent_identifiant: parentPourCreation?.identifiant,
        actif,
      });
      setModal(null);
      const aRattacherApres = !parentPourCreation && niveauType(typeNoeud) > NIVEAU_DIRECTION;
      showSuccess(
        aRattacherApres
          ? `${typeNoeud} « ${libelle.trim()} » créé(e) : glissez-le(la) dans l’organigramme pour le(la) rattacher.`
          : `${typeNoeud} « ${libelle.trim()} » créé(e).`,
      );
      await reload(created.identifiant, { silent: true });
    } catch (e) {
      setErr(writeForbiddenMessage(e));
    } finally {
      setSaving(false);
    }
  }

  async function handleEditer() {
    if (!selected) return;
    setSaving(true);
    setErr(null);
    try {
      await putOrganigrammeNoeud(selected.identifiant, {
        libelle: libelle.trim(),
        type_noeud: typeNoeud.trim(),
        titre_poste: titrePoste.trim(),
        actif,
      });
      setModal(null);
      showSuccess('Modifications enregistrées.');
      await reload(selected.identifiant, { silent: true });
    } catch (e) {
      setErr(writeForbiddenMessage(e));
    } finally {
      setSaving(false);
    }
  }

  async function handleRattacher() {
    if (!selected) return;
    const newParent = parentPickerId || null;
    const current = selected.parent_identifiant ?? null;
    if (newParent === current) {
      setModal(null);
      return;
    }
    if (newParent && collectDescendantIds(selected).has(newParent)) {
      setErr('Impossible : un élément ne peut pas être placé sous l’un de ses propres sous-éléments.');
      return;
    }
    setModal(null);
    await rattacherNoeud(selected.identifiant, newParent);
  }

  async function handleAssignManager() {
    if (!selected || !managerId) return;
    setSaving(true);
    setErr(null);
    try {
      await postOrganigrammeManager(selected.identifiant, {
        collaborateur_identifiant: managerId,
        titre_poste: managerTitre.trim() || undefined,
      });
      setModal(null);
      showSuccess('Responsable assigné.');
      await reload(selected.identifiant, { silent: true });
    } catch (e) {
      setErr(writeForbiddenMessage(e));
    } finally {
      setSaving(false);
    }
  }

  async function handleRetirerManager() {
    if (!selected) return;
    setSaving(true);
    setErr(null);
    try {
      await deleteOrganigrammeManager(selected.identifiant);
      setModal(null);
      showSuccess('Responsable retiré.');
      await reload(selected.identifiant, { silent: true });
    } catch (e) {
      setErr(writeForbiddenMessage(e));
    } finally {
      setSaving(false);
    }
  }

  const parentOfSelected = selected?.parent_identifiant
    ? findNoeud(racines, selected.parent_identifiant)
    : null;

  const collabsFiltered = useMemo(() => {
    const q = collabSearch.trim().toLowerCase();
    if (!q) return collaborateurs;
    return collaborateurs.filter((c) => {
      const hay = `${c.matricule} ${c.prenom} ${nomCollaborateur(c)}`.toLowerCase();
      return hay.includes(q);
    });
  }, [collaborateurs, collabSearch]);

  const excludeForRattacher = useMemo(() => {
    if (!selected) return new Set<string>();
    return collectDescendantIds(selected);
  }, [selected]);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Organigramme</h2>
          <p className="page__lead">
            {lectureSeule
              ? 'Consultez la hiérarchie et les responsables de chaque service.'
              : 'Visualisez la hiérarchie et désignez le responsable de chaque service.'}
          </p>
        </div>
        <div className="page__head-actions">
          <label className="org-search">
            <span className="sr-only">Rechercher</span>
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Rechercher un service ou un responsable…"
              aria-label="Rechercher un service ou un responsable"
            />
          </label>
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => void reload(selected?.identifiant ?? null)}
            disabled={loading || moving || saving}
          >
            Actualiser
          </button>
          {!lectureSeule ? (
            <button
              type="button"
              className="btn btn--primary"
              onClick={openAjouter}
              disabled={saving || moving || typesCreables(selected, dgExiste).length === 0}
              title={
                selected
                  ? typesCreables(selected, dgExiste).length === 0
                    ? `Un(e) ${selected.type_noeud} ne contient pas de sous-élément`
                    : `Créer sous « ${selected.libelle} » : ${typesCreables(selected, dgExiste).join(', ')}`
                  : 'Créer une direction (rattachée à la DG), un département ou un service à rattacher'
              }
            >
              {selected ? `+ Ajouter sous « ${selected.libelle} »` : '+ Ajouter'}
            </button>
          ) : null}
        </div>
      </div>

      {lectureSeule ? (
        <div className="alert alert--info org-mode-banner" role="status">
          Consultation uniquement — les modifications sont réservées à la RH.
        </div>
      ) : (
        <div className="org-aide-banner">
          <button
            type="button"
            className="org-aide-banner__toggle"
            aria-expanded={aideOpen}
            onClick={() => setAideOpen((v) => !v)}
          >
            Aide — comment créer / rattacher / assigner
          </button>
          {aideOpen ? (
            <p className="org-aide-banner__body">
              Hiérarchie : Direction Générale → Direction → Département → Service. Sélectionnez un élément pour
              créer en dessous uniquement les niveaux inférieurs (un service ne contient rien). Sans sélection
              (cliquez à nouveau sur l’élément sélectionné pour le désélectionner), « + Ajouter » crée une direction
              — rattachée automatiquement à la Direction Générale — ou un département / service « à rattacher » :
              il apparaît à gauche, glissez-le sur un élément de niveau supérieur. « Rattacher… » permet aussi de
              déplacer un élément existant. Le profil d’accès se modifie sur la fiche collaborateur.
            </p>
          ) : null}
        </div>
      )}

      <div className="org-toolbar" role="toolbar" aria-label="Navigation organigramme">
        <button type="button" className="btn btn--ghost btn--sm" onClick={expandAll} disabled={loading}>
          Tout développer
        </button>
        <button type="button" className="btn btn--ghost btn--sm" onClick={collapseAll} disabled={loading}>
          Tout replier
        </button>
        <label className="org-toolbar__check">
          <input
            type="checkbox"
            checked={inclureInactifs}
            onChange={(e) => setInclureInactifs(e.target.checked)}
          />
          Afficher les inactifs
        </label>
      </div>

      <div className="org-live" aria-live="polite">
        {err ? (
          <div className="alert alert--error" style={{ marginBottom: '1rem' }}>
            {err}
            {!loading && racines.length === 0 ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                style={{ marginLeft: '0.75rem' }}
                onClick={() => void reload(null)}
              >
                Réessayer
              </button>
            ) : null}
          </div>
        ) : null}
        {success ? (
          <div className="alert alert--success" style={{ marginBottom: '1rem' }}>
            {success}
          </div>
        ) : null}
        {moving ? (
          <div className="alert alert--info" style={{ marginBottom: '1rem' }}>
            Rattachement en cours…
          </div>
        ) : null}
      </div>

      <div className={`org-layout${!loading && aRattacher.length > 0 ? ' org-layout--split' : ''}`}>
        {!loading && aRattacher.length > 0 ? (
          <section className="org-unassigned" aria-labelledby="org-unassigned-title">
            <h3 id="org-unassigned-title" className="org-unassigned__title">
              À rattacher <span className="badge badge--warning">{aRattacher.length}</span>
            </h3>
            <p className="text-muted text-sm org-unassigned__hint">
              {lectureSeule
                ? 'Départements et services pas encore placés dans la hiérarchie.'
                : 'Glissez un élément sur une carte de niveau supérieur dans l’organigramme (ou utilisez « Rattacher… »).'}
            </p>
            <ul className="org-unassigned__list">
              {aRattacher.map((n) => (
                <li key={n.identifiant}>
                  <OrgNodeCard
                    noeud={n}
                    selected={selected?.identifiant === n.identifiant}
                    highlighted={false}
                    lectureSeule={lectureSeule}
                    canAddChild={typesCreables(n, dgExiste).length > 0}
                    draggable={!lectureSeule && !moving && peutRattacher(n, null)}
                    drag={drag}
                    onSelect={() => selectionner(n)}
                    onAddChild={() => openCreer(n)}
                  />
                  {n.enfants.length > 0 ? (
                    <p className="text-muted text-sm org-unassigned__children">
                      Contient : {n.enfants.map((e) => e.libelle).join(', ')}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
        ) : null}
        <div className="org-canvas" aria-busy={loading}>
          {loading ? (
            <>
              <p className="loading">Chargement de l’organigramme…</p>
              <OrgSkeleton />
            </>
          ) : err && racines.length === 0 ? (
            <div className="empty-state">
              <h3>Impossible de charger l’organigramme.</h3>
              <button type="button" className="btn btn--primary" onClick={() => void reload(null)}>
                Réessayer
              </button>
            </div>
          ) : arbre.length === 0 ? (
            <div className="empty-state">
              <h3>
                {lectureSeule
                  ? 'Aucune structure organisationnelle publiée.'
                  : 'Aucune Direction Générale pour le moment.'}
              </h3>
              {!lectureSeule ? (
                <>
                  <p>Commencez par le sommet de la hiérarchie : la Direction Générale.</p>
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => {
                      setSelected(null);
                      openCreer(null);
                    }}
                  >
                    Créer la Direction Générale
                  </button>
                </>
              ) : (
                <p>Contactez la RH pour publier l’organigramme.</p>
              )}
            </div>
          ) : (
            <OrgTreeBranch
              noeuds={arbre}
              depth={0}
              selectedId={selected?.identifiant ?? null}
              expandedIds={expandedIds}
              highlightIds={highlightIds}
              searchQuery={search}
              lectureSeule={lectureSeule}
              dgExiste={dgExiste}
              drag={drag}
              onToggle={toggleExpand}
              onSelect={selectionner}
              onAddChild={openCreer}
            />
          )}
        </div>

        <aside className="org-panel">
          {selected ? (
            <>
              <div className="org-panel__eyebrow">
                <span className="org-panel__type">{selected.type_noeud}</span>
                {!selected.actif ? <span className="badge badge--default">Inactif</span> : null}
                {sansResponsable(selected) ? (
                  <span className="badge badge--warning">Sans responsable</span>
                ) : null}
              </div>
              <h3 className="org-panel__title">{selected.libelle}</h3>
              <p className="text-muted text-sm">Code : {selected.code}</p>
              {selected.titre_poste ? (
                <p className="org-panel__poste">{selected.titre_poste}</p>
              ) : null}

              <div className="org-panel__section org-panel__responsable">
                <h4>Responsable</h4>
                {selected.manager ? (
                  <div className="org-panel__manager-card">
                    <span className="org-avatar org-avatar--lg" aria-hidden>
                      {initials(selected.manager.prenom, selected.manager.nom)}
                    </span>
                    <div>
                      <div className="org-panel__manager-name">
                        {selected.manager.prenom} {selected.manager.nom}
                      </div>
                      <div className="text-muted text-sm">
                        Mat. {selected.manager.matricule}
                        {selected.manager.profil_acces
                          ? ` · ${libelleProfilAcces(selected.manager.profil_acces)}`
                          : ''}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="org-panel__manager-empty">
                    <span className="badge badge--warning">Sans responsable</span>
                    <p className="text-muted text-sm" style={{ margin: '0.5rem 0 0' }}>
                      Les demandes M01 de cette unité pourront aller directement à la RRH.
                    </p>
                  </div>
                )}
              </div>

              <div className="org-panel__section">
                <h4>Rattachement</h4>
                {parentOfSelected ? (
                  <button
                    type="button"
                    className="org-panel__link"
                    onClick={() => {
                      setSelected(parentOfSelected);
                      setExpandedIds((prev) => {
                        const next = new Set(prev);
                        findPathIds(racines, parentOfSelected.identifiant).forEach((id) =>
                          next.add(id),
                        );
                        return next;
                      });
                    }}
                  >
                    Voir le parent : {parentOfSelected.libelle}
                  </button>
                ) : (
                  <p className="text-muted text-sm" style={{ margin: 0 }}>
                    {niveauType(selected.type_noeud) === NIVEAU_DG
                      ? 'Sommet de la hiérarchie'
                      : 'À rattacher : glissez-le dans l’organigramme ou utilisez « Rattacher… »'}
                  </p>
                )}
              </div>

              <div className="org-panel__section">
                <h4>Collaborateurs</h4>
                {selected.membres.length > 0 ? (
                  <ul className="org-panel__list">
                    {selected.membres.map((m) => (
                      <li key={m.identifiant}>
                        {m.prenom} {m.nom}
                        {m.poste_libelle ? ` — ${m.poste_libelle}` : ''}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-muted text-sm" style={{ margin: 0 }}>
                    Aucun collaborateur rattaché.
                  </p>
                )}
              </div>

              {!lectureSeule ? (
                <div className="org-panel__actions">
                  <button
                    type="button"
                    className={`btn btn--sm ${sansResponsable(selected) ? 'btn--primary' : 'btn--ghost'}`}
                    disabled={saving || moving}
                    onClick={() => openManager(selected)}
                  >
                    {selected.manager ? 'Changer le responsable' : 'Assigner le responsable'}
                  </button>
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    disabled={saving || moving}
                    onClick={() => openEditer(selected)}
                  >
                    Modifier
                  </button>
                  {niveauType(selected.type_noeud) !== NIVEAU_DG ? (
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      disabled={saving || moving}
                      onClick={() => openRattacher(selected)}
                    >
                      Rattacher…
                    </button>
                  ) : null}
                  {typesCreables(selected, dgExiste).length > 0 ? (
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      disabled={saving || moving}
                      onClick={() => openCreer(selected)}
                    >
                      Ajouter : {typesCreables(selected, dgExiste).join(' / ')}
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    onClick={() => setSelected(null)}
                  >
                    Désélectionner
                  </button>
                </div>
              ) : (
                <p className="org-panel__readonly-note">
                  Consultation — modification réservée à la RH.
                </p>
              )}
            </>
          ) : (
            <p className="text-muted">
              Sélectionnez un élément pour voir son détail et créer en dessous. Sans sélection, « + Ajouter » crée une
              direction, un département ou un service.
            </p>
          )}
        </aside>
      </div>

      {modal === 'creer' && (
        <Modal
          title={
            parentPourCreation
              ? `Ajouter sous « ${parentPourCreation.libelle} »`
              : 'Ajouter un élément'
          }
          onClose={() => setModal(null)}
          wide
        >
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="org-type">Type</label>
              <select id="org-type" value={typeNoeud} onChange={(e) => setTypeNoeud(e.target.value)}>
                {typesCreables(parentPourCreation, dgExiste).map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="org-code">Code</label>
              <input
                id="org-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="DIR-GEN"
              />
            </div>
            <div className="form-group full-width">
              <label htmlFor="org-libelle">Libellé</label>
              <input
                id="org-libelle"
                value={libelle}
                onChange={(e) => setLibelle(e.target.value)}
                placeholder="Direction générale"
              />
            </div>
            <div className="form-group full-width">
              <label htmlFor="org-poste">Titre du poste (optionnel)</label>
              <input
                id="org-poste"
                value={titrePoste}
                onChange={(e) => setTitrePoste(e.target.value)}
                placeholder="Directeur·rice"
              />
            </div>
            <div className="form-group full-width">
              <label>Parent</label>
              <input
                readOnly
                value={
                  parentPourCreation
                    ? `${parentPourCreation.type_noeud} · ${parentPourCreation.libelle}`
                    : niveauType(typeNoeud) === NIVEAU_DIRECTION
                      ? `Direction Générale (rattachement automatique)`
                      : niveauType(typeNoeud) === NIVEAU_DG
                        ? '— Sommet de la hiérarchie —'
                        : '— À rattacher : à glisser ensuite dans l’organigramme —'
                }
              />
            </div>
            <div className="form-group checkbox-group">
              <label>
                <input type="checkbox" checked={actif} onChange={(e) => setActif(e.target.checked)} />
                Actif
              </label>
            </div>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn--ghost" onClick={() => setModal(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saving || !code.trim() || !libelle.trim() || !typeNoeud.trim()}
              onClick={() => void handleCreer()}
            >
              Créer
            </button>
          </div>
        </Modal>
      )}

      {modal === 'editer' && selected && (
        <Modal title={`Modifier « ${selected.libelle} »`} onClose={() => setModal(null)} wide>
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="org-edit-type">Type</label>
              <select id="org-edit-type" value={typeNoeud} onChange={(e) => setTypeNoeud(e.target.value)}>
                {niveauType(typeNoeud) < 0 ? <option value={typeNoeud}>{typeNoeud || '—'} (non reconnu)</option> : null}
                {TYPES_NOEUD.filter(
                  (t) => niveauType(t) !== NIVEAU_DG || !dgExiste || niveauType(selected.type_noeud) === NIVEAU_DG,
                ).map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="org-edit-libelle">Libellé</label>
              <input
                id="org-edit-libelle"
                value={libelle}
                onChange={(e) => setLibelle(e.target.value)}
              />
            </div>
            <div className="form-group full-width">
              <label htmlFor="org-edit-poste">Titre du poste</label>
              <input
                id="org-edit-poste"
                value={titrePoste}
                onChange={(e) => setTitrePoste(e.target.value)}
              />
            </div>
            <div className="form-group checkbox-group">
              <label>
                <input type="checkbox" checked={actif} onChange={(e) => setActif(e.target.checked)} />
                Actif
              </label>
            </div>
          </div>
          <p className="text-muted text-sm">
            Pour changer le parent, utilisez « Rattacher… » dans le panneau détail.
          </p>
          <div className="form-actions">
            <button type="button" className="btn btn--ghost" onClick={() => setModal(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saving || !libelle.trim() || !typeNoeud.trim()}
              onClick={() => void handleEditer()}
            >
              Enregistrer
            </button>
          </div>
        </Modal>
      )}

      {modal === 'rattacher' && selected && (
        <Modal title={`Rattacher « ${selected.libelle} »`} onClose={() => setModal(null)} wide>
          <div className="form-group full-width">
            <label htmlFor="org-parent-search">Nouveau parent</label>
            <input
              id="org-parent-search"
              type="search"
              value={parentSearch}
              onChange={(e) => setParentSearch(e.target.value)}
              placeholder="Rechercher un service…"
            />
          </div>
          <ParentPickerList
            noeud={selected}
            racines={racines}
            excludeIds={excludeForRattacher}
            value={parentPickerId}
            onChange={setParentPickerId}
            filter={parentSearch}
          />
          <div className="form-actions">
            <button type="button" className="btn btn--ghost" onClick={() => setModal(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saving || moving}
              onClick={() => void handleRattacher()}
            >
              Enregistrer
            </button>
          </div>
        </Modal>
      )}

      {modal === 'manager' && selected && (
        <Modal title={`Responsable — ${selected.libelle}`} onClose={() => setModal(null)} wide>
          <div className="form-grid">
            <div className="form-group full-width">
              <label htmlFor="org-collab-search">Rechercher un collaborateur</label>
              <input
                id="org-collab-search"
                type="search"
                value={collabSearch}
                onChange={(e) => setCollabSearch(e.target.value)}
                placeholder="Matricule ou nom…"
              />
            </div>
            <div className="form-group full-width">
              <label htmlFor="org-manager-select">Collaborateur</label>
              <select
                id="org-manager-select"
                value={managerId}
                onChange={(e) => setManagerId(e.target.value)}
              >
                <option value="">— Choisir —</option>
                {collabsFiltered.map((c) => (
                  <option key={c.identifiant} value={c.identifiant}>
                    {c.matricule} — {c.prenom} {nomCollaborateur(c)}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group full-width">
              <label htmlFor="org-manager-titre">Titre de poste (optionnel)</label>
              <input
                id="org-manager-titre"
                value={managerTitre}
                onChange={(e) => setManagerTitre(e.target.value)}
                placeholder="Responsable de service"
              />
            </div>
            <p className="text-muted text-sm" style={{ margin: 0 }}>
              L’affectation du responsable ne change pas le profil d’accès. Celui-ci se définit sur
              la fiche collaborateur.
            </p>
          </div>
          <div className="form-actions">
            {selected.manager ? (
              <button
                type="button"
                className="btn btn--danger btn--sm"
                disabled={saving}
                onClick={() => setModal('confirm-retrait')}
              >
                Retirer
              </button>
            ) : null}
            <div style={{ flex: 1 }} />
            <button type="button" className="btn btn--ghost" onClick={() => setModal(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saving || !managerId}
              onClick={() => void handleAssignManager()}
            >
              Assigner
            </button>
          </div>
        </Modal>
      )}

      {modal === 'confirm-retrait' && selected && (
        <Modal title="Retirer le responsable ?" onClose={() => setModal('manager')}>
          <p>
            Retirer le responsable de « {selected.libelle} » ? Les demandes de cette unité pourront
            être traitées directement par la RRH s’il n’y a plus de responsable actif.
          </p>
          <div className="form-actions">
            <button type="button" className="btn btn--ghost" onClick={() => setModal('manager')}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--danger"
              disabled={saving}
              onClick={() => void handleRetirerManager()}
            >
              Retirer
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

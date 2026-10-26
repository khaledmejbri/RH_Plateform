import { useEffect, useState } from 'react';
import { useDialogA11y } from '../components/useDialogA11y';
import { Avatar, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, type IconName, type Tone } from '../components/ui';
import {
  deleteOrganigrammeManager,
  getCollaborateursPage,
  getOrganigramme,
  getUnites,
  nomCollaborateur,
  postCollaborateur,
  postOrganigrammeManager,
  postUnite,
  putUnite,
  type CollaborateurRow,
  type OrganigrammeNoeud,
  type Unite,
} from '../api/rhClient';

/**
 * § 5.3 / D-H02 : le responsable d'un service est le manager du nœud (valideur M01 1er niveau),
 * désigné via l'organigramme — pas déduit du profil d'accès, et le profil n'est jamais modifié ici (SCRUM-9).
 */
async function designerResponsable(noeudId: string, nouveauId: string, ancienId: string | undefined) {
  if (nouveauId && nouveauId !== ancienId) {
    await postOrganigrammeManager(noeudId, { collaborateur_identifiant: nouveauId });
  } else if (!nouveauId && ancienId) {
    await deleteOrganigrammeManager(noeudId);
  }
}

function indexerManagers(noeuds: OrganigrammeNoeud[], out = new Map<string, string>()): Map<string, string> {
  for (const n of noeuds) {
    if (n.manager) out.set(n.identifiant, n.manager.identifiant);
    indexerManagers(n.enfants, out);
  }
  return out;
}

/** Toutes les unités sous `racineId`, à n'importe quelle profondeur (§ 5.1 : arbre libre). */
function descendants(unites: Unite[], racineId: string): Unite[] {
  const enfants = unites.filter((u) => u.parent_identifiant === racineId);
  return enfants.flatMap((u) => [u, ...descendants(unites, u.identifiant)]);
}

// ─── Helpers UI ─────────────────────────────────────────────────────────────

function Badge({ label, color }: { label: string; color: 'blue' | 'purple' | 'green' | 'gray' }) {
  const tone = { blue: 'info', purple: 'progress', green: 'success', gray: 'neutral' }[color];
  return <span className={`status status--${tone}`}>{label}</span>;
}

function CollabOption({ c }: { c: CollaborateurRow }) {
  return (
    <option value={c.identifiant}>
      {c.matricule} — {c.prenom} {nomCollaborateur(c)}
    </option>
  );
}

// ─── Modal générique ─────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useDialogA11y(onClose);

  return (
    <div className="modal-backdrop" role="presentation" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal modal--md" ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal__head">
          <h3 className="modal__title" style={{ margin: 0 }}>{title}</h3>
          <button type="button" className="modal__close" aria-label="Fermer" onClick={onClose}>
            <Icon name="x" size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ─── Modal créer Département ─────────────────────────────────────────────────

function ModalCreerDept({
  collaborateurs, onClose, onDone,
}: {
  collaborateurs: CollaborateurRow[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [step, setStep] = useState<'dept' | 'chef'>('dept');
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Département
  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');

  // Chef département
  const [chefMode, setChefMode] = useState<'existant' | 'nouveau'>('existant');
  const [chefId, setChefId] = useState('');
  const [cMatricule, setCMatricule] = useState('');
  const [cPrenom, setCPrenom] = useState('');
  const [cNom, setCNom] = useState('');
  const [cEmail, setCEmail] = useState('');
  const [cMdp, setCMdp] = useState('');

  const [createdDeptId, setCreatedDeptId] = useState<string | null>(null);

  async function handleStepDept() {
    setErr(null);
    setSaving(true);
    try {
      const dept = await postUnite({ code: code.trim(), libelle: libelle.trim(), actif: true });
      setCreatedDeptId(dept.identifiant);
      setStep('chef');
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  async function handleStepChef() {
    if (!createdDeptId) return;
    setErr(null);
    setSaving(true);
    try {
      if (chefMode === 'existant') {
        if (!chefId) throw new Error('Sélectionnez un collaborateur');
        await designerResponsable(createdDeptId, chefId, undefined);
      } else {
        const cree = await postCollaborateur({
          matricule: cMatricule.trim(),
          prenom: cPrenom.trim(),
          nom: cNom.trim(),
          name: cNom.trim(),
          courriel_professionnel: cEmail.trim(),
          statut: 'ACTIF',
          unite_identifiant: createdDeptId,
          mot_de_passe_initial: cMdp,
        });
        await designerResponsable(createdDeptId, cree.identifiant, undefined);
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="Créer un département" onClose={onClose}>
      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      {step === 'dept' ? (
        <>
          <div className="field-row">
            <div className="field-row__code">
              <label htmlFor="structureorganisation-f1" className="field-label">Code</label>
              <input id="structureorganisation-f1" className="field-input" value={code} onChange={(e) => setCode(e.target.value)} required maxLength={32} placeholder="DRH" />
            </div>
            <div className="field-row__grow">
              <label htmlFor="structureorganisation-f2" className="field-label">Libellé du département</label>
              <input id="structureorganisation-f2" className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required placeholder="Direction des Ressources Humaines" />
            </div>
          </div>
          <p className="field-help">
            Étape 1/2 — Vous désignerez le responsable du département à l'étape suivante.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving || !code.trim() || !libelle.trim()} onClick={handleStepDept}>
              {saving ? 'Enregistrement…' : 'Suivant'}
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="segmented" role="group" aria-label="Origine du collaborateur">
            {(['existant', 'nouveau'] as const).map((m) => (
              <button key={m} type="button"
                className="segmented__item"
                aria-pressed={chefMode === m}
                onClick={() => setChefMode(m)}>
                {m === 'existant' ? 'Collaborateur existant' : 'Nouveau collaborateur'}
              </button>
            ))}
          </div>

          {chefMode === 'existant' ? (
            <>
              <label htmlFor="structureorganisation-f3" className="field-label">Sélectionner le responsable du département</label>
              <select id="structureorganisation-f3" className="field-input" value={chefId} onChange={(e) => setChefId(e.target.value)} required>
                <option value="">— Choisir —</option>
                {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
              </select>
            </>
          ) : (
            <div className="form-grid form-grid--2">
              <div>
                <label htmlFor="structureorganisation-f4" className="field-label">Matricule</label>
                <input id="structureorganisation-f4" className="field-input" value={cMatricule} onChange={(e) => setCMatricule(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f5" className="field-label">Prénom</label>
                <input id="structureorganisation-f5" className="field-input" value={cPrenom} onChange={(e) => setCPrenom(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f6" className="field-label">Nom</label>
                <input id="structureorganisation-f6" className="field-input" value={cNom} onChange={(e) => setCNom(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f7" className="field-label">Email</label>
                <input id="structureorganisation-f7" className="field-input" type="email" value={cEmail} onChange={(e) => setCEmail(e.target.value)} required />
              </div>
              <div className="form-grid__full">
                <label htmlFor="structureorganisation-f8" className="field-label">Mot de passe initial</label>
                <input id="structureorganisation-f8" className="field-input" type="password" minLength={8} value={cMdp} onChange={(e) => setCMdp(e.target.value)} required />
              </div>
            </div>
          )}

          <p className="field-help">
            Étape 2/2 — Le collaborateur devient responsable du service (valideur des demandes de l&apos;unité).
            Son profil d&apos;accès ne change pas : il se définit sur la fiche collaborateur.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving} onClick={handleStepChef}>
              {saving ? 'Enregistrement…' : 'Créer le département'}
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}

// ─── Modal modifier Département ──────────────────────────────────────────────

function ModalEditDept({
  dept, chef, collaborateurs, unites, onClose, onDone,
}: {
  dept: Unite;
  chef: CollaborateurRow | undefined;
  collaborateurs: CollaborateurRow[];
  unites: Unite[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [libelle, setLibelle] = useState(dept.libelle);
  const [chefId, setChefId] = useState(chef?.identifiant ?? '');
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    setErr(null);
    setSaving(true);
    try {
      await putUnite(dept.identifiant, { libelle: libelle.trim() });
      await designerResponsable(dept.identifiant, chefId, chef?.identifiant);
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  // Unités rattachées à ce département (toute profondeur)
  const unitesRattachees = unites;

  return (
    <Modal title={`Modifier — ${dept.libelle}`} onClose={onClose}>
      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <label htmlFor="structureorganisation-f9" className="field-label">Libellé du département</label>
      <input id="structureorganisation-f9" className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required />

      <label htmlFor="structureorganisation-f10" className="field-label">Responsable du département</label>
      <select id="structureorganisation-f10" className="field-input" value={chefId} onChange={(e) => setChefId(e.target.value)}>
        <option value="">— Aucun —</option>
        {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
      </select>
      <p className="field-help">
        Responsable = manager du nœud (valideur M01). Le profil d&apos;accès se définit sur la fiche collaborateur.
      </p>

      {unitesRattachees.length > 0 && (
        <div className="callout callout--info">
          <p className="callout__title">Unités rattachées ({unitesRattachees.length})</p>
          <ul className="plain-list">
            {unitesRattachees.map((u) => (
              <li key={u.identifiant}><code className="cell-code">{u.code}</code> {u.libelle}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
        <button type="button" className="btn btn--primary" disabled={saving} onClick={save}>
          {saving ? 'Enregistrement…' : 'Enregistrer'}
        </button>
      </div>
    </Modal>
  );
}

// ─── Modal créer Unité ────────────────────────────────────────────────────────

function ModalCreerUnite({
  departements, collaborateurs, onClose, onDone,
}: {
  departements: Unite[];
  collaborateurs: CollaborateurRow[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [step, setStep] = useState<'unite' | 'ro'>('unite');
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [deptId, setDeptId] = useState(departements[0]?.identifiant ?? '');
  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');

  const [roMode, setRoMode] = useState<'existant' | 'nouveau'>('existant');
  const [roId, setRoId] = useState('');
  const [cMatricule, setCMatricule] = useState('');
  const [cPrenom, setCPrenom] = useState('');
  const [cNom, setCNom] = useState('');
  const [cEmail, setCEmail] = useState('');
  const [cMdp, setCMdp] = useState('');

  const [createdUniteId, setCreatedUniteId] = useState<string | null>(null);

  async function handleStepUnite() {
    setErr(null);
    setSaving(true);
    try {
      const unite = await postUnite({
        code: code.trim(),
        libelle: libelle.trim(),
        actif: true,
        parent_identifiant: deptId,
      } as any);
      setCreatedUniteId(unite.identifiant);
      setStep('ro');
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  async function handleStepRO() {
    if (!createdUniteId) return;
    setErr(null);
    setSaving(true);
    try {
      if (roMode === 'existant') {
        if (!roId) throw new Error('Sélectionnez un collaborateur');
        await designerResponsable(createdUniteId, roId, undefined);
      } else {
        const cree = await postCollaborateur({
          matricule: cMatricule.trim(),
          prenom: cPrenom.trim(),
          nom: cNom.trim(),
          name: cNom.trim(),
          courriel_professionnel: cEmail.trim(),
          statut: 'ACTIF',
          unite_identifiant: createdUniteId,
          mot_de_passe_initial: cMdp,
        });
        await designerResponsable(createdUniteId, cree.identifiant, undefined);
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="Créer une unité" onClose={onClose}>
      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      {step === 'unite' ? (
        <>
          <label htmlFor="structureorganisation-f11" className="field-label">Service parent</label>
          <select id="structureorganisation-f11" className="field-input" value={deptId} onChange={(e) => setDeptId(e.target.value)} required>
            {departements.map((d) => (
              <option key={d.identifiant} value={d.identifiant}>{d.code} — {d.libelle}</option>
            ))}
          </select>
          <div className="field-row">
            <div className="field-row__code">
              <label htmlFor="structureorganisation-f12" className="field-label">Code</label>
              <input id="structureorganisation-f12" className="field-input" value={code} onChange={(e) => setCode(e.target.value)} required maxLength={32} placeholder="UAP1" />
            </div>
            <div className="field-row__grow">
              <label htmlFor="structureorganisation-f13" className="field-label">Libellé de l'unité</label>
              <input id="structureorganisation-f13" className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required placeholder="Unité de Production 1" />
            </div>
          </div>
          <p className="field-help">
            Étape 1/2 — Vous désignerez le responsable de l'unité à l'étape suivante.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving || !code.trim() || !libelle.trim() || !deptId} onClick={handleStepUnite}>
              {saving ? 'Enregistrement…' : 'Suivant'}
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="segmented" role="group" aria-label="Origine du collaborateur">
            {(['existant', 'nouveau'] as const).map((m) => (
              <button key={m} type="button"
                className="segmented__item"
                aria-pressed={roMode === m}
                onClick={() => setRoMode(m)}>
                {m === 'existant' ? 'Collaborateur existant' : 'Nouveau collaborateur'}
              </button>
            ))}
          </div>

          {roMode === 'existant' ? (
            <>
              <label htmlFor="structureorganisation-f14" className="field-label">Sélectionner le responsable de l'unité</label>
              <select id="structureorganisation-f14" className="field-input" value={roId} onChange={(e) => setRoId(e.target.value)} required>
                <option value="">— Choisir —</option>
                {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
              </select>
            </>
          ) : (
            <div className="form-grid form-grid--2">
              <div>
                <label htmlFor="structureorganisation-f15" className="field-label">Matricule</label>
                <input id="structureorganisation-f15" className="field-input" value={cMatricule} onChange={(e) => setCMatricule(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f16" className="field-label">Prénom</label>
                <input id="structureorganisation-f16" className="field-input" value={cPrenom} onChange={(e) => setCPrenom(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f17" className="field-label">Nom</label>
                <input id="structureorganisation-f17" className="field-input" value={cNom} onChange={(e) => setCNom(e.target.value)} required />
              </div>
              <div>
                <label htmlFor="structureorganisation-f18" className="field-label">Email</label>
                <input id="structureorganisation-f18" className="field-input" type="email" value={cEmail} onChange={(e) => setCEmail(e.target.value)} required />
              </div>
              <div className="form-grid__full">
                <label htmlFor="structureorganisation-f19" className="field-label">Mot de passe initial</label>
                <input id="structureorganisation-f19" className="field-input" type="password" minLength={8} value={cMdp} onChange={(e) => setCMdp(e.target.value)} required />
              </div>
            </div>
          )}

          <p className="field-help">
            Étape 2/2 — Le collaborateur devient responsable du service (valideur des demandes de l&apos;unité).
            Son profil d&apos;accès ne change pas : il se définit sur la fiche collaborateur.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving} onClick={handleStepRO}>
              {saving ? 'Enregistrement…' : "Créer l'unité"}
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}

// ─── Modal modifier Unité ────────────────────────────────────────────────────

function ModalEditUnite({
  unite, ro, collaborateurs, departements, onClose, onDone,
}: {
  unite: Unite;
  ro: CollaborateurRow | undefined;
  collaborateurs: CollaborateurRow[];
  departements: Unite[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [libelle, setLibelle] = useState(unite.libelle);
  const [deptId, setDeptId] = useState(unite.parent_identifiant ?? '');
  const [roId, setRoId] = useState(ro?.identifiant ?? '');
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    setErr(null);
    setSaving(true);
    try {
      await putUnite(unite.identifiant, {
        libelle: libelle.trim(),
        parent_identifiant: deptId || null,
      });
      await designerResponsable(unite.identifiant, roId, ro?.identifiant);
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={`Modifier — ${unite.libelle}`} onClose={onClose}>
      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <label htmlFor="structureorganisation-f20" className="field-label">Libellé de l'unité</label>
      <input id="structureorganisation-f20" className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required />

      <label htmlFor="structureorganisation-f21" className="field-label">Service parent</label>
      <select id="structureorganisation-f21" className="field-input" value={deptId} onChange={(e) => setDeptId(e.target.value)}>
        <option value="">— Aucun —</option>
        {departements.map((d) => (
          <option key={d.identifiant} value={d.identifiant}>{d.code} — {d.libelle}</option>
        ))}
      </select>

      <label htmlFor="structureorganisation-f22" className="field-label">Responsable de l'unité</label>
      <select id="structureorganisation-f22" className="field-input" value={roId} onChange={(e) => setRoId(e.target.value)}>
        <option value="">— Aucun —</option>
        {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
      </select>
      <p className="field-help">
        Responsable = manager du nœud (valideur M01). Le profil d&apos;accès se définit sur la fiche collaborateur.
      </p>

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
        <button type="button" className="btn btn--primary" disabled={saving} onClick={save}>
          {saving ? 'Enregistrement…' : 'Enregistrer'}
        </button>
      </div>
    </Modal>
  );
}

// ─── Carte Unité ─────────────────────────────────────────────────────────────

function UniteCard({
  unite, chemin, ro, travailleurs, collaborateurs, departements, onRefresh,
}: {
  unite: Unite;
  /** Service intermédiaire entre le département et l'unité (niveau 3 et plus). */
  chemin: string | null;
  ro: CollaborateurRow | undefined;
  travailleurs: CollaborateurRow[];
  collaborateurs: CollaborateurRow[];
  departements: Unite[];
  onRefresh: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const listId = `unite-${unite.identifiant}-membres`;

  return (
    <>
      {editOpen && (
        <ModalEditUnite
          unite={unite} ro={ro} collaborateurs={collaborateurs} departements={departements}
          onClose={() => setEditOpen(false)}
          onDone={() => { setEditOpen(false); onRefresh(); }}
        />
      )}
      <div className="struct-unit">
        <div className="struct-unit__head">
          <div className="struct-unit__main">
            <div className="struct-title">
              {chemin ? <span className="cell-meta">{chemin} ›</span> : null}
              <span className="struct-title__name">{unite.libelle}</span>
              <Badge label={unite.code} color="gray" />
              {!unite.actif && <Badge label="Inactive" color="gray" />}
            </div>
            {ro ? (
              <div className="struct-person">
                <Avatar name={`${ro.prenom} ${nomCollaborateur(ro)}`} size={28} />
                <div>
                  <span className="struct-person__name">{ro.prenom} {nomCollaborateur(ro)}</span>{' '}
                  <Badge label="Responsable" color="purple" />
                  <div className="struct-person__meta">{ro.courriel_professionnel ?? ro.matricule}</div>
                </div>
              </div>
            ) : (
              <div className="struct-missing struct-missing--warn">
                <Icon name="alert" size={14} /> Sans responsable — les demandes iront directement à la RRH
              </div>
            )}
          </div>

          <div className="struct-actions">
            <span className="cell-meta">{travailleurs.length} travailleur{travailleurs.length !== 1 ? 's' : ''}</span>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditOpen(true)}>
              <Icon name="edit" size={14} />
              Modifier
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm struct-toggle"
              aria-expanded={expanded}
              aria-controls={listId}
              aria-label={`${expanded ? 'Masquer' : 'Afficher'} les travailleurs de ${unite.libelle}`}
              onClick={() => setExpanded((v) => !v)}
            >
              <Icon name="chevronRight" size={16} />
            </button>
          </div>
        </div>

        {expanded && (
          <div className="struct-unit__body" id={listId}>
            <p className="section-title">Travailleurs ({travailleurs.length})</p>
            {travailleurs.length === 0 ? (
              <p className="cell-meta">Aucun travailleur dans cette unité.</p>
            ) : (
              <ul className="struct-members">
                {travailleurs.map((t) => (
                  <li key={t.identifiant} className="struct-member">
                    <Avatar name={`${t.prenom} ${nomCollaborateur(t)}`} size={28} />
                    <div className="struct-member__text">
                      <div className="struct-person__name struct-ellipsis">{t.prenom} {nomCollaborateur(t)}</div>
                      <div className="struct-person__meta">{t.poste_libelle ?? t.fonction ?? t.matricule}</div>
                    </div>
                    <StatusBadge statut={t.statut} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </>
  );
}

// ─── Carte Département ────────────────────────────────────────────────────────

function DeptCard({
  dept, chef, unites, toutesUnites, managers, collaborateurs, allCollaborateurs, allDepts, onRefresh,
}: {
  dept: Unite;
  chef: CollaborateurRow | undefined;
  unites: Unite[];
  toutesUnites: Unite[];
  managers: Map<string, string>;
  collaborateurs: CollaborateurRow[];
  allCollaborateurs: CollaborateurRow[];
  allDepts: Unite[];
  onRefresh: () => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [creerUniteOpen, setCreerUniteOpen] = useState(false);
  const bodyId = `dept-${dept.identifiant}-unites`;

  return (
    <>
      {editOpen && (
        <ModalEditDept
          dept={dept} chef={chef} collaborateurs={allCollaborateurs} unites={unites}
          onClose={() => setEditOpen(false)}
          onDone={() => { setEditOpen(false); onRefresh(); }}
        />
      )}
      {creerUniteOpen && (
        <ModalCreerUnite
          departements={allDepts} collaborateurs={allCollaborateurs}
          onClose={() => setCreerUniteOpen(false)}
          onDone={() => { setCreerUniteOpen(false); onRefresh(); }}
        />
      )}

      <section className="struct-dept" aria-label={`Département ${dept.libelle}`}>
        <div className={`struct-dept__head${expanded ? ' is-open' : ''}`}>
          <span className="struct-dept__icon" aria-hidden>
            <Icon name="building" size={20} />
          </span>

          <div className="struct-unit__main">
            <div className="struct-title">
              <h3 className="struct-title__name struct-title__name--lg">{dept.libelle}</h3>
              <Badge label={dept.code} color="blue" />
            </div>
            {chef ? (
              <div className="struct-person">
                <Avatar name={`${chef.prenom} ${nomCollaborateur(chef)}`} size={28} />
                <div>
                  <span className="struct-person__name">{chef.prenom} {nomCollaborateur(chef)}</span>{' '}
                  <Badge label="Responsable" color="blue" />
                  <div className="struct-person__meta">{chef.courriel_professionnel ?? chef.matricule}</div>
                </div>
              </div>
            ) : (
              <div className="struct-missing struct-missing--warn">
                <Icon name="alert" size={14} /> Sans responsable désigné
              </div>
            )}
          </div>

          <div className="struct-actions">
            <span className="cell-meta">{unites.length} unité{unites.length !== 1 ? 's' : ''}</span>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => setCreerUniteOpen(true)}>
              <Icon name="plus" size={14} />
              Unité
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditOpen(true)}>
              <Icon name="edit" size={14} />
              Modifier
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm struct-toggle"
              aria-expanded={expanded}
              aria-controls={bodyId}
              aria-label={`${expanded ? 'Replier' : 'Déplier'} ${dept.libelle}`}
              onClick={() => setExpanded((v) => !v)}
            >
              <Icon name="chevronRight" size={16} />
            </button>
          </div>
        </div>

        {expanded && (
          <div className="struct-dept__body" id={bodyId}>
            {unites.length === 0 ? (
              <EmptyState
                icon="building"
                title="Aucune unité dans ce département"
                action={
                  <button type="button" className="btn btn--secondary btn--sm" onClick={() => setCreerUniteOpen(true)}>
                    <Icon name="plus" size={14} />
                    Créer la première unité
                  </button>
                }
              />
            ) : (
              unites.map((u) => {
                const roId = managers.get(u.identifiant);
                const ro = roId ? collaborateurs.find((c) => c.identifiant === roId) : undefined;
                const travailleurs = collaborateurs.filter(
                  (c) => c.unite?.identifiant === u.identifiant && c.statut === 'ACTIF' && c.identifiant !== roId,
                );
                const parent = u.parent_identifiant !== dept.identifiant
                  ? toutesUnites.find((p) => p.identifiant === u.parent_identifiant)
                  : undefined;
                const sousUnites = new Set(descendants(toutesUnites, u.identifiant).map((d) => d.identifiant));
                const parentsPossibles = toutesUnites.filter(
                  (p) => p.identifiant !== u.identifiant && !sousUnites.has(p.identifiant),
                );
                return (
                  <UniteCard
                    key={u.identifiant}
                    unite={u} chemin={parent?.libelle ?? null} ro={ro} travailleurs={travailleurs}
                    collaborateurs={allCollaborateurs} departements={parentsPossibles}
                    onRefresh={onRefresh}
                  />
                );
              })
            )}
          </div>
        )}
      </section>
    </>
  );
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function StructureOrganisationPage() {
  const [unites, setUnites] = useState<Unite[]>([]);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [managers, setManagers] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [creerDeptOpen, setCreerDeptOpen] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      const [us, firstPage, organigramme] = await Promise.all([
        getUnites(),
        getCollaborateursPage(0, 200),
        getOrganigramme(true),
      ]);
      setUnites(us);
      setManagers(indexerManagers(organigramme.racines));
      // Charger toutes les pages si nécessaire
      let allCollabs = firstPage.contenu;
      for (let p = 1; p < firstPage.total_pages; p++) {
        const page = await getCollaborateursPage(p, 200);
        allCollabs = [...allCollabs, ...page.contenu];
      }
      setCollaborateurs(allCollabs);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur chargement');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  // Départements = unités sans parent
  const departements = unites.filter((u) => !u.parent_identifiant);

  // Unités = unités avec parent (toute profondeur)
  const unitesEnfants = unites.filter((u) => !!u.parent_identifiant);

  // Stats — responsables = managers de nœud (même source que l'organigramme)
  const totalTravailleurs = collaborateurs.filter((c) => c.statut === 'ACTIF').length;
  const totalResponsables = unites.filter((u) => managers.has(u.identifiant)).length;
  const totalSansResponsable = unites.filter((u) => u.actif && !managers.has(u.identifiant)).length;

  return (
    <div className="page">
      {creerDeptOpen && (
        <ModalCreerDept
          collaborateurs={collaborateurs}
          onClose={() => setCreerDeptOpen(false)}
          onDone={() => { setCreerDeptOpen(false); void load(); }}
        />
      )}

      <PageHeader
        title="Structure organisationnelle"
        lead={
          <>
            Départements et unités (toute profondeur) avec leur responsable, valideur des demandes de l’unité.
            Mêmes données que l’organigramme ; le profil d’accès se définit sur la fiche collaborateur.
          </>
        }
        actions={
          <button type="button" className="btn btn--primary" onClick={() => setCreerDeptOpen(true)}>
            <Icon name="plus" />
            Créer un département
          </button>
        }
      />

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <section className="dash-stats" aria-label="Chiffres clés">
        <StatCard icon="building" value={departements.length} label="Départements" tone="info" />
        <StatCard icon="clipboard" value={unitesEnfants.length} label="Unités" tone="progress" />
        <StatCard icon="briefcase" value={totalResponsables} label="Services avec responsable" tone="success" />
        <StatCard icon="target" value={totalSansResponsable} label="Services sans responsable" tone="warning" />
        <StatCard icon="users" value={totalTravailleurs} label="Collaborateurs actifs" tone="neutral" />
      </section>

      {loading ? (
        <div className="panel"><LoadingState label="Chargement de la structure…" /></div>
      ) : departements.length === 0 ? (
        <div className="panel">
          <EmptyState
            icon="building"
            title="Aucun département"
            action={
              <button type="button" className="btn btn--primary" onClick={() => setCreerDeptOpen(true)}>
                <Icon name="plus" />
                Créer le premier département
              </button>
            }
          >
            Commencez par créer votre premier département avec son chef.
          </EmptyState>
        </div>
      ) : (
        departements.map((dept) => {
          const unitesduDept = descendants(unites, dept.identifiant);
          const chefId = managers.get(dept.identifiant);
          const chef = chefId ? collaborateurs.find((c) => c.identifiant === chefId) : undefined;
          return (
            <DeptCard
              key={dept.identifiant}
              dept={dept} chef={chef} unites={unitesduDept}
              toutesUnites={unites} managers={managers}
              collaborateurs={collaborateurs} allCollaborateurs={collaborateurs}
              allDepts={departements}
              onRefresh={load}
            />
          );
        })
      )}
    </div>
  );
}

// ─── Petits composants réutilisables ─────────────────────────────────────────

function StatCard({ icon, value, label, tone }: { icon: IconName; value: number; label: string; tone: Tone }) {
  return (
    <div className="dash-stat card">
      <span className={`dash-stat__icon chip--${tone}`} aria-hidden>
        <Icon name={icon} size={20} />
      </span>
      <div className="dash-stat__value">{value}</div>
      <div className="dash-stat__label">{label}</div>
    </div>
  );
}

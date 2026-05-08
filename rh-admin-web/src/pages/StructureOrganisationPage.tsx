import { useEffect, useRef, useState } from 'react';
import {
  getCollaborateursPage,
  getUnites,
  postCollaborateur,
  postUnite,
  putCollaborateur,
  putUnite,
  type CollaborateurRow,
  type Unite,
} from '../api/rhClient';

// ─── Types locaux ────────────────────────────────────────────────────────────

type Dept = Unite & { parent_identifiant: null };
type UniteRaw = Unite & { parent_identifiant: string };

// ─── Helpers UI ─────────────────────────────────────────────────────────────

function Avatar({ name }: { name: string }) {
  const initials = name.split(' ').map((w) => w[0] ?? '').slice(0, 2).join('').toUpperCase();
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      width: 32, height: 32, borderRadius: '50%', fontSize: 12, fontWeight: 700,
      background: 'linear-gradient(135deg, #5b8def, #7c5cff)', color: '#fff',
      flexShrink: 0,
    }}>
      {initials || '?'}
    </span>
  );
}

function Badge({ label, color }: { label: string; color: 'blue' | 'purple' | 'green' | 'gray' }) {
  const styles: Record<string, { bg: string; text: string }> = {
    blue:   { bg: 'rgba(91,141,239,0.13)',  text: '#1d4ed8' },
    purple: { bg: 'rgba(124,92,255,0.13)',  text: '#6d28d9' },
    green:  { bg: 'rgba(34,197,94,0.13)',   text: '#15803d' },
    gray:   { bg: 'rgba(100,116,139,0.1)',  text: '#475569' },
  };
  const s = styles[color];
  return (
    <span style={{
      display: 'inline-block', padding: '3px 10px', borderRadius: 999,
      fontSize: 11, fontWeight: 700, letterSpacing: '0.03em',
      background: s.bg, color: s.text,
    }}>
      {label}
    </span>
  );
}

function CollabOption({ c }: { c: CollaborateurRow }) {
  return (
    <option value={c.identifiant}>
      {c.matricule} — {c.prenom} {c.nom}
    </option>
  );
}

// ─── Modal générique ─────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === 'Escape') onClose(); }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" ref={ref} style={{ maxWidth: 560 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
          <h3 className="modal__title" style={{ margin: 0 }}>{title}</h3>
          <button type="button" onClick={onClose} style={{
            background: 'none', border: 'none', cursor: 'pointer', fontSize: 20,
            color: 'var(--muted)', lineHeight: 1, padding: '0 4px',
          }}>×</button>
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
        await putCollaborateur(chefId, {
          profil_acces: 'RESPONSABLE',
          unite_identifiant: createdDeptId,
        });
      } else {
        await postCollaborateur({
          matricule: cMatricule.trim(),
          prenom: cPrenom.trim(),
          nom: cNom.trim(),
          courriel_professionnel: cEmail.trim(),
          statut: 'ACTIF',
          profil_acces: 'RESPONSABLE',
          unite_identifiant: createdDeptId,
          mot_de_passe_initial: cMdp,
        });
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
      {err && <div className="alert alert--error" style={{ marginBottom: '1rem' }}>{err}</div>}

      {step === 'dept' ? (
        <>
          <div style={{ display: 'flex', gap: '1rem', marginBottom: '0.5rem' }}>
            <div style={{ flex: '0 0 120px' }}>
              <label className="field-label">Code</label>
              <input className="field-input" value={code} onChange={(e) => setCode(e.target.value)} required maxLength={32} placeholder="DRH" />
            </div>
            <div style={{ flex: 1 }}>
              <label className="field-label">Libellé du département</label>
              <input className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required placeholder="Direction des Ressources Humaines" />
            </div>
          </div>
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0.5rem 0 1.5rem' }}>
            Étape 1/2 — Vous définirez le chef de département à l'étape suivante.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving || !code.trim() || !libelle.trim()} onClick={handleStepDept}>
              {saving ? '...' : 'Suivant →'}
            </button>
          </div>
        </>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: '1.25rem' }}>
            {(['existant', 'nouveau'] as const).map((m) => (
              <button key={m} type="button"
                className={'tab' + (chefMode === m ? ' tab--on' : '')}
                onClick={() => setChefMode(m)}
                style={{ borderRadius: 999, padding: '7px 16px', border: '1px solid var(--border)' }}>
                {m === 'existant' ? 'Collaborateur existant' : 'Nouveau collaborateur'}
              </button>
            ))}
          </div>

          {chefMode === 'existant' ? (
            <>
              <label className="field-label">Sélectionner le chef de département</label>
              <select className="field-input" value={chefId} onChange={(e) => setChefId(e.target.value)} required>
                <option value="">— Choisir —</option>
                {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
              </select>
            </>
          ) : (
            <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '0 1rem' }}>
              <div>
                <label className="field-label">Matricule</label>
                <input className="field-input" value={cMatricule} onChange={(e) => setCMatricule(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Prénom</label>
                <input className="field-input" value={cPrenom} onChange={(e) => setCPrenom(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Nom</label>
                <input className="field-input" value={cNom} onChange={(e) => setCNom(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Email</label>
                <input className="field-input" type="email" value={cEmail} onChange={(e) => setCEmail(e.target.value)} required />
              </div>
              <div style={{ gridColumn: '1/-1' }}>
                <label className="field-label">Mot de passe initial</label>
                <input className="field-input" type="password" minLength={8} value={cMdp} onChange={(e) => setCMdp(e.target.value)} required />
              </div>
            </div>
          )}

          <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0.75rem 0 1.5rem' }}>
            Étape 2/2 — Le chef de département sera affecté au profil <strong>Responsable</strong>.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving} onClick={handleStepChef}>
              {saving ? '...' : 'Créer le département'}
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
      // Affecter nouveau chef si changé
      if (chefId && chefId !== chef?.identifiant) {
        // Retirer ancien chef
        if (chef) {
          await putCollaborateur(chef.identifiant, { profil_acces: 'COLLABORATEUR' });
        }
        await putCollaborateur(chefId, {
          profil_acces: 'RESPONSABLE',
          unite_identifiant: dept.identifiant,
        });
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  // Unités rattachées à ce département
  const unitesRattachees = unites.filter((u) => u.parent_identifiant === dept.identifiant);

  return (
    <Modal title={`Modifier — ${dept.libelle}`} onClose={onClose}>
      {err && <div className="alert alert--error" style={{ marginBottom: '1rem' }}>{err}</div>}

      <label className="field-label">Libellé du département</label>
      <input className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required />

      <label className="field-label">Chef de département</label>
      <select className="field-input" value={chefId} onChange={(e) => setChefId(e.target.value)}>
        <option value="">— Aucun —</option>
        {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
      </select>

      {unitesRattachees.length > 0 && (
        <div style={{
          background: 'rgba(91,141,239,0.06)', border: '1px solid rgba(91,141,239,0.18)',
          borderRadius: 10, padding: '12px 14px', marginBottom: 14,
        }}>
          <p style={{ margin: '0 0 8px', fontSize: 13, fontWeight: 600 }}>
            Unités rattachées ({unitesRattachees.length})
          </p>
          {unitesRattachees.map((u) => (
            <div key={u.identifiant} style={{ fontSize: 13, color: 'var(--muted)', padding: '3px 0' }}>
              • {u.code} — {u.libelle}
            </div>
          ))}
        </div>
      )}

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
        <button type="button" className="btn btn--primary" disabled={saving} onClick={save}>
          {saving ? '...' : 'Enregistrer'}
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
        await putCollaborateur(roId, {
          profil_acces: 'RO',
          unite_identifiant: createdUniteId,
        });
      } else {
        await postCollaborateur({
          matricule: cMatricule.trim(),
          prenom: cPrenom.trim(),
          nom: cNom.trim(),
          courriel_professionnel: cEmail.trim(),
          statut: 'ACTIF',
          profil_acces: 'RO',
          unite_identifiant: createdUniteId,
          mot_de_passe_initial: cMdp,
        });
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
      {err && <div className="alert alert--error" style={{ marginBottom: '1rem' }}>{err}</div>}

      {step === 'unite' ? (
        <>
          <label className="field-label">Département parent</label>
          <select className="field-input" value={deptId} onChange={(e) => setDeptId(e.target.value)} required>
            {departements.map((d) => (
              <option key={d.identifiant} value={d.identifiant}>{d.code} — {d.libelle}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: '1rem' }}>
            <div style={{ flex: '0 0 120px' }}>
              <label className="field-label">Code</label>
              <input className="field-input" value={code} onChange={(e) => setCode(e.target.value)} required maxLength={32} placeholder="UAP1" />
            </div>
            <div style={{ flex: 1 }}>
              <label className="field-label">Libellé de l'unité</label>
              <input className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required placeholder="Unité de Production 1" />
            </div>
          </div>
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0.5rem 0 1.5rem' }}>
            Étape 1/2 — Vous définirez le Responsable Opérationnel à l'étape suivante.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving || !code.trim() || !libelle.trim() || !deptId} onClick={handleStepUnite}>
              {saving ? '...' : 'Suivant →'}
            </button>
          </div>
        </>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: '1.25rem' }}>
            {(['existant', 'nouveau'] as const).map((m) => (
              <button key={m} type="button"
                className={'tab' + (roMode === m ? ' tab--on' : '')}
                onClick={() => setRoMode(m)}
                style={{ borderRadius: 999, padding: '7px 16px', border: '1px solid var(--border)' }}>
                {m === 'existant' ? 'Collaborateur existant' : 'Nouveau collaborateur'}
              </button>
            ))}
          </div>

          {roMode === 'existant' ? (
            <>
              <label className="field-label">Sélectionner le Responsable Opérationnel</label>
              <select className="field-input" value={roId} onChange={(e) => setRoId(e.target.value)} required>
                <option value="">— Choisir —</option>
                {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
              </select>
            </>
          ) : (
            <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '0 1rem' }}>
              <div>
                <label className="field-label">Matricule</label>
                <input className="field-input" value={cMatricule} onChange={(e) => setCMatricule(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Prénom</label>
                <input className="field-input" value={cPrenom} onChange={(e) => setCPrenom(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Nom</label>
                <input className="field-input" value={cNom} onChange={(e) => setCNom(e.target.value)} required />
              </div>
              <div>
                <label className="field-label">Email</label>
                <input className="field-input" type="email" value={cEmail} onChange={(e) => setCEmail(e.target.value)} required />
              </div>
              <div style={{ gridColumn: '1/-1' }}>
                <label className="field-label">Mot de passe initial</label>
                <input className="field-input" type="password" minLength={8} value={cMdp} onChange={(e) => setCMdp(e.target.value)} required />
              </div>
            </div>
          )}

          <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0.75rem 0 1.5rem' }}>
            Étape 2/2 — Le RO sera affecté au profil <strong>Responsable Opérationnel</strong>.
          </p>
          <div className="modal__actions">
            <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
            <button type="button" className="btn btn--primary" disabled={saving} onClick={handleStepRO}>
              {saving ? '...' : "Créer l'unité"}
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
      if (roId && roId !== ro?.identifiant) {
        if (ro) await putCollaborateur(ro.identifiant, { profil_acces: 'COLLABORATEUR' });
        await putCollaborateur(roId, { profil_acces: 'RO', unite_identifiant: unite.identifiant });
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={`Modifier — ${unite.libelle}`} onClose={onClose}>
      {err && <div className="alert alert--error" style={{ marginBottom: '1rem' }}>{err}</div>}

      <label className="field-label">Libellé de l'unité</label>
      <input className="field-input" value={libelle} onChange={(e) => setLibelle(e.target.value)} required />

      <label className="field-label">Département parent</label>
      <select className="field-input" value={deptId} onChange={(e) => setDeptId(e.target.value)}>
        <option value="">— Aucun —</option>
        {departements.map((d) => (
          <option key={d.identifiant} value={d.identifiant}>{d.code} — {d.libelle}</option>
        ))}
      </select>

      <label className="field-label">Responsable Opérationnel (RO)</label>
      <select className="field-input" value={roId} onChange={(e) => setRoId(e.target.value)}>
        <option value="">— Aucun —</option>
        {collaborateurs.map((c) => <CollabOption key={c.identifiant} c={c} />)}
      </select>

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>Annuler</button>
        <button type="button" className="btn btn--primary" disabled={saving} onClick={save}>
          {saving ? '...' : 'Enregistrer'}
        </button>
      </div>
    </Modal>
  );
}

// ─── Carte Unité ─────────────────────────────────────────────────────────────

function UniteCard({
  unite, ro, travailleurs, collaborateurs, departements, onRefresh,
}: {
  unite: Unite;
  ro: CollaborateurRow | undefined;
  travailleurs: CollaborateurRow[];
  collaborateurs: CollaborateurRow[];
  departements: Unite[];
  onRefresh: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

  return (
    <>
      {editOpen && (
        <ModalEditUnite
          unite={unite} ro={ro} collaborateurs={collaborateurs} departements={departements}
          onClose={() => setEditOpen(false)}
          onDone={() => { setEditOpen(false); onRefresh(); }}
        />
      )}
      <div style={{
        background: 'var(--surface-2)', border: '1px solid var(--border)',
        borderRadius: 12, marginBottom: 10, overflow: 'hidden',
        transition: 'box-shadow 0.2s',
      }}>
        {/* Header unité */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px' }}>
          {/* Indent line */}
          <div style={{ width: 3, height: 36, background: 'linear-gradient(180deg,#7c5cff,#5b8def)', borderRadius: 2, flexShrink: 0 }} />

          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 700, fontSize: 14 }}>{unite.libelle}</span>
              <Badge label={unite.code} color="gray" />
              {!unite.actif && <Badge label="Inactif" color="gray" />}
            </div>
            {ro ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
                <Avatar name={`${ro.prenom} ${ro.nom}`} />
                <div>
                  <span style={{ fontSize: 12, fontWeight: 600 }}>{ro.prenom} {ro.nom}</span>
                  <Badge label="RO" color="purple" />
                  <div style={{ fontSize: 11, color: 'var(--muted)' }}>{ro.courriel_professionnel ?? ro.matricule}</div>
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, fontStyle: 'italic' }}>Aucun Responsable Opérationnel</div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>{travailleurs.length} travailleur{travailleurs.length !== 1 ? 's' : ''}</span>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditOpen(true)}>Modifier</button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setExpanded((v) => !v)}>
              {expanded ? '▲' : '▼'}
            </button>
          </div>
        </div>

        {/* Travailleurs */}
        {expanded && (
          <div style={{ borderTop: '1px solid var(--border)', padding: '10px 16px 14px 36px' }}>
            <p style={{ fontSize: 11, fontWeight: 700, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 10px' }}>
              Travailleurs ({travailleurs.length})
            </p>
            {travailleurs.length === 0 ? (
              <p style={{ fontSize: 13, color: 'var(--muted)', fontStyle: 'italic' }}>Aucun travailleur dans cette unité.</p>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 8 }}>
                {travailleurs.map((t) => (
                  <div key={t.identifiant} style={{
                    display: 'flex', alignItems: 'center', gap: 10,
                    background: 'var(--surface)', border: '1px solid var(--border)',
                    borderRadius: 10, padding: '8px 12px',
                  }}>
                    <Avatar name={`${t.prenom} ${t.nom}`} />
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {t.prenom} {t.nom}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--muted)' }}>{t.poste_libelle ?? t.fonction ?? t.matricule}</div>
                    </div>
                    <Badge label={t.statut === 'ACTIF' ? 'Actif' : t.statut} color={t.statut === 'ACTIF' ? 'green' : 'gray'} />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}

// ─── Carte Département ────────────────────────────────────────────────────────

function DeptCard({
  dept, chef, unites, collaborateurs, allCollaborateurs, allDepts, onRefresh,
}: {
  dept: Unite;
  chef: CollaborateurRow | undefined;
  unites: Unite[];
  collaborateurs: CollaborateurRow[];
  allCollaborateurs: CollaborateurRow[];
  allDepts: Unite[];
  onRefresh: () => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [creerUniteOpen, setCreerUniteOpen] = useState(false);

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

      <div style={{
        background: 'var(--surface)', border: '1px solid var(--border)',
        borderRadius: 16, marginBottom: 20,
        boxShadow: '0 6px 24px rgba(16,33,61,0.08)',
      }}>
        {/* Header département */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 14, padding: '18px 20px',
          borderBottom: expanded ? '1px solid var(--border)' : 'none',
        }}>
          <div style={{
            width: 44, height: 44, borderRadius: 12, flexShrink: 0,
            background: 'linear-gradient(135deg, #5b8def, #7c5cff)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 4px 16px rgba(91,141,239,0.35)',
          }}>
            <IconBuilding />
          </div>

          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 700, fontSize: 16 }}>{dept.libelle}</span>
              <Badge label={dept.code} color="blue" />
              <Badge label="Département" color="blue" />
            </div>
            {chef ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                <Avatar name={`${chef.prenom} ${chef.nom}`} />
                <div>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>{chef.prenom} {chef.nom}</span>
                  {' '}
                  <Badge label="Chef de département" color="blue" />
                  <div style={{ fontSize: 12, color: 'var(--muted)' }}>{chef.courriel_professionnel ?? chef.matricule}</div>
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 6, fontStyle: 'italic' }}>
                ⚠ Aucun chef de département défini
              </div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>
              {unites.length} unité{unites.length !== 1 ? 's' : ''}
            </span>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => setCreerUniteOpen(true)}>
              + Unité
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditOpen(true)}>
              Modifier
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setExpanded((v) => !v)}>
              {expanded ? '▲' : '▼'}
            </button>
          </div>
        </div>

        {/* Unités */}
        {expanded && (
          <div style={{ padding: '16px 20px' }}>
            {unites.length === 0 ? (
              <div style={{
                textAlign: 'center', padding: '24px 0', color: 'var(--muted)',
                border: '2px dashed var(--border)', borderRadius: 12, fontSize: 14,
              }}>
                Aucune unité dans ce département.{' '}
                <button type="button" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--accent)', fontWeight: 600, fontSize: 14 }}
                  onClick={() => setCreerUniteOpen(true)}>
                  Créer la première unité →
                </button>
              </div>
            ) : (
              unites.map((u) => {
                const ro = collaborateurs.find(
                  (c) => c.unite?.identifiant === u.identifiant && c.statut === 'ACTIF' &&
                    c.profil_acces === 'RO',
                );
                const travailleurs = collaborateurs.filter(
                  (c) => c.unite?.identifiant === u.identifiant && c.statut === 'ACTIF' &&
                    c.profil_acces !== 'RO' && c.profil_acces !== 'RESPONSABLE',
                );
                return (
                  <UniteCard
                    key={u.identifiant}
                    unite={u} ro={ro} travailleurs={travailleurs}
                    collaborateurs={allCollaborateurs} departements={allDepts}
                    onRefresh={onRefresh}
                  />
                );
              })
            )}
          </div>
        )}
      </div>
    </>
  );
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function StructureOrganisationPage() {
  const [unites, setUnites] = useState<Unite[]>([]);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [creerDeptOpen, setCreerDeptOpen] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      const [us, firstPage] = await Promise.all([
        getUnites(),
        getCollaborateursPage(0, 200),
      ]);
      setUnites(us);
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

  // Unités = unités avec parent
  const unitesEnfants = unites.filter((u) => !!u.parent_identifiant);

  // Stats
  const totalTravailleurs = collaborateurs.filter((c) => c.statut === 'ACTIF').length;
  const totalChefs = collaborateurs.filter((c) => c.profil_acces === 'RESPONSABLE').length;
  const totalRO = collaborateurs.filter((c) => c.profil_acces === 'RO').length;

  return (
    <div className="page">
      {creerDeptOpen && (
        <ModalCreerDept
          collaborateurs={collaborateurs}
          onClose={() => setCreerDeptOpen(false)}
          onDone={() => { setCreerDeptOpen(false); void load(); }}
        />
      )}

      {/* En-tête */}
      <div className="page__head" style={{ marginBottom: '1.5rem' }}>
        <div>
          <h2 className="page__title">Structure organisationnelle</h2>
          <p className="page__lead">
            Hiérarchie : <strong>Département</strong> → Chef de département → <strong>Unité</strong> → Responsable Opérationnel → Travailleurs
          </p>
        </div>
        <div className="page__head-actions">
          <button type="button" className="btn btn--primary" onClick={() => setCreerDeptOpen(true)}>
            + Créer un département
          </button>
        </div>
      </div>

      {err && <div className="alert alert--error">{err}</div>}

      {/* Stats */}
      <div className="grid-stats" style={{ marginBottom: '1.75rem' }}>
        <StatCard icon="🏢" value={departements.length} label="Départements" gradient="var(--accent-plaintes)" />
        <StatCard icon="📦" value={unitesEnfants.length} label="Unités" gradient="linear-gradient(135deg,#7c5cff,#a855f7)" />
        <StatCard icon="👔" value={totalChefs} label="Chefs de département" gradient="var(--accent-demandes)" />
        <StatCard icon="🎯" value={totalRO} label="Responsables Opérationnels" gradient="var(--accent-docs)" />
        <StatCard icon="👷" value={totalTravailleurs} label="Collaborateurs actifs" gradient="var(--accent-users)" />
      </div>

      {/* Arbre hiérarchique */}
      {loading ? (
        <div className="panel" style={{ textAlign: 'center', padding: '3rem' }}>
          <div style={{ fontSize: 14, color: 'var(--muted)' }}>Chargement de la structure…</div>
        </div>
      ) : departements.length === 0 ? (
        <div style={{
          textAlign: 'center', padding: '4rem 2rem', border: '2px dashed var(--border)',
          borderRadius: 16, color: 'var(--muted)',
        }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>🏢</div>
          <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 8 }}>Aucun département</div>
          <div style={{ marginBottom: 20 }}>Commencez par créer votre premier département avec son chef.</div>
          <button type="button" className="btn btn--primary" onClick={() => setCreerDeptOpen(true)}>
            + Créer le premier département
          </button>
        </div>
      ) : (
        departements.map((dept) => {
          const unitesduDept = unitesEnfants.filter(
            (u) => u.parent_identifiant === dept.identifiant,
          );
          const deptAndChildIds = new Set([dept.identifiant, ...unitesduDept.map(u => u.identifiant)]);
          const chef = collaborateurs.find(
            (c) => deptAndChildIds.has(c.unite?.identifiant ?? '') &&
              c.profil_acces === 'RESPONSABLE' && c.statut === 'ACTIF',
          );
          return (
            <DeptCard
              key={dept.identifiant}
              dept={dept} chef={chef} unites={unitesduDept}
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

function StatCard({ icon, value, label, gradient }: { icon: string; value: number; label: string; gradient: string }) {
  return (
    <div className="stat-card anim-lift">
      <div className="stat-card__accent" style={{ background: gradient }} />
      <div style={{ fontSize: 28, marginBottom: 6 }}>{icon}</div>
      <div className="stat-card__value">{value}</div>
      <div className="stat-card__title">{label}</div>
    </div>
  );
}

function IconBuilding() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.75">
      <path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v0M9 13v0M9 17v0" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

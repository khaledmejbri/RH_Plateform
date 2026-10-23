import { FormEvent, useEffect, useState } from 'react';
import {
  getCollaborateursPage,
  getFamillesMetier,
  getUnites,
  isProfilAcces,
  libelleProfilAcces,
  nomCollaborateur,
  postCollaborateur,
  putCollaborateur,
  PROFILS_ACCES,
  LIBELLES_PROFIL_ACCES,
  type CollaborateurRow,
  type FamilleMetierRow,
  type Unite,
} from '../api/rhClient';
import {
  FAMILLES_METIER_SEED,
  NIVEAUX_SENIORITE,
  libelleFamilleMetier,
  libelleNiveauSeniorite,
  normalizeNiveauSeniorite,
} from '../api/evaluationCatalog';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { ErrorAlert, Icon, LoadingState, StatusBadge } from '../components/ui';

const PROFIL_DEFAUT = 'COLLABORATEUR' as const;

function profilDepuisLigne(row: CollaborateurRow | null): string {
  if (row?.profil_acces) return row.profil_acces.toUpperCase();
  return PROFIL_DEFAUT;
}

export default function CollaborateursPage() {
  const confirm = useConfirm();
  const lectureSeule = useLectureSeule();
  const [tab, setTab] = useState<'liste' | 'form'>('liste');
  const [editing, setEditing] = useState<CollaborateurRow | null>(null);
  const [unites, setUnites] = useState<Unite[]>([]);
  const [rows, setRows] = useState<CollaborateurRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const [matricule, setMatricule] = useState('');
  const [prenom, setPrenom] = useState('');
  const [name, setNom] = useState('');
  const [courriel, setCourriel] = useState('');
  const [posteLibelle, setPosteLibelle] = useState('');
  const [fonction, setFonction] = useState('');
  const [departementLibelle, setDepartementLibelle] = useState('');
  const [dateRecrutement, setDateRecrutement] = useState('');
  const [statut, setStatut] = useState('ACTIF');
  const [uniteId, setUniteId] = useState('');
  const [profilAcces, setProfilAcces] = useState<string>(PROFIL_DEFAUT);
  const [familleMetierCode, setFamilleMetierCode] = useState('');
  const [niveauSeniorite, setNiveauSeniorite] = useState('');
  const [familles, setFamilles] = useState<FamilleMetierRow[]>(FAMILLES_METIER_SEED);
  const [catalogueStub, setCatalogueStub] = useState(false);
  const [motDePasseInitial, setMotDePasseInitial] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
    void getFamillesMetier(true)
      .then((rows) => {
        if (Array.isArray(rows) && rows.length > 0) {
          setFamilles(rows);
          setCatalogueStub(false);
        }
      })
      .catch(() => {
        setFamilles(FAMILLES_METIER_SEED);
        setCatalogueStub(true);
      });
  }, []);

  async function loadList() {
    setLoading(true);
    setErr(null);
    try {
      const p = await getCollaborateursPage(page, 15);
      setRows(p.contenu);
      setTotal(p.total_elements);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadList();
  }, [page]);

  useEffect(() => {
    if (unites.length && !uniteId) setUniteId(unites[0].identifiant);
  }, [unites, uniteId]);

  function resetForm() {
    setMatricule('');
    setPrenom('');
    setNom('');
    setCourriel('');
    setPosteLibelle('');
    setFonction('');
    setDepartementLibelle('');
    setDateRecrutement('');
    setStatut('ACTIF');
    setProfilAcces(PROFIL_DEFAUT);
    setFamilleMetierCode('');
    setNiveauSeniorite('');
    setMotDePasseInitial('');
    setEditing(null);
    if (unites[0]) setUniteId(unites[0].identifiant);
  }

  function openCreer() {
    resetForm();
    setTab('form');
  }

  function openEditer(row: CollaborateurRow) {
    setEditing(row);
    setMatricule(row.matricule);
    setPrenom(row.prenom);
    setNom(nomCollaborateur(row));
    setCourriel(row.courriel_professionnel ?? '');
    setPosteLibelle(row.poste_libelle ?? '');
    setFonction(row.fonction ?? '');
    setDepartementLibelle(row.departement_libelle ?? '');
    setDateRecrutement(row.date_recrutement ? row.date_recrutement.slice(0, 10) : '');
    setStatut(row.statut);
    setUniteId(row.unite?.identifiant ?? unites[0]?.identifiant ?? '');
    setProfilAcces(profilDepuisLigne(row));
    setFamilleMetierCode(row.famille_metier_code ?? '');
    setNiveauSeniorite(normalizeNiveauSeniorite(row.niveau_seniorite) ?? '');
    setMotDePasseInitial('');
    setTab('form');
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMsg(null);
    setErr(null);
    setSaving(true);
    const nom = name.trim();
    try {
      if (editing) {
        await putCollaborateur(editing.identifiant, {
          prenom: prenom.trim(),
          nom,
          name: nom,
          courriel_professionnel: courriel.trim(),
          poste_libelle: posteLibelle.trim() || undefined,
          fonction: fonction.trim() || undefined,
          departement_libelle: departementLibelle.trim() || undefined,
          date_recrutement: dateRecrutement || undefined,
          statut: statut.trim(),
          unite_identifiant: uniteId,
          profil_acces: profilAcces,
          famille_metier_code: familleMetierCode || null,
          niveau_seniorite: niveauSeniorite || null,
        });
        setMsg('Collaborateur mis à jour.');
      } else {
        await postCollaborateur({
          matricule: matricule.trim(),
          prenom: prenom.trim(),
          nom,
          name: nom,
          courriel_professionnel: courriel.trim(),
          poste_libelle: posteLibelle.trim() || undefined,
          fonction: fonction.trim() || undefined,
          departement_libelle: departementLibelle.trim() || undefined,
          date_recrutement: dateRecrutement || undefined,
          statut: statut.trim(),
          unite_identifiant: uniteId,
          profil_acces: profilAcces,
          famille_metier_code: familleMetierCode || undefined,
          niveau_seniorite: niveauSeniorite || undefined,
          mot_de_passe_initial: motDePasseInitial,
        });
        setMsg('Collaborateur enregistre avec succes. La creation du compte et le courriel se font en arriere-plan.');
      }
      resetForm();
      setTab('liste');
      setPage(0);
      await loadList();
    } catch (x) {
      setErr(x instanceof Error ? x.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 15));

  async function archiveCollaborateur(row: CollaborateurRow) {
    if (row.statut === 'ARCHIVE') return;
    const ok = await confirm({
      title: 'Archiver le collaborateur',
      message: `Archiver ${row.prenom} ${nomCollaborateur(row)} ?`,
      confirmLabel: 'Archiver',
      danger: true,
    });
    if (!ok) return;
    setErr(null);
    setMsg(null);
    try {
      await putCollaborateur(row.identifiant, { statut: 'ARCHIVE' });
      setMsg(`Collaborateur ${row.matricule} archive.`);
      await loadList();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur archivage');
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Collaborateurs</h2>
          <p className="page__lead">Referentiel : le compte mobile est cree en arriere-plan ; le collaborateur recoit un courriel si SMTP est configure.</p>
        </div>
        <div className="tabs">
          <button
            type="button"
            className={'tab' + (tab === 'liste' ? ' tab--on' : '')}
            onClick={() => {
              setTab('liste');
              setEditing(null);
            }}
          >
            Liste
          </button>
          {!lectureSeule ? (
            <button type="button" className={'tab' + (tab === 'form' && !editing ? ' tab--on' : '')} onClick={openCreer}>
              Creer
            </button>
          ) : null}
        </div>
      </div>

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      {tab === 'liste' ? (
        <div className="panel">
          {loading ? (
            <LoadingState label="Chargement des collaborateurs…" />
          ) : (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Matricule</th>
                      <th>Nom</th>
                      <th>Email</th>
                      <th>Profil d&apos;accès</th>
                      <th>Famille / niveau</th>
                      <th>Statut</th>
                      <th>Compte</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.identifiant}>
                        <td className="mono">{r.matricule}</td>
                        <td>
                          {r.prenom} {nomCollaborateur(r)}
                        </td>
                        <td className="muted">{r.courriel_professionnel ?? '-'}</td>
                        <td>
                          {libelleProfilAcces(r.profil_acces)}
                          {r.profil_acces ? (
                            <span className="badge badge--default" style={{ marginLeft: 8 }}>
                              {r.profil_acces}
                            </span>
                          ) : null}
                        </td>
                        <td>
                          {r.famille_metier_code || r.niveau_seniorite ? (
                            <span className="small">
                              {r.famille_metier_libelle
                                ?? libelleFamilleMetier(r.famille_metier_code, familles)}
                              {r.niveau_seniorite
                                ? ` · ${libelleNiveauSeniorite(r.niveau_seniorite)}`
                                : ''}
                            </span>
                          ) : (
                            <span className="muted small">—</span>
                          )}
                        </td>
                        <td>{r.statut}</td>
                        <td>{r.compte_utilisateur_id ? <StatusBadge statut="VALIDE" label="Lié" /> : '-'}</td>
                        <td>
                          {lectureSeule ? (
                            <span className="muted small">Lecture seule</span>
                          ) : (
                            <div className="page__head-actions">
                              <button type="button" className="btn btn--secondary btn--sm" onClick={() => openEditer(r)}>
                                Modifier
                              </button>
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                onClick={() => archiveCollaborateur(r)}
                                disabled={r.statut === 'ARCHIVE'}
                              >
                                Archiver
                              </button>
                              <button
                                type="button"
                                className="btn btn--secondary btn--sm"
                                title="Endpoint changement mot de passe a connecter cote backend."
                                disabled
                              >
                                Changer mot de passe
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="pager">
                <button type="button" className="btn btn--ghost" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                  Prec.
                </button>
                <span className="muted small">
                  {page + 1} / {totalPages} ({total})
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Suiv.
                </button>
              </div>
            </>
          )}
        </div>
      ) : (
        <form className="panel panel--form" onSubmit={onSubmit}>
          <p className="page__lead" style={{ marginTop: 0 }}>
            {editing ? `Modifier ${editing.matricule}` : 'Nouveau collaborateur'}
          </p>
          <div className="form-grid">
            <div>
              <label htmlFor="collaborateurs-f1" className="field-label">Matricule</label>
              <input id="collaborateurs-f1"
                className="field-input"
                value={matricule}
                onChange={(e) => setMatricule(e.target.value)}
                required={!editing}
                disabled={!!editing}
              />
            </div>
            <div>
              <label htmlFor="collaborateurs-f2" className="field-label">Prenom</label>
              <input id="collaborateurs-f2" className="field-input" value={prenom} onChange={(e) => setPrenom(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="collaborateurs-f3" className="field-label">Nom</label>
              <input id="collaborateurs-f3" className="field-input" value={name} onChange={(e) => setNom(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="collaborateurs-f4" className="field-label">Courriel</label>
              <input id="collaborateurs-f4" className="field-input" type="email" value={courriel} onChange={(e) => setCourriel(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="collaborateurs-f5" className="field-label">Statut</label>
              <select id="collaborateurs-f5" className="field-input" value={statut} onChange={(e) => setStatut(e.target.value)} required>
                <option value="ACTIF">ACTIF</option>
                <option value="SUSPENDU">SUSPENDU</option>
                <option value="ARCHIVE">ARCHIVE</option>
              </select>
            </div>
            <div>
              <label htmlFor="collaborateurs-f6" className="field-label">Profil d&apos;accès</label>
              <select id="collaborateurs-f6"
                className="field-input"
                value={profilAcces}
                onChange={(e) => setProfilAcces(e.target.value)}
                required
              >
                {!isProfilAcces(profilAcces) && profilAcces ? (
                  <option value={profilAcces}>{profilAcces}</option>
                ) : null}
                {PROFILS_ACCES.map((code) => (
                  <option key={code} value={code}>
                    {LIBELLES_PROFIL_ACCES[code]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="field-label" htmlFor="collab-famille">Famille métier</label>
              <select
                id="collab-famille"
                className="field-input"
                value={familleMetierCode}
                onChange={(e) => setFamilleMetierCode(e.target.value)}
              >
                <option value="">— Non renseigné —</option>
                {familles.map((f) => (
                  <option key={f.code} value={f.code}>
                    {f.libelle}
                  </option>
                ))}
              </select>
              {catalogueStub ? (
                <p className="field-help" style={{ marginTop: 4 }}>
                  Catalogue familles temporairement indisponible — liste seed affichée.
                </p>
              ) : null}
            </div>
            <div>
              <label className="field-label" htmlFor="collab-niveau">Niveau de séniorité</label>
              <select
                id="collab-niveau"
                className="field-input"
                value={niveauSeniorite}
                onChange={(e) => setNiveauSeniorite(e.target.value)}
              >
                <option value="">— Non renseigné —</option>
                {NIVEAUX_SENIORITE.map((n) => (
                  <option key={n.code} value={n.code}>
                    {n.libelle}
                  </option>
                ))}
              </select>
              <p className="field-help" style={{ marginTop: 4 }}>
                Clé matching évaluations (indépendant du profil d&apos;accès).
              </p>
            </div>
            <div>
              <label htmlFor="collaborateurs-f7" className="field-label">Unite</label>
              <select id="collaborateurs-f7" className="field-input" value={uniteId} onChange={(e) => setUniteId(e.target.value)} required>
                {unites.map((u) => (
                  <option key={u.identifiant} value={u.identifiant}>
                    {u.code} - {u.libelle}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="collaborateurs-f8" className="field-label">Poste</label>
              <input id="collaborateurs-f8" className="field-input" value={posteLibelle} onChange={(e) => setPosteLibelle(e.target.value)} />
            </div>
            <div>
              <label htmlFor="collaborateurs-f9" className="field-label">Fonction</label>
              <input id="collaborateurs-f9" className="field-input" value={fonction} onChange={(e) => setFonction(e.target.value)} />
            </div>

            <div>
              <label htmlFor="collaborateurs-f10" className="field-label">Date recrutement</label>
              <input id="collaborateurs-f10"
                className="field-input"
                type="date"
                value={dateRecrutement}
                onChange={(e) => setDateRecrutement(e.target.value)}
              />
            </div>

            {!editing ? (
              <div>
                <label htmlFor="collaborateurs-f11" className="field-label">Mot de passe initial</label>
                <input id="collaborateurs-f11"
                  className="field-input"
                  type="password"
                  minLength={8}
                  value={motDePasseInitial}
                  onChange={(e) => setMotDePasseInitial(e.target.value)}
                  required
                />
              </div>
            ) : null}
          </div>
          <div className="page__head-actions">
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() => {
                resetForm();
                setTab('liste');
              }}
            >
              Annuler
            </button>
            <button type="submit" className="btn btn--primary" disabled={saving}>
              {saving ? '...' : editing ? 'Enregistrer' : 'Creer'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

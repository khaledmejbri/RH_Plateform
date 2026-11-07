import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  getTousCollaborateurs,
  getFamillesMetier,
  getUnites,
  isProfilAcces,
  libelleProfilAcces,
  nomCollaborateur,
  postCollaborateur,
  putCollaborateur,
  PROFILS_ACCES,
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
import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { appartientSelection, cheminUnite, correspond, indexerUnites, useFiltresUrl } from '../utils/filtres';
import { formatNombre, formatPersonne } from '../utils/format';
import { libelle, optionsDomaine } from '../utils/libelles';
import { SaisieDate } from '../components/ChampsFr';
import MenuLigne from '../components/MenuLigne';

const PROFIL_DEFAUT = 'COLLABORATEUR' as const;
const TAILLE_PAGE = 15;
const FILTRES_DEFAUT = { q: '', direction: '', departement: '', service: '', profil: '', statut: '', metier: '' };

/** D-E13.c : fiche active sans famille ou sans niveau. */
function profilMetierIncomplet(r: CollaborateurRow): boolean {
  return r.statut.toUpperCase() === 'ACTIF'
    && (!(r.famille_metier_code || r.famille_metier_libelle) || !r.niveau_seniorite);
}

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
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
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
      // Filtres « intelligents » appliqués côté client : on charge toute la liste (pages de 200).
      setRows(await getTousCollaborateurs());
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadList();
  }, []);

  const indexUnites = useMemo(() => indexerUnites(unites), [unites]);
  const filtrees = useMemo(
    () =>
      rows.filter(
        (r) =>
          correspond(
            [r.prenom, nomCollaborateur(r), r.matricule, r.courriel_professionnel, r.poste_libelle, r.fonction,
              r.unite?.libelle, r.unite?.code, r.departement_libelle],
            f.q,
          ) &&
          appartientSelection(indexUnites, r.unite?.identifiant, f) &&
          (!f.profil || (r.profil_acces ?? PROFIL_DEFAUT).toUpperCase() === f.profil) &&
          (!f.statut || r.statut.toUpperCase() === f.statut) &&
          (f.metier !== 'A_COMPLETER' || profilMetierIncomplet(r)),
      ),
    [rows, f, indexUnites],
  );
  const nbMetierACompleter = useMemo(() => rows.filter(profilMetierIncomplet).length, [rows]);
  const totalPages = Math.max(1, Math.ceil(filtrees.length / TAILLE_PAGE));
  const pageCourante = Math.min(page, totalPages - 1);
  const pageRows = filtrees.slice(pageCourante * TAILLE_PAGE, (pageCourante + 1) * TAILLE_PAGE);

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
        setMsg('Collaborateur enregistré. Le compte de connexion est créé en arrière-plan.');
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

  async function archiveCollaborateur(row: CollaborateurRow) {
    if (row.statut === 'ARCHIVE') return;
    const ok = await confirm({
      title: 'Archiver le collaborateur',
      message: `Archiver ${formatPersonne(`${row.prenom} ${nomCollaborateur(row)}`, row.matricule)} ? Sa fiche ne sera plus active.`,
      confirmLabel: 'Archiver',
      danger: true,
    });
    if (!ok) return;
    setErr(null);
    setMsg(null);
    try {
      await putCollaborateur(row.identifiant, { statut: 'ARCHIVE' });
      setMsg(`${formatPersonne(`${row.prenom} ${nomCollaborateur(row)}`, row.matricule)} a été archivé.`);
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
          <p className="page__lead">Fiches et comptes : le compte de connexion est créé en arrière-plan à la création de la fiche.</p>
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
              Créer
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
              <BarreFiltres
                id="collab"
                recherche={f.q}
                onRecherche={(v) => { filtres.definir({ q: v }); setPage(0); }}
                placeholder="Nom, prénom, matricule, e-mail…"
                nbResultats={filtrees.length}
                nbTotal={rows.length}
                nbActifs={filtres.nbActifs}
                onReinitialiser={() => { filtres.reinitialiser(); setPage(0); }}
              >
                <FiltresUnite
                  idPrefixe="collab"
                  unites={unites}
                  selection={f}
                  onChange={(s) => { filtres.definir(s); setPage(0); }}
                />
                <ChampSelect
                  id="collab-profil"
                  libelle="Profil d'accès"
                  valeur={f.profil}
                  options={PROFILS_ACCES.map((p) => ({ valeur: p, libelle: libelle('profilAcces', p) }))}
                  onChange={(v) => { filtres.definir({ profil: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-statut"
                  libelle="Statut"
                  valeur={f.statut}
                  options={optionsDomaine('statutCollaborateur')}
                  onChange={(v) => { filtres.definir({ statut: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-metier"
                  libelle="Profil métier"
                  valeur={f.metier}
                  options={[{ valeur: 'A_COMPLETER', libelle: `Profil métier à compléter (${nbMetierACompleter})` }]}
                  onChange={(v) => { filtres.definir({ metier: v }); setPage(0); }}
                />
              </BarreFiltres>
              {filtrees.length === 0 ? (
                <AucunResultat onReinitialiser={filtres.nbActifs > 0 ? () => filtres.reinitialiser() : undefined} />
              ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Matricule</th>
                      <th>Nom</th>
                      <th>Courriel</th>
                      <th>Profil d&apos;accès</th>
                      <th>Famille / niveau</th>
                      <th>Statut</th>
                      <th>Compte</th>
                      <th><span className="sr-only">Actions</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageRows.map((r) => (
                      <tr key={r.identifiant}>
                        <td className="mono">{r.matricule}</td>
                        <td>
                          {r.prenom} {nomCollaborateur(r)}
                        </td>
                        <td className="muted">{r.courriel_professionnel ?? '—'}</td>
                        <td>
                          {libelleProfilAcces(r.profil_acces ?? PROFIL_DEFAUT)}
                        </td>
                        <td>
                          {r.famille_metier_code || r.famille_metier_libelle || r.niveau_seniorite ? (
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
                        <td><StatusBadge statut={r.statut} label={libelle('statutCollaborateur', r.statut)} /></td>
                        <td>{r.compte_utilisateur_id ? <StatusBadge statut="VALIDE" label="Lié" /> : '—'}</td>
                        <td>
                          {lectureSeule ? (
                            <span className="muted small">Lecture seule</span>
                          ) : (
                            <div className="page__head-actions">
                              <button type="button" className="btn btn--secondary btn--sm" onClick={() => openEditer(r)}>
                                Modifier
                              </button>
                              <MenuLigne
                                libelle={`Autres actions pour ${r.prenom} ${nomCollaborateur(r)}`}
                                actions={[
                                  {
                                    libelle: 'Archiver',
                                    desactive: r.statut === 'ARCHIVE',
                                    raison: 'Cette fiche est déjà archivée.',
                                    onSelect: () => void archiveCollaborateur(r),
                                  },
                                  {
                                    libelle: 'Changer le mot de passe',
                                    desactive: true,
                                    raison: 'Non disponible pour le moment : cette fonction n’est pas encore activée sur le serveur.',
                                    onSelect: () => {},
                                  },
                                ]}
                              />
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              )}
              <div className="pager">
                <button type="button" className="btn btn--ghost" disabled={pageCourante <= 0} onClick={() => setPage(pageCourante - 1)}>
                  Précédent
                </button>
                <span className="muted small">
                  Page {pageCourante + 1} sur {totalPages} ({formatNombre(filtrees.length, 0)} collaborateurs)
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={pageCourante + 1 >= totalPages}
                  onClick={() => setPage(pageCourante + 1)}
                >
                  Suivant
                </button>
              </div>
            </>
          )}
        </div>
      ) : (
        <form className="panel panel--form" onSubmit={onSubmit}>
          <p className="page__lead" style={{ marginTop: 0 }}>
            {editing ? `Modifier ${formatPersonne(`${editing.prenom} ${nomCollaborateur(editing)}`, editing.matricule)}` : 'Nouveau collaborateur'}
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
              <label htmlFor="collaborateurs-f2" className="field-label">Prénom</label>
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
                {optionsDomaine('statutCollaborateur').map((o) => (
                  <option key={o.valeur} value={o.valeur}>{o.libelle}</option>
                ))}
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
                  <option value={profilAcces}>{libelle('profilAcces', profilAcces)}</option>
                ) : null}
                {PROFILS_ACCES.map((code) => (
                  <option key={code} value={code}>
                    {libelle('profilAcces', code)}
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
                  Liste des familles métier temporairement indisponible : liste par défaut affichée.
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
                Sert à choisir le questionnaire technique de l’évaluation (indépendant des droits d&apos;accès).
              </p>
            </div>
            <div>
              <label htmlFor="collaborateurs-f7" className="field-label">Unité</label>
              <select id="collaborateurs-f7" className="field-input" value={uniteId} onChange={(e) => setUniteId(e.target.value)} required>
                {unites.map((u) => (
                  <option key={u.identifiant} value={u.identifiant}>
                    {u.libelle}
                  </option>
                ))}
              </select>
              {uniteId ? (
                <p className="field-help" style={{ marginTop: 4 }}>{cheminUnite(indexUnites, uniteId)}</p>
              ) : null}
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
              <label htmlFor="collaborateurs-f10" className="field-label">Date de recrutement</label>
              <SaisieDate id="collaborateurs-f10" valeur={dateRecrutement} onChange={setDateRecrutement} />
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
              {saving ? 'Enregistrement…' : editing ? 'Enregistrer' : 'Créer'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

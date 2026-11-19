import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  getUnites,
  libelleProfilAcces,
  nomCollaborateur,
  postCollaborateur,
  putCollaborateur,
  type CollaborateurRow,
  type Unite,
} from '../api/rhClient';
import {
  libelleFamilleMetier,
  libelleNiveauSeniorite,
  normalizeNiveauSeniorite,
} from '../api/evaluationCatalog';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { ErrorAlert, Icon, LoadingState, StatusBadge } from '../components/ui';
import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { cheminUnite, indexerUnites, useFiltresUrl } from '../utils/filtres';
import { formatNombre, formatPersonne } from '../utils/format';
import { libelle } from '../utils/libelles';
import { optionsFiltre, optionsFormulaire, rafraichirParametres, useParametres } from '../utils/parametresStore';
import { SaisieDate } from '../components/ChampsFr';
import FiltresChampsPerso from '../components/FiltresChampsPerso';
import SaisieValeurPerso from '../components/SaisieValeurPerso';
import { getParametrageImport, type ChampParametre } from '../api/importParametrageApi';
import {
  creerCollaborateurAvecValeurs,
  criteresDepuisUrl,
  erreursParChamp,
  exporterCollaborateurs,
  getCollaborateursFiltres,
  nbCriteres,
  PREFIXE_CRITERE,
  type CriteresPerso,
  type FiltreFixe,
} from '../api/valeursPersoApi';
import { ApiMetierError } from '../api/metierApi';
import MenuLigne from '../components/MenuLigne';
import DetailLink from '../detail/DetailLink';

const PROFIL_DEFAUT = 'COLLABORATEUR' as const;
const TAILLE_PAGE = 15;
const FILTRES_DEFAUT = { q: '', direction: '', departement: '', service: '', profil: '', statut: '', metier: '', famille: '', niveau: '' };

function profilDepuisLigne(row: CollaborateurRow | null): string {
  if (row?.profil_acces) return row.profil_acces.toUpperCase();
  return PROFIL_DEFAUT;
}

export default function CollaborateursPage() {
  useParametres();
  const confirm = useConfirm();
  const lectureSeule = useLectureSeule();
  const [tab, setTab] = useState<'liste' | 'form'>('liste');
  const [editing, setEditing] = useState<CollaborateurRow | null>(null);
  const [unites, setUnites] = useState<Unite[]>([]);
  const [pageRows, setPageRows] = useState<CollaborateurRow[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [erreurListe, setErreurListe] = useState<string | null>(null);
  const [nbMetierACompleter, setNbMetierACompleter] = useState<number | null>(null);
  const [rev, setRev] = useState(0);
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const [urlParams, setUrlParams] = useSearchParams();
  // Critères des champs personnalisés : gardés dans l'URL sous la forme `cp.<CODE>.<critère>`.
  const urlParamsTexte = urlParams.toString();
  const criteres = useMemo(() => criteresDepuisUrl(new URLSearchParams(urlParamsTexte)), [urlParamsTexte]);
  const nbFiltresActifs = filtres.nbActifs + nbCriteres(criteres);
  const [page, setPage] = useState(0);
  const [export_, setExport] = useState(false);
  const [champsPerso, setChampsPerso] = useState<ChampParametre[]>([]);
  const [valeursPerso, setValeursPerso] = useState<Record<string, string>>({});
  const [erreursPerso, setErreursPerso] = useState<Record<string, string>>({});
  const [persoOuvert, setPersoOuvert] = useState(false);
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
  const [motDePasseInitial, setMotDePasseInitial] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
    // Familles, niveaux, profils et statuts : listes du paramétrage, relues à l'ouverture de la page (D-R02.j).
    void rafraichirParametres();
    // Champs personnalisés actifs : section « Informations complémentaires » du formulaire de création.
    void getParametrageImport('collaborateurs').then(
      (p) => setChampsPerso(p.champs.filter((c) => c.personnalise && c.actif !== false).sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0))),
      () => setChampsPerso([]),
    );
  }, []);

  const loadList = useCallback(() => setRev((n) => n + 1), []);

  /** Filtres fixes envoyés au serveur (le serveur calcule sous-arbres d'unités, recherche sans accents, profil métier à compléter). */
  const filtreFixe: FiltreFixe = useMemo(
    () => ({ q: f.q.trim(), direction: f.direction, departement: f.departement, service: f.service, profil: f.profil,
      statut: f.statut, famille: f.famille, niveau: f.niveau, metier: f.metier }),
    [f],
  );
  const signature = JSON.stringify([filtreFixe, criteres]);

  // Liste paginée et filtrée côté serveur (V5) ; la frappe est temporisée, une réponse périmée est ignorée.
  useEffect(() => {
    let annule = false;
    const delai = window.setTimeout(() => {
      setLoading(true);
      setErreurListe(null);
      getCollaborateursFiltres(filtreFixe, criteres, page, TAILLE_PAGE).then(
        (p) => {
          if (annule) return;
          // Page demandée au-delà de la dernière (fiche sortie du filtre) : retour à la dernière page, qui relance la requête.
          if (p.total_pages > 0 && page >= p.total_pages) {
            setPage(p.total_pages - 1);
            return;
          }
          setPageRows(p.contenu);
          setTotal(p.total_elements);
          setTotalPages(Math.max(1, p.total_pages));
          setLoading(false);
        },
        (e: unknown) => {
          if (annule) return;
          setPageRows([]);
          setErreurListe(e instanceof ApiMetierError ? e.message : 'La liste des collaborateurs est momentanément indisponible. Rien n’a été modifié.');
          setLoading(false);
        },
      );
    }, 250);
    return () => { annule = true; window.clearTimeout(delai); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, page, rev]);

  // Compteur « profil métier à compléter » : une requête d'une ligne (le serveur compte).
  useEffect(() => {
    let annule = false;
    getCollaborateursFiltres({ metier: 'A_COMPLETER' }, {}, 0, 1).then(
      (p) => { if (!annule) setNbMetierACompleter(p.total_elements); },
      () => { if (!annule) setNbMetierACompleter(null); },
    );
    return () => { annule = true; };
  }, [rev]);

  function changerCriteres(suivant: CriteresPerso) {
    setPage(0);
    setUrlParams((prev) => {
      const p = new URLSearchParams(prev);
      for (const cle of Array.from(p.keys())) if (cle.startsWith(PREFIXE_CRITERE)) p.delete(cle);
      for (const [code, c] of Object.entries(suivant)) {
        for (const [critere, valeur] of Object.entries(c)) if (valeur) p.set(`${PREFIXE_CRITERE}${code}.${critere}`, valeur);
      }
      return p;
    }, { replace: true });
  }

  function reinitialiserTout() {
    setPage(0);
    setUrlParams((prev) => {
      const p = new URLSearchParams(prev);
      for (const cle of Array.from(p.keys())) if (cle.startsWith(PREFIXE_CRITERE)) p.delete(cle);
      for (const k of Object.keys(FILTRES_DEFAUT)) p.delete(k);
      return p;
    }, { replace: true });
  }

  async function exporter() {
    setExport(true);
    setErr(null);
    try {
      await exporterCollaborateurs(filtreFixe, criteres);
    } catch (e) {
      setErr(e instanceof ApiMetierError ? e.message : 'L’export a échoué. Réessayez.');
    } finally {
      setExport(false);
    }
  }

  const indexUnites = useMemo(() => indexerUnites(unites), [unites]);
  const pageCourante = Math.min(page, totalPages - 1);

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
    setValeursPerso({});
    setErreursPerso({});
    setPersoOuvert(false);
    setEditing(null);
    if (unites[0]) setUniteId(unites[0].identifiant);
  }

  function openCreer() {
    void rafraichirParametres();
    resetForm();
    setTab('form');
  }

  function openEditer(row: CollaborateurRow) {
    void rafraichirParametres();
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
        const perso: Record<string, string> = {};
        for (const c of champsPerso) {
          const v = (valeursPerso[c.code] ?? '').trim();
          if (v) perso[c.code] = v;
        }
        const corps = {
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
        };
        // Avec des valeurs personnalisées : appel détaillé (erreurs par champ) ; sinon client habituel.
        if (Object.keys(perso).length > 0) await creerCollaborateurAvecValeurs({ ...corps, champs_personnalises: perso });
        else await postCollaborateur(corps);
        setMsg('Collaborateur enregistré. Le compte de connexion est créé en arrière-plan.');
      }
      resetForm();
      setTab('liste');
      setPage(0);
      await loadList();
    } catch (x) {
      const parChamp = erreursParChamp(x);
      const visees = Object.keys(parChamp).filter((k) => champsPerso.some((c) => c.code === k));
      if (visees.length > 0) {
        setErreursPerso(parChamp);
        setPersoOuvert(true);
        setErr(parChamp[''] ?? 'La fiche n’a pas été créée : corrigez les informations complémentaires signalées.');
      } else {
        setErr(x instanceof Error ? x.message : 'Erreur');
      }
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
          {!lectureSeule ? <Link to="/app/collaborateurs/import" className="tab">Importer</Link> : null}
        </div>
      </div>

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      {tab === 'liste' ? (
        <div className="panel">
          {(
            <>
              <BarreFiltres
                id="collab"
                recherche={f.q}
                onRecherche={(v) => { filtres.definir({ q: v }); setPage(0); }}
                placeholder="Nom, prénom, matricule, e-mail…"
                nbResultats={total}
                nbActifs={nbFiltresActifs}
                onReinitialiser={reinitialiserTout}
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
                  options={optionsFiltre('PROFIL_ACCES')}
                  onChange={(v) => { filtres.definir({ profil: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-statut"
                  libelle="Statut"
                  valeur={f.statut}
                  options={optionsFiltre('STATUT_COLLABORATEUR')}
                  onChange={(v) => { filtres.definir({ statut: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-famille"
                  libelle="Famille métier"
                  valeur={f.famille}
                  options={optionsFiltre('FAMILLE_METIER')}
                  onChange={(v) => { filtres.definir({ famille: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-niveau"
                  libelle="Niveau de séniorité"
                  valeur={f.niveau}
                  options={optionsFiltre('NIVEAU_SENIORITE')}
                  onChange={(v) => { filtres.definir({ niveau: v }); setPage(0); }}
                />
                <ChampSelect
                  id="collab-metier"
                  libelle="Profil métier"
                  valeur={f.metier}
                  options={[{ valeur: 'A_COMPLETER', libelle: nbMetierACompleter === null ? 'Profil métier à compléter' : `Profil métier à compléter (${nbMetierACompleter})` }]}
                  onChange={(v) => { filtres.definir({ metier: v }); setPage(0); }}
                />
                <FiltresChampsPerso idPrefixe="collab" criteres={criteres} onChange={changerCriteres} />
              </BarreFiltres>
              <div className="import__actions" style={{ justifyContent: 'flex-start' }}>
                <button type="button" className="btn btn--secondary btn--sm" disabled={export_ || total === 0} aria-busy={export_} onClick={() => void exporter()}>
                  <Icon name="download" size={14} />
                  {export_ ? 'Export…' : 'Exporter (CSV)'}
                </button>
                <span className="muted small">Exporte tous les collaborateurs correspondant aux filtres actuels.</span>
              </div>
              {loading ? <LoadingState label="Chargement des collaborateurs…" /> : erreurListe ? (
                <div className="alert alert--error" role="alert">
                  {erreurListe}{' '}
                  <button type="button" className="btn btn--secondary btn--sm" onClick={loadList}>Réessayer</button>
                </div>
              ) : pageRows.length === 0 ? (
                nbFiltresActifs > 0
                  ? <AucunResultat onReinitialiser={reinitialiserTout} />
                  : <p className="detail-vide">Aucun collaborateur.</p>
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
                          <DetailLink type="collaborateur" id={r.identifiant} libelle={`${r.prenom} ${nomCollaborateur(r)}`.trim()} />
                        </td>
                        <td className="muted">{r.courriel_professionnel ?? '—'}</td>
                        <td>
                          {libelleProfilAcces(r.profil_acces ?? PROFIL_DEFAUT)}
                        </td>
                        <td>
                          {r.famille_metier_code || r.famille_metier_libelle || r.niveau_seniorite ? (
                            <span className="small">
                              {r.famille_metier_libelle
                                ?? libelleFamilleMetier(r.famille_metier_code)}
                              {r.niveau_seniorite
                                ? ` · ${libelleNiveauSeniorite(r.niveau_seniorite)}`
                                : ''}
                            </span>
                          ) : (
                            <span className="muted small">—</span>
                          )}
                        </td>
                        <td><StatusBadge referentiel="STATUT_COLLABORATEUR" statut={r.statut} label={libelle('statutCollaborateur', r.statut)} /></td>
                        <td>{r.compte_utilisateur_id ? <StatusBadge referentiel="STATUT_COLLABORATEUR" statut="VALIDE" label="Lié" /> : '—'}</td>
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
                  Page {pageCourante + 1} sur {totalPages} ({formatNombre(total, 0)} collaborateurs)
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
                {optionsFormulaire('STATUT_COLLABORATEUR', statut).map((o) => (
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
                {optionsFormulaire('PROFIL_ACCES', profilAcces).map((o) => (
                  <option key={o.valeur} value={o.valeur}>{o.libelle}</option>
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
                {optionsFormulaire('FAMILLE_METIER', familleMetierCode).map((o) => (
                  <option key={o.valeur} value={o.valeur}>{o.libelle}</option>
                ))}
              </select>
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
                {optionsFormulaire('NIVEAU_SENIORITE', niveauSeniorite).map((o) => (
                  <option key={o.valeur} value={o.valeur}>{o.libelle}</option>
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
          {!editing && champsPerso.length > 0 ? (
            <section className="fiche-section" style={{ marginTop: 16 }}>
              <h3 className="fiche-section__titre" style={{ margin: 0 }}>
                <button type="button" className="btn btn--ghost btn--sm" aria-expanded={persoOuvert} aria-controls="collab-perso"
                  onClick={() => setPersoOuvert((o) => !o)}>
                  Informations complémentaires (facultatives) {persoOuvert ? '▲' : '▼'}
                </button>
              </h3>
              <div id="collab-perso" hidden={!persoOuvert}>
                <div className="form-grid">
                  {champsPerso.map((c) => (
                    <div key={c.code}>
                      <label className="field-label" htmlFor={`collab-perso-${c.code}`}>{c.libelle}</label>
                      <SaisieValeurPerso id={`collab-perso-${c.code}`} disabled={saving}
                        champ={{ code: c.code, libelle: c.libelle, type_valeur: c.type_valeur ?? 'TEXTE', regles: c.regles ?? null,
                          valeurs_liste: (c.valeurs_liste ?? []).filter((v) => v.actif) }}
                        valeur={valeursPerso[c.code] ?? ''} erreur={erreursPerso[c.code]}
                        onChange={(v) => {
                          setValeursPerso((m) => ({ ...m, [c.code]: v }));
                          setErreursPerso((m) => { const { [c.code]: _r, ...reste } = m; return reste; });
                        }} />
                    </div>
                  ))}
                </div>
              </div>
            </section>
          ) : null}
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

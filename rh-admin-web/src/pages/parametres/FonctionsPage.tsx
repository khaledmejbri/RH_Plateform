import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  activerFonction,
  ajouterAffectationFonction,
  creerFonction,
  desactiverFonction,
  getAffectationsFonction,
  getFonctions,
  messageErreurSociete,
  modifierFonction,
  retirerAffectationFonction,
  supprimerFonction,
  type AffectationFonction,
  type FonctionRh,
} from '../../api/societeApi';
import { getTousCollaborateurs, getUnites, type CollaborateurRow, type Unite } from '../../api/rhClient';
import { SaisieDate } from '../../components/ChampsFr';
import { useConfirm } from '../../components/ConfirmDialog';
import FiltreCollaborateur from '../../components/FiltreCollaborateur';
import { EmptyState, ErrorAlert, LoadingState, PageHeader } from '../../components/ui';
import { formatDate, jourIso } from '../../utils/format';

type ContexteRoute = { lectureSeule?: boolean };

export default function FonctionsPage() {
  const { lectureSeule } = useOutletContext<ContexteRoute>() ?? {};
  const confirm = useConfirm();

  const [fonctions, setFonctions] = useState<FonctionRh[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Formulaire création / modification fonction
  const [editionId, setEditionId] = useState<string | null>(null);
  const [formOuvert, setFormOuvert] = useState(false);
  const [libelle, setLibelle] = useState('');
  const [description, setDescription] = useState('');
  const [versionEdition, setVersionEdition] = useState(0);
  const [envoi, setEnvoi] = useState(false);
  const [erreurForm, setErreurForm] = useState<string | null>(null);

  // Affectations de la fonction sélectionnée
  const [selection, setSelection] = useState<FonctionRh | null>(null);
  const [affectations, setAffectations] = useState<AffectationFonction[]>([]);
  const [affChargement, setAffChargement] = useState(false);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [unites, setUnites] = useState<Unite[]>([]);
  const [affCollabId, setAffCollabId] = useState('');
  const [affUniteId, setAffUniteId] = useState('');
  const [affDu, setAffDu] = useState(jourIso());
  const [affAu, setAffAu] = useState('');
  const [affErreur, setAffErreur] = useState<string | null>(null);
  const [affEnvoi, setAffEnvoi] = useState(false);

  const charger = useCallback(async () => {
    setChargement(true);
    setErreur(null);
    try {
      const liste = await getFonctions(false);
      setFonctions(liste);
    } catch (e) {
      setErreur(messageErreurSociete(e));
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  const chargerAffectations = useCallback(async (f: FonctionRh) => {
    setSelection(f);
    setAffChargement(true);
    setAffErreur(null);
    try {
      const [affs, collabs, unis] = await Promise.all([
        getAffectationsFonction(f.id),
        collaborateurs.length > 0 ? Promise.resolve(collaborateurs) : getTousCollaborateurs(),
        unites.length > 0 ? Promise.resolve(unites) : getUnites(),
      ]);
      setAffectations(affs);
      if (collaborateurs.length === 0) setCollaborateurs(collabs);
      if (unites.length === 0) setUnites(unis);
    } catch (e) {
      setAffErreur(messageErreurSociete(e));
    } finally {
      setAffChargement(false);
    }
  }, [collaborateurs, unites]);

  function ouvrirCreation() {
    setEditionId(null);
    setLibelle('');
    setDescription('');
    setVersionEdition(0);
    setErreurForm(null);
    setFormOuvert(true);
  }

  function ouvrirModification(f: FonctionRh) {
    setEditionId(f.id);
    setLibelle(f.libelle);
    setDescription(f.description ?? '');
    setVersionEdition(f.version);
    setErreurForm(null);
    setFormOuvert(true);
  }

  async function soumettreFonction(e: React.FormEvent) {
    e.preventDefault();
    if (lectureSeule) return;
    const lib = libelle.trim();
    if (lib.length < 2 || lib.length > 80) {
      setErreurForm('Le libellé doit contenir entre 2 et 80 caractères.');
      return;
    }
    setEnvoi(true);
    setErreurForm(null);
    try {
      if (editionId) {
        await modifierFonction(editionId, { libelle: lib, description: description.trim() || null, version: versionEdition });
        setMessage('Fonction modifiée.');
      } else {
        await creerFonction({ libelle: lib, description: description.trim() || null });
        setMessage('Fonction créée.');
      }
      setFormOuvert(false);
      await charger();
    } catch (err) {
      setErreurForm(messageErreurSociete(err));
    } finally {
      setEnvoi(false);
    }
  }

  async function basculerActif(f: FonctionRh) {
    if (lectureSeule) return;
    setErreur(null);
    setMessage(null);
    const ok = await confirm({
      title: f.actif ? 'Désactiver cette fonction ?' : 'Réactiver cette fonction ?',
      message: f.actif
        ? `La fonction « ${f.libelle} » ne pourra plus être choisie dans de nouveaux workflows.`
        : `La fonction « ${f.libelle} » redeviendra disponible.`,
      confirmLabel: f.actif ? 'Désactiver' : 'Réactiver',
      danger: f.actif,
    });
    if (!ok) return;
    try {
      if (f.actif) await desactiverFonction(f.id);
      else await activerFonction(f.id);
      setMessage(f.actif ? 'Fonction désactivée.' : 'Fonction réactivée.');
      await charger();
    } catch (err) {
      setErreur(messageErreurSociete(err));
    }
  }

  async function supprimer(f: FonctionRh) {
    if (lectureSeule) return;
    const ok = await confirm({
      title: 'Supprimer cette fonction ?',
      message: `La fonction « ${f.libelle} » sera supprimée définitivement.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    try {
      await supprimerFonction(f.id);
      if (selection?.id === f.id) setSelection(null);
      setMessage('Fonction supprimée.');
      await charger();
    } catch (err) {
      setErreur(messageErreurSociete(err));
    }
  }

  async function soumettreAffectation(e: React.FormEvent) {
    e.preventDefault();
    if (!selection || lectureSeule) return;
    if (!affCollabId) {
      setAffErreur('Sélectionnez un collaborateur.');
      return;
    }
    if (!affDu) {
      setAffErreur('La date de début est obligatoire.');
      return;
    }
    setAffEnvoi(true);
    setAffErreur(null);
    try {
      await ajouterAffectationFonction(selection.id, {
        collaborateur_id: affCollabId,
        unite_perimetre_id: affUniteId || null,
        du: affDu,
        au: affAu || null,
      });
      setAffCollabId('');
      setAffUniteId('');
      setAffAu('');
      await chargerAffectations(selection);
    } catch (err) {
      setAffErreur(messageErreurSociete(err));
    } finally {
      setAffEnvoi(false);
    }
  }

  async function retirerAff(a: AffectationFonction) {
    if (!selection || lectureSeule) return;
    const ok = await confirm({
      title: 'Retirer cette affectation ?',
      message: `Retirer ${a.collaborateur_nom} de la fonction « ${selection.libelle} » ?`,
      confirmLabel: 'Retirer',
      danger: true,
    });
    if (!ok) return;
    try {
      await retirerAffectationFonction(selection.id, a.id);
      await chargerAffectations(selection);
    } catch (err) {
      setAffErreur(messageErreurSociete(err));
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="Fonctions transverses"
        lead="Rôles transverses (ex. gestionnaire paie) attribués à des personnes, quelle que soit leur unité. Ils servent de destinataires dans les workflows. L'arbre des directions, départements et services se gère dans l'Organigramme."
        actions={
          !lectureSeule ? (
            <button type="button" className="btn btn--primary" onClick={ouvrirCreation}>
              Nouvelle fonction
            </button>
          ) : null
        }
      />

      {message ? <div className="alert alert--success" role="status" style={{ marginBottom: 12 }}>{message}</div> : null}
      {erreur ? (
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </ErrorAlert>
      ) : null}

      {formOuvert && !lectureSeule ? (
        <form className="card" style={{ marginBottom: 16 }} onSubmit={(e) => void soumettreFonction(e)}>
          <h3 style={{ marginBottom: 12 }}>{editionId ? 'Modifier la fonction' : 'Créer une fonction'}</h3>
          {erreurForm ? <ErrorAlert>{erreurForm}</ErrorAlert> : null}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12, marginTop: 8 }}>
            <div>
              <label className="field-label" htmlFor="fn-libelle">Libellé</label>
              <input
                id="fn-libelle"
                className="field-input"
                maxLength={80}
                required
                value={libelle}
                onChange={(e) => setLibelle(e.target.value)}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="fn-desc">Description (facultatif)</label>
              <input
                id="fn-desc"
                className="field-input"
                maxLength={300}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button type="submit" className="btn btn--primary" disabled={envoi}>
              {envoi ? 'Enregistrement…' : 'Enregistrer'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => setFormOuvert(false)}>
              Annuler
            </button>
          </div>
        </form>
      ) : null}

      {chargement ? <LoadingState /> : null}
      {!chargement && fonctions.length === 0 ? (
        <EmptyState title="Aucune fonction définie">
          Créez une fonction transverse pour l’assigner à des collaborateurs et la référencer dans vos workflows.
        </EmptyState>
      ) : null}

      {!chargement && fonctions.length > 0 ? (
        <div className="card" style={{ marginBottom: 16 }}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Libellé</th>
                  <th>Code</th>
                  <th>Description</th>
                  <th>Statut</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {fonctions.map((f) => (
                  <tr key={f.id}>
                    <td><strong>{f.libelle}</strong></td>
                    <td><code className="small">{f.code}</code></td>
                    <td>{f.description ?? '—'}</td>
                    <td>
                      <span className={`status status--${f.actif ? 'success' : 'neutral'}`}>
                        {f.actif ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          className="btn btn--secondary btn--sm"
                          onClick={() => void chargerAffectations(f)}
                        >
                          Affectations
                        </button>
                        {!lectureSeule ? (
                          <>
                            <button type="button" className="btn btn--ghost btn--sm" onClick={() => ouvrirModification(f)}>
                              Modifier
                            </button>
                            <button type="button" className="btn btn--ghost btn--sm" onClick={() => void basculerActif(f)}>
                              {f.actif ? 'Désactiver' : 'Réactiver'}
                            </button>
                            {!f.utilisee ? (
                              <button type="button" className="btn btn--ghost btn--sm" onClick={() => void supprimer(f)}>
                                Supprimer
                              </button>
                            ) : null}
                          </>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {selection ? (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h3>Affectations — {selection.libelle}</h3>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setSelection(null)}>
              Fermer
            </button>
          </div>
          {affErreur ? <ErrorAlert>{affErreur}</ErrorAlert> : null}

          {!lectureSeule ? (
            <form
              onSubmit={(e) => void soumettreAffectation(e)}
              style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10, marginBottom: 16, alignItems: 'end' }}
            >
              <FiltreCollaborateur
                id="aff-collab"
                collaborateurs={collaborateurs}
                valeur={affCollabId}
                onChange={setAffCollabId}
              />
              <div>
                <label className="field-label" htmlFor="aff-unite">Périmètre (unité, facultatif)</label>
                <select
                  id="aff-unite"
                  className="field-input"
                  value={affUniteId}
                  onChange={(e) => setAffUniteId(e.target.value)}
                >
                  <option value="">Toute la société</option>
                  {unites.filter((u) => u.actif).map((u) => (
                    <option key={u.identifiant} value={u.identifiant}>{u.libelle}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="field-label" htmlFor="aff-du">Du</label>
                <SaisieDate id="aff-du" valeur={affDu} onChange={setAffDu} required />
              </div>
              <div>
                <label className="field-label" htmlFor="aff-au">Au (facultatif)</label>
                <SaisieDate id="aff-au" valeur={affAu} onChange={setAffAu} />
              </div>
              <div>
                <button type="submit" className="btn btn--primary" disabled={affEnvoi}>
                  {affEnvoi ? 'Ajout…' : 'Affecter'}
                </button>
              </div>
            </form>
          ) : null}

          {affChargement ? <LoadingState /> : null}
          {!affChargement && affectations.length === 0 ? (
            <p className="muted">Aucun collaborateur affecté à cette fonction.</p>
          ) : null}
          {!affChargement && affectations.length > 0 ? (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Collaborateur</th>
                    <th>Périmètre</th>
                    <th>Du</th>
                    <th>Au</th>
                    {!lectureSeule ? <th>Action</th> : null}
                  </tr>
                </thead>
                <tbody>
                  {affectations.map((a) => (
                    <tr key={a.id}>
                      <td>{a.collaborateur_nom}{a.collaborateur_matricule ? ` (${a.collaborateur_matricule})` : ''}</td>
                      <td>{a.unite_perimetre_nom ?? 'Toute la société'}</td>
                      <td>{formatDate(a.du)}</td>
                      <td>{a.au ? formatDate(a.au) : 'Sans fin'}</td>
                      {!lectureSeule ? (
                        <td>
                          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void retirerAff(a)}>
                            Retirer
                          </button>
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

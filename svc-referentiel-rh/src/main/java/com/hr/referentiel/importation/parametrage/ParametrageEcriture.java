package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ChampPerso;
import com.hr.referentiel.champsperso.ChampPersoRepository;
import com.hr.referentiel.champsperso.ChampPersoUtilisations;
import com.hr.referentiel.champsperso.ChampPersoValeurListe;
import com.hr.referentiel.champsperso.ChampPersoValeurListeRepository;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.web.ReferentielMetierException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static com.hr.referentiel.importation.parametrage.ImportParametrageValidation.erreur;

/**
 * Gabarit commun des écritures du paramétrage d'un type (ADR § 5, extrait de {@link ImportParametrageService}) : résoudre
 * le type, verrou {@code FOR UPDATE} sur la ligne du type, contrôle de version en dernier, {@code modifie_le} et
 * version + 1 seulement si quelque chose a changé, journal, invalidation du cache après commit. À appeler dans la
 * transaction de l'écriture ; toute exception l'annule.
 */
@Component
public class ParametrageEcriture {

	private final ImportParametrageRepository parametrageRepository;
	private final ImportChampRegleRepository champRepository;
	private final ImportSynonymeRepository synonymeRepository;
	private final ChampPersoRepository champPersoRepository;
	private final ChampPersoValeurListeRepository valeurListeRepository;
	private final ImportChampsJournal journal;
	private final ParametrageImportCache cache;
	private final ChampPersoUtilisations utilisations;

	@PersistenceContext
	private EntityManager entityManager;

	public ParametrageEcriture(ImportParametrageRepository parametrageRepository, ImportChampRegleRepository champRepository,
			ImportSynonymeRepository synonymeRepository, ChampPersoRepository champPersoRepository,
			ChampPersoValeurListeRepository valeurListeRepository, ImportChampsJournal journal,
			ParametrageImportCache cache, ChampPersoUtilisations utilisations) {
		this.parametrageRepository = parametrageRepository;
		this.champRepository = champRepository;
		this.synonymeRepository = synonymeRepository;
		this.champPersoRepository = champPersoRepository;
		this.valeurListeRepository = valeurListeRepository;
		this.journal = journal;
		this.cache = cache;
		this.utilisations = utilisations;
	}

	public TypeImportParametrable resoudre(String chemin) {
		return TypeImportParametrable.parChemin(chemin)
				.orElseThrow(() -> erreur(404, "TYPE_IMPORT_INCONNU", "Type d'import inconnu.", null));
	}

	/** Verrou {@code FOR UPDATE} sur la ligne du type, puis lecture de l'état. */
	public EtatParametrage ouvrir(TypeImportParametrable type) {
		ImportParametrage p = parametrageRepository.findByIdForUpdate(type.name()).orElseThrow(() -> absent(type));
		return charger(type, p);
	}

	/** Lecture sans verrou (P1). */
	public EtatParametrage lire(TypeImportParametrable type) {
		ImportParametrage p = parametrageRepository.findById(type.name()).orElseThrow(() -> absent(type));
		return charger(type, p);
	}

	private EtatParametrage charger(TypeImportParametrable type, ImportParametrage p) {
		List<ChampPerso> persos = champPersoRepository.findByTypeImport(type.name());
		List<ChampPersoValeurListe> valeurs = persos.isEmpty() ? List.of()
				: valeurListeRepository.findByChampPersoIdInOrderByOrdreAsc(persos.stream().map(ChampPerso::getId).toList());
		return new EtatParametrage(type, p, champRepository.findByTypeImportOrderByOrdreAsc(type.name()),
				synonymeRepository.findByTypeImport(type.name()), persos, valeurs);
	}

	private static IllegalStateException absent(TypeImportParametrable type) {
		return new IllegalStateException("Paramétrage des champs d'import non amorcé pour " + type.name());
	}

	/** 409 en dernier, une fois toutes les autres règles contrôlées sur l'état relu sous verrou. */
	public void controlerVersion(EtatParametrage etat, Long recue) {
		if (recue == null || recue != etat.parametrage.getVersion()) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"Ce paramétrage a été modifié par quelqu'un d'autre.");
		}
	}

	/**
	 * Version + 1 et journal seulement si une ligne a changé ; invalide le cache après commit. Une ligne est
	 * {@code {champ, attribut, avant, apres[, libelle figé]}} ; sans libellé explicite, celui d'un champ personnalisé
	 * encore défini est figé au journal.
	 */
	public Parametrage terminer(EtatParametrage etat, List<String[]> lignes, AuteurParametre auteur, Instant maintenant) {
		if (!lignes.isEmpty()) {
			if (entityManager != null) {
				entityManager.detach(etat.parametrage);
			}
			etat.parametrage.marquerModifie(maintenant, auteur.nom());
			parametrageRepository.mettreAJourModification(
					etat.type.name(),
					com.hr.commun.societe.SocieteContexte.courante(),
					etat.parametrage.getVersion(),
					maintenant,
					auteur.nom());
			for (String[] l : lignes) {
				String libelle = l.length > 4 ? l[4] : l[0] != null && etat.estPerso(l[0]) ? etat.libelle(l[0]) : null;
				journal.ecrire(etat.type, etat.parametrage.getVersion(), l[0], l[1], l[2], l[3], auteur, maintenant, libelle);
			}
			cache.invaliderApresCommit(etat.type);
		}
		return vue(etat);
	}

	public Parametrage vue(EtatParametrage etat) {
		return ImportParametrageVues.construire(etat, utilisations);
	}
}

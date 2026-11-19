package com.hr.referentiel.service;

import com.hr.referentiel.champsperso.DefinitionsPerso;
import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.FormatValeurPerso;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.champsperso.ValeursPersoFicheService;
import com.hr.referentiel.champsperso.ValeursPersoJdbc;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Export CSV de la liste filtrée (V6, ADR partie 2, P2-7) : mêmes filtres que V5, toutes les pages (500 fiches par
 * page, mémoire bornée). Colonnes fixes dans l'ordre de la fiche T6, puis champs personnalisés ACTIFS dans l'ordre réglé
 * de l'import ; en-tête = libellé du champ ; libellés partout (jamais de code ni d'identifiant) ; formules neutralisées
 * sur chaque cellule ({@link CsvFrancais}). Aucune valeur n'est écrite dans les journaux.
 */
@Service
public class ExportCollaborateursService {

	private static final Logger log = LoggerFactory.getLogger(ExportCollaborateursService.class);
	static final int PAGE = 500;
	static final List<String> COLONNES_FIXES = List.of("Matricule", "Prénom", "Nom", "Courriel", "Unité", "Poste", "Fonction",
			"Qualité", "Affectation", "Date de recrutement", "Statut", "Profil d'accès", "Famille métier", "Niveau de séniorité");

	private final CollaborateurRepository collaborateurRepository;
	private final RechercheCollaborateursService recherche;
	private final ReferentielRhService referentielRhService;
	private final ParametreService parametreService;
	private final DefinitionsPerso definitions;
	private final ValeursPersoJdbc valeursJdbc;
	private final Clock clock;

	public ExportCollaborateursService(CollaborateurRepository collaborateurRepository, RechercheCollaborateursService recherche,
			ReferentielRhService referentielRhService, ParametreService parametreService, DefinitionsPerso definitions,
			ValeursPersoJdbc valeursJdbc, Clock clock) {
		this.collaborateurRepository = collaborateurRepository;
		this.recherche = recherche;
		this.referentielRhService = referentielRhService;
		this.parametreService = parametreService;
		this.definitions = definitions;
		this.valeursJdbc = valeursJdbc;
		this.clock = clock;
	}

	/** Nom du fichier : {@code collaborateurs_AAAAMMJJ.csv}, date de Tunis. */
	public String nomFichier() {
		return "collaborateurs_" + LocalDate.now(clock.withZone(FormatJours.TUNIS)).toString().replace("-", "") + ".csv";
	}

	@Transactional(readOnly = true)
	public byte[] exporter(FiltreCollaborateurs filtre, String utilisateur) {
		Specification<Collaborateur> spec = recherche.specification(filtre);
		List<Def> champs = definitions.lire(ValeursPersoFicheService.TYPE).values().stream().filter(d -> d.champ().isActif()).toList();
		List<String> entetes = new ArrayList<>(COLONNES_FIXES);
		champs.forEach(d -> entetes.add(d.champ().getLibelle()));
		CsvFrancais csv = new CsvFrancais().ligne(entetes);
		int lignes = 0;
		for (int page = 0;; page++) {
			Page<Collaborateur> p = collaborateurRepository.findAll(spec, PageRequest.of(page, PAGE, RechercheCollaborateursService.TRI));
			Map<UUID, Map<String, ValeurTypee>> valeurs = champs.isEmpty() ? Map.of()
					: valeursJdbc.fiches(p.getContent().stream().map(Collaborateur::getId).toList());
			for (Collaborateur c : p.getContent()) {
				csv.ligne(ligne(referentielRhService.versReponse(c), champs, valeurs.getOrDefault(c.getId(), Map.of())));
				lignes++;
			}
			if (!p.hasNext()) {
				break;
			}
		}
		log.info("Export des collaborateurs par {} : {} ligne(s), {} critère(s) personnalisé(s)", utilisateur, lignes,
				filtre.criteres().size()); // jamais de valeur dans les journaux
		return csv.octets();
	}

	private List<String> ligne(CollaborateurResponse c, List<Def> champs, Map<String, ValeurTypee> valeurs) {
		List<String> l = new ArrayList<>();
		l.add(CsvFrancais.texte(c.getMatricule()));
		l.add(CsvFrancais.texte(c.getPrenom()));
		l.add(CsvFrancais.texte(c.getNom()));
		l.add(CsvFrancais.texte(c.getCourrielProfessionnel()));
		l.add(CsvFrancais.texte(c.getUnite() == null ? null : c.getUnite().getLibelle()));
		l.add(CsvFrancais.texte(c.getPosteLibelle()));
		l.add(CsvFrancais.texte(c.getFonction()));
		l.add(CsvFrancais.texte(c.getQualite()));
		l.add(CsvFrancais.texte(c.getAffectation()));
		l.add(c.getDateRecrutement() == null ? "" : c.getDateRecrutement().format(FormatJours.JOUR));
		l.add(CsvFrancais.texte(libelle("STATUT_COLLABORATEUR", c.getStatut())));
		l.add(CsvFrancais.texte(libelle("PROFIL_ACCES", c.getProfilAcces())));
		l.add(CsvFrancais.texte(c.getFamilleMetierLibelle()));
		l.add(CsvFrancais.texte(c.getNiveauSenioriteLibelle()));
		for (Def d : champs) {
			ValeurTypee v = valeurs.get(d.champ().getCode());
			l.add(v == null ? "" : CsvFrancais.texte(FormatValeurPerso.affichage(v, d.definition())));
		}
		return l;
	}

	private String libelle(String referentiel, String code) {
		return code == null ? null : parametreService.libelleOuInconnu(referentiel, code);
	}
}

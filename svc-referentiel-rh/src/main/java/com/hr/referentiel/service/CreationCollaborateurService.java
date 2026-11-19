package com.hr.referentiel.service;

import com.hr.referentiel.champsperso.DefinitionsPerso;
import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.champsperso.ValeursPersoFicheService;
import com.hr.referentiel.dto.CollaborateurCreationRequest;
import com.hr.referentiel.dto.CollaborateurResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Façade de création d'une fiche avec ses valeurs de champs personnalisés (ADR partie 2, P2-6) : les valeurs sont
 * contrôlées AVANT la création (valeur invalide : 400, aucune fiche créée), puis la fiche est créée par
 * {@link ReferentielRhService#creerCollaborateur} (inchangé, il rejoint la transaction), puis les valeurs sont écrites
 * (origine CREATION, version 1). Aucune logique personnalisée dans {@code ReferentielRhService}.
 */
@Service
public class CreationCollaborateurService {

	private final ReferentielRhService referentielRhService;
	private final ValeursPersoFicheService valeursService;
	private final DefinitionsPerso definitions;

	public CreationCollaborateurService(ReferentielRhService referentielRhService, ValeursPersoFicheService valeursService,
			DefinitionsPerso definitions) {
		this.referentielRhService = referentielRhService;
		this.valeursService = valeursService;
		this.definitions = definitions;
	}

	@Transactional
	public CollaborateurResponse creer(CollaborateurCreationRequest req, Jwt jwt) {
		Map<String, Object> brutes = req.getChampsPersonnalises();
		if (brutes == null || brutes.isEmpty()) {
			return referentielRhService.creerCollaborateur(req);
		}
		Map<String, Def> defs = definitions.lireSousVerrou(ValeursPersoFicheService.TYPE);
		Map<String, ValeurTypee> valeurs = valeursService.controlerCreation(brutes, defs);
		CollaborateurResponse cree = referentielRhService.creerCollaborateur(req);
		valeursService.ecrireCreation(cree.getIdentifiant(), valeurs, defs, jwt);
		return cree;
	}
}

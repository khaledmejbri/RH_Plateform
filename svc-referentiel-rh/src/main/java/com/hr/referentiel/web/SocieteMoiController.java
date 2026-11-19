package com.hr.referentiel.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.IdentiteSocieteClient.SocieteResume;
import com.hr.referentiel.service.SocieteParametresService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Vue collaborateur des paramètres de sa société (§ 9.7.1 {@code GET /api/rh/v1/societe/moi}).
 */
@RestController
@RequestMapping("/api/rh/v1/societe")
public class SocieteMoiController {

	public record SocieteMoiResponse(
			@JsonProperty("code") String code,
			@JsonProperty("raison_sociale") String raisonSociale,
			@JsonProperty("jours_travailles") List<String> joursTravailles,
			@JsonProperty("heure_debut_journee") String heureDebutJournee,
			@JsonProperty("heure_fin_journee") String heureFinJournee,
			@JsonProperty("duree_max_sortie_minutes") int dureeMaxSortieMinutes
	) {
	}

	private final SocieteParametresService parametresService;
	private final IdentiteSocieteClient identiteSocieteClient;

	public SocieteMoiController(
			SocieteParametresService parametresService,
			IdentiteSocieteClient identiteSocieteClient) {
		this.parametresService = parametresService;
		this.identiteSocieteClient = identiteSocieteClient;
	}

	@GetMapping("/moi")
	@PreAuthorize("hasAnyRole('USER','RO','RH','DIRECTION','ADMIN')")
	public SocieteMoiResponse moi() {
		UUID societeId = SocieteContexte.courante();
		SocieteParametresResponse p = parametresService.obtenir();
		Optional<SocieteResume> resume = identiteSocieteClient.trouverParId(societeId);
		return new SocieteMoiResponse(
				resume.map(SocieteResume::code).orElse(null),
				resume.map(SocieteResume::raisonSociale).orElse(null),
				p.joursTravailles(),
				p.heureDebutJournee(),
				p.heureFinJournee(),
				p.dureeMaxSortieMinutes());
	}
}

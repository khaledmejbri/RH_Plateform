package com.hr.presence.web;

import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.BeneficiairePossibleResponse;
import com.hr.presence.dto.HeuresSupDtos.CreationHeuresSupRequest;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.dto.HeuresSupDtos.FenetreDuJourRequest;
import com.hr.presence.dto.HeuresSupDtos.FenetreDuJourResponse;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.dto.HeuresSupDtos.MesHeuresSupResponse;
import com.hr.presence.dto.HeuresSupDtos.SiteMobileResponse;
import com.hr.presence.service.FenetreMobileService;
import com.hr.presence.service.HeuresSupCreationService;
import com.hr.presence.service.HeuresSupService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Horaires et heures supplémentaires côté RH Connect (SCRUM-49 à 51). Identité toujours lue dans le JWT : aucun
 * endpoint n'accepte d'identifiant de collaborateur pour désigner « moi », et {@code X-Collaborateur-Id} n'est pas lu.
 */
@RestController
@RequestMapping("/api/rh/v1/mobile/presence")
public class HeuresSupMobileController {

	private final HeuresSupService service;
	private final HeuresSupCreationService creationService;
	private final FenetreMobileService fenetreService;

	public HeuresSupMobileController(HeuresSupService service, HeuresSupCreationService creationService,
									 FenetreMobileService fenetreService) {
		this.service = service;
		this.creationService = creationService;
		this.fenetreService = fenetreService;
	}

	/** Le QR est dans le corps, jamais dans l'URL ; rien n'est enregistré. */
	@PostMapping("/fenetre-du-jour")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public FenetreDuJourResponse fenetreDuJour(@Valid @RequestBody FenetreDuJourRequest request) {
		return fenetreService.fenetreDuJour(request.qrToken());
	}

	@GetMapping("/sites")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public List<SiteMobileResponse> sites() {
		return fenetreService.sitesActifs();
	}

	@GetMapping("/heures-sup/beneficiaires-possibles")
	@PreAuthorize(PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP)
	public List<BeneficiairePossibleResponse> beneficiairesPossibles() {
		return service.beneficiairesPossibles();
	}

	@PostMapping("/heures-sup/demandes")
	@PreAuthorize(PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP)
	public ResponseEntity<DemandeHeuresSupResponse> creer(@Valid @RequestBody CreationHeuresSupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(creationService.creerParDemandeur(request));
	}

	@GetMapping("/heures-sup/demandes/moi")
	@PreAuthorize(PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP)
	public List<DemandeHeuresSupResponse> mesDemandes(@RequestParam(required = false) StatutHeuresSup statut) {
		return service.mesDemandes(statut);
	}

	@GetMapping("/heures-sup/demandes/moi/comptees")
	@PreAuthorize(PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP)
	public List<HeuresComptees> mesDemandesComptees(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(name = "demande_id", required = false) UUID demandeId) {
		return service.mesDemandesComptees(du, au, demandeId);
	}

	@PostMapping("/heures-sup/demandes/{id}/annuler")
	@PreAuthorize(PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP)
	public DemandeHeuresSupResponse annuler(@PathVariable UUID id) {
		return service.annulerParDemandeur(id);
	}

	@GetMapping("/heures-sup/moi")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public MesHeuresSupResponse mesHeures(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.mesHeures(du, au);
	}
}

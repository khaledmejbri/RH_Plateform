package com.hr.presence.web;

import com.hr.presence.dto.FacialMobileDtos.AppareilIncompatibleRequest;
import com.hr.presence.dto.FacialMobileDtos.ConsentementCourant;
import com.hr.presence.dto.FacialMobileDtos.ConsentementDecisionRequest;
import com.hr.presence.dto.FacialMobileDtos.DemandeReenrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EnrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EtatFacialMoi;
import com.hr.presence.service.EnrolementFacialService;
import com.hr.presence.service.EnrolementFacialService.ResultatEnrolement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vérification faciale côté appli (D-P07) : consentement, enrôlement, retrait, état. Contrôleur mince. */
@RestController
@RequestMapping("/api/rh/v1/mobile/presence/verification-faciale")
@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
public class FacialMobileController {

	private final EnrolementFacialService service;

	public FacialMobileController(EnrolementFacialService service) {
		this.service = service;
	}

	@GetMapping("/moi")
	public EtatFacialMoi moi(@RequestParam(name = "device_id", required = false) String deviceId) {
		return service.moi(deviceId);
	}

	@GetMapping("/consentement/courant")
	public ConsentementCourant consentementCourant() {
		return service.consentementCourant();
	}

	@PostMapping("/enrolement")
	public ResponseEntity<EtatFacialMoi> enroler(@Valid @RequestBody EnrolementRequest requete) {
		ResultatEnrolement resultat = service.enroler(requete);
		return ResponseEntity.status(resultat.cree() ? HttpStatus.CREATED : HttpStatus.OK).body(resultat.etat());
	}

	@PostMapping("/consentement/acceptation")
	public EtatFacialMoi accepter(@Valid @RequestBody ConsentementDecisionRequest requete) {
		return service.accepterConsentement(requete);
	}

	@PostMapping("/consentement/refus")
	public EtatFacialMoi refuser(@Valid @RequestBody ConsentementDecisionRequest requete) {
		return service.refuserConsentement(requete);
	}

	@PostMapping("/consentement/retrait")
	public EtatFacialMoi retirer() {
		return service.retirerConsentement();
	}

	@PostMapping("/appareil-incompatible")
	public EtatFacialMoi appareilIncompatible(@Valid @RequestBody AppareilIncompatibleRequest requete) {
		return service.signalerAppareilIncompatible(requete);
	}

	@PostMapping("/demande-reenrolement")
	public EtatFacialMoi demandeReenrolement(@Valid @RequestBody DemandeReenrolementRequest requete) {
		return service.demanderReenrolement(requete);
	}
}

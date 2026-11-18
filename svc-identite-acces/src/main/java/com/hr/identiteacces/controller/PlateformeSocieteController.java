package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.SocieteDtos.AdminInitialRequest;
import com.hr.identiteacces.dto.SocieteDtos.CreerSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.ModifierSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.MotifSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.PageResponse;
import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.service.SocieteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/plateforme/v1/societes")
@PreAuthorize("hasRole('PLATEFORME')")
@RequiredArgsConstructor
public class PlateformeSocieteController {

	private final SocieteService societeService;

	@GetMapping
	@PreAuthorize("hasRole('PLATEFORME')")
	public PageResponse<SocieteResponse> lister(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return societeService.listerPagines(page, taille);
	}

	@PostMapping
	@PreAuthorize("hasRole('PLATEFORME')")
	public ResponseEntity<SocieteResponse> creer(@RequestBody(required = false) CreerSocieteRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(societeService.creer(req));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('PLATEFORME')")
	public SocieteResponse modifier(
			@PathVariable UUID id,
			@RequestBody(required = false) ModifierSocieteRequest req) {
		return societeService.modifier(id, req);
	}

	@PostMapping("/{id}/suspendre")
	@PreAuthorize("hasRole('PLATEFORME')")
	public SocieteResponse suspendre(
			@PathVariable UUID id,
			@RequestBody(required = false) MotifSocieteRequest req) {
		return societeService.suspendre(id, req);
	}

	@PostMapping("/{id}/reactiver")
	@PreAuthorize("hasRole('PLATEFORME')")
	public SocieteResponse reactiver(
			@PathVariable UUID id,
			@RequestBody(required = false) MotifSocieteRequest req) {
		return societeService.reactiver(id, req);
	}

	@PostMapping("/{id}/admin-initial")
	@PreAuthorize("hasRole('PLATEFORME')")
	public ResponseEntity<UserResponse> creerAdminInitial(
			@PathVariable UUID id,
			@RequestBody(required = false) AdminInitialRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(societeService.creerAdminInitial(id, req));
	}
}

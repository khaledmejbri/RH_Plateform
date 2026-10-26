package com.hr.referentiel.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ReferentielExceptionHandler {

	@ExceptionHandler(ReferentielMetierException.class)
	public ResponseEntity<Map<String, Object>> metier(ReferentielMetierException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", ex.getCode());
		body.put("erreur", ex.getMessage());
		return ResponseEntity.status(ex.getHttpStatus()).body(body);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException ex) {
		String msg = ex.getMessage() != null ? ex.getMessage() : "Données invalides";
		if ("NIVEAU_SENIORITE_INVALIDE".equals(msg) || "FAMILLE_METIER_INCONNUE".equals(msg)) {
			return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
					.body(Map.of("code", msg, "erreur", msg));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erreur", msg));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erreur", ex.getMessage()));
	}

	/** SCRUM-8 — sans ce handler, l'erreur SQL partait vers /error (protégé) et revenait en 403 vide. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> integrite(DataIntegrityViolationException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"code", "VIOLATION_INTEGRITE",
				"erreur", "La donnée ne respecte pas une contrainte de la base (valeur non autorisée ou doublon)."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erreur", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
		Map<String, String> details = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(err -> {
			String champ = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
			details.put(champ, err.getDefaultMessage());
		});
		Map<String, Object> body = new HashMap<>();
		body.put("erreur", "Données invalides");
		body.put("details", details);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}
}

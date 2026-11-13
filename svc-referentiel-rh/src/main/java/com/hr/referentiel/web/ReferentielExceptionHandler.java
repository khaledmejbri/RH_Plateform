package com.hr.referentiel.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ReferentielExceptionHandler {

	@ExceptionHandler(ReferentielMetierException.class)
	public ResponseEntity<Map<String, Object>> metier(ReferentielMetierException ex) {
		Map<String, Object> body = new HashMap<>();
		body.putAll(ex.getDetails());
		body.put("code", ex.getCode());
		body.put("erreur", ex.getMessage());
		// « message » : clé du contrat du paramétrage (ADR-UAT-lot3 § 6), même texte que « erreur »
		body.put("message", ex.getMessage());
		return ResponseEntity.status(ex.getHttpStatus()).body(body);
	}

	/** Deux écritures simultanées d'un paramètre : la seconde est refusée, rien n'est écrasé (D-R02.h). */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, Object>> verrouOptimiste(ObjectOptimisticLockingFailureException ex) {
		String msg = "Cette valeur a été modifiée par un autre utilisateur. Rechargez la liste puis recommencez.";
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"code", "PARAMETRE_MODIFIE_ENTRE_TEMPS", "erreur", msg, "message", msg));
	}

	/** Fichier d'import au-delà de la limite multipart : 413 en JSON (jamais la page HTML du serveur). */
	@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> fichierTropVolumineux(
			org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
		String msg = "Le fichier dépasse 10 Mo.";
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of(
				"code", "FICHIER_TROP_VOLUMINEUX", "erreur", msg, "message", msg));
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

	/** Corps absent ou JSON illisible : 400 explicite (sans ce handler l'erreur retombait sur /error, refusé en 403). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> corpsIllisible(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"code", "CORPS_INVALIDE",
				"erreur", "Corps de requête absent ou illisible : vérifiez le JSON envoyé."));
	}

	/** Route inconnue : 404 (sans ce handler, l'erreur remontait en 500). */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, String>> routeInconnue(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
				"code", "ROUTE_INCONNUE", "erreur", "Ressource introuvable."));
	}

	/** Identifiant ou paramètre mal formé (UUID, date, énumération) : 400 explicite, jamais un 403 vide via /error. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, String>> parametreMalForme(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"code", "PARAMETRE_INVALIDE",
				"erreur", "Le paramètre « " + ex.getName() + " » est mal formé."));
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

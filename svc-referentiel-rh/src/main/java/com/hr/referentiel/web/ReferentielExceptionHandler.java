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

	/** Message du doublon de matricule (CA-01.3), partagé avec le contrôle applicatif de la création. */
	public static final String MESSAGE_MATRICULE_EXISTANT = "Un collaborateur avec ce matricule existe déjà.";

	/** Index unique (societe_id, matricule) posé par la reprise multi-société et par l'entité Collaborateur. */
	static final String CONTRAINTE_MATRICULE = "idx_collab_societe_matricule";

	/** SCRUM-8 — sans ce handler, l'erreur SQL partait vers /error (protégé) et revenait en 403 vide. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> integrite(DataIntegrityViolationException ex) {
		if (estDoublonMatricule(ex)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
					"code", "MATRICULE_EXISTANT",
					"erreur", MESSAGE_MATRICULE_EXISTANT,
					"message", MESSAGE_MATRICULE_EXISTANT));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"code", "VIOLATION_INTEGRITE",
				"erreur", "La donnée ne respecte pas une contrainte de la base (valeur non autorisée ou doublon)."));
	}

	/**
	 * Doublon de matricule reconnu par le seul nom de la contrainte violée (revue W1 boucle 3, amélioration 1).
	 * Le message d'Hibernate contient le SQL de l'insert / update, qui cite toujours la colonne « matricule » :
	 * on ne se fie donc jamais à ce texte, seulement au nom de contrainte fourni par Hibernate
	 * ou au motif exact du pilote PostgreSQL.
	 */
	static boolean estDoublonMatricule(Throwable ex) {
		Throwable cur = ex;
		int profondeur = 0;
		while (cur != null && profondeur++ < 20) {
			if (cur instanceof org.hibernate.exception.ConstraintViolationException cve
					&& CONTRAINTE_MATRICULE.equalsIgnoreCase(cve.getConstraintName())) {
				return true;
			}
			// Pilote PostgreSQL (dépendance runtime) : « violates unique constraint "<nom>" ». Le nom de l'index,
			// entre guillemets, n'apparaît jamais dans le SQL de l'insert : seul ce motif exact est accepté.
			if (cur instanceof java.sql.SQLException sqle && sqle.getMessage() != null
					&& sqle.getMessage().toLowerCase(java.util.Locale.ROOT)
							.contains("constraint \"" + CONTRAINTE_MATRICULE + "\"")) {
				return true;
			}
			if (cur.getCause() == cur) {
				break;
			}
			cur = cur.getCause();
		}
		return false;
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

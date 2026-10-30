package com.hr.presence.web;

import com.hr.presence.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class PresenceExceptionHandler {

	@ExceptionHandler(PresenceMetierException.class)
	public ResponseEntity<ErrorResponse> metier(PresenceMetierException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(
				ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getMessage(), List.of(), ex.getHttpStatus())
		);
	}

	/** 409 POINTAGE_BLOQUE_VISAGE : porte la fin du blocage (D-P10.1). */
	@ExceptionHandler(PointageBloqueException.class)
	public ResponseEntity<ErrorResponse> pointageBloque(PointageBloqueException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(
				ErrorResponse.bloque(ex.getCode(), ex.getMessage(), ex.getBloqueJusqua(), ex.getHttpStatus())
		);
	}

	/** Corps JSON illisible ou valeur d'énumération inconnue (ex. plateforme, raison) : 400 plutôt que 500. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> corpsIllisible(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("REQUETE_MAL_FORMEE", "Corps de requête illisible ou valeur non reconnue",
						"Vérifiez le format JSON et les valeurs autorisées", List.of(), HttpStatus.BAD_REQUEST.value())
		);
	}

	/** Paramètre d'URL de mauvais type (ex. statut inconnu) : 400 plutôt que 500. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> parametreInvalide(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("PARAMETRE_INVALIDE", "Paramètre invalide : " + ex.getName(),
						"Valeur non reconnue pour " + ex.getName(), List.of(), HttpStatus.BAD_REQUEST.value())
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
		StringBuilder details = new StringBuilder("Champs invalides: ");
		ex.getBindingResult().getAllErrors().forEach(err -> {
			String champ = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
			details.append(champ).append(" - ").append(err.getDefaultMessage()).append("; ");
		});
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
				ErrorResponse.of(
						"VALIDATION_ERROR",
						"Données invalides",
						details.toString(),
						List.of("Vérifiez le format des champs indiqués"),
						HttpStatus.UNPROCESSABLE_ENTITY.value()
				)
		);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
				ErrorResponse.of(
						"MISSING_PARAMETER",
						"Paramètre manquant: " + ex.getParameterName(),
						ex.getMessage(),
						List.of(),
						HttpStatus.UNPROCESSABLE_ENTITY.value()
				)
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ErrorResponse.of(
						"ACCESS_DENIED",
						"Accès non autorisé",
						"Vous n'avez pas les permissions nécessaires.",
						List.of(),
						HttpStatus.FORBIDDEN.value()
				)
		);
	}

	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ErrorResponse> security(SecurityException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ErrorResponse.of(
						"ACCESS_DENIED",
						"Accès non autorisé",
						ex.getMessage() != null ? ex.getMessage() : "Accès refusé",
						List.of(),
						HttpStatus.FORBIDDEN.value()
				)
		);
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	public ResponseEntity<ErrorResponse> notAuthenticated(AuthenticationCredentialsNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				ErrorResponse.of(
						"NOT_AUTHENTICATED",
						"Authentification requise",
						"Vous devez être connecté.",
						List.of(),
						HttpStatus.UNAUTHORIZED.value()
				)
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
				ErrorResponse.of("INVALID_INPUT", ex.getMessage(), ex.getMessage(), List.of(), 422)
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> generic(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				ErrorResponse.of(
						"INTERNAL_ERROR",
						"Une erreur inattendue est survenue",
						ex.getMessage(),
						List.of("Réessayez ou contactez le support"),
						HttpStatus.INTERNAL_SERVER_ERROR.value()
				)
		);
	}
}

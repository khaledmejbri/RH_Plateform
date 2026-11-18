package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.ErrorResponse;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IdentiteMetierException.class)
	public ResponseEntity<ErrorResponse> handleMetier(IdentiteMetierException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(
				ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getHttpStatus())
		);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ErrorResponse.of("MODIFIE_ENTRE_TEMPS",
						"La ressource vient d'être modifiée par quelqu'un d'autre. Rechargez puis recommencez.",
						HttpStatus.CONFLICT.value())
		);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
		String msg = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
				? ex.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT)
				: "";
		if (msg.contains("uk_societe_code") || msg.contains("societe_code")) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					ErrorResponse.of("SOCIETE_CODE_EXISTANT", "Une société porte déjà ce code.", HttpStatus.CONFLICT.value()));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ErrorResponse.of("CONFLIT_DONNEES", "Conflit d'intégrité des données.", HttpStatus.CONFLICT.value()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				ErrorResponse.of("IDENTIFIANTS_INVALIDES", "Identifiant ou mot de passe incorrect.", HttpStatus.UNAUTHORIZED.value())
		);
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				ErrorResponse.of("IDENTIFIANTS_INVALIDES", "Identifiant ou mot de passe incorrect.", HttpStatus.UNAUTHORIZED.value())
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("CORPS_INVALIDE", "Corps de requête illisible ou valeur non reconnue.", HttpStatus.BAD_REQUEST.value())
		);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("CORPS_INVALIDE", "Paramètre invalide : " + ex.getName(), HttpStatus.BAD_REQUEST.value())
		);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("CORPS_INVALIDE", "Paramètre obligatoire manquant : " + ex.getParameterName(), HttpStatus.BAD_REQUEST.value())
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		StringBuilder details = new StringBuilder();
		ex.getBindingResult().getAllErrors().forEach(err -> {
			String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
			if (!details.isEmpty()) {
				details.append(" ; ");
			}
			details.append(field).append(" : ").append(err.getDefaultMessage());
		});
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				ErrorResponse.of("CORPS_INVALIDE", "Données invalides", details.toString(), List.of(), HttpStatus.BAD_REQUEST.value())
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ErrorResponse.of("ACCESS_DENIED", "Accès non autorisé.", HttpStatus.FORBIDDEN.value())
		);
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotAuthenticated(AuthenticationCredentialsNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				ErrorResponse.of("NOT_AUTHENTICATED", "Authentification requise.", HttpStatus.UNAUTHORIZED.value())
		);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
				ErrorResponse.of("METHODE_NON_SUPPORTEE", "Méthode HTTP non supportée.", HttpStatus.METHOD_NOT_ALLOWED.value())
		);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
				ErrorResponse.of("ROUTE_INCONNUE", "Ressource introuvable.", HttpStatus.NOT_FOUND.value())
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
		int status = ex.getStatusCode().value();
		String message = ex.getReason() != null ? ex.getReason() : "Erreur";
		return ResponseEntity.status(status).body(
				ErrorResponse.of("ERREUR_" + status, message, status)
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ErrorResponse.of("CONFLIT", ex.getMessage(), HttpStatus.CONFLICT.value())
		);
	}
}

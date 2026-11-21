package com.hr.workflow.web;

import com.hr.workflow.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class WorkflowExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WorkflowExceptionHandler.class);

	@ExceptionHandler(WorkflowMetierException.class)
	public ResponseEntity<ErrorResponse> metier(WorkflowMetierException ex, HttpServletRequest req) {
		String path = req != null ? req.getRequestURI() : null;
		if ("DEFINITION_INVALIDE".equals(ex.getCode()) && ex.getErreurs() != null) {
			return ResponseEntity.status(ex.getHttpStatus())
					.body(ErrorResponse.definitionInvalide(ex.getMessage(), path, ex.getErreurs()));
		}
		return ResponseEntity.status(ex.getHttpStatus())
				.body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getMessage(), path, ex.getHttpStatus()));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> conflitDeVersion(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of("MODIFIE_ENTRE_TEMPS",
						"La ressource a été modifiée par un autre utilisateur. Rechargez puis recommencez.",
						"Conflit de version optimiste",
						req != null ? req.getRequestURI() : null,
						HttpStatus.CONFLICT.value()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> conflitIntegrite(DataIntegrityViolationException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of("MODIFIE_ENTRE_TEMPS",
						"La ressource a été modifiée par un autre utilisateur. Rechargez puis recommencez.",
						"Conflit d'intégrité",
						req != null ? req.getRequestURI() : null,
						HttpStatus.CONFLICT.value()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> methodeNonSupportee(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(ErrorResponse.of("METHODE_NON_SUPPORTEE", "Méthode non supportée",
						ex.getMessage(), req != null ? req.getRequestURI() : null, HttpStatus.METHOD_NOT_ALLOWED.value()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> corpsIllisible(HttpMessageNotReadableException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("CORPS_INVALIDE", "Corps de requête JSON illisible ou invalide.",
						"Vérifiez le format JSON et les champs attendus.",
						req != null ? req.getRequestURI() : null, HttpStatus.BAD_REQUEST.value()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> parametreInvalide(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("PARAMETRE_INVALIDE", "Paramètre invalide : " + ex.getName(),
						"Valeur non reconnue pour " + ex.getName(),
						req != null ? req.getRequestURI() : null, HttpStatus.BAD_REQUEST.value()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
		StringBuilder details = new StringBuilder();
		ex.getBindingResult().getAllErrors().forEach(err -> {
			String champ = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
			if (!details.isEmpty()) {
				details.append(" ; ");
			}
			details.append(champ).append(" : ").append(err.getDefaultMessage());
		});
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("CORPS_INVALIDE", "Données d'entrée invalides.",
						details.toString(), req != null ? req.getRequestURI() : null, HttpStatus.BAD_REQUEST.value()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> parametreManquant(MissingServletRequestParameterException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("PARAMETRE_MANQUANT", "Paramètre obligatoire manquant : " + ex.getParameterName(),
						ex.getMessage(), req != null ? req.getRequestURI() : null, HttpStatus.BAD_REQUEST.value()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ErrorResponse.of("ACCES_REFUSE", "Accès non autorisé.",
						"Vous n'avez pas les permissions nécessaires.",
						req != null ? req.getRequestURI() : null, HttpStatus.FORBIDDEN.value()));
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	public ResponseEntity<ErrorResponse> notAuthenticated(AuthenticationCredentialsNotFoundException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ErrorResponse.of("NON_AUTHENTIFIE", "Authentification requise.",
						"Vous devez être connecté.",
						req != null ? req.getRequestURI() : null, HttpStatus.UNAUTHORIZED.value()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> routeInconnue(NoResourceFoundException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("RESSOURCE_INTROUVABLE", "Ressource introuvable.",
						"La ressource demandée n'existe pas.",
						req != null ? req.getRequestURI() : null, HttpStatus.NOT_FOUND.value()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest req) {
		log.error("Erreur interne non gérée sur {}", req != null ? req.getRequestURI() : "?", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("ERREUR_INTERNE", "Une erreur inattendue est survenue.",
						"Réessayez ou contactez le support.",
						req != null ? req.getRequestURI() : null, HttpStatus.INTERNAL_SERVER_ERROR.value()));
	}
}

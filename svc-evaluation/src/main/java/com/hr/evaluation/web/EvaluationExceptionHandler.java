package com.hr.evaluation.web;

import com.hr.evaluation.dto.ErrorResponse;
import org.hibernate.LazyInitializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler for evaluation service.
 * Provides clear, actionable error messages to users.
 */
@RestControllerAdvice
public class EvaluationExceptionHandler {

	@ExceptionHandler(EvaluationMetierException.class)
	public ResponseEntity<ErrorResponse> metier(EvaluationMetierException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(
				ErrorResponse.of(
						ex.getCode(),
						ex.getMessage(),
						ex.getMessage(),
						List.of(),
						ex.getHttpStatus()
				)
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(
				ErrorResponse.of(
						"INVALID_INPUT",
						"Données invalides: " + ex.getMessage(),
						"Vérifiez les paramètres envoyés et réessayez.",
						List.of(
								"Vérifiez que tous les champs obligatoires sont remplis",
								"Assurez-vous que les IDs sont au format UUID valide",
								"Consultez la documentation de l'API pour le format attendu"
						),
						HttpStatus.BAD_REQUEST.value()
				)
		);
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> illegalState(IllegalStateException ex) {
		return ResponseEntity.badRequest().body(
				ErrorResponse.of(
						"INVALID_STATE",
						"Opération non autorisée: " + ex.getMessage(),
						"Cette action ne peut pas être effectuée dans l'état actuel.",
						List.of(
								"Actualisez la page pour voir le dernier état",
								"Vérifiez que l'évaluation existe et est accessible",
								"Contactez le support si le problème persiste"
						),
						HttpStatus.BAD_REQUEST.value()
				)
		);
	}

	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ErrorResponse> securityException(SecurityException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ErrorResponse.of(
						"ACCESS_DENIED",
						"Accès non autorisé",
						ex.getMessage() != null ? ex.getMessage() : "Vous n'avez pas accès à cette ressource.",
						List.of(
								"Vérifiez que cette évaluation vous est assignée",
								"Actualisez la liste des évaluations",
								"Reconnectez-vous si nécessaire"
						),
						HttpStatus.FORBIDDEN.value()
				)
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ErrorResponse.of(
						"ACCESS_DENIED",
						"Accès non autorisé",
						"Vous n'avez pas les permissions nécessaires pour accéder à cette ressource.",
						List.of(
								"Vérifiez que vous êtes bien authentifié",
								"Assurez-vous que cette évaluation vous est assignée",
								"Contactez votre manager pour obtenir l'accès si nécessaire"
						),
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
						"Vous devez être connecté pour accéder à cette fonctionnalité.",
						List.of(
								"Connectez-vous à l'application",
								"Vérifiez que votre session n'a pas expiré",
								"Reconnectez-vous si nécessaire"
						),
						HttpStatus.UNAUTHORIZED.value()
				)
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
		StringBuilder details = new StringBuilder("Champs invalides: ");
		ex.getBindingResult().getAllErrors().forEach(err -> {
			String champ = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
			details.append(champ).append(" - ").append(err.getDefaultMessage()).append("; ");
		});
		
		return ResponseEntity.badRequest().body(
				ErrorResponse.of(
						"VALIDATION_ERROR",
						"Données invalides",
						details.toString(),
						List.of(
								"Vérifiez le format des champs indiqués",
								"Assurez-vous que tous les champs obligatoires sont remplis",
								"Consultez les messages d'erreur pour chaque champ"
						),
						HttpStatus.BAD_REQUEST.value()
				)
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> corpsIllisible(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(
				ErrorResponse.of(
						"CORPS_INVALIDE",
						"Corps de requête illisible",
						"Le JSON envoyé est mal formé ou un champ n'a pas le bon type.",
						List.of(
								"Vérifiez la syntaxe JSON",
								"Vérifiez le type des champs (nombre, texte, date)"
						),
						HttpStatus.BAD_REQUEST.value()
				)
		);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.badRequest().body(
				ErrorResponse.of(
						"MISSING_PARAMETER",
						"Paramètre manquant: " + ex.getParameterName(),
						"Le paramètre '" + ex.getParameterName() + "' est requis.",
						List.of(
								"Ajoutez le paramètre manquant à votre requête",
								"Vérifiez la documentation de l'API"
						),
						HttpStatus.BAD_REQUEST.value()
				)
		);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> resourceNotFound(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
				ErrorResponse.of(
						"RESOURCE_NOT_FOUND",
						"Ressource introuvable",
						"La ressource demandée n'existe pas ou a été supprimée.",
						List.of(
								"Vérifiez que l'ID de l'évaluation est correct",
								"Actualisez la liste des évaluations",
								"L'évaluation a peut-être été supprimée par un administrateur"
						),
						HttpStatus.NOT_FOUND.value()
				)
		);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
				ErrorResponse.of(
						"METHOD_NOT_ALLOWED",
						"Méthode non autorisée: " + ex.getMethod(),
						"Cette méthode HTTP n'est pas supportée pour cet endpoint.",
						List.of(
								"Utilisez la méthode HTTP appropriée (GET, POST, PUT, DELETE)",
								"Consultez la documentation de l'API"
						),
						HttpStatus.METHOD_NOT_ALLOWED.value()
				)
		);
	}

	@ExceptionHandler(LazyInitializationException.class)
	public ResponseEntity<ErrorResponse> lazyInitialization(LazyInitializationException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				ErrorResponse.of(
						"INTERNAL_ERROR",
						"Erreur technique interne",
						"Une erreur technique est survenue lors du chargement des données.",
						List.of(
								"Actualisez la page et réessayez",
								"Si le problème persiste, contactez le support technique"
						),
						HttpStatus.INTERNAL_SERVER_ERROR.value()
				)
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> genericException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				ErrorResponse.of(
						"INTERNAL_ERROR",
						"Une erreur inattendue est survenue",
						"Erreur: " + ex.getMessage(),
						List.of(
								"Actualisez la page et réessayez",
								"Si le problème persiste, contactez le support technique avec le message d'erreur"
						),
						HttpStatus.INTERNAL_SERVER_ERROR.value()
				)
		);
	}
}

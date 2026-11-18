package com.hr.commun.societe;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contexte de société courant par fil d'exécution (ThreadLocal).
 */
public final class SocieteContexte {

	/** Identifiant sentinelle ne correspondant à aucune société réelle. */
	public static final UUID AUCUNE = new UUID(0L, 0L);

	private static final ThreadLocal<UUID> COURANTE = new ThreadLocal<>();

	private SocieteContexte() {
	}

	/**
	 * Renvoie l'identifiant de la société courante ou lève {@link SocieteAbsenteException} si vide ou {@link #AUCUNE}.
	 */
	public static UUID courante() {
		UUID id = COURANTE.get();
		if (id == null || AUCUNE.equals(id)) {
			throw new SocieteAbsenteException();
		}
		return id;
	}

	/**
	 * Renvoie l'identifiant de la société courante, ou {@link Optional#empty()} si absent.
	 */
	public static Optional<UUID> couranteOuVide() {
		return Optional.ofNullable(COURANTE.get());
	}

	/**
	 * Exécute {@code action} avec la société donnée, puis restaure la valeur précédente dans un bloc {@code finally}.
	 */
	public static void executer(UUID societeId, Runnable action) {
		Objects.requireNonNull(action, "action ne doit pas être nulle");
		UUID precedente = COURANTE.get();
		definir(societeId);
		try {
			action.run();
		} finally {
			restaurer(precedente);
		}
	}

	/**
	 * Exécute {@code action} avec la société donnée, puis restaure la valeur précédente dans un bloc {@code finally}.
	 */
	public static <T> T executer(UUID societeId, Supplier<T> action) {
		Objects.requireNonNull(action, "action ne doit pas être nulle");
		UUID precedente = COURANTE.get();
		definir(societeId);
		try {
			return action.get();
		} finally {
			restaurer(precedente);
		}
	}

	/**
	 * Pose la société courante pour le fil d'exécution en cours (utilisé par les filtres HTTP).
	 */
	public static void definir(UUID societeId) {
		if (societeId == null) {
			COURANTE.remove();
		} else {
			COURANTE.set(societeId);
		}
	}

	/**
	 * Efface la société courante du fil d'exécution en cours (en fin de requête).
	 */
	public static void effacer() {
		COURANTE.remove();
	}

	private static void restaurer(UUID precedente) {
		if (precedente == null) {
			COURANTE.remove();
		} else {
			COURANTE.set(precedente);
		}
	}
}

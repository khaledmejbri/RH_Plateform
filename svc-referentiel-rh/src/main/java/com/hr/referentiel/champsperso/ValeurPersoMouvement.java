package com.hr.referentiel.champsperso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Valeur d'un champ « soldes » sur un mouvement d'ajustement (P2-9). Immuable : aucune méthode d'écriture hors insertion
 * (dans la transaction du mouvement), aucun endpoint de modification.
 */
@Entity
@Immutable
@Table(name = "rh_valeur_perso_mouvement",
		uniqueConstraints = @UniqueConstraint(name = "uk_rh_vpm", columnNames = {"mouvement_id", "champ_code"}),
		indexes = @Index(name = "idx_rh_vpm_champ", columnList = "champ_code"))
public class ValeurPersoMouvement {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "mouvement_id", nullable = false, updatable = false)
	private UUID mouvementId;

	@Column(name = "champ_code", nullable = false, length = 40, updatable = false)
	private String champCode;

	@Column(name = "valeur_texte", length = 500, updatable = false)
	private String valeurTexte;

	@Column(name = "valeur_nombre", precision = 15, scale = 4, updatable = false)
	private BigDecimal valeurNombre;

	@Column(name = "valeur_date", updatable = false)
	private LocalDate valeurDate;

	@Column(name = "valeur_liste_code", length = 40, updatable = false)
	private String valeurListeCode;

	protected ValeurPersoMouvement() {
	}
}

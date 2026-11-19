package com.hr.referentiel.champsperso;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Valeur courante d'un champ personnalisé sur une fiche (P2-3). Exactement une colonne typée renseignée ; une valeur
 * vide n'a pas de ligne. Écrite en JDBC (insert / update séparés, portable) ; l'entité porte le schéma et sert au filtre
 * (critères {@code EXISTS}).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_valeur_perso_fiche",
		uniqueConstraints = @UniqueConstraint(name = "uk_rh_valeur_perso_fiche", columnNames = {"societe_id", "collaborateur_id", "champ_code"}),
		indexes = {
				@Index(name = "idx_rh_vpf_liste", columnList = "societe_id, champ_code, valeur_liste_code"),
				@Index(name = "idx_rh_vpf_nombre", columnList = "societe_id, champ_code, valeur_nombre"),
				@Index(name = "idx_rh_vpf_date", columnList = "societe_id, champ_code, valeur_date")})
public class ValeurPersoFiche {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@TenantId
	@Column(name = "societe_id", nullable = false, updatable = false)
	private UUID societeId;

	@Column(name = "collaborateur_id", nullable = false, updatable = false)
	private UUID collaborateurId;

	@Column(name = "champ_code", nullable = false, length = 40, updatable = false)
	private String champCode;

	@Column(name = "valeur_texte", length = 500)
	private String valeurTexte;

	@Column(name = "valeur_nombre", precision = 15, scale = 4)
	private BigDecimal valeurNombre;

	@Column(name = "valeur_date")
	private LocalDate valeurDate;

	@Column(name = "valeur_liste_code", length = 40)
	private String valeurListeCode;

	@Column(name = "texte_recherche", length = 500)
	private String texteRecherche;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	protected ValeurPersoFiche() {
	}
}

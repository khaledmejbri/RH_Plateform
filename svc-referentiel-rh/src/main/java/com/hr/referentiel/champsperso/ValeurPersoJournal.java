package com.hr.referentiel.champsperso;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal des valeurs : ajout seul, texte en clair déjà formaté, jamais purgé (C-L-02, au moins 5 ans). Écrit en JDBC
 * (groupé à l'import) dans la transaction de l'écriture.
 */
@Entity
@Immutable
@Table(name = "rh_valeur_perso_journal", indexes = @Index(name = "idx_rh_vpj_collab", columnList = "collaborateur_id, horodatage"))
public class ValeurPersoJournal {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "collaborateur_id", nullable = false, updatable = false)
	private UUID collaborateurId;

	@Column(name = "champ_code", nullable = false, length = 40, updatable = false)
	private String champCode;

	@Column(name = "champ_libelle", nullable = false, length = 80, updatable = false)
	private String champLibelle;

	@Column(name = "avant", length = 600, updatable = false)
	private String avant;

	@Column(name = "apres", length = 600, updatable = false)
	private String apres;

	@Convert(converter = OrigineValeurPersoConverter.class)
	@Column(name = "origine", nullable = false, length = 12, updatable = false)
	private OrigineValeurPerso origine;

	@Column(name = "import_id", updatable = false)
	private UUID importId;

	@Column(name = "auteur_compte_id", updatable = false)
	private UUID auteurCompteId;

	@Column(name = "auteur_nom", nullable = false, length = 160, updatable = false)
	private String auteurNom;

	@Column(name = "horodatage", nullable = false, updatable = false)
	private Instant horodatage;

	protected ValeurPersoJournal() {
	}
}

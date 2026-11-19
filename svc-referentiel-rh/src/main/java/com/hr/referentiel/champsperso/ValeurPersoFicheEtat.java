package com.hr.referentiel.champsperso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Version optimiste des valeurs d'une fiche (ligne absente = version 0), verrouillée {@code FOR UPDATE} à l'écriture. */
@Entity
@Table(name = "rh_valeur_perso_fiche_etat")
public class ValeurPersoFicheEtat {

	@Id
	@Column(name = "collaborateur_id", nullable = false, updatable = false)
	private UUID collaborateurId;

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Column(name = "modifie_par_nom", nullable = false, length = 160)
	private String modifieParNom;

	protected ValeurPersoFicheEtat() {
	}
}

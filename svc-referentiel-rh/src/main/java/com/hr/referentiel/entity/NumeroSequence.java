package com.hr.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * D-L02 : compteur du numéro lisible, une ligne par préfixe et par mois (clé « DEM-202611 »).
 * La ligne n'est jamais lue puis écrite par le code Java : l'incrément passe par un upsert atomique
 * ({@code NumeroSequenceRepository#incrementer}).
 */
@Entity
@Table(name = "rh_numero_sequence")
public class NumeroSequence {

	@Id
	@Column(name = "cle", nullable = false, updatable = false, length = 24)
	private String cle;

	@Column(name = "dernier_numero", nullable = false)
	private int dernierNumero;

	public NumeroSequence() {
	}

	public String getCle() {
		return cle;
	}

	public int getDernierNumero() {
		return dernierNumero;
	}
}

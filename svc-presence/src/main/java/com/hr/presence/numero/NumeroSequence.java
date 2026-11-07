package com.hr.presence.numero;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Compteur par préfixe et par mois (D-L02) : une ligne « HS-202611 » = dernier rang attribué en novembre 2026.
 * La table n'est écrite que par {@link CompteurNumeroJdbc} (upsert atomique) ; l'entité sert à la créer.
 */
@Entity
@Table(name = "presence_numero_sequence")
public class NumeroSequence {

	@Id
	@Column(name = "cle", nullable = false, updatable = false, length = 32)
	private String cle;

	@Column(name = "dernier", nullable = false)
	private long dernier;

	protected NumeroSequence() {
	}

	public String getCle() { return cle; }

	public long getDernier() { return dernier; }
}

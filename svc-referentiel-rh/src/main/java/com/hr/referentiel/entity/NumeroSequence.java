package com.hr.referentiel.entity;

import com.hr.commun.societe.SocieteGardeEcriture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * D-L02 : compteur du numéro lisible, une ligne par société, préfixe et mois (clé « DEM-202611 »).
 * La ligne n'est jamais lue puis écrite par le code Java : l'incrément passe par un upsert atomique
 * ({@code NumeroSequenceRepository#incrementer}).
 */
@Entity
@EntityListeners(SocieteGardeEcriture.class)
@Table(name = "rh_numero_sequence", uniqueConstraints = {
		@UniqueConstraint(name = "uk_numero_seq_societe_cle", columnNames = {"societe_id", "cle"})
})
public class NumeroSequence {

	@Id
	@Column(name = "cle", nullable = false, updatable = false, length = 24)
	private String cle;

	@TenantId
	@Column(name = "societe_id", updatable = false)
	private UUID societeId;

	@Column(name = "dernier_numero", nullable = false)
	private int dernierNumero;

	public NumeroSequence() {
	}

	public String getCle() {
		return cle;
	}

	public UUID getSocieteId() {
		return societeId;
	}

	public int getDernierNumero() {
		return dernierNumero;
	}
}

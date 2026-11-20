package com.hr.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Journal d'exécution append-only d'une instance (invariant I5).
 */
@Entity
@Table(name = "wf_historique")
@Getter
@Setter
@NoArgsConstructor
public class WfHistorique {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "instance_id", nullable = false)
	private UUID instanceId;

	@Column(name = "tache_id")
	private UUID tacheId;

	@Column(name = "type_evenement", nullable = false, length = 30)
	private String typeEvenement;

	@Column(name = "acteur_compte_id")
	private UUID acteurCompteId;

	@Column(name = "acteur_nom", length = 160)
	private String acteurNom;

	@Column(name = "pour_nom", length = 160)
	private String pourNom;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "details", columnDefinition = "jsonb")
	private Map<String, Object> details;

	@Column(name = "horodatage", nullable = false)
	private Instant horodatage;
}

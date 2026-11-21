package com.hr.workflow.entity;

import com.hr.workflow.domain.StatutInstance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wf_instance")
@Getter
@Setter
@NoArgsConstructor
public class WfInstance {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "modele_version_id", nullable = false)
	private WfModeleVersion modeleVersion;

	@Column(name = "modele_code", nullable = false, length = 40)
	private String modeleCode;

	@Column(name = "modele_version_numero", nullable = false)
	private int modeleVersionNumero;

	@Column(name = "objet_type", nullable = false, length = 40)
	private String objetType;

	@Column(name = "sous_type", length = 40)
	private String sousType;

	@Column(name = "objet_id", nullable = false)
	private UUID objetId;

	@Column(name = "objet_numero", length = 24)
	private String objetNumero;

	@Column(name = "service_proprietaire", nullable = false, length = 40)
	private String serviceProprietaire;

	@Column(name = "demandeur_collaborateur_id", nullable = false)
	private UUID demandeurCollaborateurId;

	@Column(name = "demandeur_compte_id")
	private UUID demandeurCompteId;

	@Column(name = "demandeur_nom", length = 160)
	private String demandeurNom;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 12)
	private StatutInstance statut = StatutInstance.EN_COURS;

	@Column(name = "etape_courante_code", length = 40)
	private String etapeCouranteCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "variables", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> variables = new HashMap<>();

	@Column(name = "cle_demarrage", nullable = false, length = 120, unique = true)
	private String cleDemarrage;

	@Column(name = "demarre_le", nullable = false)
	private Instant demarreLe;

	@Column(name = "termine_le")
	private Instant termineLe;

	@Column(name = "resultat_motif", length = 500)
	private String resultatMotif;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}

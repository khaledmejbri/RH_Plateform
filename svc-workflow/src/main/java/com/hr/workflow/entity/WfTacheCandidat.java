package com.hr.workflow.entity;

import com.hr.workflow.domain.OrigineCandidat;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_tache_candidat")
@Getter
@Setter
@NoArgsConstructor
public class WfTacheCandidat {

	@EmbeddedId
	private WfTacheCandidatId id;

	@Column(name = "compte_id")
	private UUID compteId;

	@Column(name = "nom", length = 160)
	private String nom;

	@Enumerated(EnumType.STRING)
	@Column(name = "origine", nullable = false, length = 14)
	private OrigineCandidat origine = OrigineCandidat.TITULAIRE;

	@Column(name = "pour_collaborateur_id")
	private UUID pourCollaborateurId;

	@Column(name = "pour_nom", length = 160)
	private String pourNom;

	@Column(name = "delegation_id")
	private UUID delegationId;

	@Column(name = "ajoute_le", nullable = false)
	private Instant ajouteLe;

	@Column(name = "retire_le")
	private Instant retireLe;
}

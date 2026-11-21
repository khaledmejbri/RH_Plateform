package com.hr.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wf_modele")
@Getter
@Setter
@NoArgsConstructor
public class WfModele {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "code", nullable = false, length = 40, unique = true)
	private String code;

	@Column(name = "libelle", nullable = false, length = 80)
	private String libelle;

	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "objet_type", nullable = false, length = 40)
	private String objetType;

	@Column(name = "sous_type", length = 40)
	private String sousType;

	@Column(name = "actif", nullable = false)
	private boolean actif = true;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Column(name = "cree_par_compte_id")
	private UUID creeParCompteId;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Column(name = "modifie_par_compte_id")
	private UUID modifieParCompteId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;
}

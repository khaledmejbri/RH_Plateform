package com.hr.identiteacces.entity;

import com.hr.identiteacces.domain.StatutSociete;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "societe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Societe {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "code", nullable = false, updatable = false, length = 12)
	private String code;

	@Column(name = "raison_sociale", nullable = false, length = 120)
	private String raisonSociale;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 12)
	@Builder.Default
	private StatutSociete statut = StatutSociete.ACTIVE;

	@Column(name = "fuseau", nullable = false, length = 40)
	@Builder.Default
	private String fuseau = "Africa/Tunis";

	@Column(name = "cree_le", nullable = false, updatable = false)
	private Instant creeLe;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	@Version
	@Column(name = "version", nullable = false)
	@Builder.Default
	private Long version = 0L;

	@PrePersist
	public void prePersist() {
		Instant now = Instant.now();
		if (creeLe == null) {
			creeLe = now;
		}
		if (modifieLe == null) {
			modifieLe = now;
		}
		if (statut == null) {
			statut = StatutSociete.ACTIVE;
		}
		if (fuseau == null || fuseau.isBlank()) {
			fuseau = "Africa/Tunis";
		}
		if (version == null) {
			version = 0L;
		}
	}

	@PreUpdate
	public void preUpdate() {
		modifieLe = Instant.now();
	}
}

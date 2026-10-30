package com.hr.presence.entity;

import com.hr.presence.domain.StatutReglageFacial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Réglage société de la vérification faciale : ligne unique {@code id = 'SOCIETE'} (absente = INACTIVE / seuil par défaut). */
@Entity
@Table(name = "presence_reglage_facial")
public class PresenceReglageFacial {

	public static final String ID_SOCIETE = "SOCIETE";

	@Id
	@Column(length = 16, nullable = false, updatable = false)
	private String id = ID_SOCIETE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private StatutReglageFacial statut = StatutReglageFacial.INACTIVE;

	@Column(name = "date_mise_en_service")
	private LocalDate dateMiseEnService;

	@Column(name = "seuil_score", nullable = false)
	private int seuilScore;

	@Column(name = "cadre_legal_confirme_par", length = 64)
	private String cadreLegalConfirmePar;

	@Column(name = "cadre_legal_confirme_le")
	private Instant cadreLegalConfirmeLe;

	@Column(name = "modifie_par_compte_id", length = 64)
	private String modifieParCompteId;

	@Column(name = "modifie_le")
	private Instant modifieLe;

	public String getId() { return id; }
	public StatutReglageFacial getStatut() { return statut; }
	public void setStatut(StatutReglageFacial statut) { this.statut = statut; }
	public LocalDate getDateMiseEnService() { return dateMiseEnService; }
	public void setDateMiseEnService(LocalDate dateMiseEnService) { this.dateMiseEnService = dateMiseEnService; }
	public int getSeuilScore() { return seuilScore; }
	public void setSeuilScore(int seuilScore) { this.seuilScore = seuilScore; }
	public String getCadreLegalConfirmePar() { return cadreLegalConfirmePar; }
	public void setCadreLegalConfirmePar(String cadreLegalConfirmePar) { this.cadreLegalConfirmePar = cadreLegalConfirmePar; }
	public Instant getCadreLegalConfirmeLe() { return cadreLegalConfirmeLe; }
	public void setCadreLegalConfirmeLe(Instant cadreLegalConfirmeLe) { this.cadreLegalConfirmeLe = cadreLegalConfirmeLe; }
	public String getModifieParCompteId() { return modifieParCompteId; }
	public void setModifieParCompteId(String modifieParCompteId) { this.modifieParCompteId = modifieParCompteId; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}

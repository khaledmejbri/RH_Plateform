package com.hr.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Paramètres des évaluations propres à la société (ligne unique, identifiant 1) — SCRUM-41.
 */
@Entity
@Table(name = "evaluation_parametres")
public class EvaluationParametres {

    public static final long IDENTIFIANT_UNIQUE = 1L;

    @Id
    private Long id = IDENTIFIANT_UNIQUE;

    /** Durée d'une campagne en jours, pré-remplie à la création (modifiable par campagne). */
    @Column(name = "duree_campagne_jours", nullable = false)
    private int dureeCampagneJours;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "modifie_par")
    private UUID modifiePar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getDureeCampagneJours() { return dureeCampagneJours; }
    public void setDureeCampagneJours(int dureeCampagneJours) { this.dureeCampagneJours = dureeCampagneJours; }

    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }

    public UUID getModifiePar() { return modifiePar; }
    public void setModifiePar(UUID modifiePar) { this.modifiePar = modifiePar; }
}

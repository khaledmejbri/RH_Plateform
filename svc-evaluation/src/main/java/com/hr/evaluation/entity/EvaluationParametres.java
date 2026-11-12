package com.hr.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    /** Note (1 à 5) en dessous de laquelle un critère est insuffisant ; null lu comme 3 (D-R06, ADR lot 3 § 2). */
    @Column(name = "seuil_insuffisance")
    private Integer seuilInsuffisance;

    /** Nullable : ajoutée à une table existante, remplie par le runner d'amorçage (ADR lot 3 § 8). */
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "modifie_par")
    private UUID modifiePar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getDureeCampagneJours() { return dureeCampagneJours; }
    public void setDureeCampagneJours(int dureeCampagneJours) { this.dureeCampagneJours = dureeCampagneJours; }

    public Integer getSeuilInsuffisance() { return seuilInsuffisance; }
    public void setSeuilInsuffisance(Integer seuilInsuffisance) { this.seuilInsuffisance = seuilInsuffisance; }

    public Long getVersion() { return version; }

    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }

    public UUID getModifiePar() { return modifiePar; }
    public void setModifiePar(UUID modifiePar) { this.modifiePar = modifiePar; }
}

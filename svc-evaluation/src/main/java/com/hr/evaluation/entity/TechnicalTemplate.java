package com.hr.evaluation.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "technical_template")
public class TechnicalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nom", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "niveau_seniorite", length = 50)
    private String niveauSeniorite; // JUNIOR, MID, SENIOR, EXPERT

    @Column(name = "role_metier", length = 100)
    private String roleMetier; // DEVELOPPEUR, ARCHITECTE, DEVOPS, etc.

    @Column(name = "departement", length = 100)
    private String departement;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "cree_par")
    private UUID creePar;

    @PrePersist
    public void prePersist() {
        if (creeLe == null) {
            creeLe = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        modifieLe = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getNom() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getNiveauSeniorite() { return niveauSeniorite; }
    public void setNiveauSeniorite(String niveauSeniorite) { this.niveauSeniorite = niveauSeniorite; }
    
    public String getRoleMetier() { return roleMetier; }
    public void setRoleMetier(String roleMetier) { this.roleMetier = roleMetier; }
    
    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    
    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }
}

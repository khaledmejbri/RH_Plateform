package com.hr.evaluation.entity;

import com.hr.evaluation.domain.NatureParametre;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Valeur d'un référentiel paramétrable de svc-evaluation (ADR lot 3 § 2). Le code est immuable ; le libellé,
 * l'habillage et l'ordre se modifient. {@code premiereUtilisationLe} est la marque collante « déjà utilisée » : posée
 * une fois par un UPDATE ciblé, jamais retirée.
 */
@Entity
@Table(name = "evaluation_parametre_valeur",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_eval_param_ref_code", columnNames = {"referentiel", "code"}),
                @UniqueConstraint(name = "uk_eval_param_ref_libelle", columnNames = {"referentiel", "libelle_normalise"})})
public class ParametreValeur {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "referentiel", nullable = false, updatable = false, length = 40)
    private String referentiel;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, updatable = false, length = 12)
    private NatureParametre nature;

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "libelle_normalise", nullable = false, length = 80)
    private String libelleNormalise;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "couleur", length = 16)
    private String couleur;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "systeme", nullable = false)
    private boolean systeme;

    @Column(name = "premiere_utilisation_le")
    private Instant premiereUtilisationLe;

    /** Niveaux d'alerte uniquement : écart minimal, en étoiles (1 à 4). */
    @Column(name = "seuil_ecart")
    private Integer seuilEcart;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "modifie_par_compte_id")
    private UUID modifieParCompteId;

    public UUID getId() { return id; }
    public String getReferentiel() { return referentiel; }
    public void setReferentiel(String referentiel) { this.referentiel = referentiel; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public NatureParametre getNature() { return nature; }
    public void setNature(NatureParametre nature) { this.nature = nature; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getLibelleNormalise() { return libelleNormalise; }
    public void setLibelleNormalise(String libelleNormalise) { this.libelleNormalise = libelleNormalise; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCouleur() { return couleur; }
    public void setCouleur(String couleur) { this.couleur = couleur; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public boolean isSysteme() { return systeme; }
    public void setSysteme(boolean systeme) { this.systeme = systeme; }
    public Instant getPremiereUtilisationLe() { return premiereUtilisationLe; }
    public void setPremiereUtilisationLe(Instant premiereUtilisationLe) { this.premiereUtilisationLe = premiereUtilisationLe; }
    public Integer getSeuilEcart() { return seuilEcart; }
    public void setSeuilEcart(Integer seuilEcart) { this.seuilEcart = seuilEcart; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreeLe() { return creeLe; }
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    public UUID getModifieParCompteId() { return modifieParCompteId; }
    public void setModifieParCompteId(UUID modifieParCompteId) { this.modifieParCompteId = modifieParCompteId; }
}

package com.hr.evaluation.entity;

import com.hr.evaluation.domain.ActionJournalParametre;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal des modifications de paramètres (D-R02.g, C-L-02). Ajout seul : entité immuable, colonnes non modifiables,
 * aucun endpoint d'écriture ni de suppression, conservation sans purge (ADR lot 3 § 2).
 */
@Entity
@Immutable
@Table(name = "evaluation_parametre_journal",
        indexes = @Index(name = "idx_eval_param_journal_ref", columnList = "referentiel, horodatage"))
public class ParametreJournal {

    /** Auteur des écritures faites par l'amorçage. */
    public static final String AUTEUR_SYSTEME = "Système (automatique)";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "referentiel", nullable = false, updatable = false, length = 40)
    private String referentiel;

    @Column(name = "code", updatable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 20)
    private ActionJournalParametre action;

    @Column(name = "avant_json", updatable = false, columnDefinition = "text")
    private String avantJson;

    @Column(name = "apres_json", updatable = false, columnDefinition = "text")
    private String apresJson;

    @Column(name = "auteur_compte_id", updatable = false)
    private UUID auteurCompteId;

    @Column(name = "auteur_nom", updatable = false, length = 200)
    private String auteurNom;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage = Instant.now();

    protected ParametreJournal() {
    }

    public ParametreJournal(String referentiel, String code, ActionJournalParametre action, String avantJson,
                            String apresJson, UUID auteurCompteId, String auteurNom) {
        this.referentiel = referentiel;
        this.code = code;
        this.action = action;
        this.avantJson = avantJson;
        this.apresJson = apresJson;
        this.auteurCompteId = auteurCompteId;
        this.auteurNom = auteurNom;
    }

    public UUID getId() { return id; }
    public String getReferentiel() { return referentiel; }
    public String getCode() { return code; }
    public ActionJournalParametre getAction() { return action; }
    public String getAvantJson() { return avantJson; }
    public String getApresJson() { return apresJson; }
    public UUID getAuteurCompteId() { return auteurCompteId; }
    public String getAuteurNom() { return auteurNom; }
    public Instant getHorodatage() { return horodatage; }
}

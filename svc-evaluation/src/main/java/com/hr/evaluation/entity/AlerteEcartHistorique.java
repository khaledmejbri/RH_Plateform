package com.hr.evaluation.entity;

import com.hr.evaluation.domain.ConclusionAlerteEcart;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement de l'historique d'une alerte d'écart (D-E08.f). Ajout seul : entité immuable, colonnes non modifiables,
 * aucun endpoint de modification ni de suppression. Conservé 5 ans (C-L-02, purge hors lot).
 */
@Entity
@Immutable
@Table(name = "evaluation_alerte_ecart_historique",
        indexes = @Index(name = "idx_alerte_ecart_historique_alerte", columnList = "alerte_identifiant, cree_le"))
public class AlerteEcartHistorique {

    /** Libellé d'auteur des événements automatiques. */
    public static final String AUTEUR_SYSTEME = "SYSTEME";

    /** Libellé exposé pour un acteur automatique (D-L01.e) ; le code stocké reste {@link #AUTEUR_SYSTEME}. */
    public static final String LIBELLE_AUTEUR_SYSTEME = "Système (automatique)";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alerte_identifiant", nullable = false, updatable = false)
    private AlerteEcart alerte;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", nullable = false, updatable = false, length = 30)
    private EvenementAlerteEcart typeEvenement;

    /** Compte de l'auteur (JWT) ; {@code null} = SYSTEME. */
    @Column(name = "auteur_compte_id", updatable = false)
    private UUID auteurCompteId;

    @Column(name = "auteur_libelle", updatable = false, length = 200)
    private String auteurLibelle;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_avant", updatable = false, length = 20)
    private StatutAlerteEcart statutAvant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_apres", updatable = false, length = 20)
    private StatutAlerteEcart statutApres;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_avant", updatable = false, length = 20)
    private NiveauEcart niveauAvant;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_apres", updatable = false, length = 20)
    private NiveauEcart niveauApres;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", updatable = false, length = 40)
    private ConclusionAlerteEcart conclusion;

    @Column(name = "commentaire", updatable = false, length = 1000)
    private String commentaire;

    /** Événement automatique (auteur SYSTEME) : création, recalcul, réouverture. */
    public static AlerteEcartHistorique systeme(EvenementAlerteEcart type, StatutAlerteEcart statutAvant,
            StatutAlerteEcart statutApres, NiveauEcart niveauAvant, NiveauEcart niveauApres) {
        AlerteEcartHistorique h = new AlerteEcartHistorique();
        h.typeEvenement = type;
        h.auteurCompteId = null;
        h.auteurLibelle = AUTEUR_SYSTEME;
        h.creeLe = Instant.now();
        h.statutAvant = statutAvant;
        h.statutApres = statutApres;
        h.niveauAvant = niveauAvant;
        h.niveauApres = niveauApres;
        return h;
    }

    /** Action d'un utilisateur RH / ADMIN : prise en charge ou clôture (auteur lu dans le JWT). */
    public static AlerteEcartHistorique par(EvenementAlerteEcart type, UUID auteurCompteId, String auteurLibelle,
            StatutAlerteEcart statutAvant, StatutAlerteEcart statutApres, NiveauEcart niveau,
            ConclusionAlerteEcart conclusion, String commentaire) {
        AlerteEcartHistorique h = new AlerteEcartHistorique();
        h.typeEvenement = type;
        h.auteurCompteId = auteurCompteId;
        h.auteurLibelle = auteurLibelle;
        h.creeLe = Instant.now();
        h.statutAvant = statutAvant;
        h.statutApres = statutApres;
        h.niveauAvant = niveau;
        h.niveauApres = niveau;
        h.conclusion = conclusion;
        h.commentaire = commentaire;
        return h;
    }

    public UUID getId() { return id; }
    public AlerteEcart getAlerte() { return alerte; }
    void setAlerte(AlerteEcart alerte) { this.alerte = alerte; }
    public EvenementAlerteEcart getTypeEvenement() { return typeEvenement; }
    public UUID getAuteurCompteId() { return auteurCompteId; }
    public String getAuteurLibelle() { return auteurLibelle; }
    public Instant getCreeLe() { return creeLe; }
    public StatutAlerteEcart getStatutAvant() { return statutAvant; }
    public StatutAlerteEcart getStatutApres() { return statutApres; }
    public NiveauEcart getNiveauAvant() { return niveauAvant; }
    public NiveauEcart getNiveauApres() { return niveauApres; }
    public ConclusionAlerteEcart getConclusion() { return conclusion; }
    public String getCommentaire() { return commentaire; }
}

package com.hr.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification conservée, une ligne par compte destinataire (D-NOT01). Texte libre sans enum ni CHECK :
 * la liste des types d'objet évolue côté producteurs sans migration.
 */
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_evenement_compte",
                columnNames = {"notification_id", "compte_id"}),
        indexes = @Index(name = "idx_notification_compte_lu_recu", columnList = "compte_id, lu_le, recu_le"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private UUID id;

    /** Identifiant d'événement du producteur : sert à l'idempotence. */
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "compte_id", nullable = false)
    private UUID compteId;

    @Column(length = 200)
    private String sujet;

    @Column(length = 2000)
    private String contenu;

    @Column(length = 60)
    private String evenement;

    @Column(name = "objet_type", length = 30)
    private String objetType;

    @Column(name = "objet_id")
    private UUID objetId;

    @Column(name = "objet_numero", length = 24)
    private String objetNumero;

    @Column(name = "emis_le")
    private Instant emisLe;

    @Column(name = "recu_le", nullable = false)
    private Instant recuLe;

    /** Null = non lue. */
    @Column(name = "lu_le")
    private Instant luLe;
}

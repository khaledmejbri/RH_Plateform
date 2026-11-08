package com.hr.notification.service;

import com.hr.notification.dto.CompteurNotifications;
import com.hr.notification.dto.NotificationDto;
import com.hr.notification.dto.NotificationMessage;
import com.hr.notification.dto.PageNotifications;
import com.hr.notification.entity.Notification;
import com.hr.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Conservation, lecture et purge des notifications (D-NOT01). Toute lecture est bornée au compte de l'appelant. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationStockageService {

    static final int TAILLE_MAX = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    @Value("${notification.retention-jours:180}")
    private int retentionJours;

    @Value("${notification.purge.taille-lot:1000}")
    private int tailleLotPurge;

    /**
     * Enregistre la notification d'un compte. Vide si le même événement a déjà été enregistré pour ce compte
     * (rejeu Kafka) : l'appelant ne renvoie alors rien en temps réel.
     */
    @Transactional
    public Optional<NotificationDto> enregistrer(NotificationMessage message, UUID compteId) {
        UUID evenementId = lireUuid(message.getNotificationId()).orElseGet(UUID::randomUUID);
        if (repository.existsByNotificationIdAndCompteId(evenementId, compteId)) {
            log.info("Notification {} déjà enregistrée pour le compte {} : ignorée", evenementId, compteId);
            return Optional.empty();
        }
        Instant maintenant = clock.instant();
        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .notificationId(evenementId)
                .compteId(compteId)
                .sujet(tronquer(message.getSubject(), 200))
                .contenu(tronquer(message.getContent(), 2000))
                .evenement(tronquer(message.getEvenement(), 60))
                .objetType(tronquer(message.getObjetType(), 30))
                .objetId(lireUuid(message.getObjetId()).orElse(null))
                .objetNumero(tronquer(message.getObjetNumero(), 24))
                .emisLe(lireInstant(message.getEmisLe()).orElse(maintenant))
                .recuLe(maintenant)
                .build();
        return Optional.of(versDto(repository.saveAndFlush(n)));
    }

    @Transactional(readOnly = true)
    public PageNotifications lister(UUID compteId, Boolean lu, int page, int taille) {
        if (page < 0 || taille < 1 || taille > TAILLE_MAX) {
            throw new ParametreInvalideException("Pagination invalide : page >= 0 et taille entre 1 et " + TAILLE_MAX);
        }
        PageRequest pageable = PageRequest.of(page, taille,
                Sort.by(Sort.Direction.DESC, "recuLe").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Notification> resultat;
        if (lu == null) {
            resultat = repository.findByCompteId(compteId, pageable);
        } else if (lu) {
            resultat = repository.findByCompteIdAndLuLeIsNotNull(compteId, pageable);
        } else {
            resultat = repository.findByCompteIdAndLuLeIsNull(compteId, pageable);
        }
        List<NotificationDto> contenu = resultat.getContent().stream().map(NotificationStockageService::versDto).toList();
        return new PageNotifications(contenu, page, taille, resultat.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CompteurNotifications compter(UUID compteId) {
        return new CompteurNotifications(repository.countByCompteIdAndLuLeIsNull(compteId));
    }

    /** Idempotent : une notification déjà lue garde sa date de lecture. Autre compte ou inconnue : 404. */
    @Transactional
    public void marquerLue(UUID id, UUID compteId) {
        Notification n = repository.findByIdAndCompteId(id, compteId)
                .orElseThrow(NotificationIntrouvableException::new);
        if (n.getLuLe() == null) {
            n.setLuLe(clock.instant());
            repository.save(n);
        }
    }

    /** Marque lues les non lues reçues jusqu'à l'instant de la demande ; celles arrivées ensuite restent non lues. */
    @Transactional
    public int marquerToutesLues(UUID compteId) {
        Instant maintenant = clock.instant();
        return repository.marquerToutesLues(compteId, maintenant, maintenant);
    }

    /** Supprime les notifications reçues il y a plus de {@code retention-jours}, par lots. Retourne le total supprimé. */
    public int purger() {
        Instant limite = clock.instant().minus(Duration.ofDays(retentionJours));
        int total = 0;
        while (true) {
            int supprimees = purgerUnLot(limite);
            total += supprimees;
            if (supprimees < tailleLotPurge) {
                break;
            }
        }
        log.info("Purge des notifications : {} supprimée(s) (antérieures à {})", total, limite);
        return total;
    }

    private int purgerUnLot(Instant limite) {
        List<UUID> ids = repository.idsAnterieursA(limite, PageRequest.of(0, tailleLotPurge));
        if (!ids.isEmpty()) {
            repository.deleteAllByIdInBatch(ids);
        }
        return ids.size();
    }

    static NotificationDto versDto(Notification n) {
        return new NotificationDto(n.getId(), n.getSujet(), n.getContenu(), n.getEvenement(), n.getObjetType(),
                n.getObjetId(), n.getObjetNumero(), n.getEmisLe(), n.getRecuLe(), n.getLuLe() != null,
                "WEBSOCKET", n.getCompteId().toString(), n.getSujet(), n.getContenu());
    }

    private static Optional<UUID> lireUuid(String brut) {
        if (brut == null || brut.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(brut.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> lireInstant(String brut) {
        if (brut == null || brut.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(brut.trim()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String tronquer(String valeur, int max) {
        if (valeur == null) {
            return null;
        }
        return valeur.length() <= max ? valeur : valeur.substring(0, max);
    }
}

package com.hr.notification.repository;

import com.hr.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByNotificationIdAndCompteId(UUID notificationId, UUID compteId);

    Page<Notification> findByCompteId(UUID compteId, Pageable pageable);

    Page<Notification> findByCompteIdAndLuLeIsNull(UUID compteId, Pageable pageable);

    Page<Notification> findByCompteIdAndLuLeIsNotNull(UUID compteId, Pageable pageable);

    /** Anti-IDOR : l'identifiant seul ne suffit jamais, le compte fait partie de la clé de lecture. */
    Optional<Notification> findByIdAndCompteId(UUID id, UUID compteId);

    long countByCompteIdAndLuLeIsNull(UUID compteId);

    @Modifying
    @Query("update Notification n set n.luLe = :maintenant "
            + "where n.compteId = :compteId and n.luLe is null and n.recuLe <= :jusqua")
    int marquerToutesLues(@Param("compteId") UUID compteId, @Param("maintenant") Instant maintenant,
                          @Param("jusqua") Instant jusqua);

    @Query("select n.id from Notification n where n.recuLe < :limite order by n.recuLe")
    List<UUID> idsAnterieursA(@Param("limite") Instant limite, Pageable pageable);
}

package com.hr.presence.repository;

import com.hr.presence.entity.PresenceAuditFacial;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Audit en ajout seul : volontairement sans {@code delete} ni mise à jour (hors {@code save} d'une nouvelle trace). */
public interface AuditFacialRepository extends Repository<PresenceAuditFacial, UUID> {

	PresenceAuditFacial save(PresenceAuditFacial trace);

	List<PresenceAuditFacial> findByEnrolementIdOrderByHorodatageDesc(UUID enrolementId);
}

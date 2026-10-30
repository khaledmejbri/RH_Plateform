package com.hr.presence.repository;

import com.hr.presence.entity.PresenceReglageFacial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglageFacialRepository extends JpaRepository<PresenceReglageFacial, String> {
}

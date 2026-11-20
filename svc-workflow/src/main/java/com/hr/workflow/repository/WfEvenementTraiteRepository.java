package com.hr.workflow.repository;

import com.hr.workflow.entity.WfEvenementTraite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WfEvenementTraiteRepository extends JpaRepository<WfEvenementTraite, UUID> {
}

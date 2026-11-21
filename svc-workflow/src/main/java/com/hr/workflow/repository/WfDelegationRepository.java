package com.hr.workflow.repository;

import com.hr.workflow.entity.WfDelegation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository de la table {@code wf_delegation} (créée en W3, exploitée en W4, D-W3-04). */
public interface WfDelegationRepository extends JpaRepository<WfDelegation, UUID> {
}

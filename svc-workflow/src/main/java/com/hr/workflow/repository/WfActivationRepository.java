package com.hr.workflow.repository;

import com.hr.workflow.entity.WfActivation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfActivationRepository extends JpaRepository<WfActivation, UUID> {

	Optional<WfActivation> findByObjetType(String objetType);

	List<WfActivation> findAllByOrderByObjetTypeAsc();
}

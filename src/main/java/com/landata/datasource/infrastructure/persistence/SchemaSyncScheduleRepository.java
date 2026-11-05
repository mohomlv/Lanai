package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaSyncScheduleRepository extends JpaRepository<SchemaSyncSchedule, UUID> {
}

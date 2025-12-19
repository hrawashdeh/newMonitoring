package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceDatabaseRepository extends JpaRepository<SourceDatabase, Long> {
  Optional<SourceDatabase> findByDbCode(String dbCode);
  boolean existsByDbCode(String dbCode);
}

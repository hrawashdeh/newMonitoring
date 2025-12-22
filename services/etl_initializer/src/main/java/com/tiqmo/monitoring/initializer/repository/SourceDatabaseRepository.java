package com.tiqmo.monitoring.initializer.repository;

import com.tiqmo.monitoring.initializer.domain.SourceDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceDatabaseRepository extends JpaRepository<SourceDatabase, Long> {
    Optional<SourceDatabase> findByDbCode(String dbCode);
}

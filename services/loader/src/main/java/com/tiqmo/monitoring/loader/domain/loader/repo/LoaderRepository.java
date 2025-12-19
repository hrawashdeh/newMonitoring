package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoaderRepository extends JpaRepository<Loader, Long> {
    Optional<Loader> findByLoaderCode(String loaderCode);
    boolean existsByLoaderCode(String loaderCode);

    /**
     * Find all enabled loaders for scheduling with source database eagerly fetched.
     * Used by LoaderSchedulerService to find loaders ready for execution.
     *
     * @return list of enabled loaders with source databases loaded
     */
    @Query("SELECT l FROM Loader l JOIN FETCH l.sourceDatabase WHERE l.enabled = true")
    List<Loader> findAllByEnabledTrue();
}


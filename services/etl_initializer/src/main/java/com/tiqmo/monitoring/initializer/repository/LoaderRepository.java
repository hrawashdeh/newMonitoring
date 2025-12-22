package com.tiqmo.monitoring.initializer.repository;

import com.tiqmo.monitoring.initializer.domain.Loader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoaderRepository extends JpaRepository<Loader, Long> {
    Optional<Loader> findByLoaderCode(String loaderCode);
}

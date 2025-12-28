package com.tiqmo.monitoring.auth.repository;

import com.tiqmo.monitoring.auth.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
}
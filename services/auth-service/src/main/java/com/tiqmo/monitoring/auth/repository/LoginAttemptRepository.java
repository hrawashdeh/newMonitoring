package com.tiqmo.monitoring.auth.repository;

import com.tiqmo.monitoring.auth.domain.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Page<LoginAttempt> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    Page<LoginAttempt> findByUsernameOrderByAttemptedAtDesc(String username, Pageable pageable);

    Page<LoginAttempt> findBySuccessOrderByAttemptedAtDesc(Boolean success, Pageable pageable);

    List<LoginAttempt> findByUsernameAndAttemptedAtAfter(String username, LocalDateTime after);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.success = true AND la.attemptedAt >= :since")
    long countSuccessfulLoginsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.success = false AND la.attemptedAt >= :since")
    long countFailedLoginsSince(@Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT la.username FROM LoginAttempt la WHERE la.success = false AND la.attemptedAt >= :since GROUP BY la.username HAVING COUNT(*) >= :threshold")
    List<String> findUsersWithMultipleFailedAttempts(@Param("since") LocalDateTime since, @Param("threshold") long threshold);
}
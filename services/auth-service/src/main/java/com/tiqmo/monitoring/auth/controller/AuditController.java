package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.domain.LoginAttempt;
import com.tiqmo.monitoring.auth.infra.config.ApiKey;
import com.tiqmo.monitoring.auth.repository.LoginAttemptRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:4200"})
public class AuditController {

    private final LoginAttemptRepository loginAttemptRepository;

    @GetMapping("/login-attempts")
    @ApiKey(value = "auth.audit.loginAttempts", description = "Get login attempts with pagination")
    public ResponseEntity<PagedResponse<LoginAttemptDTO>> getLoginAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<LoginAttempt> attempts;

        if (username != null && !username.isEmpty()) {
            attempts = loginAttemptRepository.findByUsernameOrderByAttemptedAtDesc(username, pageable);
        } else if (success != null) {
            attempts = loginAttemptRepository.findBySuccessOrderByAttemptedAtDesc(success, pageable);
        } else {
            attempts = loginAttemptRepository.findAllByOrderByAttemptedAtDesc(pageable);
        }

        List<LoginAttemptDTO> dtos = attempts.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        PagedResponse<LoginAttemptDTO> response = PagedResponse.<LoginAttemptDTO>builder()
                .content(dtos)
                .page(attempts.getNumber())
                .size(attempts.getSize())
                .totalElements(attempts.getTotalElements())
                .totalPages(attempts.getTotalPages())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @ApiKey(value = "auth.audit.stats", description = "Get audit statistics")
    public ResponseEntity<AuditStats> getAuditStats() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);

        long successLast24h = loginAttemptRepository.countSuccessfulLoginsSince(last24Hours);
        long failedLast24h = loginAttemptRepository.countFailedLoginsSince(last24Hours);
        long successLast7d = loginAttemptRepository.countSuccessfulLoginsSince(last7Days);
        long failedLast7d = loginAttemptRepository.countFailedLoginsSince(last7Days);

        List<String> suspiciousUsers = loginAttemptRepository.findUsersWithMultipleFailedAttempts(last24Hours, 5);

        AuditStats stats = AuditStats.builder()
                .successfulLoginsLast24h(successLast24h)
                .failedLoginsLast24h(failedLast24h)
                .successfulLoginsLast7d(successLast7d)
                .failedLoginsLast7d(failedLast7d)
                .totalAttempts(loginAttemptRepository.count())
                .suspiciousUsers(suspiciousUsers)
                .build();

        return ResponseEntity.ok(stats);
    }

    private LoginAttemptDTO toDTO(LoginAttempt attempt) {
        return LoginAttemptDTO.builder()
                .id(attempt.getId())
                .username(attempt.getUsername())
                .ipAddress(attempt.getIpAddress())
                .userAgent(attempt.getUserAgent())
                .success(attempt.getSuccess())
                .failureReason(attempt.getFailureReason())
                .attemptedAt(attempt.getAttemptedAt() != null ? attempt.getAttemptedAt().toString() : null)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginAttemptDTO {
        private Long id;
        private String username;
        private String ipAddress;
        private String userAgent;
        private Boolean success;
        private String failureReason;
        private String attemptedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditStats {
        private long successfulLoginsLast24h;
        private long failedLoginsLast24h;
        private long successfulLoginsLast7d;
        private long failedLoginsLast7d;
        private long totalAttempts;
        private List<String> suspiciousUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}

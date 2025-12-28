package com.tiqmo.monitoring.loader.dto.loader;

import lombok.*;

/**
 * Source Database DTO for frontend display.
 * Password is intentionally excluded for security.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceDatabaseDto {
    private Long id;
    private String dbCode;
    private String ip;
    private Integer port;
    private String dbName;
    private String dbType; // MYSQL | POSTGRESQL
    private String userName;
    // Note: passWord is intentionally excluded for security
}

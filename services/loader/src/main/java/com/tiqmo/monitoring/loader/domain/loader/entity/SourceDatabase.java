package com.tiqmo.monitoring.loader.domain.loader.entity;

import com.tiqmo.monitoring.loader.infra.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "source_databases", schema = "loader",
       uniqueConstraints = @UniqueConstraint(name = "uq_source_db_code", columnNames = "db_code"))
public class SourceDatabase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "db_code", nullable = false, length = 64)
  private String dbCode;

  @Column(name = "ip", nullable = false, length = 128)
  private String ip;

  @Column(name = "port", nullable = false)
  private Integer port = 3306;          // default for MySQL

  @Column(name = "db_name", length = 128)
  private String dbName;                // e.g., k8s_wallyt_cms_db

  @Enumerated(EnumType.STRING)
  @Column(name = "db_type", nullable = false, length = 16)
  private DbType dbType;                // MYSQL | POSTGRESQL (future-proof)

  @Column(name = "user_name", nullable = false, length = 128)
  private String userName;

  /**
   * Encrypted database password (AES-256-GCM).
   * Automatically encrypted before save and decrypted after load.
   * Supports Arabic characters and all Unicode (UTF-8).
   */
  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "pass_word", nullable = false, length = 512)
  private String passWord;

  public enum DbType { MYSQL, POSTGRESQL }
}

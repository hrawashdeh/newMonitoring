package com.tiqmo.monitoring.loader.infra.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for transparent encryption/decryption of String fields.
 *
 * <p>Automatically encrypts entity fields before INSERT/UPDATE and decrypts
 * after SELECT. Apply to entity fields with {@code @Convert} annotation:
 *
 * <pre>
 * {@code @Convert(converter = EncryptedStringConverter.class)}
 * {@code @Column(name = "password", length = 512)}
 * private String password;
 * </pre>
 *
 * <p><b>Usage Notes:</b>
 * <ul>
 *   <li>Increase column length to accommodate encrypted data (recommend 512+ for VARCHAR)</li>
 *   <li>Encrypted data is larger than plaintext due to IV + auth tag + Base64 encoding</li>
 *   <li>Works with UTF-8 (Arabic, Chinese, emoji supported)</li>
 *   <li>Null values remain null (not encrypted)</li>
 * </ul>
 *
 * <p><b>Security:</b> Uses {@link EncryptionService} with AES-256-GCM.
 * Decrypted values are never logged to prevent exposure in application logs.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 * @see EncryptionService
 */
@Slf4j
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private final EncryptionService encryptionService;

  /**
   * Encrypts plaintext before saving to database.
   *
   * @param plaintext The entity field value (e.g., password, SQL query)
   * @return Base64-encoded encrypted data for database storage
   */
  @Override
  public String convertToDatabaseColumn(String plaintext) {
    if (plaintext == null) {
      return null;
    }

    try {
      String encrypted = encryptionService.encrypt(plaintext);
      log.debug("Encrypted field for database storage (length: {} -> {})",
                plaintext.length(), encrypted.length());
      return encrypted;
    } catch (EncryptionException e) {
      log.error("Failed to encrypt field during database write", e);
      throw e; // Fail-fast: prevent saving unencrypted data
    }
  }

  /**
   * Decrypts ciphertext after loading from database.
   *
   * @param ciphertext Base64-encoded encrypted data from database
   * @return Decrypted plaintext (original value)
   */
  @Override
  public String convertToEntityAttribute(String ciphertext) {
    if (ciphertext == null) {
      return null;
    }

    try {
      String decrypted = encryptionService.decrypt(ciphertext);
      // SECURITY: Never log decrypted values
      log.debug("Decrypted field from database (encrypted length: {})", ciphertext.length());
      return decrypted;
    } catch (EncryptionException e) {
      log.error("Failed to decrypt field during database read - possible causes: wrong encryption key, corrupted data", e);
      throw e; // Fail-fast: alert if decryption fails
    }
  }
}
